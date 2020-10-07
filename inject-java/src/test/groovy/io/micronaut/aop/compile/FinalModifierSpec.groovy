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
package io.micronaut.aop.compile

import com.fasterxml.jackson.databind.ObjectMapper
import io.micronaut.aop.Intercepted
import io.micronaut.aop.simple.Mutating
import io.micronaut.inject.AbstractTypeElementSpec
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.Issue

class FinalModifierSpec extends AbstractTypeElementSpec {

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2530')
    void 'test final modifier on external class produced by factory'() {
        when:
        def context = buildContext('test.MyBeanFactory', '''
package test;

import io.micronaut.aop.simple.*;
import io.micronaut.context.annotation.*;
import com.fasterxml.jackson.databind.ObjectMapper;
@Factory
class MyBeanFactory {
    @Mutating("someVal")
    @javax.inject.Singleton
    @javax.inject.Named("myMapper")
    ObjectMapper myMapper() {
        return new ObjectMapper();
    }

}

''')
        then:
        context.getBean(ObjectMapper, Qualifiers.byName("myMapper")) instanceof Intercepted

        cleanup:
        context.close()
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2479')
    void "test final modifier on inherited public method"() {
        when:
        def definition = buildBeanDefinition('test.CountryRepositoryImpl', '''
package test;

import io.micronaut.aop.simple.*;
import io.micronaut.context.annotation.*;


abstract class BaseRepositoryImpl {
    public final Object getContext() {
        return new Object();
    }
}

interface CountryRepository {    
}

@javax.inject.Singleton
@Mutating("someVal")
class CountryRepositoryImpl extends BaseRepositoryImpl implements CountryRepository {
    
    public String someMethod() {
        return "test";
    }
}
''')
        then:"Compilation passes"
        definition != null
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2479')
    void "test final modifier on inherited protected method"() {
        when:
        def definition = buildBeanDefinition('test.CountryRepositoryImpl', '''
package test;

import io.micronaut.aop.simple.*;
import io.micronaut.context.annotation.*;


abstract class BaseRepositoryImpl {
    protected final Object getContext() {
        return new Object();
    }
}

interface CountryRepository {    
}

@javax.inject.Singleton
@Mutating("someVal")
class CountryRepositoryImpl extends BaseRepositoryImpl implements CountryRepository {
    
    public String someMethod() {
        return "test";
    }
}
''')
        then:"Compilation passes"
        definition != null
    }

    @Issue('https://github.com/micronaut-projects/micronaut-core/issues/2479')
    void "test final modifier on inherited protected method - 2"() {
        when:
        def definition = buildBeanDefinition('test.CountryRepositoryImpl', '''
package test;

import io.micronaut.aop.simple.*;
import io.micronaut.context.annotation.*;


abstract class BaseRepositoryImpl {
    protected final Object getContext() {
        return new Object();
    }
}

interface CountryRepository {  
    @Mutating("someVal") 
    public String someMethod();  
}

@javax.inject.Singleton
class CountryRepositoryImpl extends BaseRepositoryImpl implements CountryRepository {
    
    @Override
    public String someMethod() {
        return "test";
    }
}
''')
        then:"Compilation passes"
        definition != null
    }

    void "test final modifier on factory with AOP advice doesn't compile"() {
        when:
        buildBeanDefinition('test.MyBeanFactory', '''
package test;

import io.micronaut.aop.simple.*;
import io.micronaut.context.annotation.*;

@Factory
class MyBeanFactory {
    @Mutating("someVal")
    @javax.inject.Singleton
    MyBean myBean() {
        return new MyBean();
    }

}

final class MyBean {


}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'error: Cannot apply AOP advice to final class. Class must be made non-final to support proxying: test.MyBean'
    }


    void "test final modifier on class with AOP advice doesn't compile"() {
        when:
        buildBeanDefinition('test.$MyBeanDefinition$Intercepted', '''
package test;

import io.micronaut.aop.simple.*;
import io.micronaut.context.annotation.*;

@Mutating("someVal")
@javax.inject.Singleton
final class MyBean {

    private String myValue;
    
    MyBean(@Value("${foo.bar}") String val) {
        this.myValue = val;
    }
    
    public String someMethod() {
        return myValue;
    }

}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'error: Cannot apply AOP advice to final class. Class must be made non-final to support proxying: test.MyBean'
    }

    void "test final modifier on method with AOP advice doesn't compile"() {
        when:
        buildBeanDefinition('test.$MyBeanDefinition$Intercepted', '''
package test;

import io.micronaut.aop.simple.*;
import io.micronaut.context.annotation.*;

@Mutating("someVal")
@javax.inject.Singleton
class MyBean {

    private String myValue;
    
    MyBean(@Value("${foo.bar}") String val) {
        this.myValue = val;
    }
    
    public final String someMethod() {
        return myValue;
    }

}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'error: Public method inherits AOP advice but is declared final. Either make the method non-public or apply AOP advice only to public methods declared on the class.'
    }

    void "test final modifier on method with AOP advice on method doesn't compile"() {
        when:
        buildBeanDefinition('test.$MyBeanDefinition$Intercepted', '''
package test;

import io.micronaut.aop.simple.*;
import io.micronaut.context.annotation.*;

@javax.inject.Singleton
class MyBean {

    private String myValue;
    
    MyBean(@Value("${foo.bar}") String val) {
        this.myValue = val;
    }
    
    @Mutating("someVal")
    public final String someMethod() {
        return myValue;
    }

}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'error: Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.'
    }
}
