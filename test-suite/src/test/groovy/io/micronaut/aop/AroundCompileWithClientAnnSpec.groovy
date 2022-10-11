package io.micronaut.aop

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext

class AroundCompileWithClientAnnSpec extends AbstractTypeElementSpec {
    void 'test executable and around'() {
        given:
        ApplicationContext context = buildContext('''
package execandaround;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import io.micronaut.context.annotation.Property;
import io.micronaut.http.client.annotation.Client;

@Singleton
class MyBean {
    @TestAnn(String.class)
    void test(@Property(name="foo") @Client("foo") String foo) {
        
    }
    
}

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@io.micronaut.context.annotation.Executable
@Around
@interface TestAnn {
    Class<?> value();
}

@InterceptorBean(TestAnn.class)
class TestInterceptor implements Interceptor {
    boolean invoked = false;
    @Override
    public Object intercept(InvocationContext context) {
        invoked = true;
        return context.proceed();
    }
} 

''')
        def instance = getBean(context, 'execandaround.MyBean')
        def interceptor = getBean(context, 'execandaround.TestInterceptor')

        when:
        instance.test("dummy")

        then:"the interceptor was invoked"
        instance instanceof Intercepted
        interceptor.invoked

    }
}
