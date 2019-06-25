package io.micronaut.management.endpoint.env

import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.client.RxHttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Shared
import spock.lang.Specification

class EnvEndpointSpec extends Specification {

    @Shared
    private EmbeddedServer embeddedServer

    void "the env endpoint is sensitive by default"() {
        given:
        this.embeddedServer = ApplicationContext.run(EmbeddedServer, Environment.TEST)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        client.exchange("/${EnvEndpoint.NAME}").blockingFirst()

        then:
        HttpClientResponseException responseException = thrown()
        responseException.status.code == 401

        cleanup:
        doCleanup(client)
    }

    void "it returns all the environment information"() {
        given:
        RxHttpClient client = buildClient()

        when:
        Map result = client.exchange("/${EnvEndpoint.NAME}", Map).blockingFirst().body()

        then:
        result.activeEnvironments == ["test"]
        result.packages.contains("io.micronaut.management.endpoint.env")
        result.propertySources.size() == 3
        result.propertySources.find {it.name == 'context'}.properties['foo.bar'] == 'baz'

        cleanup:
        doCleanup(client)
    }

    void "it returns all the properties of a property source"() {
        given:
        RxHttpClient client = buildClient()

        when:
        Map result = client.exchange("/${EnvEndpoint.NAME}/context", Map).blockingFirst().body()

        then:
        result.order == 0
        result.properties['foo.bar'] == 'baz'

        cleanup:
        doCleanup(client)
    }

    void "it returns not found if the property source doesn't exist"() {
        given:
        RxHttpClient client = buildClient()

        when:
        client.exchange("/${EnvEndpoint.NAME}/blah").blockingFirst()

        then:
        HttpClientResponseException responseException = thrown()
        responseException.status.code == 404

        cleanup:
        doCleanup(client)
    }

    private RxHttpClient buildClient() {
        this.embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.env.sensitive': false, 'foo.bar':'baz'], Environment.TEST)
        return embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())
    }

    private void doCleanup(RxHttpClient client) {
        client.close()
        embeddedServer.close()
        embeddedServer = null
    }

}
