package io.micronaut.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Property
import io.micronaut.core.convert.format.ReadableBytes
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory
import io.micronaut.inject.configuration.Engine

class ConfigPropertiesParseSpec extends AbstractTypeElementSpec {

    void "test inner class paths - pojo inheritance"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig {
    String host;


    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
    
    @ConfigurationProperties("baz")
    static class ChildConfig extends ParentConfig {
        protected String stuff;
    }
}

class ParentConfig {
    private String foo;
    
    public void setFoo(String foo) {
        this.foo = foo;
    }
}
''')
        then:
        beanDefinition.synthesize(ConfigurationReader).prefix() == 'foo.bar.baz'
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedFields[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedFields[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedFields[0].name == 'stuff'

        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.foo'
        beanDefinition.injectedMethods[0].name == 'setFoo'
    }

    void "test inner class paths - fields"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig {
    String host;


    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
    
    @ConfigurationProperties("baz")
    static class ChildConfig {
        protected String stuff;
    }
}
''')
        then:
        beanDefinition.synthesize(ConfigurationReader).prefix() == 'foo.bar.baz'
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedMethods.size() == 0
        beanDefinition.injectedFields[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedFields[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedFields[0].name == 'stuff'
    }

    void "test inner class paths - one level"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig {
    String host;


    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
    
    @ConfigurationProperties("baz")
    static class ChildConfig {
        String stuff;
    
        public String getStuff() {
            return stuff;
        }
    
        public void setStuff(String stuff) {
            this.stuff = stuff;
        }
    }
}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }


    void "test inner class paths - two levels"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig$MoreConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig {
    String host;


    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
    
    @ConfigurationProperties("baz")
    static class ChildConfig {
        String stuff;
    
        public String getStuff() {
            return stuff;
        }
    
        public void setStuff(String stuff) {
            this.stuff = stuff;
        }
        
        @ConfigurationProperties("more")
        static class MoreConfig {
            String stuff;
        
            public String getStuff() {
                return stuff;
            }
        
            public void setStuff(String stuff) {
                this.stuff = stuff;
            }
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
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig extends ParentConfig {
    String host;


    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
    
    @ConfigurationProperties("baz")
    static class ChildConfig {
        String stuff;
    
        public String getStuff() {
            return stuff;
        }
    
        public void setStuff(String stuff) {
            this.stuff = stuff;
        }
    }
}

@ConfigurationProperties("parent")
class ParentConfig {

}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'parent.foo.bar.baz.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }

    void "test setters with two arguments are not injected"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("foo.bar")
class MyConfig {
    private String host = "localhost";


    public String getHost() {
        return host;
    }

    public void setHost(String host, int port) {
        this.host = host;
    }
}


''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 0
    }

    void "test setters with two arguments from abstract parent are not injected"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.ChildConfig', '''
package test;

import io.micronaut.context.annotation.*;


abstract class MyConfig {
    private String host = "localhost";


    public String getHost() {
        return host;
    }

    public void setHost(String host, int port) {
        this.host = host;
    }
}

@ConfigurationProperties("baz")
class ChildConfig extends MyConfig {
    String stuff;

    public String getStuff() {
        return stuff;
    }

    public void setStuff(String stuff) {
        this.stuff = stuff;
    }
}


''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }

    void "test inheritance with setters"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.ChildConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("foo.bar")
class MyConfig {
    protected int port;
    String host;


    public String getHost() {
        return host;
    }

    public void setHost(String host) {
        this.host = host;
    }
}

@ConfigurationProperties("baz")
class ChildConfig extends MyConfig {
    String stuff;

    public String getStuff() {
        return stuff;
    }

    public void setStuff(String stuff) {
        this.stuff = stuff;
    }
}




''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedFields[0].name == 'port'
        beanDefinition.injectedFields[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.port'
        beanDefinition.injectedMethods[1].name == 'setStuff'
        beanDefinition.injectedMethods[1].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[1].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.host'
        beanDefinition.injectedMethods[0].name == 'setHost'
    }

    void "test annotation on package scope setters arguments"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.HttpClientConfiguration', '''
package test;

import io.micronaut.core.convert.format.*;
import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("http.client")
public class HttpClientConfiguration {
    private int maxContentLength = 1024 * 1024 * 10; // 10MB;
    
    void setMaxContentLength(@ReadableBytes int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }
    public int getMaxContentLength() {
        return maxContentLength;
    }

}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].arguments[0].synthesize(ReadableBytes)
    }

    void "test annotation on setters arguments"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.HttpClientConfiguration', '''
package test;

import io.micronaut.core.convert.format.*;
import io.micronaut.context.annotation.*;
import java.time.Duration;

@ConfigurationProperties("http.client")
public class HttpClientConfiguration {
    private int maxContentLength = 1024 * 1024 * 10; // 10MB;
    
    public void setMaxContentLength(@ReadableBytes int maxContentLength) {
        this.maxContentLength = maxContentLength;
    }
    public int getMaxContentLength() {
        return maxContentLength;
    }

}
''')
        then:
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].arguments[0].synthesizeAll().size() == 1
        beanDefinition.injectedMethods[0].arguments[0].synthesize(ReadableBytes)
    }

    void "test different inject types for config properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("foo")
class MyProperties {
    protected String fieldTest = "unconfigured";
    private final boolean privateFinal = true;
    protected final boolean protectedFinal = true;
    private boolean anotherField;
    private String internalField = "unconfigured";
    public void setSetterTest(String s) {
        this.internalField = s;
    }
    
    public String getSetter() { return this.internalField; } 
}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'fieldTest'
        beanDefinition.injectedMethods.size() == 1

        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.build().start()
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
package test;

import io.micronaut.context.annotation.*;

@ConfigurationProperties("foo")
class MyProperties extends Parent {
    protected String fieldTest = "unconfigured";
    private final boolean privateFinal = true;
    protected final boolean protectedFinal = true;
    private boolean anotherField;
    private String internalField = "unconfigured";
    public void setSetterTest(String s) {
        this.internalField = s;
    }
    
    public String getSetter() { return this.internalField; } 
}

class Parent {
    private String parentField;
    
    public void setParentTest(String s) {
        this.parentField = s;
    }
    
    public String getParentTest() { return this.parentField; } 
}
''')
        then:
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedFields.first().name == 'fieldTest'
        beanDefinition.injectedMethods.size() == 2
        beanDefinition.injectedMethods[0].name == 'setParentTest'
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.parent-test'
        beanDefinition.injectedMethods[1].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[1].getAnnotationMetadata().synthesize(Property).name() == 'foo.setter-test'
        beanDefinition.injectedMethods[1].name == 'setSetterTest'


        when:
        BeanFactory factory = beanDefinition
        ApplicationContext applicationContext = ApplicationContext.build().start()
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
        ApplicationContext applicationContext = ApplicationContext.build(["my.host": "abc"]).start()
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
}
