/*
 * Copyright 2017-2018 original authors
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
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.Collections;
import java.util.Enumeration;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.jar.JarEntry;
import java.util.jar.JarFile;

/**
 * A ResourcePatternResolver implementation that is able to resolve a
 * specified resource location path into one or more matching Resources.
 * The source path may be a simple path which has a one-to-one mapping to a
 * target Resource, or alternatively
 * may contain the special "<code>classpath*:</code>" prefix and/or
 * internal Ant-style regular expressions (matched using AntPathMatcher).
 * Both of the latter are effectively wildcards.
 *
 * <p><b>No Wildcards:</b>
 *
 * <p>In the simple case, if the specified location path does not start with the
 * <code>"classpath*:</code>" prefix, and does not contain a PathMatcher pattern,
 * this resolver will simply return a single resource via a
 * <code>getResource()</code> call on the underlying <code>ResourceLoader</code>.
 * Examples are real URLs such as "<code>file:C:/context.xml</code>", pseudo-URLs
 * such as "<code>classpath:/context.xml</code>", and simple unprefixed paths
 * such as "<code>/WEB-INF/context.xml</code>". The latter will resolve in a
 * fashion specific to the underlying <code>ResourceLoader</code> (e.g.
 * <code>ServletContextResource</code> for a <code>WebApplicationContext</code>).
 *
 * <p><b>Ant-style Patterns:</b>
 *
 * <p>When the path location contains an Ant-style pattern, e.g.:
 * <pre>
 * /WEB-INF/*-context.xml
 * com/mycompany/**&#47;applicationContext.xml
 * file:C:/some/path/*-context.xml
 * classpath:com/mycompany/**&#47;applicationContext.xml</pre>
 * the resolver follows a more complex but defined procedure to try to resolve
 * the wildcard. It produces a <code>Resource</code> for the path up to the last
 * non-wildcard segment and obtains a <code>URL</code> from it. If this URL is
 * not a "<code>jar:</code>" URL or container-specific variant (e.g.
 * "<code>zip:</code>" in WebLogic, "<code>wsjar</code>" in WebSphere", etc.),
 * then a <code>java.io.File</code> is obtained from it, and used to resolve the
 * wildcard by walking the filesystem. In the case of a jar URL, the resolver
 * either gets a <code>java.net.JarURLConnection</code> from it, or manually parses
 * the jar URL, and then traverses the contents of the jar file, to resolve the
 * wildcards.
 *
 * <p><b>Implications on portability:</b>
 *
 * <p>If the specified path is already a file URL (either explicitly, or
 * implicitly because the base <code>ResourceLoader</code> is a filesystem one,
 * then wildcarding is guaranteed to work in a completely portable fashion.
 *
 * <p>If the specified path is a classpath location, then the resolver must
 * obtain the last non-wildcard path segment URL via a
 * <code>Classloader.getResource()</code> call. Since this is just a
 * node of the path (not the file at the end) it is actually undefined
 * (in the ClassLoader Javadocs) exactly what sort of a URL is returned in
 * this case. In practice, it is usually a <code>java.io.File</code> representing
 * the directory, where the classpath resource resolves to a filesystem
 * location, or a jar URL of some sort, where the classpath resource resolves
 * to a jar location. Still, there is a portability concern on this operation.
 *
 * <p>If a jar URL is obtained for the last non-wildcard segment, the resolver
 * must be able to get a <code>java.net.JarURLConnection</code> from it, or
 * manually parse the jar URL, to be able to walk the contents of the jar,
 * and resolve the wildcard. This will work in most environments, but will
 * fail in others, and it is strongly recommended that the wildcard
 * resolution of resources coming from jars be thoroughly tested in your
 * specific environment before you rely on it.
 *
 * <p><b><code>classpath*:</code> Prefix:</b>
 *
 * <p>There is special support for retrieving multiple class path resources with
 * the same name, via the "<code>classpath*:</code>" prefix. For example,
 * "<code>classpath*:META-INF/beans.xml</code>" will find all "beans.xml"
 * files in the class path, be it in "classes" directories or in JAR files.
 * This is particularly useful for autodetecting config files of the same name
 * at the same location within each jar file. Internally, this happens via a
 * <code>ClassLoader.getResources()</code> call, and is completely portable.
 *
 * <p>The "classpath*:" prefix can also be combined with a PathMatcher pattern in
 * the rest of the location path, for example "classpath*:META-INF/*-beans.xml".
 * In this case, the resolution strategy is fairly simple: a
 * <code>ClassLoader.getResources()</code> call is used on the last non-wildcard
 * path segment to get all the matching resources in the class loader hierarchy,
 * and then off each resource the same PathMatcher resolution strategy described
 * above is used for the wildcard subpath.
 *
 * <p><b>Other notes:</b>
 *
 * <p><b>WARNING:</b> Note that "<code>classpath*:</code>" when combined with
 * Ant-style patterns will only work reliably with at least one root directory
 * before the pattern starts, unless the actual target files reside in the file
 * system. This means that a pattern like "<code>classpath*:*.xml</code>" will
 * <i>not</i> retrieve files from the root of jar files but rather only from the
 * root of expanded directories. This originates from a limitation in the JDK's
 * <code>ClassLoader.getResources()</code> method which only returns file system
 * locations for a passed-in empty String (indicating potential roots to search).
 *
 * <p><b>WARNING:</b> Ant-style patterns with "classpath:" resources are not
 * guaranteed to find matching resources if the root package to search is available
 * in multiple class path locations. This is because a resource such as<pre>
 *     com/mycompany/package1/service-context.xml
 * </pre>may be in only one location, but when a path such as<pre>
 *     classpath:com/mycompany/**&#47;service-context.xml
 * </pre>is used to try to resolve it, the resolver will work off the (first) URL
 * returned by <code>getResource("com/mycompany");</code>. If this base package
 * node exists in multiple classloader locations, the actual end resource may
 * not be underneath. Therefore, preferably, use "<code>classpath*:</code>" with the same
 * Ant-style pattern in such a case, which will search <i>all</i> class path
 * locations that contain the root package.
 *
 * @author Juergen Hoeller
 * @author Colin Sampaleanu
 * @author Marius Bogoevici
 * @author Costin Leau
 * @see java.lang.ClassLoader#getResources(String)
 * @since 1.0.2
 */
