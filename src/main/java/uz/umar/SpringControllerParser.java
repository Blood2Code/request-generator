package uz.umar;

import com.intellij.psi.*;
import java.time.LocalDate;
import java.util.*;

public class SpringControllerParser {

    private static final String PKG = "org.springframework.web.bind.annotation.";
    private static final String SWAGGER = "io.swagger.v3.oas.annotations.security.";

    public static List<EndpointModel> parse(PsiFile file) {
        List<EndpointModel> endpoints = new ArrayList<>();
        for (PsiClass cls : ControllerClasses.from(file)) {
            if (cls.getAnnotation(PKG + "RestController") == null) continue;
            String basePath = extractClassPath(cls);
            String[] classAuth = extractAuth(cls);
            for (PsiMethod method : cls.getMethods()) {
                EndpointModel ep = parseMethod(method, basePath, classAuth);
                if (ep != null) endpoints.add(ep);
            }
        }
        return endpoints;
    }

    private static String extractClassPath(PsiClass cls) {
        PsiAnnotation rm = cls.getAnnotation(PKG + "RequestMapping");
        return rm != null ? extractPath(rm) : "";
    }

    private static EndpointModel parseMethod(PsiMethod method, String basePath, String[] classAuth) {
        String[][] mappings = {
            {"GetMapping", "GET"}, {"PostMapping", "POST"}, {"PutMapping", "PUT"},
            {"DeleteMapping", "DELETE"}, {"PatchMapping", "PATCH"}
        };
        for (String[] m : mappings) {
            PsiAnnotation ann = method.getAnnotation(PKG + m[0]);
            if (ann != null) return buildModel(method, basePath, ann, m[1], classAuth);
        }
        PsiAnnotation rm = method.getAnnotation(PKG + "RequestMapping");
        if (rm != null) return buildModel(method, basePath, rm, extractHttpMethod(rm), classAuth);
        return null;
    }

    private static EndpointModel buildModel(PsiMethod method, String basePath, PsiAnnotation ann,
                                            String httpMethod, String[] classAuth) {
        String fullPath = normalizePath(basePath + extractPath(ann));

        List<String> queryParams = new ArrayList<>();
        List<String> requestHeaders = new ArrayList<>();
        Map<String, PsiType> pathVarTypes = new LinkedHashMap<>();
        List<MultipartPart> multipartParts = new ArrayList<>();
        Set<String> optionalQueryParams = new LinkedHashSet<>();
        String requestBodyJson = null;
        PsiType requestBodyPsiType = null;

        for (PsiParameter param : method.getParameterList().getParameters()) {
            PsiAnnotation partAnn = param.getAnnotation(PKG + "RequestPart");

            if (isMultipartFile(param.getType())) {
                // A file upload — whether annotated with @RequestPart, @RequestParam, or neither.
                PsiAnnotation nameAnn = partAnn != null ? partAnn : param.getAnnotation(PKG + "RequestParam");
                String name = nameAnn != null ? extractParamName(nameAnn, param.getName()) : param.getName();
                multipartParts.add(new MultipartPart(name, true));

            } else if (partAnn != null) {
                // A non-file multipart part (JSON/text field).
                multipartParts.add(new MultipartPart(extractParamName(partAnn, param.getName()), false));

            } else if (param.getAnnotation(PKG + "RequestBody") != null) {
                requestBodyPsiType = param.getType();
                requestBodyJson = JsonBodyBuilder.build(requestBodyPsiType);

            } else if (param.getAnnotation(PKG + "PathVariable") != null) {
                PsiAnnotation pvAnn = param.getAnnotation(PKG + "PathVariable");
                String name = extractParamName(pvAnn, param.getName());
                pathVarTypes.put(name, param.getType());

            } else if (param.getAnnotation(PKG + "RequestParam") != null) {
                PsiAnnotation rpAnn = param.getAnnotation(PKG + "RequestParam");
                String name = extractParamName(rpAnn, param.getName());
                String def = extractDefaultValue(rpAnn);
                if (!isRequiredParam(rpAnn, param.getType(), def)) optionalQueryParams.add(name);
                queryParams.add(name + "=" + (!def.isEmpty() ? def : typeDefault(param.getType())));

            } else if (param.getAnnotation(PKG + "RequestHeader") != null) {
                PsiAnnotation rhAnn = param.getAnnotation(PKG + "RequestHeader");
                String name = extractParamName(rhAnn, param.getName());
                requestHeaders.add(name + ": " + typeDefault(param.getType()));
            }
        }

        // @SecurityRequirement → auth metadata (method-level overrides class-level).
        // Skip if the endpoint already declares an explicit Authorization @RequestHeader.
        String[] methodAuth = extractAuth(method);
        String[] auth = methodAuth != null ? methodAuth : classAuth;
        boolean hasExplicitAuthHeader =
            requestHeaders.stream().anyMatch(h -> h.toLowerCase().startsWith("authorization:"));
        String authName = (auth != null && !hasExplicitAuthHeader) ? auth[0] : null;
        String authType = (auth != null && !hasExplicitAuthHeader) ? auth[1] : null;

        PsiType responseType = method.getReturnType();
        if (responseType != null && "void".equals(responseType.getCanonicalText())) responseType = null;

        return new EndpointModel(method.getName(), httpMethod, fullPath, queryParams, requestHeaders,
                                 requestBodyJson, requestBodyPsiType, authName, authType, pathVarTypes,
                                 multipartParts, optionalQueryParams, responseType);
    }

