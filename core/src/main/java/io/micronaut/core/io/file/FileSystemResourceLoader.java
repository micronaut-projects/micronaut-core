package io.micronaut.core.io.file;

import io.micronaut.core.io.ResourceLoader;

public interface FileSystemResourceLoader extends ResourceLoader {

    static FileSystemResourceLoader defaultLoader() {
        return new DefaultFileSystemResourceLoader();
    }

    default boolean supportsPrefix(String path) {
        return path.startsWith("file:");
    }
}
