/*
 * Copyright 2002-2013 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.cli.io.support;

import io.micronaut.cli.util.CliSettings;
import groovy.lang.Closure;
import groovy.util.ConfigObject;
import org.codehaus.groovy.runtime.DefaultGroovyMethods;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;


/**
 * Utility methods for resource handling / figuring out class names.
 *
 * @author Graeme Rocher
 * @author Juergen Hoeller
 * @since 2.0
 */
public class ResourceUtils {

    public static final String CLASS_EXTENSION = ".class";

    private static final String WINDOWS_FOLDER_SEPARATOR = "\\";

    private static final String TOP_PATH = "..";

    private static final String CURRENT_PATH = ".";

    private static final String FOLDER_SEPARATOR = "/";
    public static final String JAR_URL_SEPARATOR = "!/";

    /** Pseudo URL prefix for loading from the class path: "classpath:" */
    public static final String CLASSPATH_URL_PREFIX = "classpath:";

    /** URL prefix for loading from the file system: "file:" */
    public static final String FILE_URL_PREFIX = "file:";

    /** URL protocol for a file in the file system: "file" */
    public static final String URL_PROTOCOL_FILE = "file";

    /** URL protocol for an entry from a jar file: "jar" */
    public static final String URL_PROTOCOL_JAR = "jar";

    /** URL protocol for an entry from a zip file: "zip" */
    public static final String URL_PROTOCOL_ZIP = "zip";

    /** URL protocol for an entry from a JBoss jar file: "vfszip" */
    public static final String URL_PROTOCOL_VFSZIP = "vfszip";

    /** URL protocol for a JBoss VFS resource: "vfs" */
    public static final String URL_PROTOCOL_VFS = "vfs";

    /** URL protocol for an entry from a WebSphere jar file: "wsjar" */
    public static final String URL_PROTOCOL_WSJAR = "wsjar";

    /** URL protocol for an entry from an OC4J jar file: "code-source" */
    public static final String URL_PROTOCOL_CODE_SOURCE = "code-source";
    /**
     * The relative path to the WEB-INF directory
     */
    public static final String WEB_INF = "/WEB-INF";

    /**
     * The name of the Grails application directory
     */
    public static final String GRAILS_APP_DIR = "grails-app";



    public static final String DOMAIN_DIR_PATH = GRAILS_APP_DIR + "/domain/";

    public static final String REGEX_FILE_SEPARATOR = "[\\\\/]"; // backslashes need escaping in regexes

    /*
     Domain path is always matched against the normalized File representation of an URL and
     can therefore work with slashes as separators.
     */
    public static Pattern DOMAIN_PATH_PATTERN = Pattern.compile(".+" + REGEX_FILE_SEPARATOR + GRAILS_APP_DIR + REGEX_FILE_SEPARATOR + "domain" + REGEX_FILE_SEPARATOR + "(.+)\\.(groovy|java)");

    /*
     This pattern will match any resource within a given directory inside grails-app
     */
    public static Pattern RESOURCE_PATH_PATTERN = Pattern.compile(".+?" + REGEX_FILE_SEPARATOR + GRAILS_APP_DIR + REGEX_FILE_SEPARATOR + "(.+?)"+ REGEX_FILE_SEPARATOR +"(.+?\\.(groovy|java))");

    public static Pattern SPRING_SCRIPTS_PATH_PATTERN = Pattern.compile(".+?" + REGEX_FILE_SEPARATOR + GRAILS_APP_DIR + REGEX_FILE_SEPARATOR + "conf"+ REGEX_FILE_SEPARATOR +"spring"+ REGEX_FILE_SEPARATOR +"(.+?\\.groovy)");

    public static Pattern[] COMPILER_ROOT_PATTERNS = {
        SPRING_SCRIPTS_PATH_PATTERN,
        RESOURCE_PATH_PATTERN
    };

