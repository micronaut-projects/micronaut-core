package io.micronaut.aop.compile

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.writer.BeanDefinitionWriter

class LifeCycleWithProxySpec extends AbstractBeanDefinitionSpec {
    void "test that a simple AOP definition lifecycle hooks are invoked - annotation at class level"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('lifecycleproxy1.$MyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package lifecycleproxy1;

import io.micronaut.aop.interceptors.*;
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
        then:
        !beanDefinition.isAbstract()
        beanDefinition != null
        beanDefinition.postConstructMethods.size() == 1
        beanDefinition.preDestroyMethods.size() == 1

        when:
        def context = ApplicationContext.run()
        def instance = ((BeanFactory) beanDefinition).build(context, beanDefinition)


        then:
        instance.conversionService // injection works
        instance.someMethod() == 'good'
        instance.count == 1

        cleanup:
        context.close()
    }

    void "test that a simple AOP definition lifecycle hooks are invoked - annotation at method level with hooks last"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('lifecycleproxy2.$MyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package lifecycleproxy2;

import io.micronaut.aop.interceptors.*;
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

        when:
        def context = ApplicationContext.run()
        def instance = ((BeanFactory) beanDefinition).build(context, beanDefinition)


        then:
        instance.conversionService != null
        instance.someMethod() == 'good'
        instance.count == 1

        cleanup:
        context.close()
    }

    void "test that a simple AOP definition lifecycle hooks are invoked - annotation at method level"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('lifecycleproxy3.$MyBean' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package lifecycleproxy3;

import io.micronaut.aop.interceptors.*;
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

        when:
        def context = ApplicationContext.run()
        def instance = ((BeanFactory) beanDefinition).build(context, beanDefinition)


        then:
        instance.conversionService != null
        instance.someMethod() == 'good'
        instance.count == 1

        cleanup:
        context.close()
    }
}
