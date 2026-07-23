package uz.umar;

import com.intellij.psi.*;
import java.time.LocalDate;
import java.util.*;
import java.util.regex.*;

public class OpenApiGenerator {

    public static String generate(PsiFile file) {
        List<EndpointModel> endpoints = SpringControllerParser.parse(file);
        String controllerName = file.getVirtualFile().getNameWithoutExtension();
        String baseUrl = BaseUrlResolver.resolve(file.getProject());

        // Collect schemas for all request body and response types
        Map<String, PsiClass> schemas = new LinkedHashMap<>();
        for (EndpointModel ep : endpoints) {
            if (ep.requestBodyPsiType != null) collectSchemas(ep.requestBodyPsiType, schemas);
            if (ep.responsePsiType != null) {
                PsiType payload = ResponseTypeResolver.isArray(ep.responsePsiType)
                        ? ResponseTypeResolver.element(ep.responsePsiType)
                        : ResponseTypeResolver.unwrap(ep.responsePsiType);
                if (payload != null) collectSchemas(payload, schemas);
            }
        }

        // Group endpoints by path to merge methods under one path entry
        Map<String, List<EndpointModel>> byPath = new LinkedHashMap<>();
        for (EndpointModel ep : endpoints) {
            byPath.computeIfAbsent(ep.path, k -> new ArrayList<>()).add(ep);
        }

        StringBuilder sb = new StringBuilder();
        sb.append("openapi: 3.0.3\n");
        sb.append("info:\n");
        sb.append("  title: ").append(controllerName).append(" API\n");
        sb.append("  version: 1.0.0\n");
        sb.append("servers:\n");
        sb.append("  - url: ").append(baseUrl).append("\n\n");
        sb.append("paths:\n");

        for (Map.Entry<String, List<EndpointModel>> entry : byPath.entrySet()) {
            sb.append("  ").append(entry.getKey()).append(":\n");
            for (EndpointModel ep : entry.getValue()) {
                sb.append(buildOperation(ep));
            }
        }

        // Collect unique security schemes referenced by @SecurityRequirement
        Map<String, String> authSchemes = new LinkedHashMap<>();
        for (EndpointModel ep : endpoints) {
            if (ep.requiresAuth()) authSchemes.put(ep.authName, ep.authType);
        }

        if (!schemas.isEmpty() || !authSchemes.isEmpty()) {
            sb.append("\ncomponents:\n");
            if (!schemas.isEmpty()) {
                sb.append("  schemas:\n");
                Set<String> visited = new LinkedHashSet<>();
                for (PsiClass cls : schemas.values()) {
                    sb.append(buildSchema(cls, visited, "    "));
                }
            }
            if (!authSchemes.isEmpty()) {
                sb.append("  securitySchemes:\n");
                for (Map.Entry<String, String> e : authSchemes.entrySet()) {
                    sb.append("    ").append(e.getKey()).append(":\n");
                    sb.append("      type: http\n");
                    sb.append("      scheme: ").append(e.getValue()).append("\n");
                    if ("bearer".equals(e.getValue())) sb.append("      bearerFormat: JWT\n");
                }
            }
        }

        return sb.toString().trim();
    }

    // ── Operation ─────────────────────────────────────────────────────────────

    private static String buildOperation(EndpointModel ep) {
        StringBuilder sb = new StringBuilder();
        sb.append("    ").append(ep.httpMethod.toLowerCase()).append(":\n");
        sb.append("      operationId: ").append(ep.methodName).append("\n");
        sb.append("      summary: ").append(ep.methodName).append("\n");

        // Parameters: path vars + query params + request headers
        List<String> pathVars = extractPathVars(ep.path);
        boolean hasParams = !pathVars.isEmpty() || !ep.queryParams.isEmpty() || !ep.requestHeaders.isEmpty();

        if (hasParams) {
            sb.append("      parameters:\n");
            for (String var : pathVars) {
                sb.append("        - name: ").append(var).append("\n");
                sb.append("          in: path\n");
                sb.append("          required: true\n");
                sb.append("          schema:\n");
                sb.append("            ").append(pathVarSchema(ep.pathVarTypes.get(var), var)).append("\n");
            }
            for (String qp : ep.queryParams) {
                String[] kv = qp.split("=", 2);
                String val = kv.length > 1 ? kv[1] : "";
                sb.append("        - name: ").append(kv[0]).append("\n");
                sb.append("          in: query\n");
                sb.append("          required: ").append(!ep.isOptional(kv[0])).append("\n");
                sb.append("          schema:\n");
                sb.append("            ").append(schemaFromValue(val)).append("\n");
                if (!val.isEmpty()) sb.append("            example: ").append(yamlValue(val)).append("\n");
            }
            for (String h : ep.requestHeaders) {
                String[] kv = h.split(": ", 2);
                sb.append("        - name: ").append(kv[0]).append("\n");
                sb.append("          in: header\n");
                sb.append("          required: false\n");
                sb.append("          schema:\n");
                sb.append("            type: string\n");
            }
        }

        // Multipart body
        if (ep.isMultipart()) {
            sb.append("      requestBody:\n");
            sb.append("        required: true\n");
            sb.append("        content:\n");
            sb.append("          multipart/form-data:\n");
            sb.append("            schema:\n");
            sb.append("              type: object\n");
            sb.append("              properties:\n");
            for (MultipartPart part : ep.multipartParts) {
                sb.append("                ").append(part.name).append(":\n");
                if (part.isFile) {
                    sb.append("                  type: string\n");
                    sb.append("                  format: binary\n");
                } else {
                    sb.append("                  type: string\n");
                }
            }
        }

        // Request body
        if (ep.requestBodyPsiType != null) {
            PsiClass cls = resolveClass(ep.requestBodyPsiType);
            String schemaRef = cls != null ? "$ref: '#/components/schemas/" + cls.getName() + "'" : "type: object";
            sb.append("      requestBody:\n");
            sb.append("        required: true\n");
            sb.append("        content:\n");
            sb.append("          application/json:\n");
            sb.append("            schema:\n");
            sb.append("              ").append(schemaRef).append("\n");
        }

        if (ep.requiresAuth()) {
            sb.append("      security:\n");
            sb.append("        - ").append(ep.authName).append(": []\n");
        }

        sb.append("      responses:\n");
        sb.append("        '200':\n");
        sb.append("          description: OK\n");
        if (ep.responsePsiType != null) {
            sb.append("          content:\n");
            sb.append("            application/json:\n");
            sb.append("              schema:\n");
            sb.append(responseSchema(ep.responsePsiType, "                "));
        }

        return sb.toString();
    }

