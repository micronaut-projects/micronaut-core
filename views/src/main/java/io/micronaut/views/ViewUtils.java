package io.micronaut.views;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Utility methods for views.
 *
 * @author James Kleeh
 * @since 1.1.0
 */
public class ViewUtils {

    /**
     * Returns a path with unix style folder
     * separators that starts and ends with a "/".
     *
     * @param path The path to normalizeFile
     * @return The normalized path
     */
    @Nonnull
    public static String normalizeFolder(@Nullable String path) {
        if (path == null) {
            path = "";
        } else {
            path = normalizeFile(path, null);
        }
        if (!path.endsWith("/")) {
            path = path + "/";
        }
        return path;
    }

    /**
     * Returns a path that is converted to unix style file separators
     * and never starts with a "/". If an extension is provided and the
     * path ends with the extension, the extension will be stripped.
     * The extension parameter supports extensions that do and do not
     * begin with a ".".
     *
     * @param path The path to normalizeFile
     * @param extension The file extension
     * @return The normalized path
     */
    @Nonnull
    public static String normalizeFile(@Nonnull String path, String extension) {
        path = path.replace("\\", "/");
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        if (extension != null && !extension.startsWith(".")) {
            extension = "." + extension;
            if (path.endsWith(extension)) {
                int idx = path.indexOf(extension);
                path = path.substring(0, idx);
            }
        }
        return path;
    }
}
