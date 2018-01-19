package org.particleframework.http.client

import io.reactivex.Flowable
import org.particleframework.context.ApplicationContext
import org.particleframework.core.bind.BeanPropertyBinder
import org.particleframework.http.HttpRequest
import org.particleframework.http.annotation.Body
import org.particleframework.http.annotation.Controller
import org.particleframework.http.annotation.Delete
import org.particleframework.http.annotation.Get
import org.particleframework.http.annotation.Patch
import org.particleframework.http.annotation.Post
import org.particleframework.http.client.rxjava2.RxHttpClient
import org.particleframework.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import org.reactivestreams.Subscriber
import org.reactivestreams.Subscription
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.util.concurrent.PollingConditions

import javax.inject.Singleton
import java.util.concurrent.atomic.AtomicLong

/**
 * Created by graemerocher on 19/01/2018.
 */
class JsonStreamSpec  extends Specification {

    @Shared @AutoCleanup ApplicationContext context = ApplicationContext.run()
    @Shared EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test read JSON stream demand all"() {
        given:
        RxHttpClient client = context.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        List<Map> jsonObjects = client.jsonStream(HttpRequest.GET(
                '/jsonstream/books'
        )).toList().blockingGet()

        then:
        jsonObjects.size() == 2
        jsonObjects[0].title == 'The Stand'
        jsonObjects[1].title == 'The Shining'

    }

    void "test read JSON stream demand all POJO"() {
        given:
        RxHttpClient client = context.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        List<Book> jsonObjects = client.jsonStream(HttpRequest.GET(
                '/jsonstream/books'
        ), Book).toList().blockingGet()

        then:
        jsonObjects.size() == 2
        jsonObjects.every() { it instanceof Book}
        jsonObjects[0].title == 'The Stand'
        jsonObjects[1].title == 'The Shining'
    }

    void "test read JSON stream demand one"() {
        given:
        RxHttpClient client = context.createBean(RxHttpClient, embeddedServer.getURL())

        when:
        def stream = client.jsonStream(HttpRequest.GET(
                '/jsonstream/books'
        ))
        Map json

        stream.subscribe(new Subscriber<Map<String, Object>>() {
            @Override
            void onSubscribe(Subscription s) {
                s.request(1)
            }

            @Override
            void onNext(Map<String, Object> stringObjectMap) {
                json = stringObjectMap
            }

            @Override
            void onError(Throwable t) {

            }

            @Override
            void onComplete() {

            }
        })

        PollingConditions conditions = new PollingConditions()
        then:
        conditions.eventually {
            json != null
            json.title == "The Stand"
        }

    }
    @Controller("/jsonstream/books")
    @Singleton
    static class BookController {

        @Get('/')
        Publisher<Book> list() {
            return Flowable.just(new Book(title: "The Stand"), new Book(title: "The Shining"))
        }
    }


    static class Book {
        String title
    }
}

