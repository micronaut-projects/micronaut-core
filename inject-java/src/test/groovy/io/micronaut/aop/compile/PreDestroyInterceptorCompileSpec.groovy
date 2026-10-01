package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext

class PreDestroyInterceptorCompileSpec extends AbstractTypeElementSpec {

    void "test the bean's own @PreDestroy method is invoked when PRE_DESTROY is intercepted"() {
        given:
        ApplicationContext context = buildContext('''
package predestroybinding;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.*;
import java.util.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import javax.annotation.*;

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@InterceptorBinding(kind=InterceptorKind.AROUND)
@InterceptorBinding(kind=InterceptorKind.PRE_DESTROY)
@interface TestAnn {
}

@Singleton
@TestAnn
class TestInterceptor implements MethodInterceptor {
    List<String> calls = new ArrayList<>();
    @Override
    public Object intercept(MethodInvocationContext context) {
        calls.add("interceptor " + context.getKind());
        return context.proceed();
    }
}

@Singleton
@TestAnn
class MyBean {
    List<String> calls = new ArrayList<>();

    public String work() {
        return "work";
    }

    @PostConstruct
    void start() {
        calls.add("bean postConstruct");
    }

    @PreDestroy
    void stop() {
        calls.add("bean preDestroy");
    }
}
''')
        def interceptor = getBean(context, 'predestroybinding.TestInterceptor')

        when: "the intercepted bean is created and a method invoked"
        def instance = getBean(context, 'predestroybinding.MyBean')

        then: "the bean's @PostConstruct method still runs"
        instance instanceof Intercepted
        instance.calls == ["bean postConstruct"]

        when:
        instance.work()

        then:
        interceptor.calls == ["interceptor AROUND"]

        when: "the context is stopped"
        context.stop()

        then: "both the PRE_DESTROY interceptor and the bean's @PreDestroy method are invoked"
        interceptor.calls == ["interceptor AROUND", "interceptor PRE_DESTROY"]
        instance.calls == ["bean postConstruct", "bean preDestroy"]

        cleanup:
        context.close()
    }
}
