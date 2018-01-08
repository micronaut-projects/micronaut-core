package org.particleframework.management.endpoint.stop

import okhttp3.OkHttpClient
import okhttp3.Request
import org.particleframework.context.ApplicationContext
import org.particleframework.http.HttpStatus
import org.particleframework.runtime.server.EmbeddedServer
import spock.lang.Specification

class ServerStopEndpointSpec extends Specification {

    void "test the endpoint is disabled by default"() {
        given:
        EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

        OkHttpClient client = new OkHttpClient()

        when:
        def response = client.newCall(new Request.Builder().url(new URL(embeddedServer.getURL(), "/stop")).build()).execute()

        then:
        response.code() == HttpStatus.NOT_FOUND.code
    }
}
