/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.inject.foreach

import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.context.annotation.Property
import io.micronaut.annotation.processing.test.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition

/**
 * @author graemerocher
 * @since 1.0
 */
class EachPropertyParseSpec extends AbstractTypeElementSpec {
    void "test configuration properties inheritance from non-configuration properties"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyProperties', '''
package test;

import io.micronaut.context.annotation.*;

@EachProperty("foo")
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
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.*.parent-test'
        beanDefinition.injectedMethods[1].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[1].getAnnotationMetadata().synthesize(Property).name() == 'foo.*.setter-test'
        beanDefinition.injectedMethods[1].name == 'setSetterTest'

    }
    void "test inner class paths - one level"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@EachProperty("foo.bar")
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
        beanDefinition.synthesize(ConfigurationReader).prefix() == 'foo.bar.*.baz'
        beanDefinition.injectedFields.size() == 0
        beanDefinition.injectedMethods.size() == 1
        beanDefinition.injectedMethods[0].getAnnotationMetadata().hasAnnotation(Property)
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.*.baz.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }

    void "test inner class paths - two levels"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig$MoreConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@EachProperty("foo.bar")
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
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'foo.bar.*.baz.more.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }

    void "test inner class paths - with parent inheritance"() {
        when:
        BeanDefinition beanDefinition = buildBeanDefinition('test.MyConfig$ChildConfig', '''
package test;

import io.micronaut.context.annotation.*;
import java.time.Duration;

@EachProperty("foo.bar")
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
        beanDefinition.injectedMethods[0].getAnnotationMetadata().synthesize(Property).name() == 'parent.foo.bar.*.baz.stuff'
        beanDefinition.injectedMethods[0].name == 'setStuff'
    }
}
