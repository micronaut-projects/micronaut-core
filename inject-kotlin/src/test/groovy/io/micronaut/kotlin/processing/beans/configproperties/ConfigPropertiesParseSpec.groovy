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
/*
    void "test boolean fields starting with is[A-Z] map to set methods"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition("micronaut.issuer.FooConfigurationProperties", """
package micronaut.issuer;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("foo")
public class FooConfigurationProperties {

    private String issuer;
    private boolean isEnabled;
    protected Boolean isOther;


    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    //isEnabled field maps to setEnabled method
    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }

    //isOther field does not map to setOther method because its the class and not primitive
    public void setOther(Boolean other) {
        this.isOther = other;
    }
}

""")
        then:
        noExceptionThrown()
        beanDefinition.injectedMethods[0].name == "setIssuer"
        beanDefinition.injectedMethods[1].name == "setEnabled"
        beanDefinition.injectedFields[0].name == "isOther"
    }

    void "test configuration properties returns self"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("my")
class MyConfig {
    String host;
    public String getHost() {
        return host;
    }
    public MyConfig setHost(String host) {
        this.host = host;
        return this;
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
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties(value = "foo", includes = {"publicField", "parentPublicField"})
class MyProperties extends Parent {
    public String publicField;
    public String anotherPublicField;
}

class Parent {
    public String parentPublicField;
    public String anotherParentPublicField;
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
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties(value = "foo", includes = {"publicMethod", "parentPublicMethod"})
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

    void "test excludes on fields"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties(value = "foo", excludes = {"anotherPublicField", "anotherParentPublicField"})
class MyProperties extends Parent {
    public String publicField;
    public String anotherPublicField;
}

class Parent {
    public String parentPublicField;
    public String anotherParentPublicField;
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
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties(value = "foo", excludes = {"anotherPublicMethod", "anotherParentPublicMethod"})
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

    void "test excludes on configuration builder"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;
import io.micronaut.inject.configuration.Engine;

@ConfigurationProperties(value = "foo", excludes = {"engine", "engine2"})
class MyProperties extends Parent {

    @ConfigurationBuilder(prefixes = "with")
    Engine.Builder engine = Engine.builder();

    private Engine.Builder engine2 = Engine.builder();

    @ConfigurationBuilder(configurationPrefix = "two", prefixes = "with")
    public void setEngine2(Engine.Builder engine3) {
        this.engine2 = engine3;
    }

    public Engine.Builder getEngine2() {
        return engine2;
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
package test;

import io.micronaut.context.annotation.*;

class Test {

    @ConfigurationProperties("test")
    static class TestNestedConfig {
        private String val;

        public String getVal() {
            return val;
        }

        public void setVal(String val) {
            this.val = val;
        }
    }

}
''')

        then:
        noExceptionThrown()
        beanDefinition.injectedMethods[0].annotationMetadata.getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "test.val"
    }

    void "test property names with numbers"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.AwsConfig', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("aws")
class AwsConfig {

    private String disableEc2Metadata;
    private String disableEcMetadata;
    private String disableEc2instanceMetadata;

    public String getDisableEc2Metadata() {
        return disableEc2Metadata;
    }

    public void setDisableEc2Metadata(String disableEc2Metadata) {
        this.disableEc2Metadata = disableEc2Metadata;
    }

    public String getDisableEcMetadata() {
        return disableEcMetadata;
    }

    public void setDisableEcMetadata(String disableEcMetadata) {
        this.disableEcMetadata = disableEcMetadata;
    }

    public String getDisableEc2instanceMetadata() {
        return disableEc2instanceMetadata;
    }

    public void setDisableEc2instanceMetadata(String disableEc2instanceMetadata) {
        this.disableEc2instanceMetadata = disableEc2instanceMetadata;
    }
}
''')

        then:
        noExceptionThrown()
        beanDefinition.injectedMethods[0].getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "aws.disable-ec2-metadata"
        beanDefinition.injectedMethods[1].getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "aws.disable-ec-metadata"
        beanDefinition.injectedMethods[2].getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "aws.disable-ec2instance-metadata"
    }

    void "test inner interface EachProperty list = true"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.Parent$Child$Intercepted', '''
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.EachProperty;

import jakarta.inject.Inject;
import java.util.List;

@ConfigurationProperties("parent")
class Parent {

    private final List<Child> children;

    @Inject
    public Parent(List<Child> children) {
        this.children = children;
    }

    public List<Child> getChildren() {
        return children;
    }

    @EachProperty(value = "children", list = true)
    interface Child {
        String getPropA();
        String getPropB();
    }
}
''')

        then:
        noExceptionThrown()
        beanDefinition != null
        beanDefinition.getAnnotationMetadata().stringValue(ConfigurationReader.class, "prefix").get() == "parent.children[*]"
        beanDefinition.getRequiredMethod("getPropA").getAnnotationMetadata().getAnnotationValuesByType(Property.class).get(0).stringValue("name").get() == "parent.children[*].prop-a"
    }

    void "test config props with post construct first in file"() {
        given:
        BeanContext context = buildContext("test.EntityProperties", """
package test;

import io.micronaut.context.annotation.ConfigurationProperties;
import jakarta.annotation.PostConstruct;

@ConfigurationProperties("app.entity")
public class EntityProperties {

    private String prop;

    @PostConstruct
    public void init() {
        System.out.println("prop = " + prop);
    }

    public String getProp() {
        return prop;
    }

    public void setProp(String prop) {
        this.prop = prop;
    }
}
""")

        when:
        context.getBean(context.classLoader.loadClass("test.EntityProperties"))

        then:
        noExceptionThrown()
    }*/
}
