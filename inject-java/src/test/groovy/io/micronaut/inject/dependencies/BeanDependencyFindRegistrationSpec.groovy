package io.micronaut.inject.dependencies

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

class BeanDependencyFindRegistrationSpec extends AbstractTypeElementSpec {
    private static final String HEADER = '''
package findreg;
import io.micronaut.context.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.annotation.Nullable;
import jakarta.inject.*;
import jakarta.annotation.*;
import java.util.*;
import java.util.concurrent.*;
class Log { static final List<String> events = new CopyOnWriteArrayList<>(); }
@Prototype class Resource {
    @PreDestroy void stop() { Log.events.add("resource"); }
    @Override public boolean equals(Object o) { return o instanceof Resource; }
    @Override public int hashCode() { return 1; }
}
@Singleton class Shared { }
class Product { }
@Factory class Products {
    @Prototype @Nullable Product product() { return null; }
}
@Singleton class Owner {
    final BeanDependencyResolver resolver;
    final Resource first;
    Owner(BeanDependencyResolver resolver) {
        this.resolver = resolver;
        first = resolver.getBean(Resource.class);
    }
}
'''

    void "a group finds the registration of a dependent it created and destroy releases it once"() {
        given:
        def ctx = buildContext(HEADER)
        def log = ctx.classLoader.loadClass('findreg.Log')
        def group = ctx.createDependencyGroup()
        def registration = group.getBeanRegistration(ctx.classLoader.loadClass('findreg.Resource'))
        def found = group.findRegistration(registration.bean())

        expect:
        found.isPresent()
        found.get().is(registration)
        group.owns(registration.bean())

        when:
        def destroyed = group.destroy(found.get())

        then:
        destroyed
        log.events == ['resource']
        !group.findRegistration(registration.bean()).isPresent()
        !group.owns(registration.bean())
        !group.destroy(registration)
        log.events == ['resource']

        cleanup:
        ctx.close()
    }

    void "the resolver injected into a bean finds what it created for that bean"() {
        given:
        def ctx = buildContext(HEADER)
        def owner = ctx.getBean(ctx.classLoader.loadClass('findreg.Owner'))
        def found = owner.resolver.findRegistration(owner.first)

        expect:
        found.isPresent()
        found.get().bean().is(owner.first)
        owner.resolver.owns(owner.first)

        cleanup:
        ctx.close()
    }

    void "beans the group did not create are not found"() {
        given:
        def ctx = buildContext(HEADER)
        def resourceType = ctx.classLoader.loadClass('findreg.Resource')
        def group = ctx.createDependencyGroup()
        def sibling = ctx.createDependencyGroup()
        def child = group.createGroup()
        def own = group.getBean(resourceType)
        def siblings = sibling.getBean(resourceType)
        def childs = child.getBean(resourceType)
        def shared = group.getBean(ctx.classLoader.loadClass('findreg.Shared'))
        def unrelated = ctx.getBean(resourceType)

        expect:
        group.owns(own)
        !group.findRegistration(siblings).isPresent()
        !group.findRegistration(childs).isPresent()
        !group.findRegistration(shared).isPresent()
        !group.findRegistration(unrelated).isPresent()
        !group.owns(new Object())
        sibling.owns(siblings)
        !sibling.owns(own)
        child.owns(childs)
        !child.owns(own)

        cleanup:
        ctx.close()
    }

    void "equal but distinct beans are told apart by identity"() {
        given:
        def ctx = buildContext(HEADER)
        def resourceType = ctx.classLoader.loadClass('findreg.Resource')
        def group = ctx.createDependencyGroup()
        def first = group.getBeanRegistration(resourceType)
        def second = group.getBeanRegistration(resourceType)
        def outsider = ctx.getBean(resourceType)

        expect:
        first.bean() == second.bean()
        !first.bean().is(second.bean())
        group.findRegistration(first.bean()).get().is(first)
        group.findRegistration(second.bean()).get().is(second)
        outsider == first.bean()
        !group.owns(outsider)

        when:
        group.destroy(first)

        then:
        !group.owns(first.bean())
        group.findRegistration(second.bean()).get().is(second)

        cleanup:
        ctx.close()
    }

    void "nothing is found once the group is closed"() {
        given:
        def ctx = buildContext(HEADER)
        def group = ctx.createDependencyGroup()
        def bean = group.getBean(ctx.classLoader.loadClass('findreg.Resource'))

        when:
        group.close()

        then:
        !group.findRegistration(bean).isPresent()
        !group.owns(bean)

        cleanup:
        ctx.close()
    }

    void "a dependent destroyed outside the group is no longer found - #description"() {
        given:
        def ctx = buildContext(HEADER)
        def log = ctx.classLoader.loadClass('findreg.Log')
        def group = ctx.createDependencyGroup()
        def registration = group.getBeanRegistration(ctx.classLoader.loadClass('findreg.Resource'))
        def other = group.getBeanRegistration(ctx.classLoader.loadClass('findreg.Resource'))

        when:
        destroy(ctx, registration)

        then:
        log.events == ['resource']
        !group.findRegistration(registration.bean()).isPresent()
        !group.owns(registration.bean())
        group.findRegistration(other.bean()).get().is(other)

        when:
        group.close()

        then:
        log.events == ['resource', 'resource']

        cleanup:
        ctx.close()

        where:
        description                  | destroy
        'closing the registration'   | { c, reg -> reg.close() }
        'destroying in the context'  | { c, reg -> c.destroyBean(reg) }
    }

    void "a null bean registration is not found by null"() {
        given:
        def ctx = buildContext(HEADER)
        def group = ctx.createDependencyGroup()
        def registration = group.createBeanRegistration(ctx.getBeanDefinition(ctx.classLoader.loadClass('findreg.Product')))

        expect:
        registration.bean() == null
        !group.findRegistration(null).isPresent()
        !group.owns(null)
        group.destroy(registration)

        cleanup:
        ctx.close()
    }
}
