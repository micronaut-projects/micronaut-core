package io.micronaut.inject.beans

import io.micronaut.ast.transform.test.AbstractBeanDefinitionSpec
import io.micronaut.core.annotation.Order

class BeanDefinitionSpec extends AbstractBeanDefinitionSpec {
    void 'test order annotation'() {
        given:
        def definition = buildBeanDefinition('test.TestOrder', '''
package test;

import io.micronaut.core.annotation.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;

@Requires(property = "spec.name", value = "BeanDefinitionDelegateSpec")
@Singleton
@Order(value = 10)
class TestOrder {

}
''')
        expect:

        definition.intValue(Order).getAsInt() == 10
    }

    void 'test order annotation inner class bean'() {
        given:
        def definition = buildBeanDefinition('test.OuterBean$TestOrder', '''
package test;

import io.micronaut.core.annotation.*;
import io.micronaut.context.annotation.*;
import jakarta.inject.*;

class OuterBean {

    static interface OrderedBean {
    }
    
    @Requires(property = "spec.name", value = "BeanDefinitionDelegateSpec")
    @Singleton
    @Order(value = 10)
    static class TestOrder implements OrderedBean {
    
    }
}

''')
        expect:

        definition.intValue(Order).getAsInt() == 10
    }

    void "test a bean definition in a package with uppercase letters"() {
        when:
        def definition = buildBeanDefinition('test.A', 'TestBean', '''
package test.A;

@jakarta.inject.Singleton
class TestBean {

}
''')
        then:
        noExceptionThrown()
        definition != null
    }
}