public class PathMatchingResourcePatternResolver {

    private static final String CLASSPATH_ALL_URL_PREFIX = "classpath*:";

    private final ResourceLoader resourceLoader;

    private AntPathMatcher pathMatcher = new AntPathMatcher();

    /**
     * Create a new PathMatchingResourcePatternResolver with a DefaultResourceLoader.
     * <p>ClassLoader access will happen via the thread context class loader.
     */
    public PathMatchingResourcePatternResolver() {
        resourceLoader = new DefaultResourceLoader();
    }

    /**
     * Create a new PathMatchingResourcePatternResolver with a DefaultResourceLoader.
     *
     * @param classLoader the ClassLoader to load classpath resources with,
     *                    or <code>null</code> for using the thread context class loader
     *                    at the time of actual resource access
     */
    public PathMatchingResourcePatternResolver(ClassLoader classLoader) {
        resourceLoader = new DefaultResourceLoader(classLoader);
    }

    /**
     * Create a new PathMatchingResourcePatternResolver.
     * <p>ClassLoader access will happen via the thread context class loader.
     *
     * @param resourceLoader the ResourceLoader to load root directories and
     *                       actual resources with
     */
    public PathMatchingResourcePatternResolver(ResourceLoader resourceLoader) {
        this.resourceLoader = resourceLoader;
    }

    /**
     * @return The ResourceLoader that this pattern resolver works with.
     */
    public ResourceLoader getResourceLoader() {
        return resourceLoader;
    }

    /**
     * @return The ClassLoader that this pattern resolver works with
     * (never <code>null</code>).
     */
    public ClassLoader getClassLoader() {
        return getResourceLoader().getClassLoader();
    }

    /**
     * Set the PathMatcher implementation to use for this
     * resource pattern resolver. Default is AntPathMatcher.
     *
     * @param pathMatcher The path matcher
     */
    public void setPathMatcher(AntPathMatcher pathMatcher) {
        this.pathMatcher = pathMatcher;
    }

    /**
     * @return The PathMatcher that this resource pattern resolver uses.
     */
    public AntPathMatcher getPathMatcher() {
        return pathMatcher;
    }

    /**
     * @param location The location
     * @return The resource
     */
    public Resource getResource(String location) {
        return getResourceLoader().getResource(location);
    }

