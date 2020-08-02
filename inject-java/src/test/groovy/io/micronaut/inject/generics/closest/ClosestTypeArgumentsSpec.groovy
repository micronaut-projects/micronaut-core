package io.micronaut.inject.generics.closest

import io.micronaut.context.ApplicationContext
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Specification

class ClosestTypeArgumentsSpec extends Specification {

    void "test retrieving beans by closest type arguments"() {
        ApplicationContext ctx = ApplicationContext.run(["spec.name": ClosestTypeArgumentsSpec.simpleName])

        when: "register the global in the context first"
        ExceptionHandler bean = ctx.getBean(ExceptionHandler, Qualifiers.byTypeArgumentsClosest(Throwable.class, Object.class))

        then:
        bean instanceof GlobalExceptionHandler

        when:
        bean = ctx.getBean(ExceptionHandler, Qualifiers.byTypeArgumentsClosest(CustomError.class, Object.class))

        then:
        bean instanceof CustomErrorExceptionHandler
    }

    void "test @Singleton created once"() {
        ApplicationContext ctx = ApplicationContext.run(["spec.name": ClosestTypeArgumentsSpec.simpleName])

        when:
        CustomErrorExceptionHandler bean1 = ctx.getBean(CustomErrorExceptionHandler)
        ExceptionHandler bean2 = ctx.getBean(ExceptionHandler, Qualifiers.byTypeArgumentsClosest(CustomError.class, Object.class))

        then:
        bean1 == bean2
    }
}
