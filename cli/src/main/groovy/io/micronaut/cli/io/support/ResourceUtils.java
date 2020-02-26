/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.cli.io.support;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Iterator;
import java.util.LinkedList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Utility methods for resource handling / figuring out class names.
 *
 * @author Graeme Rocher
 * @author Juergen Hoeller
 * @since 1.0
 */
@SuppressWarnings("MagicNumber")
public class ResourceUtils {

    /**
     * The JAR URL separator.
     */
    public static final String JAR_URL_SEPARATOR = "!/";

    /**
     * URL prefix for loading from the file system: "file:".
     */
    public static final String FILE_URL_PREFIX = "file:";

    /**
     * URL protocol for a file in the file system: "file".
     */
    public static final String URL_PROTOCOL_FILE = "file";

    /**
     * URL protocol for an entry from a jar file: "jar".
     */
    public static final String URL_PROTOCOL_JAR = "jar";

    /**
     * URL protocol for an entry from a zip file: "zip".
     */
    public static final String URL_PROTOCOL_ZIP = "zip";

    /**
     * URL protocol for a JBoss VFS resource: "vfs".
     */
    public static final String URL_PROTOCOL_VFS = "vfs";

    /**
     * URL protocol for an entry from a WebSphere jar file: "wsjar".
     */
    public static final String URL_PROTOCOL_WSJAR = "wsjar";

    /**
     * URL protocol for an entry from an OC4J jar file: "code-source".
     */
    public static final String URL_PROTOCOL_CODE_SOURCE = "code-source";

    /**
     * Regex file separator.
     */
    public static final String REGEX_FILE_SEPARATOR = "[\\\\/]"; // backslashes need escaping in regexes

    /**
     * Resources are resolved against the platform specific path and must therefore obey the specific File.separator.
     */
    @SuppressWarnings("ConstantName")
    public static final Pattern[] patterns = new Pattern[6];

    private static final String WINDOWS_FOLDER_SEPARATOR = "\\";

    private static final String TOP_PATH = "..";

    private static final String CURRENT_PATH = ".";

    private static final String FOLDER_SEPARATOR = "/";

    static {
        String fs = REGEX_FILE_SEPARATOR;
        patterns[0] = Pattern.compile(createResourcePattern(fs, "src" + fs + "main" + fs + "java"));
        patterns[1] = Pattern.compile(createResourcePattern(fs, "src" + fs + "main" + fs + "groovy"));
        patterns[2] = Pattern.compile(createResourcePattern(fs, "src" + fs + "main" + fs + "kotlin"));
        patterns[3] = Pattern.compile(createResourcePattern(fs, "src" + fs + "test" + fs + "java"));
        patterns[4] = Pattern.compile(createResourcePattern(fs, "src" + fs + "test" + fs + "groovy"));
        patterns[5] = Pattern.compile(createResourcePattern(fs, "src" + fs + "test" + fs + "kotlin"));
    }

    private static String createResourcePattern(String separator, String base) {
        return ".+" + separator + base + separator + "(.+)\\.(groovy|java)$";
    }

    /**
     * Extract the filename from the given path,
     * e.g. "mypath/myfile.txt" -> "myfile.txt".
     *
     * @param path the file path (may be <code>null</code>)
     * @return the extracted filename, or <code>null</code> if none
     */
    public static String getFilename(String path) {
        if (path == null) {
            return null;
        }
        int separatorIndex = path.lastIndexOf(FOLDER_SEPARATOR);
        return (separatorIndex != -1 ? path.substring(separatorIndex + 1) : path);
    }

    /**
     * Given an input class object, return a string which consists of the
     * class's package name as a pathname, i.e., all dots ('.') are replaced by
     * slashes ('/'). Neither a leading nor trailing slash is added. The result
     * could be concatenated with a slash and the name of a resource and fed
     * directly to <code>ClassLoader.getResource()</code>. For it to be fed to
     * <code>Class.getResource</code> instead, a leading slash would also have
     * to be prepended to the returned value.
     *
     * @param clazz the input class. A <code>null</code> value or the default
     *              (empty) package will result in an empty string ("") being returned.
     * @return a path which represents the package name
     * @see ClassLoader#getResource
     * @see Class#getResource
     */
    public static String classPackageAsResourcePath(Class<?> clazz) {
        if (clazz == null) {
            return "";
        }
        String className = clazz.getName();
        int packageEndIndex = className.lastIndexOf('.');
        if (packageEndIndex == -1) {
            return "";
        }
        String packageName = className.substring(0, packageEndIndex);
        return packageName.replace('.', '/');
    }

