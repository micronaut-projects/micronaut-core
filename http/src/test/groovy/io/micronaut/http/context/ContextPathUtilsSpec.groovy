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
}
