package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.writer.BeanDefinitionWriter

class LifeCycleWithProxyTargetSpec extends AbstractTypeElementSpec {

    void "test that a proxy target AOP definition lifecycle hooks are invoked - annotation at class level"() {
        when:
        ApplicationContext context = buildContext( '''
package test;

import io.micronaut.aop.proxytarget.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.convert.*;

@Mutating("someVal")
@jakarta.inject.Singleton
class MyBean {

    @jakarta.inject.Inject public ConversionService conversionService;
    public int count = 0;

    public String someMethod() {
        return "good";
    }

    @jakarta.annotation.PostConstruct
    void created() {
        count++;
    }

    @javax.annotation.PreDestroy
    void destroyed() {
        count--;
    }

}
''')
        def beanDefinition = getBeanDefinition(context, 'test.MyBean')

        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.postConstructMethods.size() == 1
        beanDefinition.preDestroyMethods.size() == 1

        when:
        def instance = getBean(context, 'test.MyBean')

        then:"proxy post construct methods are not invoked"
        instance.conversionService // injection works
        instance.someMethod() == 'good'
        instance.count == 0

        and:"proxy target post construct methods are invoked"
        instance.interceptedTarget().count == 1

        cleanup:
        context.close()
    }

    void "test that a proxy target AOP definition lifecycle hooks are invoked - annotation at method level with hooks last"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.proxytarget.*;
import io.micronaut.core.convert.*;

@jakarta.inject.Singleton
class MyBean {

    @jakarta.inject.Inject public ConversionService conversionService;

    public int count = 0;

    @Mutating("someVal")
    public String someMethod() {
        return "good";
    }

    @jakarta.annotation.PostConstruct
    void created() {
        count++;
    }

    @javax.annotation.PreDestroy
    void destroyed() {
        count--;
    }
}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.postConstructMethods.size() == 1
        beanDefinition.preDestroyMethods.size() == 1

    }

    void "test that a proxy target AOP definition lifecycle hooks are invoked - annotation at method level"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.proxytarget.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.convert.*;

@jakarta.inject.Singleton
class MyBean {

    @jakarta.inject.Inject public ConversionService conversionService;
    public int count = 0;

    @jakarta.annotation.PostConstruct
    void created() {
        count++;
    }

    @javax.annotation.PreDestroy
    void destroyed() {
        count--;
    }

    @Mutating("someVal")
    public String someMethod() {
        return "good";
    }


}
''')
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null

    }
}

