package uz.umar;

import com.intellij.psi.PsiType;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EndpointModel {
    public final String methodName;
    public final String httpMethod;
    public final String path;             // Spring format: /api/users/{id}
    public final List<String> queryParams;    // ["page=0", "size=10"]
    public final List<String> requestHeaders; // ["X-Tenant-Id: value"]
    public final String requestBodyJson;      // null if no body
    public final PsiType requestBodyPsiType;  // null if no body — used by TS/OpenAPI generators
    public final String authName;             // @SecurityRequirement name, e.g. "bearerAuth"; null if none
    public final String authType;             // "bearer" | "basic"; null if none
    public final Map<String, PsiType> pathVarTypes; // path-var name → declared @PathVariable type
    public final List<MultipartPart> multipartParts; // @RequestPart / MultipartFile parts; empty if none
    public final Set<String> optionalQueryParams; // query-param names that are not required
    public final PsiType responsePsiType;     // method return type (raw, pre-unwrap); null if void

    public EndpointModel(String methodName, String httpMethod, String path,
                         List<String> queryParams, List<String> requestHeaders,
                         String requestBodyJson, PsiType requestBodyPsiType,
                         String authName, String authType, Map<String, PsiType> pathVarTypes,
                         List<MultipartPart> multipartParts, Set<String> optionalQueryParams,
                         PsiType responsePsiType) {
        this.methodName = methodName;
        this.httpMethod = httpMethod;
        this.path = path;
        this.queryParams = queryParams;
        this.requestHeaders = requestHeaders;
        this.requestBodyJson = requestBodyJson;
        this.requestBodyPsiType = requestBodyPsiType;
        this.authName = authName;
        this.authType = authType;
        this.pathVarTypes = pathVarTypes;
        this.multipartParts = multipartParts;
        this.optionalQueryParams = optionalQueryParams;
        this.responsePsiType = responsePsiType;
    }

    public boolean isOptional(String queryParamName) {
        return optionalQueryParams.contains(queryParamName);
    }

    public boolean requiresAuth() {
        return authType != null;
    }

    public boolean isMultipart() {
        return multipartParts != null && !multipartParts.isEmpty();
    }
}
