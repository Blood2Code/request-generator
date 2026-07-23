package uz.umar;

import com.intellij.psi.PsiFile;

public class GenerateHttpFileAction extends BaseGenerateAction {

    @Override
    protected String generate(PsiFile file) {
        return HttpTemplateGenerator.generate(file);
    }

    @Override
    protected String fileSuffix() {
        return ".http";
    }
}
