package uz.umar;

import com.intellij.psi.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class TypeScriptAxiosGenerator {

    public static String generate(PsiFile file) {
        List<EndpointModel> endpoints = SpringControllerParser.parse(file);
        String controllerName = file.getVirtualFile().getNameWithoutExtension();
        String baseUrl = BaseUrlResolver.resolve(file.getProject());

        // Collect all request body + response classes that need interfaces
        Map<String, PsiClass> interfaceMap = new LinkedHashMap<>();
        for (EndpointModel ep : endpoints) {
            if (ep.requestBodyPsiType != null) {
                collectCustomClasses(ep.requestBodyPsiType, interfaceMap);
            }
            if (ep.responsePsiType != null) {
                PsiType payload = ResponseTypeResolver.isArray(ep.responsePsiType)
                        ? ResponseTypeResolver.element(ep.responsePsiType)
                        : ResponseTypeResolver.unwrap(ep.responsePsiType);
                if (payload != null) collectCustomClasses(payload, interfaceMap);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from ").append(controllerName).append("\n");
        sb.append("import axios from 'axios';\n\n");
        sb.append("const BASE_URL = '").append(baseUrl).append("';\n");
        if (endpoints.stream().anyMatch(ep -> "bearer".equals(ep.authType)))
            sb.append("const TOKEN = 'your_token_here';\n");
        if (endpoints.stream().anyMatch(ep -> "basic".equals(ep.authType)))
            sb.append("const CREDENTIALS = 'base64_encoded_user_password';\n");
        sb.append("\n");

        // Interfaces
        if (!interfaceMap.isEmpty()) {
            Set<String> visited = new LinkedHashSet<>();
            for (PsiClass cls : interfaceMap.values()) {
                sb.append(buildInterface(cls, visited));
            }
        }

        // API object
        sb.append("export const ").append(controllerName).append("Api = {\n\n");
        for (EndpointModel ep : endpoints) {
            sb.append(buildFunction(ep));
        }
        sb.append("};\n");

        return sb.toString();
    }

    // ── Interface generation ──────────────────────────────────────────────────

    private static String buildInterface(PsiClass cls, Set<String> visited) {
        String fqn = cls.getQualifiedName();
        if (fqn == null || visited.contains(fqn)) return "";
        visited.add(fqn);

        // Generate nested interfaces first
        StringBuilder nested = new StringBuilder();
        StringBuilder fields = new StringBuilder();

        for (PsiField field : cls.getFields()) {
            if (field.hasModifierProperty(PsiModifier.STATIC)) continue;
            if ("serialVersionUID".equals(field.getName())) continue;
            if (field.getAnnotation("com.fasterxml.jackson.annotation.JsonIgnore") != null) continue;

            Map<String, PsiClass> nestedClasses = new LinkedHashMap<>();
            collectCustomClasses(field.getType(), nestedClasses);
            for (PsiClass nestedCls : nestedClasses.values()) {
                nested.append(buildInterface(nestedCls, visited));
            }

            fields.append("  ").append(field.getName()).append(": ").append(toTsType(field.getType())).append(";\n");
        }

        return nested + "interface " + cls.getName() + " {\n" + fields + "}\n\n";
    }

    private static void collectCustomClasses(PsiType type, Map<String, PsiClass> result) {
        if (type instanceof PsiClassType ct) {
            PsiClass cls = ct.resolve();
            if (cls != null) {
                String fqn = cls.getQualifiedName();
                if (fqn != null && !fqn.startsWith("java.") && !fqn.startsWith("kotlin.") && !cls.isEnum()) {
                    result.put(fqn, cls);
                }
                for (PsiType param : ct.getParameters()) collectCustomClasses(param, result);
            }
        }
        if (type instanceof PsiArrayType at) collectCustomClasses(at.getComponentType(), result);
    }

    // ── Function generation ───────────────────────────────────────────────────

    private static String buildFunction(EndpointModel ep) {
        // `?`-optional params must trail all required ones in a TS signature, so bucket them.
        List<String> params = new ArrayList<>();
        List<String> optionalParams = new ArrayList<>();
        List<String> pathVars = extractPathVars(ep.path);

        for (String var : pathVars) {
            PsiType declared = ep.pathVarTypes.get(var);
            String tsType = declared != null ? toTsType(declared) : inferPathVarTsType(var);
            params.add(var + ": " + tsType);
        }
        for (String qp : ep.queryParams) {
            String[] kv = qp.split("=", 2);
            String name = kv[0];
            String val  = kv.length > 1 ? kv[1] : "";
            String tsType = inferTsTypeFromValue(val);
            String defVal = tsDefault(val, tsType);
            if (!defVal.isEmpty()) {
                params.add(name + ": " + tsType + " = " + defVal);
            } else if (ep.isOptional(name)) {
                optionalParams.add(name + "?: " + tsType);
            } else {
                params.add(name + ": " + tsType);
            }
        }

        String tsPath = springPathToTs(ep.path);
        String url    = "`${BASE_URL}" + tsPath + "`";
        String method = ep.httpMethod.toLowerCase();

        if (ep.isMultipart()) {
            for (MultipartPart part : ep.multipartParts) {
                params.add(part.name + ": " + (part.isFile ? "File" : "string"));
            }
            params.addAll(optionalParams);
            return buildMultipartFunction(ep, params, url, method);
        }

        String bodyType = null;
        if (ep.requestBodyPsiType != null) {
            bodyType = toTsType(ep.requestBodyPsiType);
            params.add("data: " + bodyType);
        }
        params.addAll(optionalParams);

        String call = buildAxiosCall(method, url, ep.queryParams, bodyType, ep.authType, tsResponseType(ep));

        return "  " + ep.methodName + ": (" + String.join(", ", params) + ") =>\n    " + call + ",\n\n";
    }

    /** Generic type arg for the axios call, e.g. "<User>" or "<User[]>"; "" when unknown. */
    private static String tsResponseType(EndpointModel ep) {
        if (ep.responsePsiType == null) return "";
        String ts;
        if (ResponseTypeResolver.isArray(ep.responsePsiType)) {
            PsiType el = ResponseTypeResolver.element(ep.responsePsiType);
            ts = (el != null ? toTsType(el) : "unknown") + "[]";
        } else {
            ts = toTsType(ResponseTypeResolver.unwrap(ep.responsePsiType));
        }
        if (ts.isEmpty() || ts.equals("unknown") || ts.equals("void") || ts.equals("Void")) return "";
        return "<" + ts + ">";
    }

    private static String buildMultipartFunction(EndpointModel ep, List<String> params, String url, String method) {
        List<String> headerParts = new ArrayList<>();
        if ("bearer".equals(ep.authType)) headerParts.add("Authorization: `Bearer ${TOKEN}`");
        else if ("basic".equals(ep.authType)) headerParts.add("Authorization: `Basic ${CREDENTIALS}`");
        headerParts.add("'Content-Type': 'multipart/form-data'");

        List<String> configParts = new ArrayList<>();
        configParts.add("headers: { " + String.join(", ", headerParts) + " }");
        if (!ep.queryParams.isEmpty()) {
            configParts.add("params: { " + ep.queryParams.stream().map(qp -> qp.split("=")[0])
                    .collect(Collectors.joining(", ")) + " }");
        }
        String config = "{ " + String.join(", ", configParts) + " }";

        StringBuilder sb = new StringBuilder();
        sb.append("  ").append(ep.methodName).append(": (").append(String.join(", ", params)).append(") => {\n");
        sb.append("    const form = new FormData();\n");
        for (MultipartPart part : ep.multipartParts) {
            sb.append("    form.append('").append(part.name).append("', ").append(part.name).append(");\n");
        }
        sb.append("    return axios.").append(method).append(tsResponseType(ep)).append("(")
          .append(url).append(", form, ").append(config).append(");\n");
        sb.append("  },\n\n");
        return sb.toString();
    }

    private static String buildAxiosCall(String method, String url, List<String> queryParams,
                                         String bodyType, String authType, String responseType) {
        // Build a request config object combining headers (auth) and query params.
        List<String> configParts = new ArrayList<>();
        if ("bearer".equals(authType)) configParts.add("headers: { Authorization: `Bearer ${TOKEN}` }");
        else if ("basic".equals(authType)) configParts.add("headers: { Authorization: `Basic ${CREDENTIALS}` }");
        if (!queryParams.isEmpty()) {
            configParts.add("params: { " + queryParams.stream().map(qp -> qp.split("=")[0])
                    .collect(Collectors.joining(", ")) + " }");
        }
        String config = configParts.isEmpty() ? "" : "{ " + String.join(", ", configParts) + " }";
        String fn = "axios." + method + responseType;

        return switch (method) {
            case "get", "delete", "head" -> config.isEmpty()
                    ? fn + "(" + url + ")"
                    : fn + "(" + url + ", " + config + ")";
            default -> { // post, put, patch
                String body = bodyType != null ? "data" : "{}";
                yield config.isEmpty()
                    ? fn + "(" + url + ", " + body + ")"
                    : fn + "(" + url + ", " + body + ", " + config + ")";
            }
        };
    }

    // ── Type mapping ──────────────────────────────────────────────────────────

    static String toTsType(PsiType type) {
        if (type instanceof PsiPrimitiveType) {
            return switch (type.getCanonicalText()) {
                case "boolean"          -> "boolean";
                case "float", "double"  -> "number";
                case "char"             -> "string";
                case "void"             -> "void";
                default                 -> "number";
            };
        }
        if (type instanceof PsiArrayType at) return toTsType(at.getComponentType()) + "[]";
        if (type instanceof PsiClassType ct) {
            PsiClass cls = ct.resolve();
            if (cls == null) return "unknown";
            String fqn = cls.getQualifiedName();
            if (fqn == null) return "unknown";

            if (fqn.equals("java.lang.String") || fqn.equals("java.lang.Character")) return "string";
            if (fqn.equals("java.lang.Boolean")) return "boolean";
            if (fqn.contains("Integer") || fqn.contains("Long") || fqn.contains("Short")
                || fqn.contains("Byte") || fqn.contains("BigInteger")) return "number";
            if (fqn.contains("Double") || fqn.contains("Float") || fqn.contains("BigDecimal")) return "number";
            if (fqn.startsWith("java.time.") || fqn.equals("java.util.Date") || fqn.equals("java.sql.Date")) return "string";
            if (cls.isEnum()) return "string";

            if (fqn.startsWith("java.util.List") || fqn.startsWith("java.util.Set")
                || fqn.startsWith("java.util.Collection") || fqn.startsWith("java.util.ArrayList")) {
                PsiType[] p = ct.getParameters();
                return (p.length > 0 ? toTsType(p[0]) : "unknown") + "[]";
            }
            if (fqn.startsWith("java.util.Map") || fqn.startsWith("java.util.HashMap")) {
                PsiType[] p = ct.getParameters();
                return "Record<string, " + (p.length > 1 ? toTsType(p[1]) : "unknown") + ">";
            }
            if (fqn.equals("java.lang.Object")) return "unknown";

            return cls.getName() != null ? cls.getName() : "unknown";
        }
        return "unknown";
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private static String inferPathVarTsType(String varName) {
        String lower = varName.toLowerCase();
        if (lower.endsWith("id") || lower.equals("page") || lower.equals("size") || lower.equals("index")
            || lower.endsWith("num") || lower.endsWith("count") || lower.endsWith("no")) return "number";
        return "string";
    }

    private static String inferTsTypeFromValue(String val) {
        if (val.equals("true") || val.equals("false")) return "boolean";
        try { Long.parseLong(val); return "number"; } catch (NumberFormatException ignored) {}
        try { Double.parseDouble(val); return "number"; } catch (NumberFormatException ignored) {}
        return "string";
    }

    private static String tsDefault(String val, String tsType) {
        if ("number".equals(tsType) || "boolean".equals(tsType)) return val;
        return val.isEmpty() ? "" : "'" + val + "'";
    }

    private static List<String> extractPathVars(String path) {
        List<String> vars = new ArrayList<>();
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(path);
        while (m.find()) vars.add(m.group(1));
        return vars;
    }

    // Spring {id} → TS template literal ${id}
    private static String springPathToTs(String path) {
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(path);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement("${" + m.group(1) + "}"));
        m.appendTail(sb);
        return sb.toString();
    }
}
