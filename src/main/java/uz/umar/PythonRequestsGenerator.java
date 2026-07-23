package uz.umar;

import com.intellij.psi.PsiFile;
import java.util.*;
import java.util.regex.*;
import java.util.stream.Collectors;

/** Emits a Python `requests` client module from a @RestController. */
public class PythonRequestsGenerator {

    public static String generate(PsiFile file) {
        List<EndpointModel> endpoints = SpringControllerParser.parse(file);
        String controllerName = file.getVirtualFile().getNameWithoutExtension();
        String baseUrl = BaseUrlResolver.resolve(file.getProject());

        StringBuilder sb = new StringBuilder();
        sb.append("# Generated from ").append(controllerName).append("\n");
        sb.append("import requests\n\n");
        sb.append("BASE_URL = \"").append(baseUrl).append("\"\n");
        if (endpoints.stream().anyMatch(ep -> "bearer".equals(ep.authType)))
            sb.append("TOKEN = \"your_token_here\"\n");
        if (endpoints.stream().anyMatch(ep -> "basic".equals(ep.authType)))
            sb.append("CREDENTIALS = \"base64_encoded_user_password\"\n");
        sb.append("\n");

        for (EndpointModel ep : endpoints) {
            sb.append(buildFunction(ep)).append("\n\n");
        }

        return sb.toString().trim() + "\n";
    }

    private static String buildFunction(EndpointModel ep) {
        List<String> pathVars = extractPathVars(ep.path);

        // Python requires default-valued params to trail; bucket accordingly.
        List<String> required = new ArrayList<>();
        List<String> optional = new ArrayList<>();
        required.addAll(pathVars);
        for (String qp : ep.queryParams) {
            String[] kv = qp.split("=", 2);
            String name = kv[0];
            String val  = kv.length > 1 ? kv[1] : "";
            if (!val.isEmpty()) optional.add(name + "=" + pyLiteral(val));
            else if (ep.isOptional(name)) optional.add(name + "=None");
            else required.add(name);
        }
        if (ep.isMultipart()) {
            for (MultipartPart part : ep.multipartParts) required.add(part.name);
        } else if (ep.requestBodyJson != null) {
            required.add("data");
        }
        List<String> params = new ArrayList<>(required);
        params.addAll(optional);

        String pyPath = "f\"{BASE_URL}" + ep.path + "\"";  // Spring {id} == Python f-string {id}
        StringBuilder sb = new StringBuilder();
        sb.append("def ").append(snake(ep.methodName)).append("(").append(String.join(", ", params)).append("):\n");

        // Multipart file handles up front
        if (ep.isMultipart()) {
            for (MultipartPart part : ep.multipartParts) {
                if (part.isFile) {
                    sb.append("    ").append(part.name).append("_fh = open(").append(part.name).append(", \"rb\")\n");
                }
            }
        }

        sb.append("    resp = requests.").append(ep.httpMethod.toLowerCase()).append("(\n");
        sb.append("        ").append(pyPath).append(",\n");
        sb.append("        headers=").append(buildHeaders(ep)).append(",\n");
        if (!ep.queryParams.isEmpty()) {
            String dict = ep.queryParams.stream().map(qp -> qp.split("=", 2)[0])
                    .map(n -> "\"" + n + "\": " + n).collect(Collectors.joining(", "));
            sb.append("        params={").append(dict).append("},\n");
        }
        if (ep.isMultipart()) {
            String files = ep.multipartParts.stream().filter(p -> p.isFile)
                    .map(p -> "\"" + p.name + "\": " + p.name + "_fh").collect(Collectors.joining(", "));
            String texts = ep.multipartParts.stream().filter(p -> !p.isFile)
                    .map(p -> "\"" + p.name + "\": " + p.name).collect(Collectors.joining(", "));
            if (!files.isEmpty()) sb.append("        files={").append(files).append("},\n");
            if (!texts.isEmpty()) sb.append("        data={").append(texts).append("},\n");
        } else if (ep.requestBodyJson != null) {
            sb.append("        json=data,\n");
        }
        sb.append("    )\n");
        sb.append("    return resp.json()");
        return sb.toString();
    }

    private static String buildHeaders(EndpointModel ep) {
        List<String> entries = new ArrayList<>();
        if ("bearer".equals(ep.authType)) entries.add("\"Authorization\": f\"Bearer {TOKEN}\"");
        else if ("basic".equals(ep.authType)) entries.add("\"Authorization\": f\"Basic {CREDENTIALS}\"");
        for (String h : ep.requestHeaders) {
            String[] kv = h.split(": ", 2);
            entries.add("\"" + kv[0] + "\": \"" + (kv.length > 1 ? kv[1] : "") + "\"");
        }
        // requests sets multipart Content-Type (with boundary) itself
        if (!ep.isMultipart() && ep.requestBodyJson != null) entries.add("\"Content-Type\": \"application/json\"");
        entries.add("\"Accept\": \"application/json\"");
        return "{" + String.join(", ", entries) + "}";
    }

    private static String pyLiteral(String val) {
        if (val.equals("true")) return "True";
        if (val.equals("false")) return "False";
        try { Long.parseLong(val); return val; } catch (NumberFormatException ignored) {}
        try { Double.parseDouble(val); return val; } catch (NumberFormatException ignored) {}
        return "\"" + val + "\"";
    }

    // camelCase → snake_case
    private static String snake(String name) {
        return name.replaceAll("([a-z0-9])([A-Z])", "$1_$2").toLowerCase();
    }

    private static List<String> extractPathVars(String path) {
        List<String> vars = new ArrayList<>();
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(path);
        while (m.find()) vars.add(m.group(1));
        return vars;
    }
}
