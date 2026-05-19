package uz.umar;

import com.intellij.psi.*;
import java.time.LocalDate;
import java.util.*;

public class SpringControllerParser {

    private static final String PKG = "org.springframework.web.bind.annotation.";

    public static List<EndpointModel> parse(PsiJavaFile javaFile) {
        List<EndpointModel> endpoints = new ArrayList<>();
        for (PsiClass cls : javaFile.getClasses()) {
            if (cls.getAnnotation(PKG + "RestController") == null) continue;
            String basePath = extractClassPath(cls);
            for (PsiMethod method : cls.getMethods()) {
                EndpointModel ep = parseMethod(method, basePath);
                if (ep != null) endpoints.add(ep);
            }
        }
        return endpoints;
    }

    private static String extractClassPath(PsiClass cls) {
        PsiAnnotation rm = cls.getAnnotation(PKG + "RequestMapping");
        return rm != null ? extractPath(rm) : "";
    }

    private static EndpointModel parseMethod(PsiMethod method, String basePath) {
        String[][] mappings = {
            {"GetMapping", "GET"}, {"PostMapping", "POST"}, {"PutMapping", "PUT"},
            {"DeleteMapping", "DELETE"}, {"PatchMapping", "PATCH"}
        };
        for (String[] m : mappings) {
            PsiAnnotation ann = method.getAnnotation(PKG + m[0]);
            if (ann != null) return buildModel(method, basePath, ann, m[1]);
        }
        PsiAnnotation rm = method.getAnnotation(PKG + "RequestMapping");
        if (rm != null) return buildModel(method, basePath, rm, extractHttpMethod(rm));
        return null;
    }

    private static EndpointModel buildModel(PsiMethod method, String basePath, PsiAnnotation ann, String httpMethod) {
        String fullPath = normalizePath(basePath + extractPath(ann));

        List<String> queryParams = new ArrayList<>();
        List<String> requestHeaders = new ArrayList<>();
        String requestBodyJson = null;
        PsiType requestBodyPsiType = null;

        for (PsiParameter param : method.getParameterList().getParameters()) {
            if (param.getAnnotation(PKG + "RequestBody") != null) {
                requestBodyPsiType = param.getType();
                requestBodyJson = JsonBodyBuilder.build(requestBodyPsiType);

            } else if (param.getAnnotation(PKG + "RequestParam") != null) {
                PsiAnnotation rpAnn = param.getAnnotation(PKG + "RequestParam");
                String name = extractParamName(rpAnn, param.getName());
                String def = extractDefaultValue(rpAnn);
                queryParams.add(name + "=" + (!def.isEmpty() ? def : typeDefault(param.getType())));

            } else if (param.getAnnotation(PKG + "RequestHeader") != null) {
                PsiAnnotation rhAnn = param.getAnnotation(PKG + "RequestHeader");
                String name = extractParamName(rhAnn, param.getName());
                requestHeaders.add(name + ": " + typeDefault(param.getType()));
            }
        }

        return new EndpointModel(method.getName(), httpMethod, fullPath, queryParams, requestHeaders,
                                 requestBodyJson, requestBodyPsiType);
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
        PsiAnnotationMemberValue v = annotation.findAttributeValue("defaultValue");
        if (v == null) return "";
        String text = extractString(v);
        if (text.isBlank() || text.contains("DEFAULT_NONE") || text.startsWith("\\n")) return "";
        return text;
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
