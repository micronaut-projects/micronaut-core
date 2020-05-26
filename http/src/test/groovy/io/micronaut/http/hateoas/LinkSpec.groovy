package io.micronaut.http.hateoas

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.context.ApplicationContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class LinkSpec extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()

    void "test link builder"() {
        given:
        def link = Link.build("/foo/bar/{name}")
            .templated(true)
            .hreflang("es").build()

        expect:
        link.isTemplated()
        link.hreflang.isPresent()
        link.hreflang.get() == 'es'
        link.href == '/foo/bar/{name}'
    }

    void "test resource serde"() {
        given:
        def objectMapper = context.getBean(ObjectMapper)
        def test = new Test(name: "Fred")
        test.link(Link.SELF, Link.build("/test/{name}").templated(true).build())

        when:
        def json = objectMapper.writeValueAsString(test)

        then:
        json == '{"name":"Fred","_links":{"self":{"href":"/test/{name}","templated":true}}}'

        when:
        def read = objectMapper.readValue(json, Test)

        then:
        read.name == "Fred"
        def link = read.links.get(Link.SELF).get().first()
        link.href == '/test/{name}'
        link.templated == true
    }

    static class Test extends AbstractResource<Test> {
        String name
    }
}
