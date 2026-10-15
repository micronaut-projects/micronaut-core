package io.micronaut.aop.lifecycle

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

/**
 * The methods of a proxy that fronts a separate target binding different members of an annotation that does not
 * repeat: each method is intercepted by the interceptor of its own members.
 */
class ProxyTargetMemberBindingsSpec extends AbstractTypeElementSpec {

    void 'test methods binding different members each get their interceptor lazy=#lazy perTarget=#perTarget'() {
        given:
        ApplicationContext context = buildContext("""
package membersbound;

import io.micronaut.aop.*;
import io.micronaut.context.annotation.Prototype;
import jakarta.inject.Singleton;
import java.lang.annotation.*;

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@Around
@InterceptorBinding(kind = InterceptorKind.AROUND, bindMembers = true)
@interface Zone {
    String value();
}

@Prototype
@Zone("north")
class NorthInterceptor implements MethodInterceptor<Object, Object> {
    static int calls;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        calls++;
        return context.proceed();
    }
}

@Prototype
@Zone("south")
class SouthInterceptor implements MethodInterceptor<Object, Object> {
    static int calls;

    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        calls++;
        return context.proceed();
    }
}

@Singleton
@Around(proxyTarget = true, lazy = $lazy, lazyInterceptorsPerTarget = $perTarget)
class ZonedBean {
    @Zone("north")
    public String north() {
        return "north";
    }

    @Zone("south")
    public String south() {
        return "south";
    }
}
""")
        def bean = context.getBean(context.classLoader.loadClass('membersbound.ZonedBean'))

        when:
        def results = [bean.north(), bean.south()]

        then:
        results == ['north', 'south']
        context.classLoader.loadClass('membersbound.NorthInterceptor').calls == 1
        context.classLoader.loadClass('membersbound.SouthInterceptor').calls == 1

        cleanup:
        context.close()

        where:
        lazy  | perTarget
        false | false
        false | true
        true  | false
        true  | true
    }
}
