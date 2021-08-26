package io.micronaut.context.env;

import io.micronaut.core.annotation.Internal;
import io.micronaut.core.io.ResourceLoader;

import java.io.InputStream;
import java.net.URL;
import java.util.Optional;
import java.util.stream.Stream;

@Internal
public interface ResourceLoaderDelegate extends ResourceLoader {

    ResourceLoader getResourceLoaderDelegate();

    @Override
    default Optional<InputStream> getResourceAsStream(String path) {
        return getResourceLoaderDelegate().getResourceAsStream(path);
    }

    @Override
    default Optional<URL> getResource(String path) {
        return getResourceLoaderDelegate().getResource(path);
    }

    @Override
    default Stream<URL> getResources(String name) {
        return getResourceLoaderDelegate().getResources(name);
    }

    @Override
    default boolean supportsPrefix(String path) {
        return getResourceLoaderDelegate().supportsPrefix(path);
    }

    @Override
    default ResourceLoader forBase(String basePath) {
        return getResourceLoaderDelegate().forBase(basePath);
    }

}
