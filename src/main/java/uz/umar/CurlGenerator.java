package uz.umar;

import com.intellij.psi.PsiFile;
import java.util.*;
import java.util.regex.*;

public class CurlGenerator {

    public static String generate(PsiFile file) {
        List<EndpointModel> endpoints = SpringControllerParser.parse(file);
        String controllerName = file.getVirtualFile().getNameWithoutExtension();
        String baseUrl = BaseUrlResolver.resolve(file.getProject());

        StringBuilder sb = new StringBuilder();
        sb.append("#!/usr/bin/env bash\n");
        sb.append("# Generated from ").append(controllerName).append("\n");
        sb.append("BASE_URL=\"").append(baseUrl).append("\"\n");
        if (endpoints.stream().anyMatch(ep -> "bearer".equals(ep.authType)))
            sb.append("TOKEN=\"your_token_here\"\n");
        if (endpoints.stream().anyMatch(ep -> "basic".equals(ep.authType)))
            sb.append("CREDENTIALS=\"base64_encoded_user_password\"\n");
        sb.append("\n");

        for (EndpointModel ep : endpoints) {
            sb.append(buildBlock(ep)).append("\n\n");
        }

        return sb.toString().trim();
    }

    private static String buildBlock(EndpointModel ep) {
        StringBuilder sb = new StringBuilder();
        sb.append("# ── ").append(ep.methodName).append(" ──\n");

        // Declare path variables as shell vars above the curl call
        for (String var : extractPathVars(ep.path)) {
            sb.append(var.toUpperCase().replace("-", "_")).append("=1\n");
        }

        String url = "${BASE_URL}" + toShellPath(ep.path);
        if (!ep.queryParams.isEmpty()) url += "?" + String.join("&", ep.queryParams);

        sb.append("curl -s -X ").append(ep.httpMethod).append(" \"").append(url).append("\"");

        if ("bearer".equals(ep.authType)) {
            sb.append(" \\\n  -H \"Authorization: Bearer ${TOKEN}\"");
        } else if ("basic".equals(ep.authType)) {
            sb.append(" \\\n  -H \"Authorization: Basic ${CREDENTIALS}\"");
        }

        for (String h : ep.requestHeaders) {
            sb.append(" \\\n  -H \"").append(h).append("\"");
        }
        // curl sets multipart Content-Type (with boundary) automatically for -F
        if (!ep.isMultipart() && ep.requestBodyJson != null) {
            sb.append(" \\\n  -H \"Content-Type: application/json\"");
        }
        sb.append(" \\\n  -H \"Accept: application/json\"");

        if (ep.isMultipart()) {
            for (MultipartPart part : ep.multipartParts) {
                if (part.isFile) {
                    sb.append(" \\\n  -F \"").append(part.name).append("=@./").append(part.name).append("\"");
                } else {
                    sb.append(" \\\n  -F \"").append(part.name).append("=").append(part.name).append("_value\"");
                }
            }
        } else if (ep.requestBodyJson != null) {
            // Single-quote the JSON body; escape any single-quotes inside
            String body = ep.requestBodyJson.replace("'", "'\"'\"'");
            sb.append(" \\\n  -d '").append(body).append("'");
        }

        sb.append("\necho \"\"");

        String resp = JsonBodyBuilder.responseExample(ep.responsePsiType);
        if (resp != null) {
            sb.append("\n# Example 200 response:\n");
            sb.append(resp.lines().map(l -> "# " + l).collect(java.util.stream.Collectors.joining("\n")));
        }

        return sb.toString();
    }

    // Spring {id} → bash ${ID}
    private static String toShellPath(String path) {
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(path);
        StringBuffer sb = new StringBuffer();
        while (m.find()) {
            String varName = m.group(1).toUpperCase().replace("-", "_");
            m.appendReplacement(sb, Matcher.quoteReplacement("${" + varName + "}"));
        }
        m.appendTail(sb);
        return sb.toString();
    }

    private static List<String> extractPathVars(String path) {
        List<String> vars = new ArrayList<>();
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(path);
        while (m.find()) vars.add(m.group(1));
        return vars;
    }
}
