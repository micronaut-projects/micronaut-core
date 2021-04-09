package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import spock.lang.Unroll

class AroundConstructCompileSpec extends AbstractTypeElementSpec {

    @Unroll
    void 'test around construct interception - proxyTarget = #proxyTarget'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import javax.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
@TestAnn
class MyBean {
    MyBean(io.micronaut.context.env.Environment env) {}
    void test() {
    }
}

@io.micronaut.context.annotation.Factory
class MyFactory {
    @TestAnn
    @Singleton
    MyOtherBean test() {
        return new MyOtherBean();
    }
}

class MyOtherBean {}

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Around(proxyTarget=$proxyTarget)
@AroundConstruct
@interface TestAnn {
}

@Singleton
@InterceptorBean(TestAnn.class)
class TestConstructInterceptor implements ConstructorInterceptor<Object> {
    boolean invoked = false;
    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        invoked = true;
        return context.proceed();
    }
} 

@Singleton
@InterceptorBinding(TestAnn.class)
class TestInterceptor implements MethodInterceptor {
    boolean invoked = false;
    @Override
    public Object intercept(MethodInvocationContext context) {
        invoked = true;
        return context.proceed();
    }
} 

@Singleton
class AnotherInterceptor implements Interceptor {
    boolean invoked = false;
    @Override
    public Object intercept(InvocationContext context) {
        invoked = true;
        return context.proceed();
    }
} 
""")
        when:
        def interceptor = getBean(context, 'annbinding1.TestInterceptor')
        def constructorInterceptor = getBean(context, 'annbinding1.TestConstructInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding1.AnotherInterceptor')

        then:
        !constructorInterceptor.invoked
        !interceptor.invoked
        !anotherInterceptor.invoked

        when:"A bean that features constructor injection is instantiated"
        def instance = getBean(context, 'annbinding1.MyBean')

        then:"The constructor interceptor is invoked"
        constructorInterceptor.invoked

        and:"Other non-constructor interceptors are not invoked"
        !interceptor.invoked
        !anotherInterceptor.invoked


        when:"A method with interception is invoked"
        constructorInterceptor.invoked = false
        instance.test()

        then:"the methods interceptor are invoked"
        instance instanceof Intercepted
        interceptor.invoked
        !anotherInterceptor.invoked

        and:"The constructor interceptor is not"
        !constructorInterceptor.invoked

        when:"A bean that is created from a factory is instantiated"
        constructorInterceptor.invoked = false
        interceptor.invoked = false
        def factoryCreatedInstance = getBean(context, 'annbinding1.MyOtherBean')

        then:"Constructor interceptors are invoked for the created instance"
        constructorInterceptor.invoked

        and:"Other interceptors are not"
        !interceptor.invoked
        !anotherInterceptor.invoked

        cleanup:
        context.close()

        where:
        proxyTarget << [true, false]
    }



}
