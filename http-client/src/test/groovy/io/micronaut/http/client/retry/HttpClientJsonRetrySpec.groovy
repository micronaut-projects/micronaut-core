package io.micronaut.http.client.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.retry.annotation.Retryable
import io.micronaut.runtime.server.EmbeddedServer
import io.reactivex.Single
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

class HttpClientJsonRetrySpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run()

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = context.getBean(EmbeddedServer).start()

    void "test simple blocking retry"() {
        given:
        CountClient countClient = context.getBean(CountClient)
        CountController controller = context.getBean(CountController)

        when:"A method is annotated retry"
        Count result = countClient.getCount()

        then:"It executes until successful"
        result.number == 3

        when:"The threshold can never be met"
        controller.countThreshold = 10
        controller.count = 0
        countClient.getCount()

        then:"The original exception is thrown"
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad count"
    }

    void "test simply retry with rxjava"() {
        given:
        CountClient countClient = context.getBean(CountClient)
        CountController controller = context.getBean(CountController)
        controller.countThreshold = 3
        controller.count = 0

        when:"A method is annotated retry"
        Count result = countClient.getCountSingle().blockingGet()

        then:"It executes until successful"
        result.number

        when:"The threshold can never be met"
        controller.countThreshold = 10
        controller.count = 0
        def single = countClient.getCountSingle()
        single.blockingGet()

        then:"The original exception is thrown"
        def e = thrown(HttpClientResponseException)
        e.message == "Internal Server Error: Bad count"

    }


    @Client("/json-retry-test")
    @Retryable(attempts = '5', delay = '5ms')
    static interface CountClient extends CountService {

    }

    @Controller("/json-retry-test")
    static class CountController implements CountService {
        int count = 0
        int countRx = 0
        int countThreshold = 3

        @Override
        Count getCount() {
            count++
            if(count < countThreshold) {
                throw new IllegalStateException("Bad count")
            }
            return new Count(number: count)
        }

        @Override
        Single<Count> getCountSingle() {
            Single.fromCallable({->
                countRx++
                if(countRx < countThreshold) {
                    throw new IllegalStateException("Bad count")
                }
                return new Count(number:  countRx)
            })

        }
    }

    static class Count {
        int number
    }

    static interface CountService {

        @Get('/count')
        Count getCount()

        @Get('/rx-count')
        Single<Count> getCountSingle()
    }
}
