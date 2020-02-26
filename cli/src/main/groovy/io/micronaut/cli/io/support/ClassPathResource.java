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

import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.URL;
import java.util.Arrays;

/**
 * Resource implementation for class path resources.
 * Uses either a given ClassLoader or a given Class for loading resources.
 *
 * <p>Supports resolution as <code>java.io.File</code> if the class path
 * resource resides in the file system, but not for resources in a JAR.
 * Always supports resolution as URL.
 *
 * @author Juergen Hoeller
 * @see java.lang.ClassLoader#getResourceAsStream(String)
 * @see java.lang.Class#getResourceAsStream(String)
 * @since 28.12.2003
 */
public class ClassPathResource extends AbstractFileResolvingResource {

    private final String path;

    private ClassLoader classLoader;

    private Class<?> clazz;

    /**
     * Create a new ClassPathResource for ClassLoader usage.
     * A leading slash will be removed, as the ClassLoader
     * resource access methods will not accept it.
     * <p>The thread context class loader will be used for
     * loading the resource.
     *
     * @param path the absolute path within the class path
     * @see java.lang.ClassLoader#getResourceAsStream(String)
     */
    public ClassPathResource(String path) {
        this(path, (ClassLoader) null);
    }

    /**
     * Create a new ClassPathResource for ClassLoader usage.
     * A leading slash will be removed, as the ClassLoader
     * resource access methods will not accept it.
     *
     * @param path        the absolute path within the classpath
     * @param classLoader the class loader to load the resource with,
     *                    or <code>null</code> for the thread context class loader
     * @see java.lang.ClassLoader#getResourceAsStream(String)
     */
    public ClassPathResource(String path, ClassLoader classLoader) {
        String pathToUse = ResourceUtils.cleanPath(path);
        if (pathToUse.startsWith("/")) {
            pathToUse = pathToUse.substring(1);
        }
        this.path = pathToUse;
        this.classLoader = classLoader == null ? DefaultResourceLoader.getDefaultClassLoader() : classLoader;
    }

    /**
     * Create a new ClassPathResource for Class usage.
     * The path can be relative to the given class,
     * or absolute within the classpath via a leading slash.
     *
     * @param path  relative or absolute path within the class path
     * @param clazz the class to load resources with
     * @see java.lang.Class#getResourceAsStream
     */
    public ClassPathResource(String path, Class<?> clazz) {
        this.path = ResourceUtils.cleanPath(path);
        this.clazz = clazz;
    }

    /**
     * Create a new ClassPathResource with optional ClassLoader and Class.
     * Only for internal usage.
     *
     * @param path        relative or absolute path within the classpath
     * @param classLoader the class loader to load the resource with, if any
     * @param clazz       the class to load resources with, if any
     */
    protected ClassPathResource(String path, ClassLoader classLoader, Class<?> clazz) {
        this.path = ResourceUtils.cleanPath(path);
        this.classLoader = classLoader;
        this.clazz = clazz;
    }

    /**
     * @return The path for this resource (as resource path within the class path).
     */
    public final String getPath() {
        return path;
    }

    /**
     * @return The ClassLoader that this resource will be obtained from.
     */
    public final ClassLoader getClassLoader() {
        return classLoader == null ? clazz.getClassLoader() : classLoader;
    }

    /**
     * This implementation checks for the resolution of a resource URL.
     *
     * @see java.lang.ClassLoader#getResource(String)
     * @see java.lang.Class#getResource(String)
     */
    @Override
    public boolean exists() {
        URL url;
        if (clazz == null) {
            url = classLoader.getResource(path);
        } else {
            url = clazz.getResource(path);
        }
        return url != null;
    }

    /**
     * This implementation opens an InputStream for the given class path resource.
     *
     * @see java.lang.ClassLoader#getResourceAsStream(String)
     * @see java.lang.Class#getResourceAsStream(String)
     */
    @Override
    public InputStream getInputStream() throws IOException {
        InputStream is;
        if (clazz == null) {
            is = classLoader.getResourceAsStream(path);
        } else {
            is = clazz.getResourceAsStream(path);
        }
        if (is == null) {
            throw new FileNotFoundException(
                getDescription() + " cannot be opened because it does not exist");
        }
        return is;
    }

