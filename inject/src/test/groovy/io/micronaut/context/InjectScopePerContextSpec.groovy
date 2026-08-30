package io.micronaut.context

import io.micronaut.context.annotation.InjectScope
import io.micronaut.context.scope.BeanCreationContext
import io.micronaut.context.scope.CreatedBean
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanIdentifier
import spock.lang.Specification

class InjectScopePerContextSpec extends Specification {

    void "each bean context gets its own inject scope instance"() {
        given:
        ApplicationContext ctx1 = ApplicationContext.run()
        ApplicationContext ctx2 = ApplicationContext.run()

        when:
        def scope1 = findInjectScope(ctx1)
        def scope2 = findInjectScope(ctx2)

        then:
        !scope1.is(scope2)

        when: 'a bean is created in the inject scope of the first context'
        boolean closed = false
        CreatedBean<Object> createdBean = new CreatedBean<Object>() {
            @Override
            BeanDefinition<Object> definition() { null }

            @Override
            Object bean() { new Object() }

            @Override
            BeanIdentifier id() { BeanIdentifier.of("test") }

            @Override
            void close() { closed = true }
        }
        scope1.getOrCreate(new BeanCreationContext<Object>() {
            @Override
            BeanDefinition<Object> definition() { null }

            @Override
            BeanIdentifier id() { BeanIdentifier.of("test") }

            @Override
            CreatedBean<Object> create() { createdBean }
        })

        and: 'the second context finishes a resolution of its own'
        ((LifeCycle) scope2).stop()

        then: 'the first context still holds its bean'
        !closed

        when:
        ((LifeCycle) scope1).stop()

        then:
        closed

        cleanup:
        ctx1.close()
        ctx2.close()
    }

    private static findInjectScope(ApplicationContext ctx) {
        ((DefaultBeanContext) ctx).getCustomScopeRegistry().findScope(InjectScope.name).get()
    }
}