    /**
     * @param locationPattern The location pattern
     * @return The resources
     * @throws IOException if there is an error
     */
    public Resource[] getResources(String locationPattern) throws IOException {
        if (locationPattern.startsWith(CLASSPATH_ALL_URL_PREFIX)) {
            // a class path resource (multiple resources for same name possible)
            if (getPathMatcher().isPattern(locationPattern.substring(CLASSPATH_ALL_URL_PREFIX.length()))) {
                // a class path resource pattern
                return findPathMatchingResources(locationPattern);
            }
            // all class path resources with the given name
            return findAllClassPathResources(locationPattern.substring(CLASSPATH_ALL_URL_PREFIX.length()));
        }
        // Only look for a pattern after a prefix here
        // (to not get fooled by a pattern symbol in a strange prefix).
        int prefixEnd = locationPattern.indexOf(":") + 1;
        if (getPathMatcher().isPattern(locationPattern.substring(prefixEnd))) {
            // a file pattern
            return findPathMatchingResources(locationPattern);
        }
        // a single resource with the given name
        return new Resource[]{getResourceLoader().getResource(locationPattern)};
    }

    /**
     * Find all class location resources with the given location via the ClassLoader.
     *
     * @param location the absolute path within the classpath
     * @return the result as Resource array
     * @throws IOException in case of I/O errors
     * @see java.lang.ClassLoader#getResources
     * @see #convertClassLoaderURL
     */
    @SuppressWarnings("MagicNumber")
    protected Resource[] findAllClassPathResources(String location) throws IOException {
        String path = location;
        if (path.startsWith("/")) {
            path = path.substring(1);
        }
        Enumeration<URL> resourceUrls = getClassLoader().getResources(path);
        Set<Resource> result = new LinkedHashSet<Resource>(16);
        while (resourceUrls.hasMoreElements()) {
            URL url = resourceUrls.nextElement();
            result.add(convertClassLoaderURL(url));
        }
        return result.toArray(new Resource[result.size()]);
    }

    /**
     * Convert the given URL as returned from the ClassLoader into a Resource object.
     * <p>The default implementation simply creates a UrlResource instance.
     *
     * @param url a URL as returned from the ClassLoader
     * @return the corresponding Resource object
     * @see java.lang.ClassLoader#getResources
     */
    protected Resource convertClassLoaderURL(URL url) {
        return new UrlResource(url);
    }

    /**
     * Find all resources that match the given location pattern via the
     * Ant-style PathMatcher. Supports resources in jar files and zip files
     * and in the file system.
     *
     * @param locationPattern the location pattern to match
     * @return the result as Resource array
     * @throws IOException in case of I/O errors
     * @see #doFindPathMatchingJarResources
     * @see #doFindPathMatchingFileResources
     */
    @SuppressWarnings("MagicNumber")
    protected Resource[] findPathMatchingResources(String locationPattern) throws IOException {
        String rootDirPath = determineRootDir(locationPattern);
        String subPattern = locationPattern.substring(rootDirPath.length());
        Resource[] rootDirResources = getResources(rootDirPath);
        Set<Resource> result = new LinkedHashSet<Resource>(16);
        for (Resource rootDirResource : rootDirResources) {
            rootDirResource = resolveRootDirResource(rootDirResource);
            if (isJarResource(rootDirResource)) {
                result.addAll(doFindPathMatchingJarResources(rootDirResource, subPattern));
            } else {
                result.addAll(doFindPathMatchingFileResources(rootDirResource, subPattern));
            }
        }
        return result.toArray(new Resource[result.size()]);
    }

    /**
     * Determine the root directory for the given location.
     * <p>Used for determining the starting point for file matching,
     * resolving the root directory location to a <code>java.io.File</code>
     * and passing it into <code>retrieveMatchingFiles</code>, with the
     * remainder of the location as pattern.
     * <p>Will return "/WEB-INF/" for the pattern "/WEB-INF/*.xml",
     * for example.
     *
     * @param location the location to check
     * @return the part of the location that denotes the root directory
     * @see #retrieveMatchingFiles
     */
    protected String determineRootDir(String location) {
        int prefixEnd = location.indexOf(":") + 1;
        int rootDirEnd = location.length();
        while (rootDirEnd > prefixEnd && getPathMatcher().isPattern(location.substring(prefixEnd, rootDirEnd))) {
            rootDirEnd = location.lastIndexOf('/', rootDirEnd - 2) + 1;
        }
        if (rootDirEnd == 0) {
            rootDirEnd = prefixEnd;
        }
        return location.substring(0, rootDirEnd);
    }

