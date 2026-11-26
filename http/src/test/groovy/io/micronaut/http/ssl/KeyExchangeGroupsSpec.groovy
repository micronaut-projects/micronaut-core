package io.micronaut.http.ssl

import spock.lang.Specification
import spock.lang.Unroll

import javax.net.ssl.KeyManager
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLContextSpi
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLParameters
import javax.net.ssl.SSLServerSocketFactory
import javax.net.ssl.SSLSessionContext
import javax.net.ssl.SSLSocketFactory
import javax.net.ssl.TrustManager
import java.security.SecureRandom
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

    void "the JDK support check rejects a provider that ignores the configured groups"() {
        given:
        SSLContext base = SSLContext.getInstance('TLS')
        base.init(null, null, null)
        SSLContext ignoring = new IgnoringGroupsContext(base)

        expect: 'the base context enforces the groups'
        KeyExchangeGroups.jdkSupport(base).test('secp384r1')

        and: 'engines that drop the groups offer their defaults instead, so nothing counts as supported'
        !KeyExchangeGroups.jdkSupport(ignoring).test('secp384r1')
        !KeyExchangeGroups.jdkSupport(ignoring).test('x25519')

        when:
        KeyExchangeGroups.resolve(new SslConfiguration(pqcPolicy: PqcPolicy.STRICT), KeyExchangeGroups.jdkSupport(ignoring), 'JDK')

        then:
        thrown(SslConfigurationException)
    }

    void "a TLS 1.2 context supports classical groups but no post-quantum groups"() {
        given:
        SSLContext tls12 = SSLContext.getInstance('TLSv1.2')
        tls12.init(null, null, null)
        def supported = KeyExchangeGroups.jdkSupport(tls12)

        expect:
        supported.test('secp384r1')
        supported.test('x25519')
        !supported.test('X25519MLKEM768')
    }

    void "the client configuration does not inherit the default post-quantum policy"() {
        given:
        def defaults = new DefaultSslConfiguration()
        defaults.keyExchangeGroups = ['X25519MLKEM768', 'x25519'] as String[]
        defaults.pqcPolicy = PqcPolicy.STRICT

        when:
        def client = new ClientSslConfiguration(defaults, null, null, null)

        then:
        client.pqcPolicy == PqcPolicy.NONE
        client.keyExchangeGroups.get() == ['X25519MLKEM768', 'x25519'] as String[]
        client.protocols.isEmpty()
    }

    void "the supported groups are read from a ClientHello"() {
        expect:
        KeyExchangeGroups.parseSupportedGroups(java.nio.ByteBuffer.wrap(clientHello([0x000a: [0, 4, 0, 0x1d, 0x11, 0xec], 0x002b: [2, 3, 4]]))) == [0x001d, 0x11ec]
        KeyExchangeGroups.parseSupportedGroups(java.nio.ByteBuffer.wrap(clientHello([0x002b: [2, 3, 4]]))) == null
    }

    void "a server hello has no supported groups"() {
        given:
        byte[] record = clientHello([0x000a: [0, 2, 0, 0x1d]])
        record[5] = 2

        expect:
        KeyExchangeGroups.parseSupportedGroups(java.nio.ByteBuffer.wrap(record)) == null
    }

    private static byte[] clientHello(Map<Integer, List<Integer>> extensions) {
        def ext = new ByteArrayOutputStream()
        extensions.each { type, data ->
            ext.write(type >> 8); ext.write(type & 0xff)
            ext.write(data.size() >> 8); ext.write(data.size() & 0xff)
            data.each { ext.write(it) }
        }
        def body = new ByteArrayOutputStream()
        body.write(3); body.write(3)               // legacy version
        body.write(new byte[32])                   // random
        body.write(0)                              // session id
        body.write(0); body.write(2); body.write(0x13); body.write(0x01) // cipher suites
        body.write(1); body.write(0)               // compression
        body.write(ext.size() >> 8); body.write(ext.size() & 0xff)
        body.write(ext.toByteArray())
        def handshake = new ByteArrayOutputStream()
        handshake.write(1)
        handshake.write(0); handshake.write(body.size() >> 8); handshake.write(body.size() & 0xff)
        handshake.write(body.toByteArray())
        def record = new ByteArrayOutputStream()
        record.write(22); record.write(3); record.write(1)
        record.write(handshake.size() >> 8); record.write(handshake.size() & 0xff)
        record.write(handshake.toByteArray())
        return record.toByteArray()
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

/**
 * A context whose engines drop the named groups of the parameters they are given, like a
 * provider without support for {@link SSLParameters#setNamedGroups}.
 */
class IgnoringGroupsContext extends SSLContext {
    IgnoringGroupsContext(SSLContext delegate) {
        super(new Spi(delegate), delegate.provider, delegate.protocol)
    }

    static class Spi extends SSLContextSpi {
        final SSLContext delegate

        Spi(SSLContext delegate) {
            this.delegate = delegate
        }

        @Override
        protected void engineInit(KeyManager[] km, TrustManager[] tm, SecureRandom sr) {
        }

        @Override
        protected SSLSocketFactory engineGetSocketFactory() {
            delegate.socketFactory
        }

        @Override
        protected SSLServerSocketFactory engineGetServerSocketFactory() {
            delegate.serverSocketFactory
        }

        @Override
        protected SSLEngine engineCreateSSLEngine() {
            new IgnoringGroupsEngine(delegate.createSSLEngine())
        }

        @Override
        protected SSLEngine engineCreateSSLEngine(String host, int port) {
            new IgnoringGroupsEngine(delegate.createSSLEngine(host, port))
        }

        @Override
        protected SSLSessionContext engineGetServerSessionContext() {
            delegate.serverSessionContext
        }

        @Override
        protected SSLSessionContext engineGetClientSessionContext() {
            delegate.clientSessionContext
        }

        @Override
        protected SSLParameters engineGetSupportedSSLParameters() {
            delegate.supportedSSLParameters
        }
    }
}

class IgnoringGroupsEngine extends SSLEngine {
    @Delegate
    final SSLEngine delegate

    IgnoringGroupsEngine(SSLEngine delegate) {
        this.delegate = delegate
    }

    @Override
    void setSSLParameters(SSLParameters params) {
        params.namedGroups = null
        delegate.setSSLParameters(params)
    }
}
