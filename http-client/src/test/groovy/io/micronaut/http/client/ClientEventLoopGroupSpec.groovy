package io.micronaut.http.client

import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.BeanInstantiationException
import io.micronaut.http.client.exceptions.HttpClientException
import io.micronaut.inject.qualifiers.Qualifiers
import io.netty.channel.EventLoopGroup
import spock.lang.Specification


class ClientEventLoopGroupSpec extends Specification {

    void "test use invalid event loop group for client"() {
        when:
        def context = ApplicationContext.run(
                'micronaut.netty.event-loops.other.num-threads': 0,
                'micronaut.http.client.event-loop-group': 'invalid'
        )

        context.getBean(RxHttpClient)

        then:
        def e = thrown(BeanInstantiationException)
        e.cause.message =='Specified event loop group is not defined: invalid'

        cleanup:
        context.close()
    }

    void "test use valid event loop group for client"() {
        when:
        def context = ApplicationContext.run(
                'micronaut.netty.event-loops.other.num-threads': 0,
                'micronaut.http.client.event-loop-group': 'other'
        )

        RxHttpClient client = context.getBean(RxHttpClient)

        then:
        client.group == context.getBean(EventLoopGroup, Qualifiers.byName("other"))

        cleanup:
        context.close()
    }
}