    /**
     * This implementation returns a URL for the underlying class path resource.
     *
     * @see java.lang.ClassLoader#getResource(String)
     * @see java.lang.Class#getResource(String)
     */
    @Override
    public URL getURL() throws IOException {
        URL url;
        if (clazz == null) {
            url = classLoader.getResource(path);
        } else {
            url = clazz.getResource(path);
        }
        if (url == null) {
            throw new FileNotFoundException(
                getDescription() + " cannot be resolved to URL because it does not exist");
        }
        return url;
    }

    @Override
    public URI getURI() throws IOException {
        return getFile().toURI();
    }

    /**
     * This implementation creates a ClassPathResource, applying the given path
     * relative to the path of the underlying resource of this descriptor.
     */
    @Override
    public Resource createRelative(String relativePath) {
        String pathToUse = ResourceUtils.applyRelativePath(path, relativePath);
        return new ClassPathResource(pathToUse, classLoader, clazz);
    }

    /**
     * This implementation returns the name of the file that this class path
     * resource refers to.
     */
    @Override
    public String getFilename() {
        return ResourceUtils.getFilename(path);
    }

    /**
     * This implementation returns a description that includes the class path location.
     */
    @Override
    public String getDescription() {
        StringBuilder builder = new StringBuilder("class path resource [");

        if (clazz != null) {
            builder.append(ResourceUtils.classPackageAsResourcePath(clazz));
            builder.append('/');
        }

        builder.append(path);
        builder.append(']');
        return builder.toString();
    }

    /**
     * This implementation compares the underlying class path locations.
     */
    @Override
    public boolean equals(Object obj) {
        if (obj == this) {
            return true;
        }
        if (obj instanceof ClassPathResource) {
            ClassPathResource otherRes = (ClassPathResource) obj;
            return (path.equals(otherRes.path) &&
                nullSafeEquals(classLoader, otherRes.classLoader) &&
                nullSafeEquals(clazz, otherRes.clazz));
        }
        return false;
    }

    private static boolean nullSafeEquals(Object o1, Object o2) {
        if (o1 == o2) {
            return true;
        }
        if (o1 == null || o2 == null) {
            return false;
        }
        if (o1.equals(o2)) {
            return true;
        }
        if (o1.getClass().isArray() && o2.getClass().isArray()) {
            if (o1 instanceof Object[] && o2 instanceof Object[]) {
                return Arrays.equals((Object[]) o1, (Object[]) o2);
            }
            if (o1 instanceof boolean[] && o2 instanceof boolean[]) {
                return Arrays.equals((boolean[]) o1, (boolean[]) o2);
            }
            if (o1 instanceof byte[] && o2 instanceof byte[]) {
                return Arrays.equals((byte[]) o1, (byte[]) o2);
            }
            if (o1 instanceof char[] && o2 instanceof char[]) {
                return Arrays.equals((char[]) o1, (char[]) o2);
            }
            if (o1 instanceof double[] && o2 instanceof double[]) {
                return Arrays.equals((double[]) o1, (double[]) o2);
            }
            if (o1 instanceof float[] && o2 instanceof float[]) {
                return Arrays.equals((float[]) o1, (float[]) o2);
            }
            if (o1 instanceof int[] && o2 instanceof int[]) {
                return Arrays.equals((int[]) o1, (int[]) o2);
            }
            if (o1 instanceof long[] && o2 instanceof long[]) {
                return Arrays.equals((long[]) o1, (long[]) o2);
            }
            if (o1 instanceof short[] && o2 instanceof short[]) {
                return Arrays.equals((short[]) o1, (short[]) o2);
            }
        }
        return false;
    }

    /**
     * This implementation returns the hash code of the underlying
     * class path location.
     */
    @Override
    public int hashCode() {
        return path.hashCode();
    }
}
