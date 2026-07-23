package uz.umar;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.search.FilenameIndex;
import com.intellij.psi.search.GlobalSearchScope;

import java.nio.charset.StandardCharsets;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Derives the base URL from a Spring Boot project's application config
 * (server.port + server.servlet.context-path). Falls back to http://localhost:8080.
 */
public final class BaseUrlResolver {

    private BaseUrlResolver() {}

    private static final String DEFAULT = "http://localhost:8080";

    public static String resolve(Project project) {
        if (project == null) return DEFAULT;
        try {
            for (String name : new String[]{"application.properties", "application.yml", "application.yaml"}) {
                for (VirtualFile vf : FilenameIndex.getVirtualFilesByName(name, GlobalSearchScope.projectScope(project))) {
                    // Skip test config; prefer main
                    if (vf.getPath().contains("/test/")) continue;
                    String text = new String(vf.contentsToByteArray(), StandardCharsets.UTF_8);
                    String url = name.endsWith(".properties") ? fromProperties(text) : fromYaml(text);
                    if (url != null) return url;
                }
            }
        } catch (Exception ignored) {
            // fall through to default
        }
        return DEFAULT;
    }

    private static String fromProperties(String text) {
        String port = null, ctx = null;
        for (String raw : text.split("\n")) {
            String line = raw.trim();
            if (line.startsWith("#") || !line.contains("=")) continue;
            String[] kv = line.split("=", 2);
            String key = kv[0].trim();
            String val = cleanValue(kv[1]);
            if (key.equals("server.port")) port = val;
            else if (key.equals("server.servlet.context-path") || key.equals("server.context-path")) ctx = val;
        }
        return (port != null || ctx != null) ? buildUrl(port, ctx) : null;
    }

    private static String fromYaml(String text) {
        String[] lines = text.split("\n");
        String port = null, ctx = null;
        int serverIndent = -1;
        boolean inServer = false;
        for (String raw : lines) {
            if (raw.isBlank() || raw.trim().startsWith("#")) continue;
            int indent = indentOf(raw);
            String line = raw.trim();

            if (line.matches("server:\\s*")) { inServer = true; serverIndent = indent; continue; }
            if (inServer && indent <= serverIndent) inServer = false; // left the server: block

            if (inServer) {
                Matcher p = Pattern.compile("^port:\\s*(\\S+)").matcher(line);
                if (p.find()) port = cleanValue(p.group(1));
                Matcher c = Pattern.compile("^context-path:\\s*(\\S+)").matcher(line);
                if (c.find()) ctx = cleanValue(c.group(1));
            }
        }
        return (port != null || ctx != null) ? buildUrl(port, ctx) : null;
    }

    private static String buildUrl(String port, String ctx) {
        String p = port != null ? port : "8080";
        String c = ctx != null ? ctx : "";
        if (!c.isEmpty() && !c.startsWith("/")) c = "/" + c;
        if (c.endsWith("/")) c = c.substring(0, c.length() - 1);
        return "http://localhost:" + p + c;
    }

    /** Strips quotes and resolves ${VAR:default} placeholders to their default. */
    private static String cleanValue(String v) {
        v = v.trim().replaceAll("^[\"']|[\"']$", "");
        Matcher m = Pattern.compile("\\$\\{[^:}]+:([^}]*)}").matcher(v);
        if (m.matches()) return m.group(1);
        return v;
    }

    private static int indentOf(String line) {
        int i = 0;
        while (i < line.length() && line.charAt(i) == ' ') i++;
        return i;
    }
}
