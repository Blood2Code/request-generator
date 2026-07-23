package uz.umar;

import com.intellij.psi.PsiFile;
import java.util.*;
import java.util.regex.*;

public class PostmanGenerator {

    public static String generate(PsiFile file) {
        List<EndpointModel> endpoints = SpringControllerParser.parse(file);
        String controllerName = file.getVirtualFile().getNameWithoutExtension();
        String baseUrl = BaseUrlResolver.resolve(file.getProject());

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
        sb.append("    {\"key\": \"baseUrl\", \"value\": \"").append(baseUrl).append("\"}");
        if (endpoints.stream().anyMatch(ep -> "bearer".equals(ep.authType)))
            sb.append(",\n    {\"key\": \"token\", \"value\": \"your_token_here\"}");
        if (endpoints.stream().anyMatch(ep -> "basic".equals(ep.authType)))
            sb.append(",\n    {\"key\": \"credentials\", \"value\": \"base64_encoded_user_password\"}");
        sb.append("\n  ]\n");
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
        List<String> headers = new ArrayList<>();
        if ("bearer".equals(ep.authType)) headers.add("Authorization: Bearer {{token}}");
        else if ("basic".equals(ep.authType)) headers.add("Authorization: Basic {{credentials}}");
        headers.addAll(ep.requestHeaders);
        if (!ep.isMultipart() && ep.requestBodyJson != null) headers.add("Content-Type: application/json");
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
        if (ep.isMultipart()) {
            sb.append(",\n");
            sb.append("        \"body\": {\n");
            sb.append("          \"mode\": \"formdata\",\n");
            sb.append("          \"formdata\": [\n");
            for (int i = 0; i < ep.multipartParts.size(); i++) {
                MultipartPart part = ep.multipartParts.get(i);
                if (part.isFile) {
                    sb.append("            {\"key\": \"").append(esc(part.name)).append("\", \"type\": \"file\", \"src\": \"\"}");
                } else {
                    sb.append("            {\"key\": \"").append(esc(part.name))
                      .append("\", \"type\": \"text\", \"value\": \"").append(esc(part.name)).append("_value\"}");
                }
                if (i < ep.multipartParts.size() - 1) sb.append(",");
                sb.append("\n");
            }
            sb.append("          ]\n");
            sb.append("        }");
        } else if (ep.requestBodyJson != null) {
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
