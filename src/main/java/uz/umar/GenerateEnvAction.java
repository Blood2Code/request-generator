package uz.umar;

import com.intellij.psi.PsiFile;

public class GenerateEnvAction extends BaseGenerateAction {

    @Override
    protected String generate(PsiFile file) {
        return EnvGenerator.generate(file);
    }

    @Override
    protected String fileSuffix() {
        return ".env";
    }
}
