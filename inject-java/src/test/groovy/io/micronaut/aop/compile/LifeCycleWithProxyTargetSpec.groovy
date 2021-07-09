package io.micronaut.aop.compile

import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

class LifeCycleWithProxyTargetSpec extends AbstractTypeElementSpec {

    void "test that a proxy target AOP definition lifecycle hooks are invoked - annotation at class level"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyBeanDefinition$Intercepted', '''
package test;

import io.micronaut.aop.proxytarget.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.convert.*;

@Mutating("someVal")
@javax.inject.Singleton
class MyBean {

    @javax.inject.Inject public ConversionService conversionService;
    public int count = 0;
    
    public String someMethod() {
        return "good";
    }
    
    @javax.annotation.PostConstruct
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

    void "test that a proxy target AOP definition lifecycle hooks are invoked - annotation at method level with hooks last"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyBeanDefinition$Intercepted', '''
package test;

import io.micronaut.aop.proxytarget.*;
import io.micronaut.core.convert.*;

@javax.inject.Singleton
class MyBean {

    @javax.inject.Inject public ConversionService conversionService;

    public int count = 0;
    
    @Mutating("someVal")
    public String someMethod() {
        return "good";
    }

    @javax.annotation.PostConstruct
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
        BeanDefinition beanDefinition = buildBeanDefinition('test.$MyBeanDefinition$Intercepted', '''
package test;

import io.micronaut.aop.proxytarget.*;
import io.micronaut.context.annotation.*;
import io.micronaut.core.convert.*;

@javax.inject.Singleton
class MyBean {

    @javax.inject.Inject public ConversionService conversionService;
    public int count = 0;
    
    @javax.annotation.PostConstruct
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

