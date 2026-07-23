package uz.umar;

import com.intellij.psi.*;
import java.util.*;
import java.util.regex.*;

/** Emits a Spring 6.1+ RestClient Java client class from a @RestController. */
public class RestClientGenerator {

    public static String generate(PsiFile file) {
        List<EndpointModel> endpoints = SpringControllerParser.parse(file);
        String controllerName = file.getVirtualFile().getNameWithoutExtension();
        String baseUrl = BaseUrlResolver.resolve(file.getProject());
        String className = controllerName + "Client";

        boolean hasBody      = endpoints.stream().anyMatch(ep -> ep.requestBodyPsiType != null);
        boolean hasMultipart = endpoints.stream().anyMatch(EndpointModel::isMultipart);
        boolean hasArray     = endpoints.stream().anyMatch(ep -> ep.responsePsiType != null
                && ResponseTypeResolver.isArray(ep.responsePsiType));
        boolean hasBearer    = endpoints.stream().anyMatch(ep -> "bearer".equals(ep.authType));
        boolean hasBasic     = endpoints.stream().anyMatch(ep -> "basic".equals(ep.authType));

        StringBuilder sb = new StringBuilder();
        sb.append("// Generated from ").append(controllerName).append("\n");
        sb.append("import org.springframework.web.client.RestClient;\n");
        if (hasArray)                 sb.append("import org.springframework.core.ParameterizedTypeReference;\n");
        if (hasBody || hasMultipart)  sb.append("import org.springframework.http.MediaType;\n");
        if (hasMultipart) {
            sb.append("import org.springframework.util.LinkedMultiValueMap;\n");
            sb.append("import org.springframework.util.MultiValueMap;\n");
            sb.append("import org.springframework.core.io.FileSystemResource;\n");
        }
        if (hasArray) sb.append("import java.util.List;\n");
        sb.append("\n");

        sb.append("public class ").append(className).append(" {\n\n");
        sb.append("    private final RestClient client = RestClient.create(\"").append(baseUrl).append("\");\n");
        if (hasBearer) sb.append("    private static final String TOKEN = \"your_token_here\";\n");
        if (hasBasic)  sb.append("    private static final String CREDENTIALS = \"base64_encoded_user_password\";\n");
        sb.append("\n");

        for (EndpointModel ep : endpoints) {
            sb.append(buildMethod(ep));
        }

        sb.append("}\n");
        return sb.toString();
    }

    private static String buildMethod(EndpointModel ep) {
        List<String> pathVars = extractPathVars(ep.path);
        String returnType = responseJavaType(ep);
        String method = ep.httpMethod.toLowerCase();

        // Method parameters
        List<String> params = new ArrayList<>();
        for (String var : pathVars) {
            PsiType t = ep.pathVarTypes.get(var);
            params.add((t != null ? t.getPresentableText() : "Long") + " " + var);
        }
        for (String qp : ep.queryParams) {
            String[] kv = qp.split("=", 2);
            params.add(inferJavaType(kv.length > 1 ? kv[1] : "") + " " + kv[0]);
        }
        if (ep.isMultipart()) {
            for (MultipartPart part : ep.multipartParts) {
                params.add((part.isFile ? "FileSystemResource" : "String") + " " + part.name);
            }
        } else if (ep.requestBodyPsiType != null) {
            params.add(ep.requestBodyPsiType.getPresentableText() + " data");
        }

        StringBuilder sb = new StringBuilder();
        sb.append("    public ").append(returnType).append(" ").append(ep.methodName)
          .append("(").append(String.join(", ", params)).append(") {\n");

        // Multipart body assembly up front
        if (ep.isMultipart()) {
            sb.append("        MultiValueMap<String, Object> parts = new LinkedMultiValueMap<>();\n");
            for (MultipartPart part : ep.multipartParts) {
                sb.append("        parts.add(\"").append(part.name).append("\", ").append(part.name).append(");\n");
            }
        }

        // Fluent call chain
        List<String> chain = new ArrayList<>();
        chain.add("client." + method + "()");
        chain.add(buildUri(ep, pathVars));
        if ("bearer".equals(ep.authType)) chain.add(".header(\"Authorization\", \"Bearer \" + TOKEN)");
        else if ("basic".equals(ep.authType)) chain.add(".header(\"Authorization\", \"Basic \" + CREDENTIALS)");
        if (ep.isMultipart()) {
            chain.add(".contentType(MediaType.MULTIPART_FORM_DATA)");
            chain.add(".body(parts)");
        } else if (ep.requestBodyPsiType != null) {
            chain.add(".contentType(MediaType.APPLICATION_JSON)");
            chain.add(".body(data)");
        }
        chain.add(".retrieve()");
        if (returnType.equals("void")) {
            chain.add(".toBodilessEntity()");
        } else if (ep.responsePsiType != null && ResponseTypeResolver.isArray(ep.responsePsiType)) {
            chain.add(".body(new ParameterizedTypeReference<" + returnType + ">() {})");
        } else {
            chain.add(".body(" + returnType + ".class)");
        }

        String prefix = returnType.equals("void") ? "        " : "        return ";
        sb.append(prefix).append(chain.get(0)).append("\n");
        for (int i = 1; i < chain.size(); i++) {
            sb.append("                ").append(chain.get(i));
            sb.append(i == chain.size() - 1 ? ";\n" : "\n");
        }

        sb.append("    }\n\n");
        return sb.toString();
    }

    private static String buildUri(EndpointModel ep, List<String> pathVars) {
        if (ep.queryParams.isEmpty()) {
            if (pathVars.isEmpty()) return ".uri(\"" + ep.path + "\")";
            return ".uri(\"" + ep.path + "\", " + String.join(", ", pathVars) + ")";
        }
        // Query params present → use a UriBuilder lambda
        StringBuilder sb = new StringBuilder(".uri(uriBuilder -> uriBuilder\n");
        sb.append("                        .path(\"").append(ep.path).append("\")\n");
        for (String qp : ep.queryParams) {
            String name = qp.split("=", 2)[0];
            sb.append("                        .queryParam(\"").append(name).append("\", ").append(name).append(")\n");
        }
        sb.append("                        .build(").append(String.join(", ", pathVars)).append("))");
        return sb.toString();
    }

    private static String responseJavaType(EndpointModel ep) {
        if (ep.responsePsiType == null) return "void";
        if (ResponseTypeResolver.isArray(ep.responsePsiType)) {
            PsiType el = ResponseTypeResolver.element(ep.responsePsiType);
            return "List<" + (el != null ? el.getPresentableText() : "Object") + ">";
        }
        PsiType t = ResponseTypeResolver.unwrap(ep.responsePsiType);
        String name = t.getPresentableText();
        if (name.equals("Void")) return "void";
        return name;
    }

    private static String inferJavaType(String val) {
        if (val.equals("true") || val.equals("false")) return "boolean";
        try { Long.parseLong(val); return "long"; } catch (NumberFormatException ignored) {}
        try { Double.parseDouble(val); return "double"; } catch (NumberFormatException ignored) {}
        return "String";
    }

    private static List<String> extractPathVars(String path) {
        List<String> vars = new ArrayList<>();
        Matcher m = Pattern.compile("\\{([^}]+)}").matcher(path);
        while (m.find()) vars.add(m.group(1));
        return vars;
    }
}
