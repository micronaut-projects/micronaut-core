package io.micronaut.inject.factory.nullreturn

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.exceptions.BeanContextException
import io.micronaut.context.exceptions.DependencyInjectionException
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.PendingFeature

class NullReturnFactorySpec extends AbstractTypeElementSpec {

    void "test parse factory"() {
        given:
        def definition = buildBeanDefinition('test.Test2Processor', '''
package test;

import io.micronaut.context.annotation.*;

import java.util.concurrent.atomic.AtomicInteger;

@EachBean(Test2.class)
class Test2Processor {

    static AtomicInteger constructed = new AtomicInteger();

    private final Test2 d;

    Test2Processor(Test2 d) {
        this.d = d;
        constructed.incrementAndGet();
    }
}

@Factory
class TestFactory {
    @EachBean(Test1.class)
    Test2 getD(Test1 c) {
        if (c.name.equals("two")) {
            return null;
        } else {
            return new Test2();
        }
    }
}
class Test1 {
    String name;
}
class Test2 {}
''')
        expect:
        definition != null
    }

    void "test factory that returns null"() {
        given:
        BeanContext beanContext = ApplicationContext.run(["spec.name": getClass().simpleName])
        NullableFactory factory = beanContext.getBean(NullableFactory)

        when:
        beanContext.createBean(A, "hello")

        then:
        thrown(BeanContextException)

        when: "1, 2, 3 are created for B"
        Collection<B> bs = beanContext.getBeansOfType(B)

        then:
        bs.size() == 3
        bs.any { it.name == "one" }
        bs.any { it.name == "two" }
        bs.any { it.name == "three" }

        when: "1, 2 are created for C"
        Collection<C> cs = beanContext.getBeansOfType(C)

        then:
        cs.size() == 2
        cs.any { it.name == "one" }
        cs.any { it.name == "two" }

        expect: "1 is created for D"
        beanContext.getBeansOfType(D).size() == 1
        beanContext.getBean(D, Qualifiers.byName("one"))
        factory.dCalls == 2 //2 C beans

        and: "1 is created for D2"
        beanContext.getBeansOfType(D2).size() == 1 // D two is disabled
        beanContext.getBeansOfType(D3).size() == 1 // D two is disabled
        beanContext.getBeansOfType(D4).size() == 4 // Nullable C allowed
        beanContext.getBean(D2, Qualifiers.byName("one"))
        factory.d2Calls == 4 // called for every C and for C that are not enabled for existing B
        factory.d3Calls == 2 // C is not nullable and there is no C three/four
        factory.d4Calls == 4 // // called for every C and for C that are not enabled for existing B

        when: "E injects F which returns null"
        beanContext.getBean(E, Qualifiers.byName("one"))

        then: "only the each bean argument is handled for not found"
        thrown(NoSuchBeanException)

        when:
        beanContext.getBean(F)

        then:
        thrown(NoSuchBeanException)

        when:
        beanContext.getBean(FactoryConstructor)

        then:
        thrown(DependencyInjectionException)

        cleanup:
        beanContext.close()
    }

    @PendingFeature
    void "test bean factory method throwing DisableBeanException is cached"() {
        given:
        BeanContext beanContext = ApplicationContext.run(["spec.name": getClass().simpleName])
        NullableFactory factory = beanContext.getBean(NullableFactory)

        when:
        beanContext.getBeansOfType(D2)
        beanContext.getBeansOfType(D3)
        beanContext.getBeansOfType(D4)

        then:
        factory.bCalls == 4 // There are 4 B definitions (one throws disabled)
        factory.cCalls == 3 // C is foreach B - should be called 3 times only

        cleanup:
        beanContext.close()
    }

    void "test it works as expected nested resolution"() {
        given:
        BeanContext beanContext = ApplicationContext.run(["spec.name": getClass().simpleName])
        NullableFactory factory = beanContext.getBean(NullableFactory)

        expect:
        beanContext.getBeansOfType(D).size() == 1
        beanContext.getBeansOfType(C).size() == 2
        beanContext.getBeansOfType(B).size() == 3
        factory.bCalls == 6
        factory.cCalls == 4
        factory.dCalls == 2

        cleanup:
        beanContext.close()
    }

    void "test each bean on a class with null factory"() {
        given:
        DProcessor.constructed.set(0)
        ParameterDProcessor.constructed.set(0)
        NullableDProcessor.constructed.set(0)
        BeanContext beanContext = ApplicationContext.run(["spec.name": getClass().simpleName])

        expect:
        beanContext.getBeansOfType(DProcessor).size() == 1
        DProcessor.constructed.get() == 1
        beanContext.getBeansOfType(ParameterDProcessor).size() == 1
        ParameterDProcessor.constructed.get() == 1
        beanContext.getBeansOfType(NullableDProcessor).size() == 4
        NullableDProcessor.constructed.get() == 4 //3 null D beans and 1 D bean

        when:
        beanContext.getBean(DProcessor, Qualifiers.byName("one"))
        beanContext.getBean(ParameterDProcessor, Qualifiers.byName("one"))

        then:
        noExceptionThrown()

        cleanup:
        beanContext.close()
    }

}
