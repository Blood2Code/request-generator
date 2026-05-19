package uz.umar;

import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.command.WriteCommandAction;
import com.intellij.openapi.fileEditor.FileEditorManager;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.Messages;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiJavaFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

public abstract class BaseGenerateAction extends AnAction {

    protected abstract String generate(PsiJavaFile javaFile);
    protected abstract String fileSuffix();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        var psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || !(psiFile instanceof PsiJavaFile javaFile)) return;

        String content  = generate(javaFile);
        String fileName = javaFile.getVirtualFile().getNameWithoutExtension() + fileSuffix();

        String basePath = project.getBasePath();
        if (basePath == null) return;
        VirtualFile projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath);
        if (projectRoot == null) return;

        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                VirtualFile httpDir = projectRoot.findChild("http");
                if (httpDir == null) httpDir = projectRoot.createChildDirectory(this, "http");

                String controllerName = javaFile.getVirtualFile().getNameWithoutExtension();
                VirtualFile controllerDir = httpDir.findChild(controllerName);
                if (controllerDir == null) controllerDir = httpDir.createChildDirectory(this, controllerName);

                VirtualFile existing = controllerDir.findChild(fileName);
                VirtualFile outFile  = (existing != null)
                        ? existing
                        : controllerDir.createChildData(this, fileName);
                outFile.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
                FileEditorManager.getInstance(project).openFile(outFile, true);
            } catch (IOException ex) {
                Messages.showErrorDialog(project, "Failed to generate file: " + ex.getMessage(), "Error");
            }
        });
    }
}
