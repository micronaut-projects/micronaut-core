package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Consumes
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Produces
import io.micronaut.http.client.annotation.Client
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Single
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class XmlRequestResponseSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    @Shared
    XmlClient xmlClient = embeddedServer.getApplicationContext().getBean(XmlClient)

    def 'verify client can parse xml content'() {
        when:
        Single<XmlModel> model = xmlClient.getXmlContent()
        then:
        model.blockingGet().value == 'test'
    }

    @Client('/media/xml/')
    @Consumes(MediaType.APPLICATION_XML)
    static interface XmlClient {
        @Get
        Single<XmlModel> getXmlContent();
    }

    @Controller('/media/xml/')
    @Produces(MediaType.APPLICATION_XML)
    static class XmlController {

        @Get
        Single<String> getXmlContent() {
            return Single.just('<XmlModel><value>test</value></XmlModel>')
        }
    }

    static class XmlModel {
        public String value
    }
}
