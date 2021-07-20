package io.micronaut.http.server.converters

import io.micronaut.context.ApplicationContext
import io.micronaut.core.convert.ConversionService
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class ReactiveTypeConverterRegistrarSpec extends Specification {

    @AutoCleanup
    @Shared
    ApplicationContext context = ApplicationContext.run()

    @Unroll
    void 'test converting reactive type #from.getClass().getSimpleName() to #target'() {
        expect:
        ConversionService.SHARED.convert(from, target).isPresent()

        where:
        from                   | target
        Object                 | Publisher

    }
}
