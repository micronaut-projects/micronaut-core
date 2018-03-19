package io.micronaut.core.io

import io.micronaut.core.io.file.DefaultFileSystemResourceLoader
import io.micronaut.core.io.file.FileSystemResourceLoader
import spock.lang.Specification

import java.nio.file.Paths

class FileSystemResourceLoaderSpec extends Specification {

    void "test resolving a resource"() {
        given:
        FileSystemResourceLoader loader = new DefaultFileSystemResourceLoader(base)

        expect:
        Paths.get(loader.getResource(resource).get().toURI()).toFile().isDirectory()

        where:
        base        | resource
        "."         | "src"
        "."         | "file:src/main"
        "src"       | "main"
        "file:src"  | "main"
        "file:src"  | "file:main"
        "file:src"  | "file:/main"
    }
}
