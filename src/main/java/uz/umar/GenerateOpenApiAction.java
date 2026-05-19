package uz.umar;

import com.intellij.psi.PsiJavaFile;

public class GenerateOpenApiAction extends PaidGenerateAction {

    @Override
    protected String generate(PsiJavaFile javaFile) {
        return OpenApiGenerator.generate(javaFile);
    }

    @Override
    protected String fileSuffix() {
        return ".openapi.yaml";
    }
}
