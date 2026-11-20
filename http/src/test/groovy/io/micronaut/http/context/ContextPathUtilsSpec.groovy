package io.micronaut.http.context

import spock.lang.Specification

class ContextPathUtilsSpec extends Specification {

    void "ContextPathUtils.prependContextPath with serverContextPathProvider"() {
        expect:
        '/bar/foo' == ContextPathUtils.prepend(URI.create("/foo"), new ServerContextPathProvider() {
            @Override
            String getContextPath() {
                "/bar"
            }
        }).toString()
    }

    void "ContextPathUtils.prependContextPath with ClientContextPathProvider"() {
        expect:
        '/bar/foo' == ContextPathUtils.prepend(URI.create("/foo"), new ClientContextPathProvider() {
            @Override
            Optional<String> getContextPath() {
                Optional.of("/bar")
            }
        }).toString()
    }

    void "ContextPathUtils.prependContextPath with path"() {
        expect:
        '/bar/foo' == ContextPathUtils.prepend(URI.create("/foo"),"/bar").toString()
        '/foo' == ContextPathUtils.prepend(URI.create("/foo"), null as String).toString()
    }

    void "ContextPathUtils.prepend keeps empty path segments of the request URI"(String contextPath, String uri, String expected) {
        expect:
        ContextPathUtils.prepend(URI.create(uri), contextPath).toString() == expected

        where:
        contextPath | uri                       | expected
        '/'         | '/catch/a//b'             | '/catch/a//b'
        '/'         | '/catch/a;x=1//b;y=2?q=1' | '/catch/a;x=1//b;y=2?q=1'
        '/'         | '/a/b//'                  | '/a/b//'
        '/bar'      | '/a//b'                   | '/bar/a//b'
        '/bar/'     | '/a//b'                   | '/bar/a//b'
        '/bar//'    | 'a//b'                    | '/bar/a//b'
        '/bar'      | '/a?x=//y'                | '/bar/a?x=//y'
        '/'         | '//evil/x'                | '/evil/x'
        '/bar'      | '//evil/x'                | '/bar/evil/x'
        '/bar'      | '/'                       | '/bar'
        '/'         | '/'                       | '/'
        '/'         | '?a=b'                    | '/?a=b'
        '/bar'      | '?a=b'                    | '/bar?a=b'
        '/bar'      | ''                        | '/bar'
    }
}
