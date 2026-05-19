package uz.umar;

import com.intellij.psi.PsiJavaFile;

public class GenerateHttpFileAction extends BaseGenerateAction {

    @Override
    protected String generate(PsiJavaFile javaFile) {
        return HttpTemplateGenerator.generate(javaFile);
    }

    @Override
    protected String fileSuffix() {
        return ".http";
    }
}