    /**
     * Resolve the specified resource for path matching.
     * <p>The default implementation detects an Equinox OSGi "bundleresource:"
     * / "bundleentry:" URL and resolves it into a standard jar file URL that
     * can be traversed using Spring's standard jar file traversal algorithm.
     *
     * @param original the resource to resolve
     * @return the resolved resource (may be identical to the passed-in resource)
     * @throws IOException in case of resolution failure
     */
    protected Resource resolveRootDirResource(Resource original) throws IOException {
        return original;
    }

    /**
     * @param resource the resource handle to check (usually the root directory to start path matching from)
     * @return Whether the given resource handle indicates a jar resource that the <code>doFindPathMatchingJarResources</code>
     * method can handle.
     * The default implementation checks against the URL protocols "jar", "zip" and "wsjar" (the latter are used by
     * BEA WebLogic Server and IBM WebSphere, respectively, but can be treated like jar files).
     * @throws IOException if there is an error
     * @see #doFindPathMatchingJarResources
     */
    protected boolean isJarResource(Resource resource) throws IOException {
        return ResourceUtils.isJarURL(resource.getURL());
    }

    /**
     * Find all resources in jar files that match the given location pattern
     * via the Ant-style PathMatcher.
     *
     * @param rootDirResource the root directory as Resource
     * @param subPattern      the sub pattern to match (below the root directory)
     * @return the Set of matching Resource instances
     * @throws IOException in case of I/O errors
     * @see java.net.JarURLConnection
     */
    @SuppressWarnings("MagicNumber")
    protected Set<Resource> doFindPathMatchingJarResources(Resource rootDirResource, String subPattern)
        throws IOException {

        URLConnection con = rootDirResource.getURL().openConnection();
        JarFile jarFile;
        String jarFileUrl;
        String rootEntryPath;
        boolean newJarFile = false;

        if (con instanceof JarURLConnection) {
            // Should usually be the case for traditional JAR files.
            JarURLConnection jarCon = (JarURLConnection) con;
            ResourceUtils.useCachesIfNecessary(jarCon);
            jarFile = jarCon.getJarFile();
            jarFileUrl = jarCon.getJarFileURL().toExternalForm();
            JarEntry jarEntry = jarCon.getJarEntry();
            rootEntryPath = (jarEntry != null ? jarEntry.getName() : "");
        } else {
            // No JarURLConnection -> need to resort to URL file parsing.
            // We'll assume URLs of the format "jar:path!/entry", with the protocol
            // being arbitrary as long as following the entry format.
            // We'll also handle paths with and without leading "file:" prefix.
            String urlFile = rootDirResource.getURL().getFile();
            int separatorIndex = urlFile.indexOf(ResourceUtils.JAR_URL_SEPARATOR);
            if (separatorIndex != -1) {
                jarFileUrl = urlFile.substring(0, separatorIndex);
                rootEntryPath = urlFile.substring(separatorIndex + ResourceUtils.JAR_URL_SEPARATOR.length());
                jarFile = getJarFile(jarFileUrl);
            } else {
                jarFile = new JarFile(urlFile);
                jarFileUrl = urlFile;
                rootEntryPath = "";
            }
            newJarFile = true;
        }

        try {
            if (!"".equals(rootEntryPath) && !rootEntryPath.endsWith("/")) {
                // Root entry path must end with slash to allow for proper matching.
                // The Sun JRE does not return a slash here, but BEA JRockit does.
                rootEntryPath = rootEntryPath + "/";
            }
            Set<Resource> result = new LinkedHashSet<Resource>(8);
            for (Enumeration<JarEntry> entries = jarFile.entries(); entries.hasMoreElements(); ) {
                JarEntry entry = entries.nextElement();
                String entryPath = entry.getName();
                if (entryPath.startsWith(rootEntryPath)) {
                    String relativePath = entryPath.substring(rootEntryPath.length());
                    if (getPathMatcher().match(subPattern, relativePath)) {
                        result.add(rootDirResource.createRelative(relativePath));
                    }
                }
            }
            return result;
        } finally {
            // Close jar file, but only if freshly obtained -
            // not from JarURLConnection, which might cache the file reference.
            if (newJarFile) {
                jarFile.close();
            }
        }
    }

