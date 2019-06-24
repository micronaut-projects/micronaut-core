package io.micronaut.management.endpoint.env

import groovy.json.JsonOutput
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.Specification

class EnvEndpointSpec extends Specification {

    void "it return property sources"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['endpoints.env.sensitive': false], Environment.TEST)
        RxHttpClient client = embeddedServer.applicationContext.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        Map result = client.exchange("/${EnvEndpoint.NAME}", Map).blockingFirst().body()
        println JsonOutput.prettyPrint(JsonOutput.toJson(result))

        then:
        result.activeEnvironments == ["test"]
        result.packages.contains("io.micronaut.management.endpoint.env")
        result.propertySources.size() == 3
        result.propertySources.find {it.name == 'context'}.properties['endpoints.env.sensitive'] == false

        cleanup:
        client.close()
        embeddedServer.close()
    }
}