    /**
     * @param con The URL connection
     */
    public static void useCachesIfNecessary(URLConnection con) {
        con.setUseCaches(con.getClass().getName().startsWith("JNLP"));
    }

    /**
     * Gets the class name of the specified Micronaut resource.
     *
     * @param resource The Spring Resource
     * @return The class name or null if the resource is not a Micronaut class
     */
    public static String getClassName(Resource resource) {
        try {
            return getClassName(resource.getFile().getAbsolutePath());
        } catch (IOException e) {
            return null;
        }
    }

    /**
     * Returns the class name for a resource.
     *
     * @param path The path to check
     * @return The class name or null if it doesn't exist
     */
    public static String getClassName(String path) {
        for (Pattern pattern : patterns) {
            Matcher m = pattern.matcher(path);
            if (m.find()) {
                return m.group(1).replaceAll("[/\\\\]", ".");
            }
        }
        return null;
    }

    /**
     * Resolve the given resource URL to a <code>java.io.File</code>,
     * i.e. to a file in the file system.
     *
     * @param resourceUrl the resource URL to resolve
     * @param description a description of the original resource that
     *                    the URL was created for (for example, a class path location)
     * @return a corresponding File object
     * @throws java.io.FileNotFoundException if the URL cannot be resolved to
     *                                       a file in the file system
     */
    public static File getFile(URL resourceUrl, String description) throws FileNotFoundException {
        if (!URL_PROTOCOL_FILE.equals(resourceUrl.getProtocol())) {
            throw new FileNotFoundException(
                description + " cannot be resolved to absolute file path " +
                    "because it does not reside in the file system: " + resourceUrl);
        }
        try {
            return new File(toURI(resourceUrl).getSchemeSpecificPart());
        } catch (URISyntaxException ex) {
            // Fallback for URLs that are not valid URIs (should hardly ever happen).
            return new File(resourceUrl.getFile());
        }
    }

    /**
     * Determine whether the given URL points to a resource in a jar file,
     * that is, has protocol "jar", "zip", "wsjar" or "code-source".
     * <p>"zip" and "wsjar" are used by BEA WebLogic Server and IBM WebSphere, respectively,
     * but can be treated like jar files. The same applies to "code-source" URLs on Oracle
     * OC4J, provided that the path contains a jar separator.
     *
     * @param url the URL to check
     * @return whether the URL has been identified as a JAR URL
     */
    public static boolean isJarURL(URL url) {
        String protocol = url.getProtocol();
        return (URL_PROTOCOL_JAR.equals(protocol) ||
            URL_PROTOCOL_ZIP.equals(protocol) ||
            URL_PROTOCOL_WSJAR.equals(protocol) ||
            (URL_PROTOCOL_CODE_SOURCE.equals(protocol) && url.getPath().contains(JAR_URL_SEPARATOR)));
    }

    /**
     * Resolve the given resource URI to a <code>java.io.File</code>,
     * i.e. to a file in the file system.
     *
     * @param resourceUri the resource URI to resolve
     * @param description a description of the original resource that
     *                    the URI was created for (for example, a class path location)
     * @return a corresponding File object
     * @throws FileNotFoundException if the URL cannot be resolved to
     *                               a file in the file system
     */
    public static File getFile(URI resourceUri, String description) throws FileNotFoundException {
        if (!URL_PROTOCOL_FILE.equals(resourceUri.getScheme())) {
            throw new FileNotFoundException(
                description + " cannot be resolved to absolute file path " +
                    "because it does not reside in the file system: " + resourceUri);
        }
        return new File(resourceUri.getSchemeSpecificPart());
    }

    /**
     * Create a URI instance for the given URL,
     * replacing spaces with "%20" quotes first.
     * <p>Furthermore, this method works on JDK 1.4 as well,
     * in contrast to the <code>URL.toURI()</code> method.
     *
     * @param url the URL to convert into a URI instance
     * @return the URI instance
     * @throws URISyntaxException if the URL wasn't a valid URI
     * @see java.net.URL#toURI()
     */
    public static URI toURI(URL url) throws URISyntaxException {
        return toURI(url.toString());
    }

