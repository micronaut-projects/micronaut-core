package io.micronaut.inject.convert

import io.micronaut.context.ApplicationContext
import spock.lang.Specification

class ConverterAsLambdaSpec extends Specification {

    void "test converter registered via lambda"() {
        given:
        ApplicationContext ctx = ApplicationContext.run()

        expect:
        ctx.conversionService.convert("foo", TestConverters.Foo).isPresent()

        cleanup:
        ctx.close()
    }
}
