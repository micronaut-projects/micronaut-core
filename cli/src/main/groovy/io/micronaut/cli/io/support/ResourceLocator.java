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
import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Used to locate resources at build / development time.
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public class ResourceLocator {
    public static final String WILDCARD = "*";
    public static final String FILE_SEPARATOR = File.separator;
    public static final String CLOSURE_MARKER = "$";
    public static final String WEB_APP_DIR = "web-app";

    protected static final Resource NULL_RESOURCE = new ByteArrayResource("null".getBytes());

    protected PathMatchingResourcePatternResolver patchMatchingResolver;
    protected List<String> classSearchDirectories = new ArrayList<String>();
    protected List<String> resourceSearchDirectories = new ArrayList<String>();
    protected Map<String, Resource> classNameToResourceCache = new ConcurrentHashMap<String, Resource>();
    protected Map<String, Resource> uriToResourceCache = new ConcurrentHashMap<String, Resource>();
    protected ResourceLoader defaultResourceLoader = new FileSystemResourceLoader();

    /**
     * Set the search location.
     *
     * @param searchLocation The search location
     */
    public void setSearchLocation(String searchLocation) {
        ResourceLoader resourceLoader = getDefaultResourceLoader();
        patchMatchingResolver = new PathMatchingResourcePatternResolver(resourceLoader);
        initializeForSearchLocation(searchLocation);
    }

    /**
     * @return The default resource loader
     */
    protected ResourceLoader getDefaultResourceLoader() {
        return defaultResourceLoader;
    }

    /**
     * Set search locations.
     *
     * @param searchLocations The search locations
     */
    public void setSearchLocations(Collection<String> searchLocations) {
        patchMatchingResolver = new PathMatchingResourcePatternResolver(getDefaultResourceLoader());
        for (String searchLocation : searchLocations) {
            initializeForSearchLocation(searchLocation);
        }
    }

    private void initializeForSearchLocation(String searchLocation) {
        String searchLocationPlusSlash = searchLocation.endsWith("/") ? searchLocation : searchLocation + FILE_SEPARATOR;
        classSearchDirectories.add(searchLocationPlusSlash + "src/main/java");
        classSearchDirectories.add(searchLocationPlusSlash + "src/main/groovy");
        classSearchDirectories.add(searchLocationPlusSlash + "src/main/kotlin");
        resourceSearchDirectories.add(searchLocationPlusSlash);
    }

    /**
     * Find a resource for URI.
     *
     * @param uri The uri
     * @return The resource if found, {@code null} otherwise
     */
    public Resource findResourceForURI(String uri) {
        Resource resource = uriToResourceCache.get(uri);
        if (resource == null) {

            PluginResourceInfo info = inferPluginNameFromURI(uri);

            String uriWebAppRelative = WEB_APP_DIR + uri;

            for (String resourceSearchDirectory : resourceSearchDirectories) {
                Resource res = resolveExceptionSafe(resourceSearchDirectory + uriWebAppRelative);
                if (res.exists()) {
                    resource = res;
                } else {
                    Resource dir = resolveExceptionSafe(resourceSearchDirectory);
                    if (dir.exists() && info != null) {
                        String filename = dir.getFilename();
                        if (filename != null && filename.equals(info.pluginName)) {
                            Resource pluginFile = dir.createRelative(WEB_APP_DIR + info.uri);
                            if (pluginFile.exists()) {
                                resource = pluginFile;
                            }
                        }
                    }
                }
            }

            if (resource == null || !resource.exists()) {
                Resource tmp = defaultResourceLoader != null ? defaultResourceLoader.getResource(uri) : null;
                if (tmp != null && tmp.exists()) {
                    resource = tmp;
                }
            }

            if (resource != null) {
                uriToResourceCache.put(uri, resource);
            }
        }
        return resource == NULL_RESOURCE ? null : resource;
    }

    private PluginResourceInfo inferPluginNameFromURI(String uri) {
        if (uri.startsWith("/plugins/")) {
            String withoutPluginsPath = uri.substring("/plugins/".length(), uri.length());
            int i = withoutPluginsPath.indexOf('/');
            if (i > -1) {
                PluginResourceInfo info = new PluginResourceInfo();
                info.pluginName = withoutPluginsPath.substring(0, i);
                info.uri = withoutPluginsPath.substring(i, withoutPluginsPath.length());
                return info;
            }
        }
        return null;
    }

    /**
     * Find a resource for class name.
     *
     * @param className The class name
     * @return The resource if found, {@code null} otherwise
     */
    public Resource findResourceForClassName(String className) {

        if (className.contains(CLOSURE_MARKER)) {
            className = className.substring(0, className.indexOf(CLOSURE_MARKER));
        }
        Resource resource = classNameToResourceCache.get(className);
        if (resource == null) {
            String classNameWithPathSeparator = className.replace(".", FILE_SEPARATOR);
            for (String pathPattern : getSearchPatternForExtension(classNameWithPathSeparator, ".groovy", ".java", ".kt")) {
                resource = resolveExceptionSafe(pathPattern);
                if (resource != null && resource.exists()) {
                    classNameToResourceCache.put(className, resource);
                    break;
                }
            }
        }
        return resource != null && resource.exists() ? resource : null;
    }

    private List<String> getSearchPatternForExtension(String classNameWithPathSeparator, String... extensions) {

        List<String> searchPatterns = new ArrayList<String>();
        for (String extension : extensions) {
            String filename = classNameWithPathSeparator + extension;
            for (String classSearchDirectory : classSearchDirectories) {
                searchPatterns.add(classSearchDirectory + FILE_SEPARATOR + filename);
            }
        }

        return searchPatterns;
    }

    private Resource resolveExceptionSafe(String pathPattern) {
        try {
            Resource[] resources = patchMatchingResolver.getResources("file:" + pathPattern);
            if (resources != null && resources.length > 0) {
                return resources[0];
            }
        } catch (IOException e) {
            return null;
        }
        return null;
    }

    /**
     * Sets the default resource loader.
     *
     * @param resourceLoader The resource loader
     */
    public void setResourceLoader(ResourceLoader resourceLoader) {
        defaultResourceLoader = resourceLoader;
    }

    /**
     * Plugin resource info.
     */
    class PluginResourceInfo {
        String pluginName;
        String uri;
    }
}
