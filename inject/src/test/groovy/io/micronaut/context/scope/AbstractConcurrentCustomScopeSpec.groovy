package io.micronaut.context.scope

import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanIdentifier
import jakarta.inject.Singleton
import spock.lang.Specification

import java.util.concurrent.ConcurrentHashMap

class AbstractConcurrentCustomScopeSpec extends Specification {

    void "remove evicts the bean so the next resolution creates a new instance"() {
        given:
        def scope = new TestScope()
        def id = BeanIdentifier.of("myBean")
        def creationContext = new TestCreationContext(id)

        when:
        def first = scope.getOrCreate(creationContext)

        then:
        scope.scopeMap.size() == 1

        when:
        def removed = scope.remove(id)

        then:
        removed.present
        removed.get().is(first)
        creationContext.created[0].closed
        scope.scopeMap.isEmpty()

        when:
        def second = scope.getOrCreate(creationContext)

        then:
        !second.is(first)
        creationContext.created.size() == 2
        !creationContext.created[1].closed
    }

    void "remove of an unknown identifier is empty and leaves the scope untouched"() {
        given:
        def scope = new TestScope()
        def creationContext = new TestCreationContext(BeanIdentifier.of("myBean"))
        def bean = scope.getOrCreate(creationContext)

        when:
        def removed = scope.remove(BeanIdentifier.of("otherBean"))

        then:
        !removed.present
        scope.scopeMap.size() == 1
        scope.getOrCreate(creationContext).is(bean)
    }

    static class TestScope extends AbstractConcurrentCustomScope<Singleton> {

        final Map<BeanIdentifier, CreatedBean<?>> scopeMap = new ConcurrentHashMap<>()

        TestScope() {
            super(Singleton)
        }

        @Override
        boolean isRunning() {
            return true
        }

        @Override
        protected Map<BeanIdentifier, CreatedBean<?>> getScopeMap(boolean forCreation) {
            return scopeMap
        }

        @Override
        void close() {
            destroyScope(scopeMap)
        }
    }

    static class TestCreationContext implements BeanCreationContext<Object> {

        final BeanIdentifier id
        final List<TestCreatedBean> created = []

        TestCreationContext(BeanIdentifier id) {
            this.id = id
        }

        @Override
        BeanDefinition<Object> definition() {
            return null
        }

        @Override
        BeanIdentifier id() {
            return id
        }

        @Override
        CreatedBean<Object> create() {
            def createdBean = new TestCreatedBean(id: id, bean: new Object())
            created << createdBean
            return createdBean
        }
    }

    static class TestCreatedBean implements CreatedBean<Object> {

        BeanIdentifier id
        Object bean
        boolean closed

        @Override
        BeanDefinition<Object> definition() {
            return null
        }

        @Override
        Object bean() {
            return bean
        }

        @Override
        BeanIdentifier id() {
            return id
        }

        @Override
        void close() {
            closed = true
        }
    }
}
