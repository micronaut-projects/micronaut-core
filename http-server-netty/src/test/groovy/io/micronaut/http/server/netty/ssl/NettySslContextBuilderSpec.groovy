package io.micronaut.http.server.netty.ssl

import io.micronaut.http.netty.NettySslContextBuilder

import spock.lang.Specification

class NettySslContextBuilderSpec extends Specification {
    def 'alternative builder sees unsupported cipher policy'() {
        given:
        def builder = new PolicyBuilder()

        expect:
        !builder.policy()

        when:
        builder.ciphers(['unknown'], true)

        then:
        builder.policy()

        when:
        builder.ciphers(['unknown'], false)

        then:
        !builder.policy()

        when:
        builder.http2()

        then:
        !builder.policy()
    }

    private static class PolicyBuilder extends NettySslContextBuilder {
        PolicyBuilder() { super(false) }
        boolean policy() { ignoreUnsupportedCiphers() }
    }
}
