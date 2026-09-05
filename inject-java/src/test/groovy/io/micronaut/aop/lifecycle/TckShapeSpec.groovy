package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/** The compatibility kit's assertion shape, run against whatever lifecycle interception this branch has. */
class TckShapeSpec extends AbstractTypeElementSpec {

    void 'test the sequence the compatibility kit asserts'() {
        given:
        ApplicationContext context = buildContext('''
package tck.shape;

import io.micronaut.aop.*;
import io.micronaut.core.annotation.Order;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import jakarta.inject.Singleton;
import java.lang.annotation.*;
import java.util.*;

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
@InterceptorBinding(kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind = InterceptorKind.PRE_DESTROY)
@interface Guarded {
}

abstract class Recording implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> ctx) {
        Rocket.SEQUENCE.add(getClass().getSimpleName());
        return ctx.proceed();
    }
}

@Singleton @Order(10)
@InterceptorBinding(value = Guarded.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Guarded.class, kind = InterceptorKind.PRE_DESTROY)
class Airborne extends Recording {}

@Singleton @Order(20)
@InterceptorBinding(value = Guarded.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Guarded.class, kind = InterceptorKind.PRE_DESTROY)
class SuperDestruction extends Recording {}

@Singleton @Order(30)
@InterceptorBinding(value = Guarded.class, kind = InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(value = Guarded.class, kind = InterceptorKind.PRE_DESTROY)
class Destruction extends Recording {}

class Weapon {
    @PostConstruct void postConstructWeapon() { Rocket.SEQUENCE.add("Weapon"); }
    @PreDestroy void preDestroyWeapon() { Rocket.SEQUENCE.add("Weapon"); }
}

@Singleton
@Guarded
class Rocket extends Weapon {
    static final List<String> SEQUENCE = new ArrayList<>();
    @PostConstruct void postConstruct() { SEQUENCE.add("Rocket"); }
    @PreDestroy void preDestroy() { SEQUENCE.add("Rocket"); }
}
''')
        def sequence = context.classLoader.loadClass('tck.shape.Rocket').SEQUENCE

        when:
        def bean = context.getBean(context.classLoader.loadClass('tck.shape.Rocket'))

        then:
        sequence == ['Airborne', 'SuperDestruction', 'Destruction', 'Weapon', 'Rocket']

        when:
        sequence.clear()
        context.destroyBean(bean)

        then:
        sequence == ['Airborne', 'SuperDestruction', 'Destruction', 'Weapon', 'Rocket']

        cleanup:
        context.close()
    }
}
