package org.particleframework.management.endpoint.beans

import groovy.json.JsonSlurper
import okhttp3.OkHttpClient
import okhttp3.Request
import org.particleframework.context.ApplicationContext
import org.particleframework.http.HttpStatus
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.Specification

class BeansEndpointSpec extends Specification {

    /**
     * Known failure of the scope. Relies on changes to the annotation
     * metadata to return the correct result.
     */
    void "test beans endpoint"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)
        OkHttpClient client = new OkHttpClient()

        when:
        def response = client.newCall(new Request.Builder().url(new URL(embeddedServer.getURL(), "/beans")).build()).execute()
        Map result = new JsonSlurper().parseText(response.body().string())
        Map<String, Map<String, Object>> beans = result.beans

        then:
        response.code() == HttpStatus.OK.code
        beans["org.particleframework.management.endpoint.beans.\$BeansEndpointDefinition"].dependencies[0] == "org.particleframework.management.endpoint.beans.BeanAggregator"
        beans["org.particleframework.management.endpoint.beans.\$BeansEndpointDefinition"].dependencies[1] == "org.particleframework.context.BeanContext"
        beans["org.particleframework.management.endpoint.beans.\$BeansEndpointDefinition"].scope == "singleton"
        beans["org.particleframework.management.endpoint.beans.\$BeansEndpointDefinition"].type == "org.particleframework.management.endpoint.beans.BeansEndpoint"

        cleanup:
        embeddedServer.close()
    }
}