    /**
     * Determine whether the given URL points to a resource in the file system,
     * that is, has protocol "file" or "vfs".
     *
     * @param url the URL to check
     * @return whether the URL has been identified as a file system URL
     */
    public static boolean isFileURL(URL url) {
        String protocol = url.getProtocol();
        return (URL_PROTOCOL_FILE.equals(protocol) || protocol.startsWith(URL_PROTOCOL_VFS));
    }

    /**
     * Apply the given relative path to the given path,
     * assuming standard Java folder separation (i.e. "/" separators).
     *
     * @param path         the path to start from (usually a full file path)
     * @param relativePath the relative path to apply
     *                     (relative to the full file path above)
     * @return the full file path that results from applying the relative path
     */
    public static String applyRelativePath(String path, String relativePath) {
        int separatorIndex = path.lastIndexOf(FOLDER_SEPARATOR);
        if (separatorIndex != -1) {
            String newPath = path.substring(0, separatorIndex);
            if (!relativePath.startsWith(FOLDER_SEPARATOR)) {
                newPath += FOLDER_SEPARATOR;
            }
            return newPath + relativePath;
        }
        return relativePath;
    }

    /**
     * Normalize the path by suppressing sequences like "path/.." and
     * inner simple dots.
     * <p>The result is convenient for path comparison. For other uses,
     * notice that Windows separators ("\") are replaced by simple slashes.
     *
     * @param path the original path
     * @return the normalized path
     */
    @SuppressWarnings("EmptyBlock")
    public static String cleanPath(String path) {
        if (path == null) {
            return null;
        }
        String pathToUse = replace(path, WINDOWS_FOLDER_SEPARATOR, FOLDER_SEPARATOR);

        // Strip prefix from path to analyze, to not treat it as part of the
        // first path element. This is necessary to correctly parse paths like
        // "file:core/../core/io/Resource.class", where the ".." should just
        // strip the first "core" directory while keeping the "file:" prefix.
        int prefixIndex = pathToUse.indexOf(":");
        String prefix = "";
        if (prefixIndex != -1) {
            prefix = pathToUse.substring(0, prefixIndex + 1);
            pathToUse = pathToUse.substring(prefixIndex + 1);
        }
        if (pathToUse.startsWith(FOLDER_SEPARATOR)) {
            prefix = prefix + FOLDER_SEPARATOR;
            pathToUse = pathToUse.substring(1);
        }

        String[] pathArray = delimitedListToStringArray(pathToUse, FOLDER_SEPARATOR);
        List<String> pathElements = new LinkedList<String>();
        int tops = 0;

        for (int i = pathArray.length - 1; i >= 0; i--) {
            String element = pathArray[i];
            if (CURRENT_PATH.equals(element)) {
                // Points to current directory - drop it.
            } else if (TOP_PATH.equals(element)) {
                // Registering top path found.
                tops++;
            } else {
                if (tops > 0) {
                    // Merging path element with element corresponding to top path.
                    tops--;
                } else {
                    // Normal path element found.
                    pathElements.add(0, element);
                }
            }
        }

        // Remaining top paths need to be retained.
        for (int i = 0; i < tops; i++) {
            pathElements.add(0, TOP_PATH);
        }

        return prefix + collectionToDelimitedString(pathElements, FOLDER_SEPARATOR);
    }

    private static String collectionToDelimitedString(Collection<?> coll, String delim) {
        return collectionToDelimitedString(coll, delim, "", "");
    }

    private static String collectionToDelimitedString(Collection<?> coll, String delim, String prefix, String suffix) {
        if (coll == null || coll.isEmpty()) {
            return "";
        }
        StringBuilder sb = new StringBuilder();
        Iterator<?> it = coll.iterator();
        while (it.hasNext()) {
            sb.append(prefix).append(it.next()).append(suffix);
            if (it.hasNext()) {
                sb.append(delim);
            }
        }
        return sb.toString();
    }

    /**
     * Take a String which is a delimited list and convert it to a String array.
     * <p>A single delimiter can consists of more than one character: It will still
     * be considered as single delimiter string, rather than as bunch of potential
     * delimiter characters - in contrast to <code>tokenizeToStringArray</code>.
     *
     * @param str       the input String
     * @param delimiter the delimiter between elements (this is a single delimiter,
     *                  rather than a bunch individual delimiter characters)
     * @return an array of the tokens in the list
     */
    private static String[] delimitedListToStringArray(String str, String delimiter) {
        return delimitedListToStringArray(str, delimiter, null);
    }

