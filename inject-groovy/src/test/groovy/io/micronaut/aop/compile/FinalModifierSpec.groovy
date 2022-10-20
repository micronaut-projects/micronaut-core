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

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.inject.writer.BeanDefinitionWriter

class FinalModifierSpec extends AbstractBeanDefinitionSpec {
    void "test final modifier with AOP advice doesn't compile"() {
        when:
        buildBeanDefinition('test.$FinalModifierMyBean1' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.proxytarget.*;
import io.micronaut.context.annotation.*;

@Mutating("someVal")
@jakarta.inject.Singleton
final class FinalModifierMyBean1 {

    private String myValue;

    FinalModifierMyBean1(String val) {
        this.myValue = val;
    }

    public String someMethod() {
        return myValue;
    }

}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Cannot apply AOP advice to final class. Class must be made non-final to support proxying: test.FinalModifierMyBean1'
    }

    void "test final modifier on method with AOP advice doesn't compile"() {
        when:
        buildBeanDefinition('test.$FinalModifierMyBean2' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.proxytarget.*;
import io.micronaut.context.annotation.*;

@Mutating("someVal")
@jakarta.inject.Singleton
class FinalModifierMyBean2 {

    private String myValue;

    FinalModifierMyBean2(String val) {
        this.myValue = val;
    }

    public final String someMethod() {
        return myValue;
    }

}
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains 'Public method inherits AOP advice but is declared final. Either make the method non-public or apply AOP advice only to public methods declared on the class.'
    }

    void "test final modifier on method with explicit AOP advice doesn't compile"() {
        when:
        buildBeanDefinition('test.$FinalModifierMyBean2' + BeanDefinitionWriter.CLASS_SUFFIX + BeanDefinitionWriter.PROXY_SUFFIX, '''
package test;

import io.micronaut.aop.proxytarget.*;
import io.micronaut.context.annotation.*;

@jakarta.inject.Singleton
class FinalModifierMyBean2 {

    private String myValue;

    FinalModifierMyBean2(String val) {
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
        e.message.contains 'Method defines AOP advice but is declared final. Change the method to be non-final in order for AOP advice to be applied.'
    }
}
