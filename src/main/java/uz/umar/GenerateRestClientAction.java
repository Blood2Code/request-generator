package uz.umar;

import com.intellij.psi.PsiFile;

public class GenerateRestClientAction extends PaidGenerateAction {

    @Override
    protected String generate(PsiFile file) {
        return RestClientGenerator.generate(file);
    }

    @Override
    protected String fileSuffix() {
        return "Client.java";
    }
}