    /**
     * Take a String which is a delimited list and convert it to a String array.
     * <p>A single delimiter can consists of more than one character: It will still
     * be considered as single delimiter string, rather than as bunch of potential
     * delimiter characters - in contrast to <code>tokenizeToStringArray</code>.
     *
     * @param str           the input String
     * @param delimiter     the delimiter between elements (this is a single delimiter,
     *                      rather than a bunch individual delimiter characters)
     * @param charsToDelete a set of characters to delete. Useful for deleting unwanted
     *                      line breaks: e.g. "\r\n\f" will delete all new lines and line feeds in a String.
     * @return an array of the tokens in the list
     */
    private static String[] delimitedListToStringArray(String str, String delimiter, String charsToDelete) {
        if (str == null) {
            return new String[0];
        }
        if (delimiter == null) {
            return new String[]{str};
        }
        List<String> result = new ArrayList<String>();
        if ("".equals(delimiter)) {
            for (int i = 0; i < str.length(); i++) {
                result.add(deleteAny(str.substring(i, i + 1), charsToDelete));
            }
        } else {
            int pos = 0;
            int delPos;
            while ((delPos = str.indexOf(delimiter, pos)) != -1) {
                result.add(deleteAny(str.substring(pos, delPos), charsToDelete));
                pos = delPos + delimiter.length();
            }
            if (str.length() > 0 && pos <= str.length()) {
                // Add rest of String, but not in case of empty input.
                result.add(deleteAny(str.substring(pos), charsToDelete));
            }
        }
        return toStringArray(result);
    }

    private static String[] toStringArray(Collection<String> collection) {
        if (collection == null) {
            return null;
        }
        return collection.toArray(new String[0]);
    }

    private static String deleteAny(String inString, String charsToDelete) {
        if (!hasLength(inString) || !hasLength(charsToDelete)) {
            return inString;
        }
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < inString.length(); i++) {
            char c = inString.charAt(i);
            if (charsToDelete.indexOf(c) == -1) {
                sb.append(c);
            }
        }
        return sb.toString();
    }

    /**
     * Replace all occurences of a substring within a string with
     * another string.
     *
     * @param inString   String to examine
     * @param oldPattern String to replace
     * @param newPattern String to insert
     * @return a String with the replacements
     */
    private static String replace(String inString, String oldPattern, String newPattern) {
        if (!hasLength(inString) || !hasLength(oldPattern) || newPattern == null) {
            return inString;
        }
        StringBuilder sb = new StringBuilder();
        int pos = 0; // our position in the old string
        int index = inString.indexOf(oldPattern);
        // the index of an occurrence we've found, or -1
        int patLen = oldPattern.length();
        while (index >= 0) {
            sb.append(inString.substring(pos, index));
            sb.append(newPattern);
            pos = index + patLen;
            index = inString.indexOf(oldPattern, pos);
        }
        sb.append(inString.substring(pos));
        // remember to append any characters to the right of a match
        return sb.toString();
    }

    private static boolean hasLength(CharSequence str) {
        return (str != null && str.length() > 0);
    }

    /**
     * Extract the URL for the actual jar file from the given URL
     * (which may point to a resource in a jar file or to a jar file itself).
     *
     * @param jarUrl the original URL
     * @return the URL for the actual jar file
     * @throws MalformedURLException if no valid jar file URL could be extracted
     */
    public static URL extractJarFileURL(URL jarUrl) throws MalformedURLException {
        String urlFile = jarUrl.getFile();
        int separatorIndex = urlFile.indexOf(JAR_URL_SEPARATOR);
        if (separatorIndex != -1) {
            String jarFile = urlFile.substring(0, separatorIndex);
            try {
                return new URL(jarFile);
            } catch (MalformedURLException ex) {
                // Probably no protocol in original jar URL, like "jar:C:/mypath/myjar.jar".
                // This usually indicates that the jar file resides in the file system.
                if (!jarFile.startsWith("/")) {
                    jarFile = "/" + jarFile;
                }
                return new URL(FILE_URL_PREFIX + jarFile);
            }
        }
        return jarUrl;
    }

    /**
     * Create a URI instance for the given location String,
     * replacing spaces with "%20" quotes first.
     *
     * @param location the location String to convert into a URI instance
     * @return the URI instance
     * @throws URISyntaxException if the location wasn't a valid URI
     */
    public static URI toURI(String location) throws URISyntaxException {
        return new URI(replace(location, " ", "%20"));
    }

}
