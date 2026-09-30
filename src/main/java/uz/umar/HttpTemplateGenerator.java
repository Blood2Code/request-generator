package uz.umar;

import com.intellij.psi.PsiFile;
import java.util.List;

public class HttpTemplateGenerator {

    public static String generate(PsiFile file) {
        List<EndpointModel> endpoints = SpringControllerParser.parse(file);
        String baseUrl = BaseUrlResolver.resolve(file.getProject());

        StringBuilder sb = new StringBuilder();
        sb.append("@baseUrl = ").append(baseUrl).append("\n");
        if (endpoints.stream().anyMatch(ep -> "bearer".equals(ep.authType)))
            sb.append("@token = your_token_here\n");
        if (endpoints.stream().anyMatch(ep -> "basic".equals(ep.authType)))
            sb.append("@credentials = base64_encoded_user_password\n");
        sb.append("\n");

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

        if ("bearer".equals(ep.authType)) sb.append("Authorization: Bearer {{token}}\n");
        else if ("basic".equals(ep.authType)) sb.append("Authorization: Basic {{credentials}}\n");
        for (String h : ep.requestHeaders) sb.append(h).append("\n");
        if (ep.isMultipart()) sb.append("Content-Type: multipart/form-data; boundary=WebAppBoundary\n");
        else if (ep.requestBodyJson != null) sb.append("Content-Type: application/json\n");
        sb.append("Accept: application/json\n");

        if (ep.isMultipart()) {
            sb.append("\n").append(buildMultipartBody(ep));
        } else if (ep.requestBodyJson != null) {
            sb.append("\n").append(ep.requestBodyJson);
        }

        String resp = JsonBodyBuilder.responseExample(ep.responsePsiType);
        if (resp != null) {
            sb.append("\n\n### Example 200 response:\n");
            sb.append(resp.lines().map(l -> "### " + l).collect(java.util.stream.Collectors.joining("\n")));
        }

        return sb.toString();
    }

    private static String buildMultipartBody(EndpointModel ep) {
        StringBuilder sb = new StringBuilder();
        for (MultipartPart part : ep.multipartParts) {
            sb.append("--WebAppBoundary\n");
            if (part.isFile) {
                sb.append("Content-Disposition: form-data; name=\"").append(part.name)
                  .append("\"; filename=\"").append(part.name).append("\"\n");
                sb.append("Content-Type: application/octet-stream\n\n");
                sb.append("< ./").append(part.name).append("\n");
            } else {
                sb.append("Content-Disposition: form-data; name=\"").append(part.name).append("\"\n\n");
                sb.append(part.name).append("_value\n");
            }
        }
        sb.append("--WebAppBoundary--");
        return sb.toString();
    }

    // Spring {id} → HTTP Client {{id}}
    private static String toHttpClientPath(String path) {
        return path.replaceAll("\\{([^}]+)}", "{{$1}}");
    }
}
