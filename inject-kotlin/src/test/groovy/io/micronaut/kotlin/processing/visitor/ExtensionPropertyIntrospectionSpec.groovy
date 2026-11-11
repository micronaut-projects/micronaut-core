package io.micronaut.kotlin.processing.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.beans.BeanIntrospection

class ExtensionPropertyIntrospectionSpec extends AbstractKotlinCompilerSpec {

    void "member extension properties are not bean properties"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Holder', '''
package test

import io.micronaut.core.annotation.Introspected

@Introspected
class Holder(val name: String) {
    val String.shout: String get() = uppercase()

    var Int.counter: Int
        get() = this + 1
        set(value) {}
}
''')

        when:
        def bean = introspection.instantiate("micronaut")
        def values = introspection.beanProperties.collect { it.get(bean) }

        then:
        noExceptionThrown()
        values == ["micronaut"]
        introspection.propertyNames as List == ['name']
    }

    void "member extension properties are not bean properties of the class element"() {
        expect:
        buildClassElementMapped('test.Holder', '''
package test

class Holder(val name: String) {
    val String.shout: String get() = uppercase()

    var Int.counter: Int
        get() = this + 1
        set(value) {}
}
''') { ce ->
            assert ce.beanProperties*.name == ['name']
            assert ce.syntheticBeanProperties*.name == ['name']
            true
        }
    }
}
