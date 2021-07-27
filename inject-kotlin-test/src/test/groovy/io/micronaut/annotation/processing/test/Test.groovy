package io.micronaut.annotation.processing.test

class Test extends AbstractKotlinCompilerSpec {
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
    val a: String
}
''')
        expect:
        introspection.propertyNames.toList() == ['a']
    }

    void "context"() {
        given:
        def context = buildContext('example.Foo', '''
package example

import javax.inject.Inject
import javax.inject.Singleton

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
}