    /*
    Resources are resolved against the platform specific path and must therefore obey the
    specific File.separator.
     */
    public static final Pattern GRAILS_RESOURCE_PATTERN_FIRST_MATCH;
    public static final Pattern GRAILS_RESOURCE_PATTERN_SECOND_MATCH;
    public static final Pattern GRAILS_RESOURCE_PATTERN_THIRD_MATCH;
    public static final Pattern GRAILS_RESOURCE_PATTERN_FOURTH_MATCH;
    public static final Pattern GRAILS_RESOURCE_PATTERN_FIFTH_MATCH;
    public static final Pattern GRAILS_RESOURCE_PATTERN_SIXTH_MATCH;
    public static final Pattern GRAILS_RESOURCE_PATTERN_SEVENTH_MATCH;
    public static final Pattern GRAILS_RESOURCE_PATTERN_EIGHTH_MATCH;
    public static final Pattern GRAILS_RESOURCE_PATTERN_NINTH_MATCH;
    public static final Pattern GRAILS_RESOURCE_PATTERN_TENTH_MATCH;
    public static final Pattern GRAILS_RESOURCE_PATTERN_ELEVENTH_MATCH;

    static {
        String fs = REGEX_FILE_SEPARATOR;

        GRAILS_RESOURCE_PATTERN_FIRST_MATCH = Pattern.compile(createResourcePattern(fs, GRAILS_APP_DIR +fs+ "conf" +fs + "spring"));
        GRAILS_RESOURCE_PATTERN_THIRD_MATCH = Pattern.compile(createResourcePattern(fs, GRAILS_APP_DIR +fs +"[\\w-]+"));
        GRAILS_RESOURCE_PATTERN_SEVENTH_MATCH = Pattern.compile(createResourcePattern(fs, "src" + fs + "main" + fs + "java"));
        GRAILS_RESOURCE_PATTERN_EIGHTH_MATCH = Pattern.compile(createResourcePattern(fs, "src" + fs + "main" + fs + "groovy"));

        GRAILS_RESOURCE_PATTERN_NINTH_MATCH = Pattern.compile(createResourcePattern(fs, "src" + fs + "test" + fs + "groovy"));
        GRAILS_RESOURCE_PATTERN_TENTH_MATCH = Pattern.compile(createResourcePattern(fs, "src" + fs + "test" + fs + "java"));
        GRAILS_RESOURCE_PATTERN_ELEVENTH_MATCH = Pattern.compile(createResourcePattern(fs, "src" + fs + "test" + fs + "functional"));

        GRAILS_RESOURCE_PATTERN_FIFTH_MATCH = Pattern.compile(createResourcePattern(fs, "grails-tests"));
        fs = "/";
        GRAILS_RESOURCE_PATTERN_SECOND_MATCH = Pattern.compile(createResourcePattern(fs, GRAILS_APP_DIR +fs+ "conf" +fs + "spring"));
        GRAILS_RESOURCE_PATTERN_FOURTH_MATCH = Pattern.compile(createResourcePattern(fs, GRAILS_APP_DIR +fs +"[\\w-]+"));
        GRAILS_RESOURCE_PATTERN_SIXTH_MATCH = Pattern.compile(createResourcePattern(fs, "grails-tests"));
    }

    public static final Pattern[] patterns = new Pattern[]{
        GRAILS_RESOURCE_PATTERN_FIRST_MATCH,
        GRAILS_RESOURCE_PATTERN_THIRD_MATCH,
        GRAILS_RESOURCE_PATTERN_SEVENTH_MATCH,
        GRAILS_RESOURCE_PATTERN_EIGHTH_MATCH,
        GRAILS_RESOURCE_PATTERN_FOURTH_MATCH,
        GRAILS_RESOURCE_PATTERN_FIFTH_MATCH,
        GRAILS_RESOURCE_PATTERN_SIXTH_MATCH,
        GRAILS_RESOURCE_PATTERN_NINTH_MATCH,
        GRAILS_RESOURCE_PATTERN_TENTH_MATCH,
        GRAILS_RESOURCE_PATTERN_ELEVENTH_MATCH
    };

