package io.micronaut.core.io

import io.micronaut.core.io.file.DefaultFileSystemResourceLoader
import io.micronaut.core.io.file.FileSystemResourceLoader
import io.micronaut.core.io.scan.ClassPathResourceLoader
import io.micronaut.core.io.scan.DefaultClassPathResourceLoader
import spock.lang.Specification

class ResourceResolverSpec extends Specification {

    void "test the correct type is returned"() {
        given:
        ResourceResolver resolver = new ResourceResolver()

        expect:
        resolver.getLoader(ClassPathResourceLoader).get() instanceof DefaultClassPathResourceLoader
        resolver.getLoader(FileSystemResourceLoader).get() instanceof DefaultFileSystemResourceLoader
        resolver.getSupportingLoader("classpath:foo").get() instanceof DefaultClassPathResourceLoader
        resolver.getSupportingLoader("file:foo").get() instanceof DefaultFileSystemResourceLoader
        resolver.getLoaderForBasePath("classpath:foo").get() instanceof DefaultClassPathResourceLoader
        resolver.getLoaderForBasePath("file:foo").get() instanceof DefaultFileSystemResourceLoader
    }
}
