package io.micronaut.kotlin.processing.visitor

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.reflect.exception.InstantiationException

class ObjectIntrospectionSpec extends AbstractKotlinCompilerSpec {

    void "test #kind introspection instantiates the singleton"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected
$declaration Test {
    fun hello() = "hello"
}
""")
        def singleton = introspection.beanType.getField("INSTANCE").get(null)

        then:
        introspection.instantiate().is(singleton)
        introspection.instantiateInternal(new Object[0]).is(singleton)
        introspection.instantiate(new Object[0]).is(singleton)
        introspection.getConstructorArguments().length == 0

        where:
        kind          | declaration
        "object"      | "object"
        "data object" | "data object"
    }

    void "test nested data object introspection instantiates the singleton"() {
        when:
        def classLoader = buildClassLoader("test.Shape", """
package test

import io.micronaut.core.annotation.Introspected

sealed interface Shape {
    @Introspected
    data object Point : Shape
}
""")
        BeanIntrospection introspection = classLoader.loadClass("test.\$Shape\$Point\$Introspection").newInstance()
        def singleton = introspection.beanType.getField("INSTANCE").get(null)

        then:
        introspection.instantiate().is(singleton)
    }

    void "test companion object is not instantiable"() {
        when:
        def classLoader = buildClassLoader("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

class Test {
    @Introspected
    companion object
}
""")
        BeanIntrospection introspection = classLoader.loadClass("test.\$Test\$Companion\$Introspection").newInstance()
        introspection.instantiate()

        then:
        thrown(InstantiationException)
    }

    void "test object with a static creator instantiates through the creator"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.Introspected

@Introspected
object Test {
    @JvmStatic
    @Creator
    fun create(): Test = Test
}
""")
        def singleton = introspection.beanType.getField("INSTANCE").get(null)

        then:
        introspection.instantiate().is(singleton)
        introspection.instantiateInternal(new Object[0]).is(singleton)
    }

    void "test object introspection with constructors does not expose the private constructor"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected(constructors = true)
object Test {
    val x = "a"
}
""")
        def singleton = introspection.beanType.getField("INSTANCE").get(null)

        then:
        introspection.instantiate().is(singleton)
        introspection.getConstructors().every { it.arguments.length == 0 && it.instantiate().is(singleton) }
    }

    void "test companion object introspection with constructors does not expose the private constructor"() {
        when:
        def classLoader = buildClassLoader("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

class Test {
    @Introspected(constructors = true)
    companion object
}
""")
        BeanIntrospection introspection = classLoader.loadClass("test.\$Test\$Companion\$Introspection").newInstance()

        then:
        introspection.getConstructors().every {
            try {
                it.instantiate()
                true
            } catch (InstantiationException ignored) {
                true
            }
        }
    }
}
