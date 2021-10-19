package io.micronaut.docs.factories.primitive;

import io.micronaut.context.BeanContext
import spock.lang.AutoCleanup
import spock.lang.Shared;
import spock.lang.Specification;


class EngineSpec extends Specification{
    @Shared @AutoCleanup BeanContext beanContext = BeanContext.run()
    void "test primitive factories"() {
        given:
        final V8Engine engine = beanContext.getBean(V8Engine.class)

        expect:
        engine.cylinders == 8
    }
}
