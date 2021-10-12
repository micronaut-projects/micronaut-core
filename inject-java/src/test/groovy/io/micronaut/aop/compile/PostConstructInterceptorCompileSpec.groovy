package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import spock.lang.Unroll

class PostConstructInterceptorCompileSpec extends AbstractTypeElementSpec {
    @Unroll
    void 'test post construct with around interception - proxyTarget = #proxyTarget'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import javax.annotation.*;

@Singleton
@TestAnn
class MyBean {
    @Inject io.micronaut.context.env.Environment env;
    int invoked;

    MyBean(io.micronaut.context.env.Environment env) {}
    void test() {
    }

    @PostConstruct
    void init() {
        System.out.println("INVOKED POST CONSTRUCT");
        this.invoked++;
    }
}

@io.micronaut.context.annotation.Factory
class MyFactory {
    @TestAnn
    @Singleton
    MyOtherBean test(io.micronaut.context.env.Environment env) {
        return new MyOtherBean();
    }
}

class MyOtherBean {}

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Around(proxyTarget=$proxyTarget)
@InterceptorBinding(kind=InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind=InterceptorKind.PRE_DESTROY)
@interface TestAnn {
}


@Singleton
@InterceptorBean(TestAnn.class)
class TestInterceptor implements MethodInterceptor {
    int invoked;
    @Override
    public Object intercept(MethodInvocationContext context) {
        invoked++;
        return context.proceed();
    }
}

@Singleton
@InterceptorBinding(value=TestAnn.class, kind=InterceptorKind.POST_CONSTRUCT)
class PostConstructTestInterceptor implements MethodInterceptor {
    int invoked;
    @Override
    public Object intercept(MethodInvocationContext context) {
        invoked++;
        return context.proceed();
    }
}

@Singleton
@InterceptorBinding(value=TestAnn.class, kind=InterceptorKind.PRE_DESTROY)
class PreDestroyTestInterceptor implements MethodInterceptor {
    int invoked;
    @Override
    public Object intercept(MethodInvocationContext context) {
        invoked++;
        return context.proceed();
    }
}    

@Singleton
class AnotherInterceptor implements Interceptor {
    int invoked;
    @Override
    public Object intercept(InvocationContext context) {
        invoked++;
        return context.proceed();
    }
} 
""")
        when:
        def interceptor = getBean(context, 'annbinding1.TestInterceptor')
        def constructorInterceptor = getBean(context, 'annbinding1.PostConstructTestInterceptor')
        def destroyInterceptor = getBean(context, 'annbinding1.PreDestroyTestInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding1.AnotherInterceptor')

        then:
        !interceptor.invoked
        !anotherInterceptor.invoked
        !constructorInterceptor.invoked

        when:"A bean that featuring post construct injection is instantiated"
        def instance = getBean(context, 'annbinding1.MyBean')

        then:"The interceptors that apply to post construction are invoked"
        (proxyTarget ? instance.interceptedTarget() : instance).invoked == 1
        interceptor.invoked == 1
        constructorInterceptor.invoked == 1
        anotherInterceptor.invoked == 0
        destroyInterceptor.invoked == 0


        when:"A method with interception is invoked"
        instance.test()

        then:"the methods interceptor are invoked"
        instance instanceof Intercepted
        interceptor.invoked == 2
        constructorInterceptor.invoked == 1
        anotherInterceptor.invoked == 0


        when:"A bean that is created from a factory is instantiated"
        def factoryCreatedInstance = getBean(context, 'annbinding1.MyOtherBean')

        then:"post construct interceptors are invoked for the created instance"
        interceptor.invoked == 3
        constructorInterceptor.invoked == 2
        anotherInterceptor.invoked == 0

        when:
        context.stop()

        then:
        // TODO: Discuss why we are invoking destroy hooks for proxies
        interceptor.invoked == proxyTarget ? 6 : 5
        constructorInterceptor.invoked == 2
        anotherInterceptor.invoked == 0
        // TODO: Discuss why we are invoking destroy hooks for proxies
        destroyInterceptor.invoked == proxyTarget ? 3 : 2


        where:
        proxyTarget << [true, false]
    }


    void 'test post construct & pre destroy without around interception'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;
import javax.annotation.*;

@Singleton
@TestAnn
class MyBean {
    @Inject io.micronaut.context.env.Environment env;
    int invoked;

    MyBean(io.micronaut.context.env.Environment env) {}
    void test() {
    }

    @PostConstruct
    void init() {
        this.invoked++;
    }
}

@io.micronaut.context.annotation.Factory
class MyFactory {
    @TestAnn
    @Singleton
    MyOtherBean test(io.micronaut.context.env.Environment env) {
        return new MyOtherBean();
    }
}

class MyOtherBean {}

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@InterceptorBinding(kind=InterceptorKind.POST_CONSTRUCT)
@InterceptorBinding(kind=InterceptorKind.PRE_DESTROY)
@interface TestAnn {
}


@Singleton
@InterceptorBean(TestAnn.class)
class TestInterceptor implements MethodInterceptor {
    int invoked;
    @Override
    public Object intercept(MethodInvocationContext context) {
        invoked++;
        return context.proceed();
    }
}

@Singleton
@InterceptorBinding(value=TestAnn.class, kind=InterceptorKind.POST_CONSTRUCT)
class PostConstructTestInterceptor implements MethodInterceptor {
    int invoked;
    @Override
    public Object intercept(MethodInvocationContext context) {
        invoked++;
        return context.proceed();
    }
}

@Singleton
@InterceptorBinding(value=TestAnn.class, kind=InterceptorKind.PRE_DESTROY)
class PreDestroyTestInterceptor implements MethodInterceptor {
    int invoked;
    @Override
    public Object intercept(MethodInvocationContext context) {
        invoked++;
        return context.proceed();
    }
}    

@Singleton
class AnotherInterceptor implements Interceptor {
    int invoked;
    @Override
    public Object intercept(InvocationContext context) {
        invoked++;
        return context.proceed();
    }
} 
""")
        when:
        def interceptor = getBean(context, 'annbinding1.TestInterceptor')
        def constructorInterceptor = getBean(context, 'annbinding1.PostConstructTestInterceptor')
        def destroyInterceptor = getBean(context, 'annbinding1.PreDestroyTestInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding1.AnotherInterceptor')

        then:
        !interceptor.invoked
        !anotherInterceptor.invoked
        !constructorInterceptor.invoked

        when:"A bean that featuring post construct injection is instantiated"
        def instance = getBean(context, 'annbinding1.MyBean')

        then:"The interceptors that apply to post construction are invoked"
        interceptor.invoked == 1
        instance.invoked == 1
        constructorInterceptor.invoked == 1
        anotherInterceptor.invoked == 0
        destroyInterceptor.invoked == 0


        when:"A method with interception is invoked"
        instance.test()

        then:"the methods interceptor are invoked"
        interceptor.invoked == 1
        constructorInterceptor.invoked == 1
        anotherInterceptor.invoked == 0


        when:"A bean that is created from a factory is instantiated"
        def factoryCreatedInstance = getBean(context, 'annbinding1.MyOtherBean')

        then:"post construct interceptors are invoked for the created instance"
        interceptor.invoked == 2
        constructorInterceptor.invoked == 2
        anotherInterceptor.invoked == 0

        when:
        context.stop()

        then:
        interceptor.invoked == 4
        constructorInterceptor.invoked == 2
        anotherInterceptor.invoked == 0
        destroyInterceptor.invoked == 2

    }
}
