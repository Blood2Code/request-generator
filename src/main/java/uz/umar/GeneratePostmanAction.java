package uz.umar;

import com.intellij.psi.PsiFile;

public class GeneratePostmanAction extends PaidGenerateAction {

    @Override
    protected String generate(PsiFile file) {
        return PostmanGenerator.generate(file);
    }

    @Override
    protected String fileSuffix() {
        return ".postman.json";
    }
}
