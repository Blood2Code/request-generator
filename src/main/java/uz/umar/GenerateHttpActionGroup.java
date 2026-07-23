package uz.umar;

import com.intellij.openapi.actionSystem.*;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
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
        if (psiFile == null) return false;
        for (PsiClass cls : ControllerClasses.from(psiFile)) {
            if (cls.getAnnotation("org.springframework.web.bind.annotation.RestController") != null) {
                return true;
            }
        }
        return false;
    }
}
