package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.aop.InterceptorBinding
import io.micronaut.aop.InterceptorKind
import io.micronaut.context.ApplicationContext
import io.micronaut.context.env.Environment
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.inject.annotation.NamedAnnotationTransformer
import io.micronaut.inject.visitor.VisitorContext
import spock.lang.Unroll

import java.lang.annotation.Annotation

class AroundConstructCompileSpec extends AbstractTypeElementSpec {

    void 'test around construct with annotation mapper - plus members'() {
        given:
        ApplicationContext context = buildContext('''
package aroundconstructmapperbindingmembers;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.*;
import jakarta.inject.Singleton;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
@TestAnn2
class MyBean {
    @TestAnn(num=1)
    MyBean() {
    }
}

@Retention(RUNTIME)
@Target({ElementType.ANNOTATION_TYPE})
@interface MyInterceptorBinding {
}

@Retention(RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.TYPE})
@MyInterceptorBinding
@interface TestAnn {
    int num();
}

@Retention(RUNTIME)
@Target({ElementType.CONSTRUCTOR, ElementType.TYPE})
@MyInterceptorBinding
@interface TestAnn2 {
}

@Singleton
@TestAnn(num=1)
class TestInterceptor implements ConstructorInterceptor<Object> {
    public boolean invoked = false;
    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        invoked = true;
        return context.proceed();
    }
}

@Singleton
@TestAnn(num=2)
class TestInterceptor2 implements ConstructorInterceptor<Object> {
    public boolean invoked = false;
    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        invoked = true;
        return context.proceed();
    }
}

''')


        when:
        def interceptor = getBean(context, 'aroundconstructmapperbindingmembers.TestInterceptor')
        def interceptor2 = getBean(context, 'aroundconstructmapperbindingmembers.TestInterceptor2')

        then:
        !interceptor.invoked
        !interceptor2.invoked

        when:
        def instance = getBean(context, 'aroundconstructmapperbindingmembers.MyBean')

        then:"the interceptor was invoked"
        interceptor.invoked
        !interceptor2.invoked

    }

    void 'test around construct on type and constructor with proxy target + bind members'() {
        given:
        ApplicationContext context = buildContext("""
package ctorbinding;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@FooClassBinding
@Singleton
class Foo {

    @FooCtorBinding
    public Foo() {
    }
}


@Target({ TYPE, CONSTRUCTOR })
@Retention(RUNTIME)
@Documented
@InterceptorBinding(kind = InterceptorKind.AROUND, bindMembers = true)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT, bindMembers = true)
@interface FooCtorBinding {

}

@Target({ TYPE })
@Retention(RUNTIME)
@Documented
@InterceptorBinding(kind = InterceptorKind.AROUND, bindMembers = true)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT, bindMembers = true)
@Around(proxyTarget = true)
@interface FooClassBinding {
}

@Singleton
@FooClassBinding
class Interceptor1 implements ConstructorInterceptor<Object> {
    public boolean intercepted = false;
    @Override public Object intercept(ConstructorInvocationContext<Object> context) {
        intercepted = true;
        return context.proceed();
    }
}

@Singleton
@FooCtorBinding
class Interceptor2 implements ConstructorInterceptor<Object> {
    public boolean intercepted = false;
    @Override public Object intercept(ConstructorInvocationContext<Object> context) {
        intercepted = true;
        return context.proceed();
    }
}
""")
        when:
        def i1 = getBean(context, 'ctorbinding.Interceptor1')
        def i2 = getBean(context, 'ctorbinding.Interceptor2')

        then:
        !i1.intercepted
        !i2.intercepted

        when:
        def bean = getBean(context, 'ctorbinding.Foo')

        then:
        i1.intercepted
        i2.intercepted

        cleanup:
        context.close()
    }

    void 'test around construct on type and constructor with proxy target'() {
        given:
        ApplicationContext context = buildContext("""
package ctorbinding;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@FooClassBinding
@Singleton
class Foo {

    @FooCtorBinding
    public Foo() {
    }
}


@Target({ TYPE, CONSTRUCTOR })
@Retention(RUNTIME)
@Documented
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
@interface FooCtorBinding {

}

@Target({ TYPE })
@Retention(RUNTIME)
@Documented
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
@Around(proxyTarget = true)
@interface FooClassBinding {
}

@Singleton
@FooClassBinding
class Interceptor1 implements ConstructorInterceptor<Object> {
    public boolean intercepted = false;
    @Override public Object intercept(ConstructorInvocationContext<Object> context) {
        intercepted = true;
        return context.proceed();
    }
}

@Singleton
@FooCtorBinding
class Interceptor2 implements ConstructorInterceptor<Object> {
    public boolean intercepted = false;
    @Override public Object intercept(ConstructorInvocationContext<Object> context) {
        intercepted = true;
        return context.proceed();
    }
}
""")
        when:
        def i1 = getBean(context, 'ctorbinding.Interceptor1')
        def i2 = getBean(context, 'ctorbinding.Interceptor2')

        then:
        !i1.intercepted
        !i2.intercepted

        when:
        def bean = getBean(context, 'ctorbinding.Foo')

        then:
        i1.intercepted
        i2.intercepted

        cleanup:
        context.close()
    }

