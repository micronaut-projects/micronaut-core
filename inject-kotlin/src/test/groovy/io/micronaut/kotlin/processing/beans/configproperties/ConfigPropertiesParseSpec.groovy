package io.micronaut.kotlin.processing.beans.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.BeanContext
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Property
import io.micronaut.core.convert.format.ReadableBytes
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.kotlin.processing.beans.configuration.Engine
import spock.lang.Specification

import static io.micronaut.kotlin.processing.KotlinCompiler.*


class ConfigPropertiesParseSpec extends Specification {

    void "test inner class paths - pojo inheritance"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("foo.bar")
class MyConfig {

    var host: String? = null
    
    @ConfigurationProperties("baz")
    class ChildConfig: ParentConfig() {
        internal var stuff: String? = null
    }
}

open class ParentConfig {
    var foo: String? = null
}
''')
        then:
        beanDefinition != null
        beanDefinition.synthesize(ConfigurationReader).prefix() == 'foo.bar.baz'
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff$main'

        beanDefinition.injectedMethods[1].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[1].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.foo'
        beanDefinition.injectedMethods[1].name == 'setFoo'
    }

    void "test inner class paths - two levels"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig$MoreConfig', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("foo.bar")
class MyConfig {
    var host: String? = null
    
    @ConfigurationProperties("baz")
    class ChildConfig {
        var stuff: String? = null
    
        @ConfigurationProperties("more")
        class MoreConfig {
            var stuff: String? = null
        }
    }
}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.more.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }

    void "test inner class paths - with parent inheritance"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("foo.bar")
class MyConfig: ParentConfig() {
    var host: String? = null

    @ConfigurationProperties("baz")
    class ChildConfig {
        var stuff: String? = null
    }
}

@ConfigurationProperties("parent")
open class ParentConfig
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'parent.foo.bar.baz.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }

    void "test annotation on setters arguments"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.HttpClientConfiguration', '''
package test

import io.micronaut.core.convert.format.ReadableBytes
import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("http.client")
class HttpClientConfiguration {

    @ReadableBytes
    var maxContentLength: Int = 1024 * 1024 * 10

}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].arguments[0].synthesize(ReadableBytes)
    }

    void "test different inject types for config properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("foo")
open class MyProperties {
    open var fieldTest: String = "unconfigured"
    private val privateFinal = true
    open val protectedFinal = true
    private val anotherField = false
    private var internalField = "unconfigured"
    
    fun setSetterTest(s: String) {
        this.internalField = s
    }

    fun getSetter() = internalField
}
''')
        then:
        beanDefinition != null
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].name == 'setFieldTest'
        beanDefinition.injectedMethods[1].name == 'setSetterTest'

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.builder().start()
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.setter == "unconfigured"
        bean.@fieldTest == "unconfigured"

        when:
        applicationContext.environment.addPropertySource(
                "test",
                ['foo.setterTest' :'foo',
                'foo.fieldTest' :'bar']
        )
        bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.setter == "foo"
        bean.@fieldTest == "bar"
    }

    void "test configuration properties inheritance from non-configuration properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("foo")
class MyProperties: Parent() {
  
    open var fieldTest: String = "unconfigured"
    private val privateFinal = true
    open val protectedFinal = true
    private val anotherField = false
    private var internalField = "unconfigured"
    
    fun setSetterTest(s: String) {
        this.internalField = s
    }

    fun getSetter() = internalField
}

open class Parent {
    var parentTest: String?= null
}
''')
        then:
        beanDefinition.injectedMethods.size() == 3
        beanDefinition.injectedMethods[0].name == 'setFieldTest'
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.field-test'

        beanDefinition.injectedMethods[1].name == 'setParentTest'
        beanDefinition.injectedMethods[1].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[1].getAnnotationMetadata().synthesize(Property).name() == 'foo.parent-test'

        beanDefinition.injectedMethods[2].name == 'setSetterTest'
        beanDefinition.injectedMethods[2].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[2].getAnnotationMetadata().synthesize(Property).name() == 'foo.setter-test'

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.builder().start()
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.setter == "unconfigured"
        bean.@fieldTest == "unconfigured"
        bean.parentTest == null

        when:
        applicationContext.environment.addPropertySource(
                "test",
                ['foo.setterTest' :'foo',
                'foo.fieldTest' :'bar',
                'foo.parentTest': 'baz']
        )
        bean = factory.build(applicationContext, beanDefinition)

        then:
        bean != null
        bean.setter == "foo"
        bean.@fieldTest == "bar"
        bean.parentTest == "baz"
    }

    void "test includes on properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(value = "foo", includes = ["publicField", "parentPublicField"])