    /**
     * Resolve the given jar file URL into a JarFile object.
     *
     * @param jarFileUrl The jar file URL
     * @return The jar file.
     * @throws IOException if there is an error
     */
    protected JarFile getJarFile(String jarFileUrl) throws IOException {
        if (jarFileUrl.startsWith(ResourceUtils.FILE_URL_PREFIX)) {
            try {
                return new JarFile(ResourceUtils.toURI(jarFileUrl).getSchemeSpecificPart());
            } catch (URISyntaxException ex) {
                // Fallback for URLs that are not valid URIs (should hardly ever happen).
                return new JarFile(jarFileUrl.substring(ResourceUtils.FILE_URL_PREFIX.length()));
            }
        }
        return new JarFile(jarFileUrl);
    }

    /**
     * Find all resources in the file system that match the given location pattern
     * via the Ant-style PathMatcher.
     *
     * @param rootDirResource the root directory as Resource
     * @param subPattern      the sub pattern to match (below the root directory)
     * @return the Set of matching Resource instances
     * @throws IOException in case of I/O errors
     * @see #retrieveMatchingFiles
     */
    protected Set<Resource> doFindPathMatchingFileResources(Resource rootDirResource, String subPattern)
        throws IOException {

        File rootDir;
        try {
            rootDir = rootDirResource.getFile().getAbsoluteFile();
        } catch (IOException ex) {
            return Collections.emptySet();
        }
        return doFindMatchingFileSystemResources(rootDir, subPattern);
    }

    /**
     * Find all resources in the file system that match the given location pattern
     * via the Ant-style PathMatcher.
     *
     * @param rootDir    the root directory in the file system
     * @param subPattern the sub pattern to match (below the root directory)
     * @return the Set of matching Resource instances
     * @throws IOException in case of I/O errors
     * @see #retrieveMatchingFiles
     */
    protected Set<Resource> doFindMatchingFileSystemResources(File rootDir, String subPattern) throws IOException {
        Set<File> matchingFiles = retrieveMatchingFiles(rootDir, subPattern);
        Set<Resource> result = new LinkedHashSet<Resource>(matchingFiles.size());
        for (File file : matchingFiles) {
            result.add(new FileSystemResource(file));
        }
        return result;
    }

    /**
     * Retrieve files that match the given path pattern,
     * checking the given directory and its subdirectories.
     *
     * @param rootDir the directory to start from
     * @param pattern the pattern to match against,
     *                relative to the root directory
     * @return the Set of matching File instances
     * @throws IOException if directory contents could not be retrieved
     */
    @SuppressWarnings("MagicNumber")
    protected Set<File> retrieveMatchingFiles(File rootDir, String pattern) throws IOException {
        if (!rootDir.exists()) {
            // Silently skip non-existing directories.
            return Collections.emptySet();
        }
        if (!rootDir.isDirectory()) {
            // Complain louder if it exists but is no directory.
            return Collections.emptySet();
        }
        if (!rootDir.canRead()) {
            return Collections.emptySet();
        }
        String fullPattern = rootDir.getAbsolutePath().replace(File.separator, "/");
        if (!pattern.startsWith("/")) {
            fullPattern += "/";
        }
        fullPattern = fullPattern + pattern.replace(File.separator, "/");
        Set<File> result = new LinkedHashSet<File>(8);
        doRetrieveMatchingFiles(fullPattern, rootDir, result);
        return result;
    }

    /**
     * Recursively retrieve files that match the given pattern,
     * adding them to the given result list.
     *
     * @param fullPattern the pattern to match against,
     *                    with prepended root directory path
     * @param dir         the current directory
     * @param result      the Set of matching File instances to add to
     * @throws IOException if directory contents could not be retrieved
     */
    protected void doRetrieveMatchingFiles(String fullPattern, File dir, Set<File> result) throws IOException {
        File[] dirContents = dir.listFiles();
        if (dirContents == null) {
            return;
        }
        for (File content : dirContents) {
            String currPath = content.getAbsolutePath().replace(File.separator, "/");
            if (content.isDirectory() && getPathMatcher().matchStart(fullPattern, currPath + "/")) {
                if (content.canRead()) {
                    doRetrieveMatchingFiles(fullPattern, content, result);
                }
            }
            if (getPathMatcher().match(fullPattern, currPath)) {
                result.add(content);
            }
        }
    }
}
