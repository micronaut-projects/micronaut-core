package io.micronaut.http.resource;

import io.micronaut.context.annotation.Factory;
import io.micronaut.core.io.ResourceLoader;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.core.io.file.DefaultFileSystemResourceLoader;
import io.micronaut.core.io.file.FileSystemResourceLoader;
import io.micronaut.core.io.scan.ClassPathResourceLoader;
import io.micronaut.core.io.scan.DefaultClassPathResourceLoader;

import javax.inject.Singleton;

/**
 * Creates beans for {@link ResourceLoader}s to handle static resource requests. Registers
 * a resource resolver that uses those beans.
 *
 * @author James Kleeh
 * @since 1.0
 */
@Factory
public class ResourceLoaderFactory {

    @Singleton
    ClassPathResourceLoader getClassPathResourceLoader() {
        return new DefaultClassPathResourceLoader(ResourceLoaderFactory.class.getClassLoader());
    }

    @Singleton
    FileSystemResourceLoader fileSystemResourceLoader() {
        return new DefaultFileSystemResourceLoader();
    }

    @Singleton
    ResourceResolver resourceResolver(ResourceLoader[] resourceLoaders) {
        return new ResourceResolver(resourceLoaders);
    }
}
