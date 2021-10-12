package io.micronaut.inject.beanbuilder

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.aop.Intercepted
import io.micronaut.context.ApplicationContext
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.core.annotation.NonNull
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.annotation.AnnotationTransformer
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.visitor.TypeElementVisitor

import java.lang.annotation.Annotation
import java.util.function.Supplier

class BeanElementBuilderSpec extends AbstractTypeElementSpec {

    void "test define additional bean from type element visitor"() {
        given:
        def context = buildContext('''
package addbean;

import io.micronaut.inject.beanbuilder.*;

@SomeInterceptor
@Monitored
class Test {
    public boolean invoked = false;
    @AroundInvoke
    Object invoke(CustomInvocationContext context) {
        invoked = true;
        return context.proceed();
    }    
}

@jakarta.inject.Singleton
class SomeBean {
    @Monitored
    void test() {}
}
''')
        when:
        def someBean = getBean(context, 'addbean.SomeBean')

        then:
        someBean instanceof Intercepted

        when:
        def interceptorForBean = someBean.@$interceptors[0][0]


        then:
        interceptorForBean
        interceptorForBean.accessibleEnv
        interceptorForBean.valFromMethod
        interceptorForBean.fromMethod
        !interceptorForBean.registration.bean.invoked

        when:
        someBean.test()

        then:
        interceptorForBean.registration.bean.invoked

        when:"A bean is retrieved that uses a custom createWith from a static method"
        def bean = context.getBean(BeanWithStaticCreator)

        then:"The bean was created"
        bean

        when:"A non exposed type is used"
        context.getBean(TestBeanWithStaticCreator)

        then:
        thrown(NoSuchBeanException)

        when:
        def definition = context.getBeanDefinition(TestInterceptorAdapter)

        then:
        definition
        !definition.getTypeArguments(Supplier).isEmpty()
    }

    void "test define additional bean from type element visitor on type"() {
        given:
        def context = buildContext('''
package addbean;

import io.micronaut.inject.beanbuilder.*;

@SomeInterceptor
@Monitored
class Test {
    public boolean invoked = false;
    @AroundInvoke
    Object invoke(CustomInvocationContext context) {
        invoked = true;
        return context.proceed();
    }    
}

@jakarta.inject.Singleton
@Monitored
class SomeBean {
    void test() {}
}
''')
        when:
        def someBean = getBean(context, 'addbean.SomeBean')

        then:
        someBean instanceof Intercepted

        when:
        def interceptorForBean = someBean.@$interceptors[0][0]


        then:
        interceptorForBean
        interceptorForBean.accessibleEnv
        interceptorForBean.valFromMethod
        interceptorForBean.fromMethod
        !interceptorForBean.registration.bean.invoked

        when:
        someBean.test()

        then:
        interceptorForBean.registration.bean.invoked

        when:"A bean is retrieved that uses a custom createWith from a static method"
        def bean = context.getBean(BeanWithStaticCreator)

        then:"The bean was created"
        bean

        when:"A non exposed type is used"
        context.getBean(TestBeanWithStaticCreator)

        then:
        thrown(NoSuchBeanException)

        when:
        def definition = context.getBeanDefinition(TestInterceptorAdapter)

        then:
        definition
        !definition.getTypeArguments(Supplier).isEmpty()
    }

    void "test define additional bean from type element visitor on type - binding via remapper"() {
        given:
        def context = buildContext('''
package addbean;

import io.micronaut.inject.beanbuilder.*;

@SomeInterceptor
@MonitoredToo
class Test {
    public boolean invoked = false;
    @AroundInvoke
    Object invoke(CustomInvocationContext context) {
        invoked = true;
        return context.proceed();
    }    
}

@jakarta.inject.Singleton
@MonitoredToo
class SomeBean {
    void test() {}
}
''')
        when:
        def someBean = getBean(context, 'addbean.SomeBean')

        then:
        someBean instanceof Intercepted

        when:
        def interceptorForBean = someBean.@$interceptors[0][0]


        then:
        interceptorForBean
        interceptorForBean.accessibleEnv
        interceptorForBean.valFromMethod
        interceptorForBean.fromMethod
        !interceptorForBean.registration.bean.invoked

        when:
        someBean.test()

        then:
        interceptorForBean.registration.bean.invoked

        when:"A bean is retrieved that uses a custom createWith from a static method"
        def bean = context.getBean(BeanWithStaticCreator)

        then:"The bean was created"
        bean

        when:"A non exposed type is used"
        context.getBean(TestBeanWithStaticCreator)

        then:
        thrown(NoSuchBeanException)

        when:
        def definition = context.getBeanDefinition(TestInterceptorAdapter)

        then:
        definition
        !definition.getTypeArguments(Supplier).isEmpty()
    }

    void "test define additional bean from type element visitor on type - binding via remapper on method"() {
        given:
        def context = buildContext('''
package addbean;

import io.micronaut.inject.beanbuilder.*;

@SomeInterceptor
@MonitoredToo
class Test {
    public boolean invoked = false;
    @AroundInvoke
    Object invoke(CustomInvocationContext context) {
        invoked = true;
        return context.proceed();
    }    
}

@jakarta.inject.Singleton
class SomeBean {
    @MonitoredToo
    void test() {}
}
''')
        when:
        def someBean = getBean(context, 'addbean.SomeBean')

        then:
        someBean instanceof Intercepted

        when:
        def interceptorForBean = someBean.@$interceptors[0][0]


        then:
        interceptorForBean
        interceptorForBean.accessibleEnv
        interceptorForBean.valFromMethod
        interceptorForBean.fromMethod
        !interceptorForBean.registration.bean.invoked

        when:
        someBean.test()

        then:
        interceptorForBean.registration.bean.invoked

        when:"A bean is retrieved that uses a custom createWith from a static method"
        def bean = context.getBean(BeanWithStaticCreator)

        then:"The bean was created"
        bean

        when:"A non exposed type is used"
        context.getBean(TestBeanWithStaticCreator)

        then:
        thrown(NoSuchBeanException)

        when:
        def definition = context.getBeanDefinition(TestInterceptorAdapter)

        then:
        definition
        !definition.getTypeArguments(Supplier).isEmpty()
    }

    @Override
    protected Collection<TypeElementVisitor> getLocalTypeElementVisitors() {
        return [new TestBeanDefiningVisitor()]
    }

}