    void 'test around construct on type and constructor'() {
        given:
        ApplicationContext context = buildContext("""
package ctorbinding;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.Singleton;
import static java.lang.annotation.ElementType.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@FooClassBinding
@Singleton
class Foo {

    @FooCtorBinding
    public Foo() {
    }
}


@Target({ TYPE, CONSTRUCTOR })
@Retention(RUNTIME)
@Documented
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
@interface FooCtorBinding {

}

@Target({ TYPE })
@Retention(RUNTIME)
@Documented
@InterceptorBinding(kind = InterceptorKind.AROUND)
@InterceptorBinding(kind = InterceptorKind.AROUND_CONSTRUCT)
@interface FooClassBinding {
}

@Singleton
@FooClassBinding
class Interceptor1 implements ConstructorInterceptor<Object> {
    public boolean intercepted = false;
    @Override public Object intercept(ConstructorInvocationContext<Object> context) {
        intercepted = true;
        return context.proceed();
    }
}

@Singleton
@FooCtorBinding
class Interceptor2 implements ConstructorInterceptor<Object> {
    public boolean intercepted = false;
    @Override public Object intercept(ConstructorInvocationContext<Object> context) {
        intercepted = true;
        return context.proceed();
    }
}
""")
        when:
        def i1 = getBean(context, 'ctorbinding.Interceptor1')
        def i2 = getBean(context, 'ctorbinding.Interceptor2')

        then:
        !i1.intercepted
        !i2.intercepted

        when:
        def bean = getBean(context, 'ctorbinding.Foo')

        then:
        i1.intercepted
        i2.intercepted

        cleanup:
        context.close()
    }

    @Unroll
    void 'test around construct with around interception - proxyTarget = #proxyTarget'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.*;
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
    MyOtherBean test(io.micronaut.context.env.Environment env) {
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
    Object[] parameters;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        invoked = true;
        parameters = context.getParameterValues();
        return context.proceed();
    }
}

@Singleton
@InterceptorBean(TestAnn.class)
class TypeSpecificConstructInterceptor implements ConstructorInterceptor<MyBean> {
    boolean invoked = false;
    Object[] parameters;

    @Override
    public MyBean intercept(ConstructorInvocationContext<MyBean> context) {
        invoked = true;
        parameters = context.getParameterValues();
        MyBean mb = context.proceed();
        return mb;
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
        def typeSpecificInterceptor = getBean(context, 'annbinding1.TypeSpecificConstructInterceptor')
        def anotherInterceptor = getBean(context, 'annbinding1.AnotherInterceptor')

        then:
        !constructorInterceptor.invoked
        !interceptor.invoked
        !anotherInterceptor.invoked

        when:"A bean that features constructor injection is instantiated"
        def instance = getBean(context, 'annbinding1.MyBean')

        then:"The constructor interceptor is invoked"
        constructorInterceptor.invoked
        typeSpecificInterceptor.invoked
        constructorInterceptor.parameters.size() == 1

        and:"Other non-constructor interceptors are not invoked"
        !interceptor.invoked
        !anotherInterceptor.invoked


        when:"A method with interception is invoked"
        constructorInterceptor.invoked = false
        typeSpecificInterceptor.invoked = false
        instance.test()

        then:"the methods interceptor are invoked"
        instance instanceof Intercepted
        interceptor.invoked
        !anotherInterceptor.invoked

        and:"The constructor interceptor is not"
        !constructorInterceptor.invoked
        !typeSpecificInterceptor.invoked

        when:"A bean that is created from a factory is instantiated"
        constructorInterceptor.invoked = false
        interceptor.invoked = false
        def factoryCreatedInstance = getBean(context, 'annbinding1.MyOtherBean')

        then:"Constructor interceptors are invoked for the created instance"
        constructorInterceptor.invoked
        !typeSpecificInterceptor.invoked
        constructorInterceptor.parameters.size() == 1

        and:"Other interceptors are not"
        !interceptor.invoked
        !anotherInterceptor.invoked

        cleanup:
        context.close()

        where:
        proxyTarget << [true, false]
    }

    void 'test around construct without around interception'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.*;
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
    MyOtherBean test(io.micronaut.context.env.Environment env) {
        return new MyOtherBean();
    }
}

class MyOtherBean {}

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@AroundConstruct
@interface TestAnn {
}

@Singleton
@InterceptorBean(TestAnn.class)
class TestConstructInterceptor implements ConstructorInterceptor<Object> {
    boolean invoked = false;
    Object[] parameters;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        invoked = true;
        parameters = context.getParameterValues();
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
        !(instance instanceof Intercepted)
        constructorInterceptor.invoked
        constructorInterceptor.parameters.size() == 1

