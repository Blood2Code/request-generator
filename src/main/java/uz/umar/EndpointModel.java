package uz.umar;

import com.intellij.psi.PsiType;
import java.util.List;

public class EndpointModel {
    public final String methodName;
    public final String httpMethod;
    public final String path;             // Spring format: /api/users/{id}
    public final List<String> queryParams;    // ["page=0", "size=10"]
    public final List<String> requestHeaders; // ["X-Tenant-Id: value"]
    public final String requestBodyJson;      // null if no body
    public final PsiType requestBodyPsiType;  // null if no body — used by TS/OpenAPI generators

    public EndpointModel(String methodName, String httpMethod, String path,
                         List<String> queryParams, List<String> requestHeaders,
                         String requestBodyJson, PsiType requestBodyPsiType) {
        this.methodName = methodName;
        this.httpMethod = httpMethod;
        this.path = path;
        this.queryParams = queryParams;
        this.requestHeaders = requestHeaders;
        this.requestBodyJson = requestBodyJson;
        this.requestBodyPsiType = requestBodyPsiType;
    }
}
