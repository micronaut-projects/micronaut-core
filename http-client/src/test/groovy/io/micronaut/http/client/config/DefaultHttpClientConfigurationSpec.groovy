package io.micronaut.http.client.config

import io.micronaut.context.ApplicationContext
import io.micronaut.http.client.DefaultHttpClientConfiguration
import io.micronaut.http.client.HttpClientConfiguration
import spock.lang.Issue
import spock.lang.Specification

import java.time.Duration

class DefaultHttpClientConfigurationSpec extends Specification {

    void "test config for #key"() {
        given:
        def ctx = ApplicationContext.run(
                ("micronaut.http.client.$key".toString()): value
        )
        HttpClientConfiguration config = ctx.getBean(HttpClientConfiguration)

        expect:
        config[property] == expected

        cleanup:
        ctx.close()

        where:
        key                         | property                 | value   | expected
        'read-timeout'              | 'readTimeout'            | '15s'   | Optional.of(Duration.ofSeconds(15))
        'proxy-type'                | 'proxyType'              | 'http'  | Proxy.Type.HTTP
        'read-idle-timeout'         | 'readIdleTimeout'        | '-1s'   | Optional.of(Duration.ofSeconds(-1))
        'read-idle-timeout'         | 'readIdleTimeout'        | '1s'    | Optional.of(Duration.ofSeconds(1))
        'read-idle-timeout'         | 'readIdleTimeout'        | '-1'    | Optional.empty()
        'connect-ttl'               | 'connectTtl'             | '1s'    | Optional.of(Duration.ofSeconds(1))
        'exception-on-error-status' | 'exceptionOnErrorStatus' | 'false' | false
        'shutdown-quiet-period'     | 'shutdownQuietPeriod'    | '1ms'   | Optional.of(Duration.ofMillis(1))
        'shutdown-quiet-period'     | 'shutdownQuietPeriod'    | '2s'    | Optional.of(Duration.ofSeconds(2))
        'shutdown-timeout'          | 'shutdownTimeout'        | '100ms' | Optional.of(Duration.ofMillis(100))
        'shutdown-timeout'          | 'shutdownTimeout'        | '15s'   | Optional.of(Duration.ofSeconds(15))
        'follow-redirects'          | 'followRedirects'        | 'false' | false
        'max-header-size'           | 'maxHeaderSize'          | '16384' | 16384
    }

    void "test pool config"() {
        given:
        def ctx = ApplicationContext.run(
                'micronaut.http.client.pool.enabled': false
        )
        HttpClientConfiguration config = ctx.getBean(HttpClientConfiguration)

        expect:
        !config.connectionPoolConfiguration.enabled

        cleanup:
        ctx.close()
    }

    void "test WebSocket compression config"() {
        given:
        def ctx = ApplicationContext.run(
                'micronaut.http.client.ws.compression.enabled': false
        )
        HttpClientConfiguration config = ctx.getBean(HttpClientConfiguration)
        HttpClientConfiguration.WebSocketCompressionConfiguration compressionConfig = config.getWebSocketCompressionConfiguration()

        expect:
        !config.webSocketCompressionConfiguration.enabled

        cleanup:
        ctx.close()
    }

    void "test overriding logger for the client"() {
        given:
        def ctx = ApplicationContext.run(
                'micronaut.http.client.loggerName': 'myclient.custom.logger'
        )
        HttpClientConfiguration config = ctx.getBean(HttpClientConfiguration)

        expect:
        config.loggerName == Optional.of('myclient.custom.logger')

        cleanup:
        ctx.close()
    }

    void "test setting a proxy selector" () {
        given: "a http client config and two addresses"
        def config = new DefaultHttpClientConfiguration()

        when: "I register proxy selector that use proxy for addressOne but not for addressTwo"
        config.setProxySelector(new ProxySelector() {

            @Override
            List<Proxy> select(URI uri) {
                uri.host == 'a' ? [ new Proxy(Proxy.Type.HTTP, new InetSocketAddress(8080)) ] : [ Proxy.NO_PROXY ]
            }

            @Override
            void connectFailed(URI uri, SocketAddress sa, IOException ioe) {
                // do nothing
            }
        })

        then: "proxy is used for first address but not for the second"
        def proxyOne = config.resolveProxy(false, "a", 80)

        proxyOne.type() == Proxy.Type.HTTP
        proxyOne.address().port == 8080

        config.resolveProxy(false, "b", 80) == Proxy.NO_PROXY
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/pull/10013')
    void "default connection pool idle timeout"() {
        given:
        def cfg = new DefaultHttpClientConfiguration()

        expect:
        cfg.connectionPoolIdleTimeout.empty
    }
}
