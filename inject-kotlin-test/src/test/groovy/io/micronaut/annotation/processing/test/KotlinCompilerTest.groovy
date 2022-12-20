package io.micronaut.annotation.processing.test

import io.micronaut.core.beans.BeanIntrospection
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

    // issue https://github.com/micronaut-projects/micronaut-core/issues/8500
    void "build kotlin bean definition inheriting java innner static class with executable method"() {
        given:
        def definition = buildBeanDefinition('test.Test', """
package test;
import io.micronaut.annotation.processing.test.TestJavaBeans
import jakarta.inject.Singleton

@Singleton
class Test: TestJavaBeans.$className() {
}
""")

        expect:
        definition != null

        where:
        className                |_
        'ScheduledPublicBean'    |_
        'ScheduledProtectedBean' |_
        'ScheduledDefaultBean'   |_
    }

    // issue https://github.com/micronaut-projects/micronaut-core/issues/8500
    void "build kotlin bean definition inheriting java single class with executable method"() {
        given:
        def definition = buildBeanDefinition('test.Test', """
package test;
import io.micronaut.annotation.processing.test.$className
import jakarta.inject.Singleton

@Singleton
class Test: $className() {
}
""")

        expect:
        definition != null

        where:
        className                      ||_
        'PublicScheduledPublicBean'    ||_
        'PublicScheduledProtectedBean' ||_
        'PublicScheduledDefaultBean'   ||_
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
        introspection.propertyNames.toList() == ['id', 'name', 'getSurname', 'isDeleted', 'isImportant', 'corrected', 'upgraded', 'isMyBool', 'isMyBool2', 'myBool3', 'myBool4', 'myBool5']
    }
}
