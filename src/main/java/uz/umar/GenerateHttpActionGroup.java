package uz.umar;

import com.intellij.openapi.actionSystem.*;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

public class GenerateHttpActionGroup extends DefaultActionGroup {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        e.getPresentation().setEnabledAndVisible(isRestControllerFile(psiFile));
    }

    private boolean isRestControllerFile(PsiFile psiFile) {
        if (!(psiFile instanceof PsiJavaFile javaFile)) return false;
        for (var cls : javaFile.getClasses()) {
            if (cls.getAnnotation("org.springframework.web.bind.annotation.RestController") != null) {
                return true;
            }
        }
        return false;
    }
}
