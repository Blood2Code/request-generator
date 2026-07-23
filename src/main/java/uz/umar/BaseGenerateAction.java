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
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

import java.io.IOException;

public abstract class BaseGenerateAction extends AnAction {

    protected abstract String generate(PsiFile file);
    protected abstract String fileSuffix();

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (project == null || psiFile == null || psiFile.getVirtualFile() == null) return;

        String content  = generate(psiFile);
        String fileName = psiFile.getVirtualFile().getNameWithoutExtension() + fileSuffix();

        String basePath = project.getBasePath();
        if (basePath == null) return;
        VirtualFile projectRoot = LocalFileSystem.getInstance().findFileByPath(basePath);
        if (projectRoot == null) return;

        String controllerName = psiFile.getVirtualFile().getNameWithoutExtension();
        WriteCommandAction.runWriteCommandAction(project, () -> {
            try {
                VirtualFile outFile = HttpOutput.write(project, projectRoot, controllerName, fileName, content, this);
                FileEditorManager.getInstance(project).openFile(outFile, true);
            } catch (IOException ex) {
                Messages.showErrorDialog(project, "Failed to generate file: " + ex.getMessage(), "Error");
            }
        });
    }
}
