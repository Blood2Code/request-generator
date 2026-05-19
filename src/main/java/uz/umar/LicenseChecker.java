package uz.umar;

import com.intellij.ui.LicensingFacade;

public class LicenseChecker {

    static final String PLUGIN_ID = "uz.umar.http-request-generator";

    public static boolean hasLicense() {
        try {
            LicensingFacade facade = LicensingFacade.getInstance();
            // null means the IDE doesn't support licensing (dev/sandbox) → allow
            if (facade == null) return true;
            String stamp = facade.getConfirmationStamp(PLUGIN_ID);
            return stamp != null && !stamp.isEmpty();
        } catch (Throwable t) {
            return true; // any unexpected error → don't block the user
        }
    }
}
