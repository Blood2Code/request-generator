package uz.umar;

import com.intellij.psi.PsiFile;

public class GenerateOpenApiAction extends PaidGenerateAction {

    @Override
    protected String generate(PsiFile file) {
        return OpenApiGenerator.generate(file);
    }

    @Override
    protected String fileSuffix() {
        return ".openapi.yaml";
    }
}
