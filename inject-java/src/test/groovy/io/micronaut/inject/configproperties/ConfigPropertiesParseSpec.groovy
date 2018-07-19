/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Property
import io.micronaut.core.convert.format.ReadableBytes
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.BeanFactory

/**
 * @author Graeme Rocher
 * @since 1.0
 */
class   ConfigPropertiesParseSpec extends AbstractTypeElementSpec {

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
        beanDefinition.getAnnotation(ConfigurationReader).prefix() == 'foo.bar.baz'
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedFields[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedFields[0].getAnnotationMetadata().getAnnotation(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedFields[0].name == 'stuff'

        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().getAnnotation(Property).name() == 'foo.bar.baz.foo'
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
        beanDefinition.getAnnotation(ConfigurationReader).prefix() == 'foo.bar.baz'
        beanDefinition.injectedFields.size() == 1
        beanDefinition.injectedMethods.size() == 0
        beanDefinition.injectedFields[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedFields[0].getAnnotationMetadata().getAnnotation(Property).name() == 'foo.bar.baz.stuff'
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
        beanDefinition.injectedMethods[0].getAnnotationMetadata().getAnnotation(Property).name() == 'foo.bar.baz.stuff'
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
        beanDefinition.injectedMethods[0].getAnnotationMetadata().getAnnotation(Property).name() == 'foo.bar.baz.more.stuff'
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
        beanDefinition.injectedMethods[0].getAnnotationMetadata().getAnnotation(Property).name() == 'parent.foo.bar.baz.stuff'
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
        beanDefinition.injectedFields[0].getAnnotationMetadata().getAnnotation(Property).name() == 'foo.bar.port'
        beanDefinition.injectedMethods[1].name == 'setStuff'
        beanDefinition.injectedMethods[1].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[1].getAnnotationMetadata().getAnnotation(Property).name() == 'foo.bar.baz.stuff'
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().getAnnotation(Property).name() == 'foo.bar.host'
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
        beanDefinition.injectedMethods[0].arguments[0].getAnnotation(ReadableBytes)
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
        beanDefinition.injectedMethods[0].arguments[0].getAnnotations().size() == 1
        beanDefinition.injectedMethods[0].arguments[0].getAnnotation(ReadableBytes)
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
        beanDefinition.injectedMethods[0].getAnnotationMetadata().getAnnotation(Property).name() == 'foo.parent-test'
        beanDefinition.injectedMethods[1].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[1].getAnnotationMetadata().getAnnotation(Property).name() == 'foo.setter-test'
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
}
