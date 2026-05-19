package uz.umar;

import com.intellij.psi.PsiJavaFile;

public class GenerateTypeScriptAxiosAction extends PaidGenerateAction {

    @Override
    protected String generate(PsiJavaFile javaFile) {
        return TypeScriptAxiosGenerator.generate(javaFile);
    }

    @Override
    protected String fileSuffix() {
        return ".ts";
    }
}
