package uz.umar;

import com.intellij.ide.BrowserUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.ui.Messages;
import org.jetbrains.annotations.NotNull;

public abstract class PaidGenerateAction extends BaseGenerateAction {

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        if (LicenseChecker.hasLicense()) {
            super.actionPerformed(e);
            return;
        }

        int choice = Messages.showOkCancelDialog(
            e.getProject(),
            "<html>" +
            "<b>HTTP Request Generator Pro</b> — $0.99<br><br>" +
            "<b>Free:</b>  .http &nbsp;(JetBrains HTTP Client)<br>" +
            "<b>Pro:</b> &nbsp; cURL &nbsp;·&nbsp; Postman &nbsp;·&nbsp; TypeScript Axios &nbsp;·&nbsp; JS fetch &nbsp;·&nbsp; OpenAPI YAML<br><br>" +
            "Get the license on JetBrains Marketplace." +
            "</html>",
            "Pro Feature",
            "Get Pro  →",
            "Cancel",
            Messages.getInformationIcon()
        );

        if (choice == Messages.OK) {
            BrowserUtil.browse("https://plugins.jetbrains.com/plugin/" + LicenseChecker.PLUGIN_ID);
        }
    }
}
