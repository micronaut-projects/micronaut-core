package io.micronaut.http.client

import groovy.transform.EqualsAndHashCode
import io.micronaut.context.ApplicationContext
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.validation.Valid

class LargeJsonRequestSpec extends Specification {
    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    MyClient myClient = embeddedServer.getApplicationContext().getBean(MyClient)

    void "test send many large JSON requests"() {

        List threads = []
        List results  = []
        100.times {
            threads << Thread.start {
                def myType = new MyType(
                        one: "one" * 100,
                        two: Integer.MAX_VALUE,
                        three: Long.MAX_VALUE,
                        four: Double.MAX_VALUE,
                        five: Float.MAX_VALUE,
                        six: "six" * 100,
                        seven: "seven" * 50,
                        eight: "eight" * 100
                )
                def result = myClient.send(myType)
                results.add( result == myType )
            }
        }

        threads*.join()

        expect:
        threads.size() == 100
        results.every { it == true }
    }


    @Client('/')
    static interface MyClient {
        @Post(value = "/large/json",
                consumes = "application/json" )
        MyType send(@Valid @Body MyType myType)
    }


    @Controller('/')
    static class MyController {

        @Post(value = "/large/json",
                consumes = "application/json" )
        MyType receive(@Valid @Body MyType myType) {
            return myType
        }
    }

    @EqualsAndHashCode
    static class MyType {
        String one
        Integer two
        Long three
        String four
        String five
        String six
        String seven
        String eight
    }
}