    public static final Pattern[] grailsAppResourcePatterns = new Pattern[]{
            GRAILS_RESOURCE_PATTERN_FIRST_MATCH,
            GRAILS_RESOURCE_PATTERN_THIRD_MATCH,
            GRAILS_RESOURCE_PATTERN_FOURTH_MATCH,
            GRAILS_RESOURCE_PATTERN_FIFTH_MATCH,
            GRAILS_RESOURCE_PATTERN_SIXTH_MATCH,
            GRAILS_RESOURCE_PATTERN_ELEVENTH_MATCH
    };

    private static Map<String, Boolean> KNOWN_PATHS = new LinkedHashMap<String, Boolean>() {
        @Override
        protected boolean removeEldestEntry(Map.Entry eldest) {
            return this.size() > 100;
        }
    };

    private static Map<String, Boolean> KNOWN_DOMAIN_CLASSES = DefaultGroovyMethods.withDefault(new LinkedHashMap<String, Boolean>(){
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Boolean> eldest) {
            return this.size() > 100;
        }
    }, new Closure(ResourceUtils.class) {

        @Override
        public Object call(Object... args) {
            String path = args[0].toString();
            return DOMAIN_PATH_PATTERN.matcher(path).find();
        }
    });

    private static String createResourcePattern(String separator, String base) {
        return ".+" + separator + base + separator + "(.+)\\.(groovy|java)$";
    }

    /**
     * Checks whether the file referenced by the given url is a domain class
     *
     * @param url The URL instance
     * @return true if it is a domain class
     */

    public static boolean isDomainClass(URL url) {
        if (url == null) return false;
        return KNOWN_DOMAIN_CLASSES.get(url.getFile());
    }

    /**
     * Extract the filename from the given path,
     * e.g. "mypath/myfile.txt" -> "myfile.txt".
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
     * @param clazz the input class. A <code>null</code> value or the default
     * (empty) package will result in an empty string ("") being returned.
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

    public static void useCachesIfNecessary(URLConnection con) {
        con.setUseCaches(con.getClass().getName().startsWith("JNLP"));
    }

    /**
     * Gets the class name of the specified Grails resource
     *
     * @param resource The Spring Resource
     * @return The class name or null if the resource is not a Grails class
     */
    public static String getClassName(Resource resource) {
        try {
            return getClassName(resource.getFile().getAbsolutePath());
        }
        catch (IOException e) {
             return null;
        }
    }

    /**
     * Returns the class name for a Grails resource.
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
     * Returns the class name for a compiled class file
     *
     * @param path The path to check
     * @return The class name or null if it doesn't exist
     */
    public static String getClassNameForClassFile(String rootDir, String path) {
        path = path.replace("/", ".");
        path = path.replace('\\', '.');
        path = path.substring(0, path.length() - CLASS_EXTENSION.length());
        if (rootDir != null) {
            path = path.substring(rootDir.length());
        }
        return path;
    }

    /**
     * Resolve the given resource URL to a <code>java.io.File</code>,
     * i.e. to a file in the file system.
     * @param resourceUrl the resource URL to resolve
     * @param description a description of the original resource that
     * the URL was created for (for example, a class path location)
     * @return a corresponding File object
     * @throws java.io.FileNotFoundException if the URL cannot be resolved to
     * a file in the file system
     */
    public static File getFile(URL resourceUrl, String description) throws FileNotFoundException {
        if (!URL_PROTOCOL_FILE.equals(resourceUrl.getProtocol())) {
            throw new FileNotFoundException(
                    description + " cannot be resolved to absolute file path " +
                            "because it does not reside in the file system: " + resourceUrl);
        }
        try {
            return new File(toURI(resourceUrl).getSchemeSpecificPart());
        }
        catch (URISyntaxException ex) {
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
     * @param resourceUri the resource URI to resolve
     * @param description a description of the original resource that
     * the URI was created for (for example, a class path location)
     * @return a corresponding File object
     * @throws FileNotFoundException if the URL cannot be resolved to
     * a file in the file system
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
     * Resolve the given resource URI to a <code>java.io.File</code>,
     * i.e. to a file in the file system.
     * @param resourceUri the resource URI to resolve
     * @return a corresponding File object
     * @throws FileNotFoundException if the URL cannot be resolved to
     * a file in the file system
     */
    public static File getFile(URI resourceUri) throws FileNotFoundException {
        return getFile(resourceUri, "URI");
    }

    /**
     * Create a URI instance for the given URL,
     * replacing spaces with "%20" quotes first.
     * <p>Furthermore, this method works on JDK 1.4 as well,
     * in contrast to the <code>URL.toURI()</code> method.
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
     * @param path the path to start from (usually a full file path)
     * @param relativePath the relative path to apply
     * (relative to the full file path above)
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
     * @param path the original path
     * @return the normalized path
     */
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
            }
            else if (TOP_PATH.equals(element)) {
                // Registering top path found.
                tops++;
            }
            else {
                if (tops > 0) {
                    // Merging path element with element corresponding to top path.
                    tops--;
                }
                else {
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
     * @param str the input String
     * @param delimiter the delimiter between elements (this is a single delimiter,
     * rather than a bunch individual delimiter characters)
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
     * @param str the input String
     * @param delimiter the delimiter between elements (this is a single delimiter,
     * rather than a bunch individual delimiter characters)
     * @param charsToDelete a set of characters to delete. Useful for deleting unwanted
     * line breaks: e.g. "\r\n\f" will delete all new lines and line feeds in a String.
     * @return an array of the tokens in the list
     */
    private static String[] delimitedListToStringArray(String str, String delimiter, String charsToDelete) {
        if (str == null) {
            return new String[0];
        }
        if (delimiter == null) {
            return new String[] {str};
        }
        List<String> result = new ArrayList<String>();
        if ("".equals(delimiter)) {
            for (int i = 0; i < str.length(); i++) {
                result.add(deleteAny(str.substring(i, i + 1), charsToDelete));
            }
        }
        else {
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
        return collection.toArray(new String[collection.size()]);
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
     * @param inString String to examine
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
            }
            catch (MalformedURLException ex) {
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
     * @param location the location String to convert into a URI instance
     * @return the URI instance
     * @throws URISyntaxException if the location wasn't a valid URI
     */
    public static URI toURI(String location) throws URISyntaxException {
        return new URI(replace(location, " ", "%20"));
    }

    /**
     * Checks whether the specified path is a Grails path.
     *
     * @param path The path to check
     * @return true if it is a Grails path
     */

    public static boolean isGrailsPath(String path) {
        if(KNOWN_PATHS.containsKey(path)) {
            return KNOWN_PATHS.get(path);
        }
        for (Pattern grailsAppResourcePattern : grailsAppResourcePatterns) {
            Matcher m = grailsAppResourcePattern.matcher(path);
            if (m.find()) {
                KNOWN_PATHS.put(path, true);
                return true;
            }
        }
        KNOWN_PATHS.put(path, false);
        return false;
    }

    /**
     * Checks whether the specified path is a Grails path.
     *
     * @param path The path to check
     * @return true if it is a Grails path
     */
    public static boolean isProjectSource(String path) {
        for (Pattern grailsAppResourcePattern : patterns) {
            Matcher m = grailsAppResourcePattern.matcher(path);
            if (m.find()) {
                return true;
            }
        }
        return false;
    }

    /**
     * Checks whether the specified path is a Grails path.
     *
     * @param r The resoruce to check
     * @return true if it is a Grails path
     */
    public static boolean isProjectSource(Resource r) {
        try {
            String file = r.getURL().getFile();
            return isProjectSource(file) || file.endsWith("GrailsPlugin.groovy");
        }
        catch (IOException e) {
            return false;
        }
    }
    /**
     * Checks whether the specific resources is a Grails resource. A Grails resource is a Groovy or Java class under the grails-app directory
     *
     * @param r The resource to check
     * @return True if it is a Grails resource
     */
    public static boolean isGrailsResource(Resource r) {
        try {
            String file = r.getURL().getFile();
            return isGrailsPath(file) || file.endsWith("GrailsPlugin.groovy");
        }
        catch (IOException e) {
            return false;
        }
    }

    public static Resource getViewsDir(Resource resource) {
        if (resource == null) return null;

        Resource appDir = getAppDir(resource);
        if(appDir == null) return null;
        return appDir.createRelative("views");
    }

    public static Resource getAppDir(Resource resource) {
        if (resource == null) return null;

        try {
            File file = resource.getFile();
            while(file != null && !file.getName().equals(GRAILS_APP_DIR)) {
                file = file.getParentFile();
            }
            if (file != null) {
                return new FileSystemResource(file.getAbsolutePath() + '/');
            }
        } catch (IOException e) {
        }

        try {
            String url = resource.getURL().toString();

            int i = url.lastIndexOf(GRAILS_APP_DIR);
            if (i > -1) {
                url = url.substring(0, i+10);
                return new UrlResource(url + '/');
            }

            return null;
        }
        catch (MalformedURLException e) {
            return null;
        }
        catch (IOException e) {
            return null;
        }
    }

    private static final Pattern PLUGIN_PATTERN = Pattern.compile(".+?(/plugins/.+?/"+GRAILS_APP_DIR+"/.+)");

    /**
     * Takes a Grails resource (one located inside the grails-app dir) and gets its relative path inside the WEB-INF directory
     * when deployed.
     *
     * @param resource The Grails resource, which is a file inside the grails-app dir
     * @return The relative URL of the file inside the WEB-INF dir at deployment time or null if it cannot be established
     */
    public static String getRelativeInsideWebInf(Resource resource) {
        if (resource == null) return null;

        try {
            String url = resource.getURL().toString();
            int i = url.indexOf(WEB_INF);
            if (i > -1) {
                return url.substring(i);
            }

            Matcher m = PLUGIN_PATTERN.matcher(url);
            if (m.find()) {
                return WEB_INF + m.group(1);
            }

            i = url.lastIndexOf(GRAILS_APP_DIR);
            if (i > -1) {
                return WEB_INF + "/" + url.substring(i);
            }
        }
        catch (IOException e) {
            return null;
        }
        return null;
    }

    private static final Pattern PLUGIN_RESOURCE_PATTERN = Pattern.compile(".+?/(plugins/.+?)/"+GRAILS_APP_DIR+"/.+");

    /**
     * Retrieves the static resource path for the given Grails resource artifact (controller/taglib etc.)
     *
     * @param resource The Resource
     * @param contextPath The additonal context path to prefix
     * @return The resource path
     */
    public static String getStaticResourcePathForResource(Resource resource, String contextPath) {

        if (contextPath == null) contextPath = "";
        if (resource == null) return contextPath;

        String url;
        try {
            url = resource.getURL().toString();
        }
        catch (IOException e) {
            return contextPath;
        }

        Matcher m = PLUGIN_RESOURCE_PATTERN.matcher(url);
        if (m.find()) {
            return (contextPath.length() > 0 ? contextPath + "/" : "") + m.group(1);
        }

        return contextPath;
    }

    /**
     * Get the path relative to an artefact folder under grails-app i.e:
     *
     * Input: /usr/joe/project/grails-app/conf/BootStrap.groovy
     * Output: BootStrap.groovy
     *
     * Input: /usr/joe/project/grails-app/domain/com/mystartup/Book.groovy
     * Output: com/mystartup/Book.groovy
     *
     * @param path The path to evaluate
     * @return The path relative to the root folder grails-app
     */
    public static String getPathFromRoot(String path) {
        for (Pattern COMPILER_ROOT_PATTERN : COMPILER_ROOT_PATTERNS) {
            Matcher m = COMPILER_ROOT_PATTERN.matcher(path);
            if (m.find()) {
                return m.group(m.groupCount()-1);
            }
        }
        return null;
    }

    /**
     * Gets the path relative to the project base directory.
     *
     * Input: /usr/joe/project/grails-app/conf/BootStrap.groovy
     * Output: grails-app/conf/BootStrap.groovy
     *
     * @param path The path
     * @return The path relative to the base directory or null if it can't be established
     */
    public static String getPathFromBaseDir(String path) {
        int i = path.indexOf("grails-app/");
        if(i > -1 ) {
            return path.substring(i + 11);
        }
        else {
            try {
                File baseDir = CliSettings.BASE_DIR;
                String basePath = baseDir != null ? baseDir.getCanonicalPath() : null;
                if(basePath != null) {
                    String canonicalPath = new File(path).getCanonicalPath();
                    return canonicalPath.substring(basePath.length()+1);
                }
            } catch (IOException e) {
                // ignore
            }
        }
        return null;
    }

    /**
     * Takes a file path and returns the name of the folder under grails-app i.e:
     *
     * Input: /usr/joe/project/grails-app/domain/com/mystartup/Book.groovy
     * Output: domain
     *
     * @param path The path
     * @return The domain or null if not known
     */
    public static String getArtefactDirectory(String path) {

        if (path != null) {
            final Matcher matcher = RESOURCE_PATH_PATTERN.matcher(path);
            if (matcher.find()) {
                return matcher.group(1);
            }
        }
        return null;
    }

    /**
     * Takes any number of Strings and appends them into a uri, making
     * sure that a forward slash is inserted between each piece and
     * making sure that no duplicate slashes are in the uri
     *
     * <pre>
     * Input: ""
     * Output: ""
     *
     * Input: "/alpha", "/beta", "/gamma"
     * Output: "/alpha/beta/gamma
     *
     * Input: "/alpha/, "/beta/", "/gamma"
     * Output: "/alpha/beta/gamma
     *
     * Input: "/alpha/", "/beta/", "/gamma/"
     * Output "/alpha/beta/gamma/
     *
     * Input: "alpha", "beta", "gamma"
     * Output: "alpha/beta/gamma
     * </pre>
     *
     * @param pieces Strings to concatenate together into a uri
     * @return a uri
     */
    public static String appendPiecesForUri(String... pieces) {
        if (pieces==null || pieces.length==0) return "";

        // join parts && strip double slashes
        StringBuilder builder = new StringBuilder(16 * pieces.length);
        char previous = 0;
        for (int i=0; i < pieces.length;i++) {
            String piece = pieces[i];
            if (piece != null && piece.length() > 0) {
                for (int j=0, maxlen=piece.length();j < maxlen;j++) {
                    char current=piece.charAt(j);
                    if (!(previous=='/' && current=='/')) {
                        builder.append(current);
                        previous = current;
                    }
                }
                if (i + 1 < pieces.length && previous != '/') {
                    builder.append('/');
                    previous='/';
                }
            }
        }
        return builder.toString();
    }

    @SuppressWarnings("unchecked")
    public static Object instantiateFromConfig(ConfigObject config, String configKey, String defaultClassName)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException, LinkageError {
        return instantiateFromFlatConfig(config.flatten(), configKey, defaultClassName);
    }

    public static Object instantiateFromFlatConfig(Map<String, Object> flatConfig, String configKey, String defaultClassName)
            throws InstantiationException, IllegalAccessException, ClassNotFoundException, LinkageError {
        String className = defaultClassName;
        Object configName = flatConfig.get(configKey);
        if (configName instanceof CharSequence) {
            className = configName.toString();
        }
        return forName(className, DefaultResourceLoader.getDefaultClassLoader()).newInstance();
    }

    private static Class<?> forName(String className, ClassLoader defaultClassLoader) throws ClassNotFoundException {
        return defaultClassLoader.loadClass(className);
    }
}
