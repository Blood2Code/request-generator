package uz.umar;

import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * Bulk-generates .http files for every @RestController under the selected directory.
 * Shown in the Project View popup on directories/packages.
 */
public class GenerateAllHttpAction extends AnAction {

    private static final String REST_CONTROLLER = "org.springframework.web.bind.annotation.RestController";

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        VirtualFile vf = e.getData(CommonDataKeys.VIRTUAL_FILE);
        e.getPresentation().setEnabledAndVisible(vf != null && vf.isDirectory());
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        VirtualFile dir = e.getData(CommonDataKeys.VIRTUAL_FILE);
        if (project == null || dir == null) return;

        String basePath = project.getBasePath();
        VirtualFile projectRoot = basePath != null ? LocalFileSystem.getInstance().findFileByPath(basePath) : null;
        if (projectRoot == null) return;

        List<PsiFile> controllers = new ArrayList<>();
        collectControllers(dir, PsiManager.getInstance(project), controllers);

        if (controllers.isEmpty()) {
            Messages.showInfoMessage(project, "No @RestController classes found under \"" + dir.getName() + "\".",
                                     "HTTP Request Generator");
            return;
        }

        int[] written = {0};
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                for (PsiFile jf : controllers) {
                    String name = jf.getVirtualFile().getNameWithoutExtension();
                    String content = HttpTemplateGenerator.generate(jf);
                    HttpOutput.write(project, projectRoot, name, name + ".http", content, this);
                    written[0]++;
                }
            } catch (IOException ex) {
                Messages.showErrorDialog(project, "Failed to generate files: " + ex.getMessage(), "Error");
            }
        });

        Messages.showInfoMessage(project, "Generated .http for " + written[0] + " controller(s).",
                                 "HTTP Request Generator");
    }

    private void collectControllers(VirtualFile dir, PsiManager pm, List<PsiFile> out) {
        for (VirtualFile child : dir.getChildren()) {
            if (child.isDirectory()) {
                collectControllers(child, pm, out);
            } else if ("java".equals(child.getExtension()) || "kt".equals(child.getExtension())) {
                PsiFile pf = pm.findFile(child);
                if (pf == null) continue;
                for (PsiClass cls : ControllerClasses.from(pf)) {
                    if (cls.getAnnotation(REST_CONTROLLER) != null) {
                        out.add(pf);
                        break;
                    }
                }
            }
        }
    }
}
