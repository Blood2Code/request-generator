package uz.umar;

import com.intellij.psi.PsiFile;

public class GenerateJsFetchAction extends PaidGenerateAction {

    @Override
    protected String generate(PsiFile file) {
        return JavaScriptFetchGenerator.generate(file);
    }

    @Override
    protected String fileSuffix() {
        return ".js";
    }
}