    /** OpenAPI schema for a response type, unwrapping ResponseEntity/Optional/Flux/Page etc. */
    private static String responseSchema(PsiType type, String indent) {
        if (ResponseTypeResolver.isArray(type)) {
            PsiType el = ResponseTypeResolver.element(type);
            return indent + "type: array\n" + indent + "items:\n"
                 + (el != null ? buildPropertySchema(el, indent + "  ") : indent + "  type: object\n");
        }
        return buildPropertySchema(ResponseTypeResolver.unwrap(type), indent);
    }

    // ── Schema ────────────────────────────────────────────────────────────────

    private static String buildSchema(PsiClass cls, Set<String> visited, String indent) {
        String fqn = cls.getQualifiedName();
        if (fqn == null || visited.contains(fqn)) return "";
        visited.add(fqn);

        StringBuilder sb = new StringBuilder();

        // Collect nested classes and generate them first
        for (PsiField field : cls.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
            if ("serialVersionUID".equals(field.getName())) continue;
            Map<String, PsiClass> nested = new LinkedHashMap<>();
            collectSchemas(field.getType(), nested);
            for (PsiClass nestedCls : nested.values()) {
                sb.append(buildSchema(nestedCls, visited, indent));
            }
        }

        sb.append(indent).append(cls.getName()).append(":\n");
        sb.append(indent).append("  type: object\n");
        sb.append(indent).append("  properties:\n");

        for (PsiField field : cls.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
            if ("serialVersionUID".equals(field.getName())) continue;
            if (field.getAnnotation("com.fasterxml.jackson.annotation.JsonIgnore") != null) continue;

            sb.append(indent).append("    ").append(field.getName()).append(":\n");
            sb.append(buildPropertySchema(field.getType(), indent + "      "));
        }

        return sb.toString();
    }

    private static String buildPropertySchema(PsiType type, String indent) {
        if (type instanceof PsiPrimitiveType) {
            return switch (type.getCanonicalText()) {
                case "boolean"         -> indent + "type: boolean\n";
                case "float", "double" -> indent + "type: number\n";
                case "long"            -> indent + "type: integer\n" + indent + "format: int64\n";
                default                -> indent + "type: integer\n";
            };
        }
        if (type instanceof PsiArrayType at) {
            return indent + "type: array\n" + indent + "items:\n"
                   + buildPropertySchema(at.getComponentType(), indent + "  ");
        }
        if (type instanceof PsiClassType ct) {
            PsiClass cls = ct.resolve();
            if (cls == null) return indent + "type: string\n";
            String fqn = cls.getQualifiedName();
            if (fqn == null) return indent + "type: string\n";

            if (fqn.equals("java.lang.String") || fqn.equals("java.lang.Character"))
                return indent + "type: string\n" + indent + "example: \"\"\n";
            if (fqn.equals("java.lang.Boolean"))
                return indent + "type: boolean\n";
            if (fqn.equals("java.lang.Long"))
                return indent + "type: integer\n" + indent + "format: int64\n";
            if (fqn.contains("Integer") || fqn.contains("Short") || fqn.contains("Byte"))
                return indent + "type: integer\n" + indent + "example: 0\n";
            if (fqn.contains("Double") || fqn.contains("Float") || fqn.contains("BigDecimal"))
                return indent + "type: number\n" + indent + "example: 0.0\n";
            if (fqn.equals("java.time.LocalDate") || fqn.equals("java.util.Date") || fqn.equals("java.sql.Date"))
                return indent + "type: string\n" + indent + "format: date\n"
                       + indent + "example: \"" + LocalDate.now() + "\"\n";
            if (fqn.contains("LocalDateTime") || fqn.contains("ZonedDateTime") || fqn.contains("OffsetDateTime"))
                return indent + "type: string\n" + indent + "format: date-time\n"
                       + indent + "example: \"" + LocalDate.now() + "T00:00:00\"\n";
            if (cls.isEnum()) {
                StringBuilder enumSb = new StringBuilder(indent + "type: string\n" + indent + "enum:\n");
                for (PsiField f : cls.getFields()) {
                    if (f instanceof PsiEnumConstant) enumSb.append(indent).append("  - ").append(f.getName()).append("\n");
                }
                return enumSb.toString();
            }
            if (fqn.startsWith("java.util.List") || fqn.startsWith("java.util.Set")
                || fqn.startsWith("java.util.Collection") || fqn.startsWith("java.util.ArrayList")) {
                PsiType[] params = ct.getParameters();
                return indent + "type: array\n" + indent + "items:\n"
                       + (params.length > 0 ? buildPropertySchema(params[0], indent + "  ") : indent + "  type: unknown\n");
            }
            if (fqn.startsWith("java.util.Map") || fqn.startsWith("java.util.HashMap"))
                return indent + "type: object\n" + indent + "additionalProperties: true\n";

            // Custom class → $ref
            return indent + "$ref: '#/components/schemas/" + cls.getName() + "'\n";
        }
        return indent + "type: string\n";
    }

