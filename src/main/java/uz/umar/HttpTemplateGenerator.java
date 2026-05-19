package uz.umar;

import com.intellij.psi.PsiJavaFile;
import java.util.List;

public class HttpTemplateGenerator {

    public static String generate(PsiJavaFile javaFile) {
        List<EndpointModel> endpoints = SpringControllerParser.parse(javaFile);

        StringBuilder sb = new StringBuilder();
        sb.append("@baseUrl = http://localhost:8080\n\n");

        for (EndpointModel ep : endpoints) {
            sb.append(buildBlock(ep)).append("\n\n");
        }

        return sb.toString().trim();
    }

    private static String buildBlock(EndpointModel ep) {
        String httpPath = toHttpClientPath(ep.path);

        StringBuilder sb = new StringBuilder();
        sb.append("### ").append(ep.methodName).append("\n");

        String url = ep.httpMethod + " {{baseUrl}}" + httpPath;
        if (!ep.queryParams.isEmpty()) url += "?" + String.join("&", ep.queryParams);
        sb.append(url).append("\n");

        for (String h : ep.requestHeaders) sb.append(h).append("\n");
        if (ep.requestBodyJson != null) sb.append("Content-Type: application/json\n");
        sb.append("Accept: application/json\n");

        if (ep.requestBodyJson != null) {
            sb.append("\n").append(ep.requestBodyJson);
        }

        return sb.toString();
    }

    // Spring {id} → HTTP Client {{id}}
    private static String toHttpClientPath(String path) {
        return path.replaceAll("\\{([^}]+)}", "{{$1}}");
    }
}
