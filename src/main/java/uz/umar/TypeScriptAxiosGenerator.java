package uz.umar;

import com.intellij.psi.*;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class TypeScriptAxiosGenerator {

    public static String generate(PsiJavaFile javaFile) {
        List<EndpointModel> endpoints = SpringControllerParser.parse(javaFile);
        String controllerName = javaFile.getVirtualFile().getNameWithoutExtension();

        // Collect all request body classes that need interfaces
        Map<String, PsiClass> interfaceMap = new LinkedHashMap<>();
        for (EndpointModel ep : endpoints) {
            if (ep.requestBodyPsiType != null) {
                collectCustomClasses(ep.requestBodyPsiType, interfaceMap);
            }
        }

        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from ").append(controllerName).append("\n");
        sb.append("import axios from 'axios';\n\n");
        sb.append("const BASE_URL = 'http://localhost:8080';\n\n");

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
        List<String> params = new ArrayList<>();
        List<String> pathVars = extractPathVars(ep.path);

        for (String var : pathVars) {
            params.add(var + ": " + inferPathVarTsType(var));
        }
        for (String qp : ep.queryParams) {
            String[] kv = qp.split("=", 2);
            String name = kv[0];
            String val  = kv.length > 1 ? kv[1] : "";
            String tsType = inferTsTypeFromValue(val);
            String defVal = tsDefault(val, tsType);
            params.add(name + ": " + tsType + (defVal.isEmpty() ? "" : " = " + defVal));
        }

        String bodyType = null;
        if (ep.requestBodyPsiType != null) {
            bodyType = toTsType(ep.requestBodyPsiType);
            params.add("data: " + bodyType);
        }

        String tsPath  = springPathToTs(ep.path);
        String url     = "`${BASE_URL}" + tsPath + "`";
        String method  = ep.httpMethod.toLowerCase();
        String call    = buildAxiosCall(method, url, ep.queryParams, bodyType);

        return "  " + ep.methodName + ": (" + String.join(", ", params) + ") =>\n    " + call + ",\n\n";
    }

    private static String buildAxiosCall(String method, String url, List<String> queryParams, String bodyType) {
        String paramsArg = queryParams.isEmpty() ? "" :
            "{ params: { " + queryParams.stream().map(qp -> qp.split("=")[0]).collect(Collectors.joining(", ")) + " } }";

        return switch (method) {
            case "get", "delete", "head" -> queryParams.isEmpty()
                    ? "axios." + method + "(" + url + ")"
                    : "axios." + method + "(" + url + ", " + paramsArg + ")";
            default -> { // post, put, patch
                String body = bodyType != null ? "data" : "{}";
                yield queryParams.isEmpty()
                    ? "axios." + method + "(" + url + ", " + body + ")"
                    : "axios." + method + "(" + url + ", " + body + ", " + paramsArg + ")";
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
