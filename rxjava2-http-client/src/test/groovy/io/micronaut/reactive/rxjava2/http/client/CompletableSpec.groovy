package io.micronaut.reactive.rxjava2.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Completable
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class CompletableSpec extends Specification {
    @AutoCleanup
    @Shared
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, ['spec.name': 'CompletableSpec'])

    @Shared
    MyGetClient myGetClient = embeddedServer.applicationContext.getBean(MyGetClient)

    void "test completable returns 200"() {
        when:
        MyGetClient client = this.myGetClient
        def returnsNull = client.completable().blockingGet()
        def ex = client.completableError().blockingGet()

        then:
        returnsNull == null
        ex instanceof HttpClientResponseException
        ex.message.contains("completable error")
    }

    //@Requires(property = 'spec.name', value = 'CompletableSpec')
    @Client("/get")
    static interface MyGetClient {
        @Get("/completable")
        Completable completable()

        @Get("/completable/error")
        Completable completableError()
    }

    //@Requires(property = 'spec.name', value = 'CompletableSpec')
    @Controller("/get")
    static class GetController {
        @Get("/completable")
        Completable completable(){
            return Completable.complete()
        }

        @Get("/completable/error")
        Completable completableError() {
            return Completable.error(new RuntimeException("completable error"))
        }
    }
}
