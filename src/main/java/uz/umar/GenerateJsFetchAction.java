package uz.umar;

import com.intellij.psi.PsiJavaFile;

public class GenerateJsFetchAction extends PaidGenerateAction {

    @Override
    protected String generate(PsiJavaFile javaFile) {
        return JavaScriptFetchGenerator.generate(javaFile);
    }

    @Override
    protected String fileSuffix() {
        return ".js";
    }
}
