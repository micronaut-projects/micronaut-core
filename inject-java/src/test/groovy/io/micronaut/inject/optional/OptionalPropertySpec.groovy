package io.micronaut.inject.optional

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class OptionalPropertySpec extends AbstractTypeElementSpec {

    void "test get bean with optionals not present"() {
        given:
        ApplicationContext context = ApplicationContext.run()
        BeanWithOptionals opts = context.getBean(BeanWithOptionals)

        expect:
        !opts.optionalDouble.isPresent()
        !opts.optionalInt.isPresent()
        !opts.optionalLong.isPresent()
        !opts.optionalDoubleFromConstructor.isPresent()
        !opts.optionalIntFromConstructor.isPresent()
        !opts.optionalLongFromConstructor.isPresent()

        cleanup:
        context.close()
    }

    void "test get bean with optionals present"() {
        given:
        ApplicationContext context = ApplicationContext.run(
                'long.prop':Long.MAX_VALUE,
                'double.prop':'10.5',
                'integer.prop':'10',
                'string.prop':'good'
        )
        BeanWithOptionals opts = context.getBean(BeanWithOptionals)

        expect:
        opts.stringOptional.isPresent()
        opts.stringOptional.get() == 'good'
        opts.optionalDouble.isPresent()
        opts.optionalInt.isPresent()
        opts.optionalLong.isPresent()
        opts.optionalInt.asInt == 10
        opts.optionalDouble.asDouble == 10.5d
        opts.optionalLong.asLong == Long.MAX_VALUE
        opts.optionalDoubleFromConstructor.isPresent()
        opts.optionalIntFromConstructor.isPresent()
        opts.optionalLongFromConstructor.isPresent()
        opts.optionalIntFromConstructor.asInt == 10
        opts.optionalDoubleFromConstructor.asDouble == 10.5d
        opts.optionalLongFromConstructor.asLong == Long.MAX_VALUE

        cleanup:
        context.close()
    }
}
