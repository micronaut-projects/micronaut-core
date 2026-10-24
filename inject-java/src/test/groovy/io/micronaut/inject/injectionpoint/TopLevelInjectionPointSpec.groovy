package io.micronaut.inject.injectionpoint

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec

/**
 * A bean obtained by a top level lookup was not injected anywhere, so a nullable
 * {@link io.micronaut.inject.InjectionPoint} it asks for is null. The root segment the context pushes
 * for the creation of the bean itself is not an injection point.
 */
class TopLevelInjectionPointSpec extends AbstractTypeElementSpec {

    void "test a nullable injection point of a constructor is null for a top level lookup"() {
        given:
        def context = buildContext('''
package probe;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.InjectionPoint;

@Prototype
class Thing {
    final InjectionPoint<?> ip;
    Thing(@Nullable InjectionPoint<?> ip) { this.ip = ip; }
}
''')

        expect:
        getBean(context, 'probe.Thing').ip == null

        cleanup:
        context.close()
    }

    void "test a nullable injection point of a field is null for a top level lookup"() {
        given:
        def context = buildContext('''
package probe;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.InjectionPoint;
import jakarta.inject.Inject;

@Prototype
class Thing {
    @Inject @Nullable InjectionPoint<?> ip;
}
''')

        expect:
        getBean(context, 'probe.Thing').ip == null

        cleanup:
        context.close()
    }

    void "test a nullable injection point of a factory method is null for a top level lookup"() {
        given:
        def context = buildContext('''
package probe;

import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.InjectionPoint;

class Thing {
    final InjectionPoint<?> ip;
    Thing(InjectionPoint<?> ip) { this.ip = ip; }
}

@Factory
class ThingFactory {
    @Prototype
    Thing thing(@Nullable InjectionPoint<?> ip) { return new Thing(ip); }
}
''')

        expect:
        getBean(context, 'probe.Thing').ip == null

        cleanup:
        context.close()
    }

    void "test a nullable injection point still describes the injection when the bean is injected"() {
        given:
        def context = buildContext('''
package probe;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.inject.InjectionPoint;
import jakarta.inject.Singleton;

@Prototype
class Thing {
    final InjectionPoint<?> ip;
    Thing(@Nullable InjectionPoint<?> ip) { this.ip = ip; }
}

@Singleton
class Consumer {
    final Thing thing;
    Consumer(Thing thing) { this.thing = thing; }
}
''')

        when:
        def ip = getBean(context, 'probe.Consumer').thing.ip

        then:
        ip != null
        ip.declaringBean.beanType.name == 'probe.Consumer'

        cleanup:
        context.close()
    }

    void "test a required injection point still describes the injection when the bean is injected"() {
        given:
        def context = buildContext('''
package probe;

import io.micronaut.context.annotation.Prototype;
import io.micronaut.inject.InjectionPoint;
import jakarta.inject.Singleton;

@Prototype
class Thing {
    final InjectionPoint<?> ip;
    Thing(InjectionPoint<?> ip) { this.ip = ip; }
}

@Singleton
class Consumer {
    final Thing thing;
    Consumer(Thing thing) { this.thing = thing; }
}
''')

        expect:
        getBean(context, 'probe.Consumer').thing.ip.declaringBean.beanType.name == 'probe.Consumer'

        cleanup:
        context.close()
    }
}
