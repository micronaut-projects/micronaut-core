package org.particleframework.core.io;

import org.particleframework.core.io.file.FileSystemResourceLoader;
import org.particleframework.core.io.scan.ClassPathResourceLoader;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.util.Enumeration;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Basic abstraction over resource loading
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface ResourceLoader {

    /**
     * Obtains a resource as a stream
     *
     * @param path The path
     * @return An optional resource
     */
    Optional<InputStream> getResourceAsStream(String path);

    /**
     * @return The class loader for this resource loader
     */
    ClassLoader getClassLoader();

    default Optional<URL> getResource(String path) {
        URL resource = getClassLoader().getResource(path);
        if(resource != null) {
            return Optional.of(resource);
        }
        return Optional.empty();
    }

    default Stream<URL> getResources(String fileName) {
        Enumeration<URL> all;
        try {
            all = getClassLoader().getResources(fileName);
        } catch (IOException e) {
            return Stream.empty();
        }
        Stream.Builder<URL> builder = Stream.builder();
        while (all.hasMoreElements()) {
            URL url = all.nextElement();
            builder.accept(url);
        }
        return builder.build();
    }

    /**
     * Create a resource loader for the given classloader
     * @param classLoader The class loader
     * @return The resource loader
     */
    static ClassPathResourceLoader of(ClassLoader classLoader) {
        return new ClassPathResourceLoader(classLoader);
    }

    static Optional<ResourceLoader> forPath(String path, ClassLoader classLoader) {
        if (path.startsWith("classpath:")) {
            return Optional.of(new ClassPathResourceLoader(classLoader, path.substring(10)));
        } else if (path.startsWith("file:")) {
            return Optional.of(new FileSystemResourceLoader(new File(path.substring(5))));
        } else {
            return Optional.empty();
        }
    }

    static Optional<ResourceLoader> forResource(String path, ClassLoader classLoader) {
        if (path.startsWith("classpath:")) {
            return Optional.of(new ClassPathResourceLoader(classLoader, ""));
        } else if (path.startsWith("file:")) {
            return Optional.of(new FileSystemResourceLoader(new File(String.valueOf(path.charAt(5)))));
        } else {
            return Optional.empty();
        }
    }
}
