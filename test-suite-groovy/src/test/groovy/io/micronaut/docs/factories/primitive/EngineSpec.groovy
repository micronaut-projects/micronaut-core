package io.micronaut.docs.factories.primitive

import io.micronaut.context.ApplicationContext;
import spock.lang.AutoCleanup
import spock.lang.Shared;
import spock.lang.Specification;


class EngineSpec extends Specification{
    @Shared @AutoCleanup ApplicationContext beanContext = ApplicationContext.run()
    void "test primitive factories"() {
        given:
        final V8Engine engine = beanContext.getBean(V8Engine.class)

        expect:
        engine.cylinders == 8
    }
}
