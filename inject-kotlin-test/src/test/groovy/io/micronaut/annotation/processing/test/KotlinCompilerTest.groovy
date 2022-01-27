package io.micronaut.annotation.processing.test

import io.micronaut.core.version.SemanticVersion
import spock.lang.Requires
import spock.util.environment.Jvm

// fails due to https://issues.apache.org/jira/browse/GROOVY-10145
@Requires({
    SemanticVersion.isAtLeastMajorMinor(GroovySystem.version, 4, 0) ||
            !Jvm.current.isJava16Compatible()
})
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
}
