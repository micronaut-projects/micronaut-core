package io.micronaut.aop.proxytarget

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.qualifiers.Qualifiers

/**
 * A proxy finds its target by the class of the target, which a target restricted to the types it exposes does
 * not expose.
 */
class TypedProxyTargetCreationSpec extends AbstractTypeElementSpec {

    private static final String INTERCEPTOR = """
import io.micronaut.aop.Around;
import io.micronaut.aop.InterceptorBean;
import io.micronaut.aop.MethodInterceptor;
import io.micronaut.aop.MethodInvocationContext;
import io.micronaut.context.annotation.Bean;
import jakarta.inject.Named;
import jakarta.inject.Singleton;
import java.lang.annotation.Retention;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Around(proxyTarget = true)
@Retention(RUNTIME)
@interface Shouting {
}

@Singleton
@InterceptorBean(Shouting.class)
class ShoutingInterceptor implements MethodInterceptor<Object, Object> {
    @Override
    public Object intercept(MethodInvocationContext<Object, Object> context) {
        return context.proceed().toString().toUpperCase();
    }
}

interface Greeter {
    String greet(String name);
}
"""

    void 'a proxy of a target exposed only as its interface is created'() {
        given:
        def context = buildContext('typedproxy.DefaultGreeter', """
package typedproxy;
$INTERCEPTOR
@Singleton
@Bean(typed = Greeter.class)
@Shouting
class DefaultGreeter implements Greeter {
    @Override
    public String greet(String name) {
        return "hello " + name;
    }
}
""")
        def greeterClass = context.classLoader.loadClass('typedproxy.Greeter')
        def targetClass = context.classLoader.loadClass('typedproxy.DefaultGreeter')

        expect:
        context.getBean(greeterClass).greet('world') == 'HELLO WORLD'
        context.getBeansOfType(targetClass).isEmpty()

        cleanup:
        context?.close()
    }

    void 'a proxy of a qualified target exposed only as its interface is created'() {
        given:
        def context = buildContext('typedproxy.EnglishGreeter', """
package typedproxy;
$INTERCEPTOR
@Singleton
@Named("english")
@Bean(typed = Greeter.class)
@Shouting
class EnglishGreeter implements Greeter {
    @Override
    public String greet(String name) {
        return "hello " + name;
    }
}

@Singleton
@Named("french")
@Bean(typed = Greeter.class)
@Shouting
class FrenchGreeter implements Greeter {
    @Override
    public String greet(String name) {
        return "bonjour " + name;
    }
}
""")
        def greeterClass = context.classLoader.loadClass('typedproxy.Greeter')

        expect:
        context.getBean(greeterClass, Qualifiers.byName('english')).greet('world') == 'HELLO WORLD'
        context.getBean(greeterClass, Qualifiers.byName('french')).greet('world') == 'BONJOUR WORLD'
        context.getBeansOfType(greeterClass)*.greet('world') as Set == ['HELLO WORLD', 'BONJOUR WORLD'] as Set

        cleanup:
        context?.close()
    }
}
