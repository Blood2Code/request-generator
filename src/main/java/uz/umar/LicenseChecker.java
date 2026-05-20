package uz.umar;

import com.intellij.ui.LicensingFacade;

public class LicenseChecker {

    static final String PLUGIN_ID = "uz.umar.http-request-generator";

    // true = dev mode (all pro features enabled), false = prod (real license check)
    private static final boolean DEV_MODE = true;

    // ⬇ After first upload to JetBrains Marketplace, replace with the real URL:
    // https://plugins.jetbrains.com/plugin/{numeric-id}-request-generator
    static final String MARKETPLACE_URL =
            "https://plugins.jetbrains.com/plugin/31844-http-request-generator/versions/stable/1051528";

    public static boolean hasLicense() {
        if (DEV_MODE) return true;
        try {
            LicensingFacade facade = LicensingFacade.getInstance();
            if (facade == null) return true;
            String stamp = facade.getConfirmationStamp(PLUGIN_ID);
            return stamp != null && !stamp.isEmpty();
        } catch (Throwable t) {
            return true;
        }
    }
}
