package io.micronaut.http.ssl

import spock.lang.Specification
import spock.lang.Unroll

import java.util.function.Predicate

class KeyExchangeGroupsSpec extends Specification {

    private static final Predicate<String> ALL = { true } as Predicate<String>
    private static final Predicate<String> CLASSICAL_ONLY = { !KeyExchangeGroups.isPostQuantum(it) } as Predicate<String>

    void "no groups and the default policy keep the engine defaults"() {
        expect:
        KeyExchangeGroups.resolve(new SslConfiguration(), ALL, "test") == null
    }

    void "configured groups are applied in order, trimmed and without duplicates"() {
        given:
        def ssl = new SslConfiguration(keyExchangeGroups: ['X25519MLKEM768', ' x25519 ', 'X25519', 'secp256r1'] as String[])

        expect:
        KeyExchangeGroups.resolve(ssl, ALL, "test") == ['X25519MLKEM768', 'x25519', 'secp256r1']
    }

    void "unsupported groups fail with the engine name"() {
        given:
        def ssl = new SslConfiguration(keyExchangeGroups: ['X25519MLKEM768', 'x25519'] as String[])

        when:
        KeyExchangeGroups.resolve(ssl, CLASSICAL_ONLY, "JDK")

        then:
        def e = thrown(SslConfigurationException)
        e.message == 'The JDK TLS implementation does not support the configured key exchange groups [X25519MLKEM768]'
    }

    void "relaxed policy defaults to a hybrid group first with classical fallbacks"() {
        given:
        def ssl = new SslConfiguration(pqcPolicy: PqcPolicy.RELAXED)

        expect:
        KeyExchangeGroups.resolve(ssl, ALL, "test") == ['X25519MLKEM768', 'x25519', 'secp256r1', 'secp384r1']
    }

    void "strict policy defaults only contain supported post-quantum groups"() {
        given:
        def ssl = new SslConfiguration(pqcPolicy: PqcPolicy.STRICT)

        expect:
        KeyExchangeGroups.resolve(ssl, KeyExchangeGroups.caseInsensitive(['x25519mlkem768', 'x25519']), "test") == ['X25519MLKEM768']
    }

    @Unroll
    void "#policy policy without engine support for post-quantum groups fails"() {
        given:
        def ssl = new SslConfiguration(pqcPolicy: policy)

        when:
        KeyExchangeGroups.resolve(ssl, CLASSICAL_ONLY, "JDK")

        then:
        def e = thrown(SslConfigurationException)
        e.message.startsWith("The ${name} post-quantum policy requires a post-quantum key exchange group, but the JDK TLS implementation supports none of")

        where:
        policy           | name
        PqcPolicy.RELAXED | 'relaxed'
        PqcPolicy.STRICT  | 'strict'
    }

    void "strict policy rejects classical groups"() {
        given:
        def ssl = new SslConfiguration(pqcPolicy: PqcPolicy.STRICT, keyExchangeGroups: ['X25519MLKEM768', 'x25519'] as String[])

        when:
        KeyExchangeGroups.resolve(ssl, ALL, "test")

        then:
        def e = thrown(SslConfigurationException)
        e.message == 'The strict post-quantum policy only allows post-quantum key exchange groups, but found [x25519]'
    }

    void "relaxed policy requires a post-quantum group"() {
        given:
        def ssl = new SslConfiguration(pqcPolicy: PqcPolicy.RELAXED, keyExchangeGroups: ['x25519', 'secp256r1'] as String[])

        when:
        KeyExchangeGroups.resolve(ssl, ALL, "test")

        then:
        def e = thrown(SslConfigurationException)
        e.message == 'The relaxed post-quantum policy requires at least one post-quantum key exchange group, such as X25519MLKEM768, but found [x25519, secp256r1]'
    }

    void "relaxed policy accepts a hybrid group followed by classical groups"() {
        given:
        def ssl = new SslConfiguration(pqcPolicy: PqcPolicy.RELAXED, keyExchangeGroups: ['X25519MLKEM768', 'x25519'] as String[])

        expect:
        KeyExchangeGroups.resolve(ssl, ALL, "test") == ['X25519MLKEM768', 'x25519']
    }

    void "relaxed policy requires a classical fallback group"() {
        given:
        def ssl = new SslConfiguration(pqcPolicy: PqcPolicy.RELAXED, keyExchangeGroups: ['X25519MLKEM768'] as String[])

        when:
        KeyExchangeGroups.resolve(ssl, ALL, "test")

        then:
        def e = thrown(SslConfigurationException)
        e.message.startsWith('The relaxed post-quantum policy requires at least one classical key exchange group as a fallback')
    }

