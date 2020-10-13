package io.micronaut.aop.introduction.with_around


import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.BeanIntrospection
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification
import spock.lang.Unroll

class IntroductionWithAroundOnConcreteClassSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run()

    @Unroll
    void "test introduction with around for #clazz"(Class clazz) {
        when:
            def beanDefinition = applicationContext.findProxyTargetBeanDefinition(clazz, null).get()
            def bean = applicationContext.getBean(beanDefinition)

        then:
            bean instanceof CustomProxy
            ((CustomProxy) bean).isProxy()
            bean.getId() == 1
            bean.getName() == null

        when:
            bean = applicationContext.getProxyTargetBean(clazz, null)

        then:
            bean instanceof CustomProxy
            ((CustomProxy) bean).isProxy()
            bean.getId() == 1
            bean.getName() == null

        where:
            clazz << [MyBean1, MyBean2, MyBean3, MyBean4, MyBean5, MyBean6]
    }

    void "test introspected preset"(Class clazz) {
        when:
            def introspection = BeanIntrospection.getIntrospection(clazz)
        then:
            introspection

        where:
            clazz << [MyBean4, MyBean5, MyBean6]
    }
}
