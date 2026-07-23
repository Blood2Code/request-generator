package uz.umar;

import com.intellij.psi.PsiFile;
import java.util.List;

/** Emits a .env file with the base URL and any auth variables the controller needs. */
public class EnvGenerator {

    public static String generate(PsiFile file) {
        List<EndpointModel> endpoints = SpringControllerParser.parse(file);
        String controllerName = file.getVirtualFile().getNameWithoutExtension();
        String baseUrl = BaseUrlResolver.resolve(file.getProject());

        StringBuilder sb = new StringBuilder();
        sb.append("# Generated from ").append(controllerName).append("\n");
        sb.append("BASE_URL=").append(baseUrl).append("\n");
        if (endpoints.stream().anyMatch(ep -> "bearer".equals(ep.authType)))
            sb.append("TOKEN=your_token_here\n");
        if (endpoints.stream().anyMatch(ep -> "basic".equals(ep.authType)))
            sb.append("CREDENTIALS=base64_encoded_user_password\n");

        return sb.toString().trim();
    }
}
