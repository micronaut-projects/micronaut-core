package io.micronaut.inject.beanbuilder

import io.micronaut.aop.Intercepted
import io.micronaut.ast.groovy.TypeElementVisitorStart
import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.visitor.AllElementsVisitor

class BeanElementBuilderSpec extends AbstractBeanDefinitionSpec {
    def setup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, TestBeanDefiningVisitor.name)
    }

    def cleanup() {
        System.setProperty(TypeElementVisitorStart.ELEMENT_VISITORS_PROPERTY, "")
        AllElementsVisitor.clearVisited()
    }

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
''', false)
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
    }
}
