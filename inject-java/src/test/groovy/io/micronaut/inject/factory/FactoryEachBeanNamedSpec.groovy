package io.micronaut.inject.factory

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.EachBean
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.runtime.context.scope.Refreshable
import jakarta.inject.Named
import spock.lang.Specification

class FactoryEachBeanNamedSpec extends Specification {

    static final SPEC_NAME = "FactoryEachBeanNamedSpec"

    void "check the factory"() {
        given:
        BeanContext beanContext = ApplicationContext.run(["spec.name": SPEC_NAME])

        expect:
        beanContext.getBeansOfType(IntegerReturnerWrapper2).size() == 2
        beanContext.getBeansOfType(IntegerReturnerWrapper).size() == 2

        cleanup:
        beanContext.close()
    }

    static interface IntegerReturner {
        int getInteger()
    }

    @Refreshable
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class One implements IntegerReturner {
        @Override
        int getInteger() {
            1
        }
    }

    @Refreshable
    @Named("two")
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class Two implements IntegerReturner {
        @Override
        int getInteger() {
            2
        }
    }

    static class IntegerReturnerWrapper {
        final IntegerReturner inty

        IntegerReturnerWrapper(IntegerReturner inty) {
            this.inty = inty
        }
    }

    @EachBean(IntegerReturner)
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class IntegerReturnerWrapper2 {
        final IntegerReturner inty

        IntegerReturnerWrapper2(IntegerReturner inty) {
            this.inty = inty
        }
    }

    @Factory
    @Requires(property = "spec.name", value = SPEC_NAME)
    static class IntegerReturnerFactory {

        @EachBean(IntegerReturner)
        IntegerReturnerWrapper buildWithFactory(IntegerReturner inty) {
            new IntegerReturnerWrapper(inty)
        }
    }
}