    /** A @RequestParam is optional if it has a defaultValue, required=false, or an Optional type. */
    private static boolean isRequiredParam(PsiAnnotation ann, PsiType type, String defaultValue) {
        if (!defaultValue.isEmpty()) return false;
        PsiAnnotationMemberValue r = ann.findAttributeValue("required");
        if (r != null && "false".equals(r.getText())) return false;
        if (type instanceof PsiClassType ct) {
            PsiClass c = ct.resolve();
            if (c != null && "java.util.Optional".equals(c.getQualifiedName())) return false;
        }
        return true;
    }

    /** True for MultipartFile, MultipartFile[], or List/Collection<MultipartFile>. */
    private static boolean isMultipartFile(PsiType type) {
        if (type instanceof PsiArrayType at) return isMultipartFile(at.getComponentType());
        if (type instanceof PsiClassType ct) {
            PsiClass cls = ct.resolve();
            String fqn = cls != null ? cls.getQualifiedName() : null;
            if (fqn == null) return false;
            if (fqn.equals("org.springframework.web.multipart.MultipartFile")) return true;
            // List<MultipartFile> / Collection<MultipartFile>
            if (fqn.startsWith("java.util.")) {
                for (PsiType p : ct.getParameters()) if (isMultipartFile(p)) return true;
            }
        }
        return false;
    }

    // ── Security ───────────────────────────────────────────────────────────────

    /**
     * Reads a @SecurityRequirement (or @SecurityRequirements container) on the owner.
     * Returns [name, type] where type is "bearer" or "basic", or null if absent.
     */
    static String[] extractAuth(PsiModifierListOwner owner) {
        PsiAnnotation single = owner.getAnnotation(SWAGGER + "SecurityRequirement");
        if (single != null) return authFrom(single);

        // @SecurityRequirements is the repeatable container: value = array of @SecurityRequirement
        PsiAnnotation multi = owner.getAnnotation(SWAGGER + "SecurityRequirements");
        if (multi != null) {
            PsiAnnotation first = firstAnnotation(multi.findAttributeValue("value"));
            if (first != null) return authFrom(first);
        }
        return null;
    }

    private static String[] authFrom(PsiAnnotation requirement) {
        PsiAnnotationMemberValue nameVal = requirement.findAttributeValue("name");
        String name = nameVal != null ? extractString(nameVal) : "";
        if (name.isEmpty()) name = "bearerAuth";
        String type = name.toLowerCase().contains("basic") ? "basic" : "bearer";
        return new String[]{name, type};
    }

    private static PsiAnnotation firstAnnotation(PsiAnnotationMemberValue value) {
        if (value instanceof PsiAnnotation a) return a;
        if (value instanceof PsiArrayInitializerMemberValue arr) {
            for (PsiAnnotationMemberValue item : arr.getInitializers()) {
                if (item instanceof PsiAnnotation a) return a;
            }
        }
        return null;
    }

