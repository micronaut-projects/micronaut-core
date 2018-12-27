package io.micronaut.http.uri

import spock.lang.Specification
import spock.lang.Unroll

class UriBuilderSpec extends Specification {

    void "test uri builder expand"() {
        given:
        def builder = UriBuilder.of("/person/{name}")

        when:
        builder.path("/features/{feature}")
        def result = builder.expand(name:"Fred Flintstone", feature:"age")

        then:
        result.toString() == '/person/Fred%20Flintstone/features/age'

        when:
        builder.fragment('{#hash}')
        result = builder.expand(name:"Fred Flintstone", feature:"age", hash: "val")

        then:
        result.toString() == '/person/Fred%20Flintstone/features/age#val'

        when:
        builder.queryParam("q", "hello world")
        result = builder.expand(name:"Fred Flintstone", feature:"age", hash: "val")

        then:
        result.toString() == '/person/Fred%20Flintstone/features/age?q=hello+world#val'

        when:
        builder.host("myhost")
        builder.scheme("http")
        builder.port(9090)
        builder.userInfo("username:p@s\$w0rd")
        result = builder.expand(name:"Fred Flintstone", feature:"age", hash: "val")

        then:
        result.toString() == 'http://username:p%40s%24w0rd@myhost:9090/person/Fred%20Flintstone/features/age?q=hello+world#val'
    }
    void "test uri builder toString()"() {
        given:
        def builder = UriBuilder.of("")

        when:
        builder.path("/foo")

        then:
        builder.toString() == '/foo'

        when:
        builder.path("/bar/")
               .path('/baz')

        then:
        builder.toString() == '/foo/bar/baz'

        when:
        builder.host("myhost")

        then:
        builder.toString() == '//myhost/foo/bar/baz'

        when:
        builder.port(9090)

        then:
        builder.toString() == '//myhost:9090/foo/bar/baz'

        when:
        builder.scheme("https")

        then:
        builder.toString() == 'https://myhost:9090/foo/bar/baz'

        when:
        builder.queryParam("offset", 10)

        then:
        builder.toString() == 'https://myhost:9090/foo/bar/baz?offset=10'
    }

    @Unroll
    void "test queryParam method for uri #uri"() {

        given:
        def builder = UriBuilder.of(uri)
        for (p in params) {
            if (p.value instanceof List) {
                builder.queryParam(p.key, *p.value)
            } else {
                builder.queryParam(p.key, p.value)
            }
        }

        expect:
        builder.toString() == expected

        where:
        uri                  | params                  | expected
        '/foo?existing=true' | ['foo': 'bar']          | '/foo?existing=true&foo=bar'
        '/foo'               | ['foo': 'bar']          | '/foo?foo=bar'
        '/foo'               | ['foo': 'hello world']  | '/foo?foo=hello+world'
        '/foo'               | ['foo': ['bar', 'baz']] | '/foo?foo=bar&foo=baz'
    }
}
