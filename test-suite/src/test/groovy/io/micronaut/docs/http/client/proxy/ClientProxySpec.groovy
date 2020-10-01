package io.micronaut.docs.http.client.proxy

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.runtime.server.EmbeddedServer
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.utility.MountableFile
import spock.lang.AutoCleanup
import spock.lang.Retry
import spock.lang.Specification

@Retry
class ClientProxySpec extends Specification {

    static final int PROXY_PORT = 3128
    static final String URL = "https://micronaut.io/"
    static final String HTML_FRAGMENT = 'https://micronaut.io/documentation.html'

    @AutoCleanup
    GenericContainer proxyContainer =
            new GenericContainer('sameersbn/squid:latest')
                    .withCopyFileToContainer(MountableFile.forClasspathResource('/squid.conf'), '/etc/squid/squid.conf')
                    .withExposedPorts(PROXY_PORT)
                    .waitingFor(new HostPortWaitStrategy())

    @AutoCleanup
    EmbeddedServer embeddedServer

    def setup() {
        proxyContainer.start()
    }

    void "test downloading via http proxy using proxy-address"() {
        given:
        startServer([
                'micronaut.http.client.exception-on-error-status': false,
                'micronaut.http.client.proxy-type'               : 'http',
                'micronaut.http.client.proxy-address'            : "${proxyHost}:${proxyPort}"
        ])

        when: 'download page via proxy'
        def response = downloadPage()

        then: 'page is downloaded'
        response.status == HttpStatus.OK
        response.body().contains(HTML_FRAGMENT)

        when: 'proxy container is stopped'
        proxyContainer.stop()

        then: 'page download fails'
        downloadFailsWithException() instanceof HttpClientException
    }

    private String getProxyHost() {
        proxyContainer.containerIpAddress
    }

    private int getProxyPort() {
        proxyContainer.getMappedPort(PROXY_PORT)
    }

    private void startServer(Map<String, Object> config) {
        embeddedServer = ApplicationContext.run(EmbeddedServer, config, Environment.TEST)
    }

    private HttpClient getClient() {
        embeddedServer.applicationContext.getBean(HttpClient)
    }

    private HttpResponse<String> downloadPage() {
        client.toBlocking().exchange(URL, String)
    }

    private Throwable downloadFailsWithException() {
        try {
            downloadPage()
            return null
        } catch (Throwable ex) {
            return ex
        }
    }
}
