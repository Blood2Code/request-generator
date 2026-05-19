package uz.umar;

import com.intellij.psi.PsiJavaFile;

public class GeneratePostmanAction extends PaidGenerateAction {

    @Override
    protected String generate(PsiJavaFile javaFile) {
        return PostmanGenerator.generate(javaFile);
    }

    @Override
    protected String fileSuffix() {
        return ".postman.json";
    }
}
