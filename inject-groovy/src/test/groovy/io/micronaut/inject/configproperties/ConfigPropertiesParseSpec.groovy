package io.micronaut.inject.configproperties

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Property
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.InstantiatableBeanDefinition
import io.micronaut.inject.configuration.Engine

class ConfigPropertiesParseSpec extends AbstractBeanDefinitionSpec {

    void "test configuration properties returns self"() {
            when:
            BeanDefinition beanDefinition = buildBeanDefinition('io.micronaut.inject.configproperties.MyConfig1', '''
package io.micronaut.inject.configproperties

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("my")
class MyConfig1 {
    private String host
    String getHost() {
        host
    }
    MyConfig1 setHost(String host) {
        this.host = host
        this
    }
}''')
            InstantiatableBeanDefinition factory = beanDefinition
            ApplicationContext applicationContext = ApplicationContext.builder(["my.host": "abc"]).start()
            def bean = factory.instantiate(applicationContext)

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
    @io.micronaut.core.annotation.Nullable
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

    @io.micronaut.core.annotation.Nullable
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
        beanDefinition.injectedMethods[1].arguments[0].isDeclaredNullable()
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
        InstantiatableBeanDefinition factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.run(
                'foo.manufacturer':'Subaru',
               // 'foo.two.manufacturer':'Subaru',
                'foo.three.manufacturer':'Subaru'
        )
        def bean = factory.instantiate(applicationContext)

        then:
        ((Engine.Builder) bean.engine).build().manufacturer == 'Subaru'
        //((Engine.Builder) bean.engine2).build().manufacturer == 'Subaru'
        ((Engine.Builder) bean.engine3).build().manufacturer == 'Subaru'
    }

    void "test name is correct with inner classes of non config props class"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.Test\$TestNestedConfig", '''
package test

import io.micronaut.context.annotation.*

class Test {

    @ConfigurationProperties("test")
    static class TestNestedConfig {

        String val
    }

}
''')

        then:
        noExceptionThrown()
        beanDefinition.injectedMethods[0].annotationMetadata.getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "test.val"
    }

    void "test inner interface EachProperty list = true"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Parent$Child$Intercepted', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty

import jakarta.inject.Inject
import java.util.List

@ConfigurationProperties("parent")
class Parent {

    final List<Child> children

    @Inject
    public Parent(List<Child> children) {
        this.children = children
    }

    @EachProperty(value = "children", list = true)
    static interface Child {
        String getPropA()
        String getPropB()
    }
}
''')

        then:
        noExceptionThrown()
        beanDefinition != null
        beanDefinition.getAnnotationMetadata().stringValue(ConfigurationReader.class, "prefix").get() == "parent.children[*]"
        beanDefinition.getRequiredMethod("getPropA").getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "parent.children[*].prop-a"
    }
}
