package io.micronaut.http.client.jdk

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.runtime.server.EmbeddedServer
import org.testcontainers.DockerClientFactory
import org.testcontainers.containers.GenericContainer
import org.testcontainers.containers.wait.strategy.HostPortWaitStrategy
import org.testcontainers.utility.MountableFile
import spock.lang.AutoCleanup
import spock.lang.Requires
import spock.lang.Specification
import spock.util.environment.RestoreSystemProperties

@Requires({ DockerClientFactory.instance().isDockerAvailable() })
class ClientProxySpec extends Specification {

    static final int PROXY_PORT = 3128
    static final String URL = "https://micronaut.io/"
    static final String HTML_FRAGMENT = 'Home - Micronaut Framework'

    @AutoCleanup
    GenericContainer proxyContainer =
            new GenericContainer('sameersbn/squid:latest')
                    .withCopyFileToContainer(MountableFile.forClasspathResource('/squid.conf'), '/etc/squid/squid.conf')
                    .withExposedPorts(PROXY_PORT)
                    .withLogConsumer { outputFrame -> print("SQUID::\t${outputFrame.getUtf8String()}") }
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

    @RestoreSystemProperties
    void "test downloading via http proxy using default proxy-selector"() {
        given:
        System.setProperty('https.proxyHost', proxyHost)
        System.setProperty('https.proxyPort', proxyPort.toString())
        startServer([
                'micronaut.http.client.exception-on-error-status': false,
                'micronaut.http.client.proxy-selector'           : 'default'
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
        proxyContainer.host
    }

    private int getProxyPort() {
        def port = proxyContainer.getMappedPort(PROXY_PORT)
        println("Proxy port: ${port}")
        port
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
