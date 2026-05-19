package uz.umar;

import com.intellij.psi.PsiJavaFile;
import java.util.*;
import java.util.regex.*;

public class PostmanGenerator {

    private static final String BASE_URL = "http://localhost:8080";

    public static String generate(PsiJavaFile javaFile) {
        List<EndpointModel> endpoints = SpringControllerParser.parse(javaFile);
        String controllerName = javaFile.getVirtualFile().getNameWithoutExtension();

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("  \"info\": {\n");
        sb.append("    \"name\": \"").append(esc(controllerName)).append("\",\n");
        sb.append("    \"schema\": \"https://schema.getpostman.com/json/collection/v2.1.0/collection.json\"\n");
        sb.append("  },\n");
        sb.append("  \"item\": [\n");

        for (int i = 0; i < endpoints.size(); i++) {
            sb.append(buildItem(endpoints.get(i)));
            if (i < endpoints.size() - 1) sb.append(",");
            sb.append("\n");
        }

        sb.append("  ],\n");
        sb.append("  \"variable\": [\n");
        sb.append("    {\"key\": \"baseUrl\", \"value\": \"").append(BASE_URL).append("\"}\n");
        sb.append("  ]\n");
        sb.append("}");

        return sb.toString();
    }

    private static String buildItem(EndpointModel ep) {
        StringBuilder sb = new StringBuilder();
        sb.append("    {\n");
        sb.append("      \"name\": \"").append(esc(ep.methodName)).append("\",\n");
        sb.append("      \"request\": {\n");
        sb.append("        \"method\": \"").append(ep.httpMethod).append("\",\n");

        // Headers
        List<String> headers = new ArrayList<>(ep.requestHeaders);
        if (ep.requestBodyJson != null) headers.add("Content-Type: application/json");
        headers.add("Accept: application/json");

        sb.append("        \"header\": [\n");
        for (int i = 0; i < headers.size(); i++) {
            String[] kv = headers.get(i).split(": ", 2);
            sb.append("          {\"key\": \"").append(esc(kv[0]))
              .append("\", \"value\": \"").append(kv.length > 1 ? esc(kv[1]) : "").append("\"}");
            if (i < headers.size() - 1) sb.append(",");
            sb.append("\n");
        }
        sb.append("        ]");

        // Body
        if (ep.requestBodyJson != null) {
            sb.append(",\n");
            sb.append("        \"body\": {\n");
            sb.append("          \"mode\": \"raw\",\n");
            sb.append("          \"raw\": \"").append(esc(ep.requestBodyJson)).append("\",\n");
            sb.append("          \"options\": {\"raw\": {\"language\": \"json\"}}\n");
            sb.append("        }");
        }

        // URL
        sb.append(",\n");
        sb.append("        \"url\": ").append(buildUrl(ep)).append("\n");

        sb.append("      }\n");
        sb.append("    }");
        return sb.toString();
    }

    private static String buildUrl(EndpointModel ep) {
        // Spring {id} → Postman :id  (in path segments and raw URL)
        String postmanPath = ep.path.replaceAll("\\{([^}]+)}", ":$1");
        String rawUrl = "{{baseUrl}}" + postmanPath;
        if (!ep.queryParams.isEmpty()) rawUrl += "?" + String.join("&", ep.queryParams);

        String[] segments = ep.path.replaceFirst("^/", "").split("/");

        StringBuilder sb = new StringBuilder();
        sb.append("{\n");
        sb.append("          \"raw\": \"").append(esc(rawUrl)).append("\",\n");
        sb.append("          \"host\": [\"{{baseUrl}}\"],\n");

        // Path segments: {id} → :id
        sb.append("          \"path\": [");
        List<String> nonEmpty = new ArrayList<>();
        for (String seg : segments) {
            if (!seg.isEmpty()) nonEmpty.add(seg.replaceAll("\\{([^}]+)}", ":$1"));
        }
        for (int i = 0; i < nonEmpty.size(); i++) {
            sb.append("\"").append(esc(nonEmpty.get(i))).append("\"");
            if (i < nonEmpty.size() - 1) sb.append(", ");
        }
        sb.append("]");

        // Query params
        if (!ep.queryParams.isEmpty()) {
            sb.append(",\n          \"query\": [\n");
            for (int i = 0; i < ep.queryParams.size(); i++) {
                String[] kv = ep.queryParams.get(i).split("=", 2);
                sb.append("            {\"key\": \"").append(esc(kv[0]))
                  .append("\", \"value\": \"").append(kv.length > 1 ? esc(kv[1]) : "").append("\"}");
                if (i < ep.queryParams.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("          ]");
        }

        // Path variables
        List<String> pathVars = extractPathVars(ep.path);
        if (!pathVars.isEmpty()) {
            sb.append(",\n          \"variable\": [\n");
            for (int i = 0; i < pathVars.size(); i++) {
                sb.append("            {\"key\": \"").append(esc(pathVars.get(i)))
                  .append("\", \"value\": \"1\"}");
                if (i < pathVars.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("          ]");
        }

        sb.append("\n        }");
        return sb.toString();
    }

    private static List<String> extractPathVars(String path) {
        List<String> vars = new ArrayList<>();
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(path);
        while (m.find()) vars.add(m.group(1));
        return vars;
    }

    private static String esc(String s) {
        return s.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }
}
