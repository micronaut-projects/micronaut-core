package io.micronaut.web.router

import io.micronaut.core.io.ResourceResolver
import io.micronaut.web.router.resource.StaticResourceConfiguration
import io.micronaut.web.router.resource.StaticResourceResolver
import spock.lang.Specification

class StaticResourceResolverSpec extends Specification {

    void "test the path is not mangled between resolution attempts"() {
        given:
        ResourceResolver rr = new ResourceResolver()
        StaticResourceConfiguration config1 = new StaticResourceConfiguration(rr)
        config1.setPaths(["classpath:public"])
        config1.setMapping("/**")
        StaticResourceConfiguration config2 = new StaticResourceConfiguration(rr)
        config2.setPaths(["classpath:other"])
        config2.setMapping("/other/**")
        StaticResourceResolver resolver = new StaticResourceResolver([config1, config2])

        when:
        URL url = resolver.resolve("/").get()

        then:
        url.toString().endsWith("public/index.html")

        when:
        url = resolver.resolve("/other").get()

        then:
        url.toString().endsWith("other/index.html")
    }
}