class MyProperties: Parent() {
    var publicField: String? = null
    var anotherPublicField: String? = null
}

open class Parent {
    var parentPublicField: String? = null
    var anotherParentPublicField: String? = null
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].name == "setPublicField"
        beanDefinition.injectedMethods[1].name == "setParentPublicField"
    }

    void "test excludes on properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties(value = "foo", excludes = ["anotherPublicField", "anotherParentPublicField"])
class MyProperties: Parent() {
    var publicField: String? = null
    var anotherPublicField: String? = null
}

open class Parent {
    var parentPublicField: String? = null
    var anotherParentPublicField: String? = null
}
''')
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].name == "setPublicField"
        beanDefinition.injectedMethods[1].name == "setParentPublicField"
    }


    void "test excludes on configuration builder"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test

import io.micronaut.context.annotation.*
import io.micronaut.kotlin.processing.beans.configuration.Engine

@ConfigurationProperties(value = "foo", excludes = ["engine", "engine2"])
class MyProperties {

    @ConfigurationBuilder(prefixes = ["with"])
    var engine = Engine.builder()

    @ConfigurationBuilder(configurationPrefix = "two", prefixes = ["with"])
    var engine2 = Engine.builder()
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
                'foo.two.manufacturer':'Subaru'
        )
        def bean = factory.build(applicationContext, beanDefinition)

        then:
        ((Engine.Builder) bean.engine).build().manufacturer == 'Subaru'
        ((Engine.Builder) bean.getEngine2()).build().manufacturer == 'Subaru'
    }

    void "test name is correct with inner classes of non config props class"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("test.Test\$TestNestedConfig", '''
package test

import io.micronaut.context.annotation.ConfigurationProperties

class Test {

    @ConfigurationProperties("test")
    class TestNestedConfig {
        var x: String? = null
    }

}
''')

        then:
        noExceptionThrown()
        beanDefinition.injectedMethods[0].annotationMetadata.getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "test.x"
    }

    void "test property names with numbers"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AwsConfig', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("aws")
class AwsConfig {

    var disableEc2Metadata: String? = null
    var disableEcMetadata: String? = null
    var disableEc2instanceMetadata: String? = null
}
''')

        then:
        noExceptionThrown()
        beanDefinition.injectedMethods[0].getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "aws.disable-ec2-metadata"
        beanDefinition.injectedMethods[1].getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "aws.disable-ec-metadata"
        beanDefinition.injectedMethods[2].getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "aws.disable-ec2instance-metadata"
    }

    void "test inner class EachProperty list = true"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Parent$Child', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty

import jakarta.inject.Inject

@ConfigurationProperties("parent")
class Parent(val children: List<Child>) {

    @EachProperty(value = "children", list = true)
    class Child {
        var propA: String? = null
        var propB: String? = null
    }
}
''')

        then:
        noExceptionThrown()
        beanDefinition != null
        beanDefinition.getAnnotationMetadata().stringValue(ConfigurationReader.class, "prefix").get() == "parent.children[*]"
        beanDefinition.injectedMethods[0].getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "parent.children[*].prop-a"
    }

    void "test config props with post construct first in file"() {
        given:
        BeanContext context = buildContext("""
package test

import io.micronaut.context.annotation.ConfigurationProperties
import jakarta.annotation.PostConstruct

@ConfigurationProperties("app.entity")
class EntityProperties {

    @PostConstruct
    fun init() {
        println("prop = \$prop")
    }
    
    var prop: String? = null
}
""")

        when:
        context.getBean(context.classLoader.loadClass("test.EntityProperties"))

        then:
        noExceptionThrown()
    }
}
