package io.micronaut.aop.introduction.with_around


import io.micronaut.context.ApplicationContext
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.inject.ExecutableMethod
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
            def proxyTargetBeanDefinition = applicationContext.getProxyTargetBeanDefinition(clazz, null)
            def beanDefinition = applicationContext.getBeanDefinition(clazz, null)
            def bean = applicationContext.getBean(proxyTargetBeanDefinition)

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

        and:
            beanDefinition.getExecutableMethods().size() == 5
            proxyTargetBeanDefinition.getExecutableMethods().size() == 5

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

    void "test executable methods count for introduction with executable"() {
        when:
            def clazz = MyBean7.class
            def proxyTargetBeanDefinition = applicationContext.getProxyTargetBeanDefinition(clazz, null)
            def beanDefinition = applicationContext.getBeanDefinition(clazz, null)
        then:
            proxyTargetBeanDefinition.getExecutableMethods().size() == 5
            beanDefinition.getExecutableMethods().size() == 5
    }

    void "test executable methods count for around with executable"() {
        when:
            def clazz = MyBean8.class
            def proxyTargetBeanDefinition = applicationContext.getProxyTargetBeanDefinition(clazz, null)
            def beanDefinition = applicationContext.getBeanDefinition(clazz, null)
        then:
            proxyTargetBeanDefinition.getExecutableMethods().size() == 4
            beanDefinition.getExecutableMethods().size() == 4
    }

    void "test a multidimensional array property"() {
        when:
        def clazz = MyBean9.class
        def proxyTargetBeanDefinition = applicationContext.getProxyTargetBeanDefinition(clazz, null)
        def beanDefinition = applicationContext.getBeanDefinition(clazz, null)

        then:
        proxyTargetBeanDefinition.getExecutableMethods().size() == 5
        beanDefinition.getExecutableMethods().size() == 5
        beanDefinition.findMethod("getMultidim").get().getReturnType().asArgument().getType() == String[][].class
        beanDefinition.findMethod("setMultidim", String[][].class).isPresent()
        beanDefinition.findMethod("getPrimitiveMultidim").get().getReturnType().asArgument().getType() == int[][].class
        beanDefinition.findMethod("setPrimitiveMultidim", int[][].class).isPresent()

        when:
        MyBean9 bean = applicationContext.getBean(proxyTargetBeanDefinition)
        ExecutableMethod getMultiDim = beanDefinition.findMethod('getMultidim').get()
        ExecutableMethod setMultiDim = beanDefinition.findMethod('setMultidim', String[][].class).get()
        ExecutableMethod getPrimitiveMultidim = beanDefinition.findMethod('getPrimitiveMultidim').get()
        ExecutableMethod setPrimitiveMultidim = beanDefinition.findMethod('setPrimitiveMultidim', int[][].class).get()

        then:
        getMultiDim.invoke(bean) == null
        getPrimitiveMultidim.invoke(bean) == null

        when:
        setMultiDim.invoke(bean, new Object[] { new String[][] { new String[] { "test" }, new String[] { "abc" } } })
        setPrimitiveMultidim.invoke(bean, new Object[] { new int[][] { new int[] { 1 }, new int[] { 2 }} })

        then:
        bean.getMultidim()[0][0] == "test"
        bean.getPrimitiveMultidim()[0][0] == 1
        getMultiDim.invoke(bean)[0][0] == "test"
        getPrimitiveMultidim.invoke(bean)[0][0] == 1
    }
}
