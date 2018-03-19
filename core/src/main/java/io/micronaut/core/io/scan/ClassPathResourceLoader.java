package io.micronaut.core.io.scan;

import io.micronaut.core.io.ResourceLoader;

public interface ClassPathResourceLoader extends ResourceLoader {

    static ClassPathResourceLoader defaultLoader(ClassLoader classLoader) {
        return new DefaultClassPathResourceLoader(classLoader);
    }

    ClassLoader getClassLoader();

    default boolean supportsPrefix(String path) {
        return path.startsWith("classpath:");
    }
}