    void "relaxed policy defaults fail without a supported classical group"() {
        given:
        def ssl = new SslConfiguration(pqcPolicy: PqcPolicy.RELAXED)

        when:
        KeyExchangeGroups.resolve(ssl, { KeyExchangeGroups.isPostQuantum(it) } as Predicate<String>, "test")

        then:
        def e = thrown(SslConfigurationException)
        e.message.startsWith('The relaxed post-quantum policy requires a classical key exchange group as a fallback')
    }

    @Unroll
    void "strict policy rejects protocols #protocols"() {
        given:
        def ssl = new SslConfiguration(pqcPolicy: PqcPolicy.STRICT, protocols: protocols as String[])

        when:
        KeyExchangeGroups.resolve(ssl, ALL, "test")

        then:
        def e = thrown(SslConfigurationException)
        e.message.startsWith('The strict post-quantum policy only allows TLSv1.3')

        where:
        protocols << [['TLSv1.2'], ['TLSv1.3', 'TLSv1.2']]
    }

    void "strict policy enables only TLS 1.3 when no protocols are configured"() {
        given:
        def ssl = new SslConfiguration(pqcPolicy: PqcPolicy.STRICT)

        expect:
        ssl.protocols.get() == ['TLSv1.3'] as String[]
        new SslConfiguration().protocols.isEmpty()
        new SslConfiguration(pqcPolicy: PqcPolicy.STRICT, protocols: ['TLSv1.3'] as String[]).protocols.get() == ['TLSv1.3'] as String[]
    }

    @Unroll
    void "the group entry '#entry' is rejected"() {
        given:
        def ssl = new SslConfiguration(keyExchangeGroups: [entry] as String[])

        when:
        KeyExchangeGroups.resolve(ssl, ALL, "test")

        then:
        def e = thrown(SslConfigurationException)
        e.message == "Invalid TLS key exchange group '${entry}': configure each group as its own list entry"

        where:
        entry << ['X25519MLKEM768:X25519', 'x25519 secp256r1']
    }

    void "nothing is configured by default"() {
        expect:
        !KeyExchangeGroups.isConfigured(new SslConfiguration())
        KeyExchangeGroups.isConfigured(new SslConfiguration(pqcPolicy: PqcPolicy.RELAXED))
        KeyExchangeGroups.isConfigured(new SslConfiguration(keyExchangeGroups: ['x25519'] as String[]))
    }

    void "post-quantum policies require TLS 1.3 when protocols are configured"() {
        given:
        def ssl = new SslConfiguration(pqcPolicy: PqcPolicy.RELAXED, protocols: ['TLSv1.2'] as String[])

        when:
        KeyExchangeGroups.resolve(ssl, ALL, "test")

        then:
        def e = thrown(SslConfigurationException)
        e.message == 'The relaxed post-quantum policy requires TLSv1.3, but the enabled protocols are [TLSv1.2]'
    }

    void "an empty group list fails"() {
        given:
        def ssl = new SslConfiguration(keyExchangeGroups: [' '] as String[])

        when:
        KeyExchangeGroups.resolve(ssl, ALL, "test")

        then:
        thrown(SslConfigurationException)
    }

    @Unroll
    void "#group is post-quantum: #expected"() {
        expect:
        KeyExchangeGroups.isPostQuantum(group) == expected

        where:
        group                | expected
        'X25519MLKEM768'     | true
        'SecP256r1MLKEM768'  | true
        'MLKEM1024'          | true
        'x25519'             | false
        'secp256r1'          | false
        'ffdhe2048'          | false
    }

    void "the JDK support check probes the groups the engine offers"() {
        given:
        def supported = KeyExchangeGroups.jdkSupport(null)

        expect:
        supported.test('x25519')
        supported.test('X25519')
        supported.test('secp384r1')
        !supported.test('bogus')
    }

    void "a non ClientHello record has no supported groups"() {
        expect:
        KeyExchangeGroups.parseSupportedGroups(java.nio.ByteBuffer.wrap([23, 3, 3, 0, 0] as byte[])) == null
        KeyExchangeGroups.parseSupportedGroups(java.nio.ByteBuffer.wrap([22, 3, 1] as byte[])) == null
    }

    void "the server configuration inherits groups and policy from the default configuration"() {
        given:
        def defaults = new DefaultSslConfiguration()
        defaults.keyExchangeGroups = ['X25519MLKEM768'] as String[]
        defaults.pqcPolicy = PqcPolicy.STRICT

        when:
        def server = new ServerSslConfiguration(defaults, null, null, null)

        then:
        server.keyExchangeGroups.get() == ['X25519MLKEM768'] as String[]
        server.pqcPolicy == PqcPolicy.STRICT
    }
}
