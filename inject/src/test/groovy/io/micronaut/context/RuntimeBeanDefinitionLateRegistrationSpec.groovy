package io.micronaut.context

import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.AutoCleanup
import spock.lang.Specification

class RuntimeBeanDefinitionLateRegistrationSpec extends Specification {

    @AutoCleanup
    BeanContext context = BeanContext.run()

    void 'a runtime definition registered after its interface was looked up is found through the interface'() {
        given:
        assert context.getBeanDefinitions(LateInterface).isEmpty()

        when:
        context.registerBeanDefinition(RuntimeBeanDefinition.of(new LateImpl()))

        then:
        context.containsBean(LateImpl)
        context.getBeanDefinitions(LateInterface).size() == 1
        context.getBeanRegistrations(LateInterface).size() == 1
        context.getBeansOfType(LateInterface).size() == 1
        context.getBean(LateInterface) instanceof LateImpl
        context.getBeanDefinitions(LateBase).size() == 1
    }

    void 'a runtime definition registered after its interface was looked up is found with a qualifier'() {
        given:
        assert context.findBean(LateInterface, Qualifiers.byName("late")).isEmpty()

        when:
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(new LateImpl()).named("late").build())

        then:
        context.findBean(LateInterface, Qualifiers.byName("late")).isPresent()
        context.getBeansOfType(LateInterface, Qualifiers.byName("late")).size() == 1
    }

    void 'a runtime definition registered before any interface lookup is found through the interface'() {
        when:
        context.registerBeanDefinition(RuntimeBeanDefinition.of(new LateImpl()))

        then:
        context.getBeanDefinitions(LateInterface).size() == 1
        context.getBeansOfType(LateInterface).size() == 1
    }

    void 'a runtime definition with restricted exposed types is not found through a non exposed interface'() {
        given:
        assert context.getBeanDefinitions(LateInterface).isEmpty()
        assert context.getBeanDefinitions(OtherInterface).isEmpty()

        when:
        context.registerBeanDefinition(RuntimeBeanDefinition.builder(LateImpl, () -> new LateImpl())
                .exposedTypes(OtherInterface)
                .build())

        then:
        context.getBeanDefinitions(OtherInterface).size() == 1
        context.getBeanDefinitions(LateInterface).isEmpty()
        context.getBeansOfType(LateInterface).isEmpty()
    }

    static interface LateInterface {
    }

    static interface OtherInterface {
    }

    static abstract class LateBase implements LateInterface {
    }

    static class LateImpl extends LateBase implements OtherInterface {
    }
}
