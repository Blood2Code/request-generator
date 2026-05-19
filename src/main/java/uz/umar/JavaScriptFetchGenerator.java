package uz.umar;

import com.intellij.psi.PsiJavaFile;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

public class JavaScriptFetchGenerator {

    public static String generate(PsiJavaFile javaFile) {
        List<EndpointModel> endpoints = SpringControllerParser.parse(javaFile);
        String controllerName = javaFile.getVirtualFile().getNameWithoutExtension();

        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from ").append(controllerName).append("\n");
        sb.append("const BASE_URL = 'http://localhost:8080';\n\n");
        sb.append("export const ").append(controllerName).append("Api = {\n\n");

        for (EndpointModel ep : endpoints) {
            sb.append(buildFunction(ep));
        }

        sb.append("};\n");
        return sb.toString();
    }

    private static String buildFunction(EndpointModel ep) {
        List<String> params = new ArrayList<>();

        for (String var : extractPathVars(ep.path)) params.add(var);
        for (String qp : ep.queryParams) {
            String[] kv = qp.split("=", 2);
            String def = kv.length > 1 && !kv[1].isEmpty() ? " = " + defaultLiteral(kv[1]) : "";
            params.add(kv[0] + def);
        }
        if (ep.requestBodyJson != null) params.add("data");

        String tsPath = springPathToTs(ep.path);
        String indent = "    ";
        StringBuilder sb = new StringBuilder();
        sb.append("  ").append(ep.methodName).append(": async (").append(String.join(", ", params)).append(") => {\n");

        // URL building
        if (ep.queryParams.isEmpty()) {
            sb.append(indent).append("const res = await fetch(`${BASE_URL}").append(tsPath).append("`, {\n");
        } else {
            sb.append(indent).append("const url = new URL(`${BASE_URL}").append(tsPath).append("`);\n");
            for (String qp : ep.queryParams) {
                String name = qp.split("=")[0];
                sb.append(indent).append("url.searchParams.set('").append(name).append("', String(").append(name).append("));\n");
            }
            sb.append(indent).append("const res = await fetch(url.toString(), {\n");
        }

        // Fetch options
        sb.append(indent).append("  method: '").append(ep.httpMethod).append("',\n");
        sb.append(indent).append("  headers: {");

        List<String> headers = new ArrayList<>(ep.requestHeaders);
        if (ep.requestBodyJson != null) headers.add(0, "'Content-Type': 'application/json'");
        headers.add("'Accept': 'application/json'");

        if (headers.size() == 1) {
            sb.append(" ").append(headers.get(0)).append(" }");
        } else {
            sb.append("\n");
            for (int i = 0; i < headers.size(); i++) {
                String h = headers.get(i).contains(":") && !headers.get(i).startsWith("'")
                        ? formatRawHeader(headers.get(i))
                        : headers.get(i);
                sb.append(indent).append("    ").append(h);
                if (i < headers.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append(indent).append("  }");
        }

        if (ep.requestBodyJson != null) {
            sb.append(",\n").append(indent).append("  body: JSON.stringify(data)\n");
        } else {
            sb.append("\n");
        }

        sb.append(indent).append("});\n");
        sb.append(indent).append("return res.json();\n");
        sb.append("  },\n\n");

        return sb.toString();
    }

    // "X-Tenant-Id: value" → "'X-Tenant-Id': 'value'"
    private static String formatRawHeader(String header) {
        String[] parts = header.split(": ", 2);
        return "'" + parts[0] + "': '" + (parts.length > 1 ? parts[1] : "") + "'";
    }

    private static String defaultLiteral(String val) {
        try { Long.parseLong(val); return val; } catch (NumberFormatException ignored) {}
        try { Double.parseDouble(val); return val; } catch (NumberFormatException ignored) {}
        if (val.equals("true") || val.equals("false")) return val;
        return "'" + val + "'";
    }

    private static List<String> extractPathVars(String path) {
        List<String> vars = new ArrayList<>();
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(path);
        while (m.find()) vars.add(m.group(1));
        return vars;
    }

    private static String springPathToTs(String path) {
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(path);
        StringBuffer sb = new StringBuffer();
        while (m.find()) m.appendReplacement(sb, Matcher.quoteReplacement("${" + m.group(1) + "}"));
        m.appendTail(sb);
        return sb.toString();
    }
}
