package uz.umar;

import com.intellij.psi.PsiJavaFile;

public class GenerateCurlAction extends PaidGenerateAction {

    @Override
    protected String generate(PsiJavaFile javaFile) {
        return CurlGenerator.generate(javaFile);
    }

    @Override
    protected String fileSuffix() {
        return ".sh";
    }
}
