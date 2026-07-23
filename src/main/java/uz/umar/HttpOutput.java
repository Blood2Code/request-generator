package uz.umar;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/** Shared writer: places generated files under {projectRoot}/http/{controllerName}/. */
public final class HttpOutput {

    private HttpOutput() {}

    public static VirtualFile write(Project project, VirtualFile projectRoot, String controllerName,
                                    String fileName, String content, Object requestor) throws IOException {
        VirtualFile httpDir = projectRoot.findChild("http");
        if (httpDir == null) httpDir = projectRoot.createChildDirectory(requestor, "http");

        VirtualFile controllerDir = httpDir.findChild(controllerName);
        if (controllerDir == null) controllerDir = httpDir.createChildDirectory(requestor, controllerName);

        VirtualFile existing = controllerDir.findChild(fileName);
        VirtualFile outFile = (existing != null) ? existing : controllerDir.createChildData(requestor, fileName);
        outFile.setBinaryContent(content.getBytes(StandardCharsets.UTF_8));
        return outFile;
    }
}