        and:"Other non-constructor interceptors are not invoked"
        !interceptor.invoked
        !anotherInterceptor.invoked


        when:"A method with interception is invoked"
        constructorInterceptor.invoked = false
        instance.test()

        then:"the methods interceptor are invoked"
        !interceptor.invoked
        !anotherInterceptor.invoked

        and:"The constructor interceptor is not"
        !constructorInterceptor.invoked

        when:"A bean that is created from a factory is instantiated"
        constructorInterceptor.invoked = false
        interceptor.invoked = false
        def factoryCreatedInstance = getBean(context, 'annbinding1.MyOtherBean')

        then:"Constructor interceptors are invoked for the created instance"
        !(factoryCreatedInstance instanceof Intercepted)
        constructorInterceptor.invoked
        constructorInterceptor.parameters.size() == 1

        and:"Other interceptors are not"
        !interceptor.invoked
        !anotherInterceptor.invoked

        cleanup:
        context.close()

    }

    void 'test around construct declared on constructor only'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
class MyBean {
    @TestAnn
    MyBean(io.micronaut.context.env.Environment env) {}

    void test() {
    }
}

@Retention(RUNTIME)
@Target({ElementType.CONSTRUCTOR})
@AroundConstruct
@Around
@interface TestAnn {
}

@Singleton
@InterceptorBean(TestAnn.class)
class TestConstructInterceptor implements ConstructorInterceptor<Object> {
    boolean invoked = false;
    Object[] parameters;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        invoked = true;
        parameters = context.getParameterValues();
        return context.proceed();
    }
}

""")
        when:
        def constructorInterceptor = getBean(context, 'annbinding1.TestConstructInterceptor')

        then:
        !constructorInterceptor.invoked

        when:"A bean that features constructor injection is instantiated"
        def instance = getBean(context, 'annbinding1.MyBean')

        then:"The constructor interceptor is invoked"
        !(instance instanceof Intercepted)
        constructorInterceptor.invoked
        constructorInterceptor.parameters.size() == 1


        cleanup:
        context.close()

    }

    void 'test around construct without around interception - interceptors from factory'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
@TestAnn
class MyBean {
    MyBean(io.micronaut.context.env.Environment env) {}
    void test() {
    }
}

@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@AroundConstruct
@interface TestAnn {
}

@Factory
class InterceptorFactory {
    boolean aroundConstructInvoked = false;

    @InterceptorBean(TestAnn.class)
    ConstructorInterceptor<Object> aroundIntercept() {
        return (context) -> {
            this.aroundConstructInvoked = true;
            return context.proceed();
        };
    }

}

""")
        when:
        def factory = getBean(context, 'annbinding1.InterceptorFactory')

        then:
        !factory.aroundConstructInvoked

        when:"A bean that features constructor injection is instantiated"
        def instance = getBean(context, 'annbinding1.MyBean')

        then:"The constructor interceptor is invoked"
        !(instance instanceof Intercepted)
        factory.aroundConstructInvoked

        cleanup:
        context.close()
    }

    void 'test around construct with introduction advice'() {
        given:
        ApplicationContext context = buildContext("""
package annbinding1;

import java.lang.annotation.*;
import io.micronaut.aop.*;
import jakarta.inject.*;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Singleton
@TestAnn
abstract class MyBean {
    MyBean(io.micronaut.context.env.Environment env) {}
    abstract String test();
}


@Retention(RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@Introduction
@AroundConstruct
@interface TestAnn {
}

@Singleton
@InterceptorBean(TestAnn.class)
class TestConstructInterceptor implements ConstructorInterceptor<Object> {
    boolean invoked = false;
    Object[] parameters;

    @Override
    public Object intercept(ConstructorInvocationContext<Object> context) {
        invoked = true;
        parameters = context.getParameterValues();
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
        return "good";
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
        instance instanceof Intercepted
        constructorInterceptor.invoked
        constructorInterceptor.parameters.size() == 1
        constructorInterceptor.parameters[0] instanceof Environment

        and:"Other non-constructor interceptors are not invoked"
        !interceptor.invoked
        !anotherInterceptor.invoked


        when:"A method with interception is invoked"
        constructorInterceptor.invoked = false
        def result = instance.test()

        then:"the methods interceptor are invoked"
        interceptor.invoked
        result == 'good'
        !anotherInterceptor.invoked

        and:"The constructor interceptor is not"
        !constructorInterceptor.invoked

        cleanup:
        context.close()

    }

    static class TestStereotypeAnnTransformer implements NamedAnnotationTransformer {

        @Override
        String getName() {
            return 'aroundconstructmapperbindingmembers.MyInterceptorBinding'
        }

        @Override
        List<AnnotationValue<?>> transform(AnnotationValue<Annotation> annotation, VisitorContext visitorContext) {
            return Collections.singletonList(AnnotationValue.builder(InterceptorBinding)
                    .member("kind", InterceptorKind.AROUND_CONSTRUCT)
                    .member("bindMembers", true)
                    .build())
        }
    }
}

