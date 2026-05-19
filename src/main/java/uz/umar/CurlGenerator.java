package uz.umar;

import com.intellij.psi.PsiJavaFile;
import java.util.*;
import java.util.regex.*;

public class CurlGenerator {

    private static final String BASE_URL = "http://localhost:8080";

    public static String generate(PsiJavaFile javaFile) {
        List<EndpointModel> endpoints = SpringControllerParser.parse(javaFile);
        String controllerName = javaFile.getVirtualFile().getNameWithoutExtension();

        StringBuilder sb = new StringBuilder();
        sb.append("#!/usr/bin/env bash\n");
        sb.append("# Generated from ").append(controllerName).append("\n");
        sb.append("BASE_URL=\"").append(BASE_URL).append("\"\n\n");

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

        String url = BASE_URL + toShellPath(ep.path);
        if (!ep.queryParams.isEmpty()) url += "?" + String.join("&", ep.queryParams);

        sb.append("curl -s -X ").append(ep.httpMethod).append(" \"").append(url).append("\"");

        for (String h : ep.requestHeaders) {
            sb.append(" \\\n  -H \"").append(h).append("\"");
        }
        if (ep.requestBodyJson != null) {
            sb.append(" \\\n  -H \"Content-Type: application/json\"");
        }
        sb.append(" \\\n  -H \"Accept: application/json\"");

        if (ep.requestBodyJson != null) {
            // Single-quote the JSON body; escape any single-quotes inside
            String body = ep.requestBodyJson.replace("'", "'\"'\"'");
            sb.append(" \\\n  -d '").append(body).append("'");
        }

        sb.append("\necho \"\"");
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
