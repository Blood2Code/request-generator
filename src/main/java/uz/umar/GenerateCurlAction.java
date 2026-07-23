package uz.umar;

import com.intellij.psi.PsiFile;

public class GenerateCurlAction extends PaidGenerateAction {

    @Override
    protected String generate(PsiFile file) {
        return CurlGenerator.generate(file);
    }

    @Override
    protected String fileSuffix() {
        return ".sh";
    }
}
