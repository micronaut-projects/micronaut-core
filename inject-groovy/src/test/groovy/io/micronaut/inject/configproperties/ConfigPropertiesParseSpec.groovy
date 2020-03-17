package io.micronaut.inject.configproperties

import io.micronaut.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.configuration.Engine

class ConfigPropertiesParseSpec extends AbstractBeanDefinitionSpec {

    void "test configuration properties returns self"() {
            when:
            BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("my")
class MyConfig {
    private String host
    String getHost() {
        host
    }
    MyConfig setHost(String host) {
        this.host = host
        this
    }
}''')
            BeanFactory factory = beanDefinition
            ApplicationContext applicationContext = ApplicationContext.builder(["my.host": "abc"]).start()
            def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean.getHost() == "abc"
    }

    void "test includes on fields"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties(value = "foo", includes = ["publicField", "parentPublicField"])
class MyProperties extends Parent {
    public String publicField
    public String anotherPublicField
}

class Parent {
    public String parentPublicField
    public String anotherParentPublicField
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedFields.size() == 2
        beanDefinition.injectedFields[0].name == "parentPublicField"
        beanDefinition.injectedFields[1].name == "publicField"
    }

    void "test includes on methods"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties(value = "foo", includes = ["publicMethod", "parentPublicMethod"])
class MyProperties extends Parent {

    public void setPublicMethod(String value) {}
    public void setAnotherPublicMethod(String value) {}
}

class Parent {
    public void setParentPublicMethod(String value) {}
    public void setAnotherParentPublicMethod(String value) {}
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].name == "setParentPublicMethod"
        beanDefinition.injectedMethods[1].name == "setPublicMethod"
    }

    void "test includes on properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties(value = "foo", includes = ["property", "parentProperty"])
class MyProperties extends Parent {
    String property
    String anotherProperty
}

class Parent {
    String parentProperty
    String anotherParentProperty
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].name == "setParentProperty"
        beanDefinition.injectedMethods[1].name == "setProperty"
    }

    void "test excludes on fields"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties(value = "foo", excludes = ["anotherPublicField", "anotherParentPublicField"])
class MyProperties extends Parent {
    public String publicField
    public String anotherPublicField
}

class Parent {
    public String parentPublicField
    public String anotherParentPublicField
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedFields.size() == 2
        beanDefinition.injectedFields[0].name == "parentPublicField"
        beanDefinition.injectedFields[1].name == "publicField"
    }

    void "test excludes on methods"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties(value = "foo", excludes = ["anotherPublicMethod", "anotherParentPublicMethod"])
class MyProperties extends Parent {

    public void setPublicMethod(String value) {}
    public void setAnotherPublicMethod(String value) {}
}

class Parent {
    public void setParentPublicMethod(String value) {}
    public void setAnotherParentPublicMethod(String value) {}
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].name == "setParentPublicMethod"
        beanDefinition.injectedMethods[1].name == "setPublicMethod"
    }

    void "test excludes on properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*

@ConfigurationProperties(value = "foo", excludes = ["anotherProperty", "anotherParentProperty"])
class MyProperties extends Parent {

    String property
    String anotherProperty
}

class Parent {
    String parentProperty
    String anotherParentProperty
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].name == "setParentProperty"
        beanDefinition.injectedMethods[1].name == "setProperty"
    }

    void "test excludes on configuration builder"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*
import io.micronaut.inject.configuration.Engine

@ConfigurationProperties(value = "foo", excludes = ["engine", "engine2", "engine3"])
class MyProperties extends Parent {

    @ConfigurationBuilder(prefixes = "with") 
    Engine.Builder engine = Engine.builder()
    
    /*
    @ConfigurationBuilder(configurationPrefix = "two", prefixes = "with")
    @groovy.transform.PackageScope 
    Engine.Builder engine2 = Engine.builder()
    
    Engine.Builder getEngine2() {
        engine2
    }
    */
    
    private Engine.Builder engine3 = Engine.builder()
    
    @ConfigurationBuilder(configurationPrefix = "three", prefixes = "with") 
    void setEngine3(Engine.Builder engine3) {
        this.engine3 = engine3;
    }   
    
    Engine.Builder getEngine3() {
        engine3
    }
}

class Parent {
    void setEngine(Engine.Builder engine) {}
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.isEmpty()
        beanDefinition.injectedFields.isEmpty()

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'foo.manufacturer':'Subaru',
               // 'foo.two.manufacturer':'Subaru',
                'foo.three.manufacturer':'Subaru'
        )
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        ((Engine.Builder) bean.engine).build().manufacturer == 'Subaru'
        //((Engine.Builder) bean.engine2).build().manufacturer == 'Subaru'
        ((Engine.Builder) bean.engine3).build().manufacturer == 'Subaru'
    }

}
