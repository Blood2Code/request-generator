package uz.umar;

import com.intellij.psi.PsiFile;

public class GeneratePythonAction extends PaidGenerateAction {

    @Override
    protected String generate(PsiFile file) {
        return PythonRequestsGenerator.generate(file);
    }

    @Override
    protected String fileSuffix() {
        return ".py";
    }
}