    // ── Annotation helpers ────────────────────────────────────────────────────

    static String extractPath(PsiAnnotation annotation) {
        PsiAnnotationMemberValue v = annotation.findAttributeValue("value");
        if (v == null) v = annotation.findAttributeValue("path");
        return v != null ? extractString(v) : "";
    }

    static String extractParamName(PsiAnnotation annotation, String fallback) {
        PsiAnnotationMemberValue v = annotation.findAttributeValue("value");
        if (v == null) v = annotation.findAttributeValue("name");
        if (v != null) {
            String text = extractString(v);
            if (!text.isEmpty()) return text;
        }
        return fallback;
    }

    static String extractDefaultValue(PsiAnnotation annotation) {
        // findDeclaredAttributeValue → only the explicitly-written value; null when the user
        // never set defaultValue (findAttributeValue would leak Spring's DEFAULT_NONE sentinel).
        PsiAnnotationMemberValue v = annotation.findDeclaredAttributeValue("defaultValue");
        if (v == null) return "";
        String text = extractString(v);
        if (isDefaultNone(text)) return "";
        return text;
    }

    /**
     * True for Spring's ValueConstants.DEFAULT_NONE sentinel. Its value is
     * "\n\t\t\n\t\t\n\uE000\uE001\uE002\n\t\t\t\t\n" — non-blank because of the
     * private-use-area markers, so a plain isBlank() check alone would miss it.
     */
    private static boolean isDefaultNone(String text) {
        if (text.isBlank() || text.contains("DEFAULT_NONE") || text.startsWith("\\n")) return true;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= '\uE000' && c <= '\uF8FF') return true; // Unicode private-use area
        }
        return false;
    }

    static String extractString(PsiAnnotationMemberValue value) {
        if (value instanceof PsiLiteralExpression lit) {
            Object v = lit.getValue();
            return v != null ? v.toString() : "";
        }
        if (value instanceof PsiArrayInitializerMemberValue arr) {
            PsiAnnotationMemberValue[] items = arr.getInitializers();
            return items.length > 0 ? extractString(items[0]) : "";
        }
        String text = value.getText();
        return (text.startsWith("\"") && text.endsWith("\""))
               ? text.substring(1, text.length() - 1)
               : text;
    }

    static String extractHttpMethod(PsiAnnotation annotation) {
        PsiAnnotationMemberValue m = annotation.findAttributeValue("method");
        if (m == null) return "GET";
        String text = m.getText();
        if (text.contains("POST"))   return "POST";
        if (text.contains("PUT"))    return "PUT";
        if (text.contains("DELETE")) return "DELETE";
        if (text.contains("PATCH"))  return "PATCH";
        return "GET";
    }

    static String typeDefault(PsiType type) {
        if (type instanceof PsiPrimitiveType) {
            return switch (type.getCanonicalText()) {
                case "boolean"        -> "true";
                case "float", "double" -> "0.0";
                default                -> "0";
            };
        }
        if (type instanceof PsiClassType ct) {
            PsiClass cls = ct.resolve();
            if (cls != null && cls.isEnum()) {
                for (PsiField f : cls.getFields()) {
                    if (f instanceof PsiEnumConstant) return f.getName();
                }
            }
            String fqn = ct.getCanonicalText();
            if (fqn.contains("Boolean"))                       return "true";
            if (fqn.contains("Integer") || fqn.contains("Long") || fqn.contains("Short")) return "0";
            if (fqn.contains("Double") || fqn.contains("Float") || fqn.contains("BigDecimal")) return "0.0";
            if (fqn.contains("LocalDate") && !fqn.contains("Time")) return LocalDate.now().toString();
            if (fqn.contains("LocalDateTime") || fqn.contains("ZonedDateTime")) return LocalDate.now() + "T00:00:00";
        }
        return "";
    }

    static String normalizePath(String path) {
        if (path.isEmpty()) return "/";
        if (!path.startsWith("/")) path = "/" + path;
        return path.replaceAll("//+", "/");
    }
}
