package io.micronaut.http.server.netty.jackson

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpResponse
import io.micronaut.http.client.RxHttpClient
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class JsonViewServerFilterSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer,
            [
                    'jackson.jsonViewEnabled': true
            ],
            "test")

    @Shared
    @AutoCleanup
    RxHttpClient client = embeddedServer.getApplicationContext().createBean(RxHttpClient, embeddedServer.getURL())


    def "invoking /jsonview/none does not specify @JsonView, thus, all properties are returned"() {
        when:
        HttpResponse<TestModel> rsp = client.toBlocking().exchange('/jsonview/none', TestModel)

        then:
        rsp.body() == JsonViewController.TEST_MODEL
    }

    def "invoking /jsonview/public specifies 'public' @JsonView, thus, only public properties are returned"() {
        when:
        HttpResponse<TestModel> rsp = client.toBlocking().exchange('/jsonview/public', TestModel)

        then:
        rsp.body().firstName == JsonViewController.TEST_MODEL.firstName
        rsp.body().lastName == JsonViewController.TEST_MODEL.lastName
        rsp.body().birthdate == null
        rsp.body().password == null
    }

    def "invoking /jsonview/internal specifies 'internal' @JsonView, thus, only internal properties are returned"() {
        when:
        HttpResponse<TestModel> rsp = client.toBlocking().exchange('/jsonview/internal', TestModel)

        then:
        rsp.body().firstName == JsonViewController.TEST_MODEL.firstName
        rsp.body().lastName == JsonViewController.TEST_MODEL.lastName
        rsp.body().birthdate == JsonViewController.TEST_MODEL.birthdate
        rsp.body().password == null
    }

    def "invoking /jsonview/admin specifies 'admin' @JsonView, thus, admin (all) properties are returned"() {
        when:
        HttpResponse<TestModel> rsp = client.toBlocking().exchange('/jsonview/admin', TestModel)

        then:
        rsp.body() == JsonViewController.TEST_MODEL
    }
}
