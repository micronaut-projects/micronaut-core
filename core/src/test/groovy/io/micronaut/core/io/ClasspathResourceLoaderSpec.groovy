package io.micronaut.core.io

import io.micronaut.core.io.scan.ClassPathResourceLoader
import io.micronaut.core.io.scan.DefaultClassPathResourceLoader
import spock.lang.Specification

class ClasspathResourceLoaderSpec extends Specification {

    void "test resolving a resource"() {
        given:
        ClassPathResourceLoader loader = new DefaultClassPathResourceLoader(getClass().getClassLoader(), base)

        expect:
        loader.getResource(resource).get().text == "bar.txt"

        where:
        base             | resource
        null             | "foo/bar.txt"
        null             | "classpath:foo/bar.txt"
        "foo"            | "bar.txt"
        "/foo"           | "bar.txt"
        "classpath:foo"  | "bar.txt"
        "classpath:foo"  | "classpath:bar.txt"
        "classpath:/foo" | "classpath:bar.txt"
        "classpath:/foo" | "classpath:/bar.txt"
        "classpath:foo"  | "classpath:/bar.txt"
    }
}
