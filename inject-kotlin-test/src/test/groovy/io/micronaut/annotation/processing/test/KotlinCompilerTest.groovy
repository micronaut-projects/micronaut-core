package io.micronaut.annotation.processing.test

import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.version.SemanticVersion
import spock.lang.Ignore
import spock.lang.IgnoreIf
import spock.lang.Requires
import spock.util.environment.Jvm

class KotlinCompilerTest extends AbstractKotlinCompilerSpec {
    void "simple class"() {
        given:
        def cl = buildClassLoader('example.Test', '''
package example

class Test {}
''')
        expect:
        cl.loadClass("example.Test") != null
    }

    void "introspection"() {
        given:
        def introspection = buildBeanIntrospection('example.Test', '''
package example

import io.micronaut.core.annotation.Introspected

@Introspected
class Test {
    val a: String = "test"
}
''')
        expect:
        introspection.propertyNames.toList() == ['a']
    }

    void "context"() {
        given:
        def context = buildContext('example.Foo', '''
package example

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class Foo {
    @Inject var bar: Bar? = null
}

@Singleton
class Bar {

}
''')
        def bean = getBean(context, "example.Foo")

        expect:
        bean.bar != null
    }

    void "build bean definition"() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;
import jakarta.inject.Singleton;

@Singleton
class Test {
}
''')

        expect:
        definition != null
    }

    void "introspection with 'is' properties"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('example.Test', '''\
package example

import io.micronaut.core.annotation.Introspected

@Introspected
class Test(
        var id: Long,
        var name: String,
        var getSurname: String,
        var isDeleted: Boolean,
        var isOptional: Boolean?,
        val isImportant: Boolean,
        var corrected: Boolean,
        val upgraded: Boolean,
) {
    val isMyBool: Boolean
        get() = false
    var isMyBool2: Boolean
        get() = false
        set(v) {}
    var myBool3: Boolean
        get() = false
        set(v) {}
   val myBool4: Boolean
        get() = false
   var myBool5: Boolean = false
}
''')
        expect:
        introspection.propertyNames as Set == ['id', 'name', 'getSurname', 'isDeleted', 'isOptional', 'isImportant', 'corrected', 'upgraded', 'isMyBool', 'isMyBool2', 'myBool3', 'myBool4', 'myBool5'] as Set
    }
}
