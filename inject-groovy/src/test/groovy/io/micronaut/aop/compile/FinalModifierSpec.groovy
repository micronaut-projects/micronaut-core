package io.micronaut.aop.compile

import io.micronaut.AbstractBeanDefinitionSpec

class FinalModifierSpec extends AbstractBeanDefinitionSpec {
    void "test final modifier with AOP advice doesn't compile"() {
        when:
        buildBeanDefinition('test.$FinalModifierMyBean1Definition$Intercepted', '''
package test;

import io.micronaut.aop.proxytarget.*;
import io.micronaut.context.annotation.*;

@Mutating("someVal")
@javax.inject.Singleton
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
        buildBeanDefinition('test.$FinalModifierMyBean2Definition$Intercepted', '''
package test;

import io.micronaut.aop.proxytarget.*;
import io.micronaut.context.annotation.*;

@Mutating("someVal")
@javax.inject.Singleton
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
        e.message.contains 'Public method defines AOP advice but is declared final. Either make the method non-public or apply AOP advice only to public methods declared on class.'
    }
}