    private static void collectSchemas(PsiType type, Map<String, PsiClass> result) {
        if (type instanceof PsiClassType ct) {
            PsiClass cls = ct.resolve();
            if (cls != null) {
                String fqn = cls.getQualifiedName();
                if (fqn != null && !fqn.startsWith("java.") && !fqn.startsWith("kotlin.") && !cls.isEnum()) {
                    result.put(fqn, cls);
                    for (PsiField f : cls.getFields()) {
                        if (!f.hasModifierProperty(PsiModifier.STATIC)) collectSchemas(f.getType(), result);
                    }
                }
                for (PsiType p : ct.getParameters()) collectSchemas(p, result);
            }
        }
        if (type instanceof PsiArrayType at) collectSchemas(at.getComponentType(), result);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static PsiClass resolveClass(PsiType type) {
        if (type instanceof PsiClassType ct) return ct.resolve();
        return null;
    }

    private static List<String> extractPathVars(String path) {
        List<String> vars = new ArrayList<>();
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(path);
        while (m.find()) vars.add(m.group(1));
        return vars;
    }

    private static String inferPathVarSchema(String varName) {
        String lower = varName.toLowerCase();
        if (lower.endsWith("id") || lower.endsWith("num") || lower.equals("page") || lower.equals("size"))
            return "type: integer\n            format: int64";
        return "type: string";
    }

    /** Inline schema for a path var from its declared type; falls back to name-based guess. */
    private static String pathVarSchema(PsiType type, String varName) {
        if (type != null) {
            String s = simpleSchema(type);
            if (s != null) return s;
        }
        return inferPathVarSchema(varName);
    }

    /** Inline OpenAPI schema for a scalar type, or null if not a simple scalar/enum. */
    private static String simpleSchema(PsiType type) {
        if (type instanceof PsiPrimitiveType) {
            return switch (type.getCanonicalText()) {
                case "boolean"         -> "type: boolean";
                case "long"            -> "type: integer\n            format: int64";
                case "float", "double" -> "type: number";
                default                -> "type: integer";
            };
        }
        if (type instanceof PsiClassType ct) {
            PsiClass cls = ct.resolve();
            String fqn = cls != null ? cls.getQualifiedName() : type.getCanonicalText();
            if (fqn == null) return null;
            if (fqn.equals("java.lang.Long"))                                        return "type: integer\n            format: int64";
            if (fqn.contains("Integer") || fqn.contains("Short") || fqn.contains("Byte")
                || fqn.equals("java.math.BigInteger"))                               return "type: integer";
            if (fqn.contains("Double") || fqn.contains("Float") || fqn.contains("BigDecimal")) return "type: number";
            if (fqn.equals("java.lang.Boolean"))                                     return "type: boolean";
            if (fqn.equals("java.lang.String") || fqn.equals("java.lang.Character")) return "type: string";
            if (fqn.equals("java.util.UUID"))                                        return "type: string\n            format: uuid";
            if (cls != null && cls.isEnum())                                         return "type: string";
        }
        return null;
    }

    private static String schemaFromValue(String val) {
        try { Long.parseLong(val); return "type: integer"; } catch (NumberFormatException ignored) {}
        try { Double.parseDouble(val); return "type: number"; } catch (NumberFormatException ignored) {}
        if (val.equals("true") || val.equals("false")) return "type: boolean";
        return "type: string";
    }

    private static String yamlValue(String val) {
        try { Long.parseLong(val); return val; } catch (NumberFormatException ignored) {}
        try { Double.parseDouble(val); return val; } catch (NumberFormatException ignored) {}
        if (val.equals("true") || val.equals("false")) return val;
        return "\"" + val + "\"";
    }
}
