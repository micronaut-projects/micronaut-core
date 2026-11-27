package io.micronaut.http.client.jdk

import spock.lang.Specification

import javax.net.ssl.SSLContext
import javax.net.ssl.SSLEngine
import javax.net.ssl.SSLEngineResult
import javax.net.ssl.SSLParameters
import java.nio.ByteBuffer

class KeyExchangeGroupsSslContextSpec extends Specification {

    private static SSLContext base() {
        SSLContext context = SSLContext.getInstance('TLS')
        context.init(null, null, null)
        return context
    }

    void "engines start with the groups and keep them when parameters without groups are set"() {
        given:
        SSLContext context = new KeyExchangeGroupsSslContext(base(), ['secp384r1', 'x25519'] as String[])

        when:
        SSLEngine engine = context.createSSLEngine('example.com', 443)

        then:
        engine.SSLParameters.namedGroups == ['secp384r1', 'x25519'] as String[]
        engine.peerHost == 'example.com'
        engine.peerPort == 443

        when: 'the client applies its own parameters, which have no groups'
        engine.SSLParameters = new SSLParameters(protocols: ['TLSv1.3'] as String[])

        then:
        engine.SSLParameters.namedGroups == ['secp384r1', 'x25519'] as String[]
        engine.enabledProtocols == ['TLSv1.3'] as String[]

        when: 'explicit groups are kept as they are'
        def explicit = engine.SSLParameters
        explicit.namedGroups = ['x25519'] as String[]
        engine.SSLParameters = explicit

        then:
        engine.SSLParameters.namedGroups == ['x25519'] as String[]
    }

    void "the context delegates and exposes the groups in its default parameters"() {
        given:
        SSLContext delegate = base()
        SSLContext context = new KeyExchangeGroupsSslContext(delegate, ['secp384r1'] as String[])

        expect:
        context.defaultSSLParameters.namedGroups == ['secp384r1'] as String[]
        context.supportedSSLParameters.namedGroups != null
        context.socketFactory != null
        context.serverSocketFactory != null
        context.clientSessionContext != null
        context.serverSessionContext != null
        context.protocol == delegate.protocol
        context.provider == delegate.provider
        context.createSSLEngine().SSLParameters.namedGroups == ['secp384r1'] as String[]

        when:
        context.init(null, null, null)

        then:
        thrown(UnsupportedOperationException)
    }

    void "the engine delegates the handshake and its settings"() {
        given:
        SSLEngine engine = new KeyExchangeGroupsSslContext(base(), ['x25519'] as String[]).createSSLEngine()

        when:
        engine.useClientMode = true
        engine.needClientAuth = false
        engine.wantClientAuth = false
        engine.enableSessionCreation = true
        engine.enabledCipherSuites = engine.supportedCipherSuites
        engine.enabledProtocols = ['TLSv1.3'] as String[]
        engine.handshakeApplicationProtocolSelector = { e, protocols -> null }

        then:
        engine.useClientMode
        !engine.needClientAuth
        !engine.wantClientAuth
        engine.enableSessionCreation
        engine.enabledCipherSuites.length > 0
        engine.supportedProtocols.contains('TLSv1.3')
        engine.handshakeApplicationProtocolSelector != null
        engine.applicationProtocol == null
        engine.handshakeApplicationProtocol == null

        when: 'the ClientHello is written'
        ByteBuffer out = ByteBuffer.allocate(engine.session.packetBufferSize)
        engine.beginHandshake()
        SSLEngineResult result = engine.wrap([ByteBuffer.allocate(0)] as ByteBuffer[], 0, 1, out)

        then:
        result.bytesProduced() > 0
        engine.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_UNWRAP
        engine.handshakeSession == null || engine.handshakeSession.protocol != null
        engine.delegatedTask == null

        when: 'nothing has arrived yet'
        SSLEngineResult read = engine.unwrap(ByteBuffer.allocate(0), [ByteBuffer.allocate(engine.session.applicationBufferSize)] as ByteBuffer[], 0, 1)

        then:
        read.status == SSLEngineResult.Status.BUFFER_UNDERFLOW

        when: 'closing still has a close_notify to write'
        engine.closeOutbound()

        then:
        !engine.outboundDone
        engine.handshakeStatus == SSLEngineResult.HandshakeStatus.NEED_WRAP

        when:
        engine.closeInbound()

        then:
        thrown(javax.net.ssl.SSLException)
        engine.inboundDone
    }
}
