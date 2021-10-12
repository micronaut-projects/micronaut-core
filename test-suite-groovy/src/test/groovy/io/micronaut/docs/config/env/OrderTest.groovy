package io.micronaut.docs.config.env

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

import java.util.stream.Collectors


class OrderTest extends Specification {

    void "test order on factories"() {
        ApplicationContext applicationContext = ApplicationContext.run()
        List<RateLimit> rateLimits = applicationContext.streamOfType(RateLimit)
                .collect(Collectors.toList())

        expect:
        rateLimits.size() == 2
        rateLimits[0].limit == 1000
        rateLimits[1].limit == 100

        cleanup:
        applicationContext.close()
    }
}
