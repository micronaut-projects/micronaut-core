package io.micronaut.kotlin.processing.beans

import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.Order
import io.micronaut.http.client.annotation.Client
import io.micronaut.inject.qualifiers.Qualifiers
import spock.lang.PendingFeature
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.*

class BeanDefinitionSpec extends Specification {
    void "test property annotation on properties and targeting params"() {
        given:
        def context = KotlinCompiler.buildContext('''
package test
import io.micronaut.context.annotation.Property
import io.micronaut.core.convert.format.MapFormat
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class BeanWithProperty {

    @set:Inject
    @setparam:Property(name="app.string")
    var stringParam:String ?= null

    @set:Inject
    @setparam:Property(name="app.map")
    @setparam:MapFormat(transformation = MapFormat.MapTransformation.FLAT)
    var mapParam:Map<String, String> ?= null

    @Property(name="app.string")
    var stringParamTwo:String ?= null

    @Property(name="app.map")
    @MapFormat(transformation = MapFormat.MapTransformation.FLAT)
    var mapParamTwo:Map<String, String> ?= null
}
''', false, ["app.string": "Hello", "app.map.yyy.xxx": 2, "app.map.yyy.yyy": 3])

        def bean = KotlinCompiler.getBean(context, 'test.BeanWithProperty')

        expect:
        bean.stringParam == 'Hello'

        cleanup:
        context.close()
    }

    void "test annotations targeting field on properties"() {
        given:
        def definition = buildBeanDefinition('test.TestBean', '''
package test

import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
class TestBean {
    @Inject @field:Named("one") lateinit var otherBean: OtherBean
}

@Singleton
@Named("one")
class OtherBean
''')
        expect:
        definition != null
        definition.injectedMethods.size() == 1
        definition.injectedMethods[0].annotationMetadata.hasAnnotation(AnnotationUtil.NAMED)
        definition.injectedMethods[0].arguments[0].annotationMetadata.hasAnnotation(AnnotationUtil.NAMED)
    }

    void "test annotations targeting field on properties - client"() {
        given:
        def definition = buildBeanDefinition('test.TestBean', '''
package test

import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
class TestBean {
    @Inject @field:Client("/test") lateinit var client: HttpClient
}

''')
        expect:
        definition != null
        definition.injectedMethods.size() == 1
        definition.injectedMethods[0].annotationMetadata.hasAnnotation(Client)
        definition.injectedMethods[0].arguments[0].annotationMetadata.hasAnnotation(Client)
    }

    void "test controller with constructor arguments"() {
        given:
        def definition = buildBeanDefinition('controller.TestController', '''
package controller

import io.micronaut.context.annotation.*
import io.micronaut.http.annotation.Controller
import jakarta.inject.*
import jakarta.inject.Singleton

@Controller
class TestController(var bar : Bar)

@Singleton
class Bar
''')
        expect:
        definition != null
    }

    void "test limit the exposed bean types"() {
        given:
        def definition = buildBeanDefinition('limittypes.Test', '''
package limittypes

import io.micronaut.context.annotation.*
import jakarta.inject.*

@Singleton
@Bean(typed = [Runnable::class])
class Test: Runnable {

    override fun run() {
    }
}

''')
        expect:
        definition.exposedTypes == [Runnable] as Set
    }

    void "test limit the exposed bean types - reference"() {
        given:
        def reference = buildBeanDefinitionReference('limittypes.Test', '''
package limittypes

import io.micronaut.context.annotation.*
import jakarta.inject.*

@Singleton
@Bean(typed = [Runnable::class])
class Test: Runnable {

    override fun run(){
    }
}

''')
        expect:
        reference.exposedTypes == [Runnable] as Set
    }

    void "test fail compilation on invalid exposed bean type"() {
        when:
        buildBeanDefinition('limittypes.Test', '''
package limittypes

import io.micronaut.context.annotation.*
import jakarta.inject.*

@Singleton
@Bean(typed = [Runnable::class])
class Test
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [java.lang.Runnable] that is not implemented by the bean type")
    }

    void "test exposed types on factory with AOP"() {
        when:
        buildBeanDefinition('limittypes.Test$Method0', '''
package limittypes

import io.micronaut.kotlin.processing.aop.Logged
import io.micronaut.context.annotation.*
import jakarta.inject.Singleton

@Factory
class Test {

    @Singleton
    @Bean(typed = [X::class])
    @Logged
    fun method(): Y {
        return Y()
    }
}

interface X

open class Y: X
''')

        then:
        noExceptionThrown()
    }

    void "test fail compilation on exposed subclass of bean type"() {
        when:
        buildBeanDefinition('limittypes.Test', '''
package limittypes

import io.micronaut.context.annotation.*
import jakarta.inject.*

@Singleton
@Bean(typed = [X::class])
open class Test

class X : Test()
''')
        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [limittypes.X] that is not implemented by the bean type")
    }

    void "test fail compilation on exposed subclass of bean type with factory"() {
        when:
        buildBeanDefinition('limittypes.Test$Method0', '''
package limittypes

import io.micronaut.context.annotation.*
import jakarta.inject.Singleton

@Factory
class Test {

    @Singleton
    @Bean(typed = [X::class, Y::class])
    fun method(): X {
        return Y()
    }
}

interface X

class Y: X
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [limittypes.Y] that is not implemented by the bean type")
    }

    void "test exposed bean types with factory invalid type"() {
        when:
        buildBeanDefinition('limittypes.Test$Method0', '''
package limittypes

import io.micronaut.context.annotation.*
import jakarta.inject.Singleton

@Factory
class Test {

    @Singleton
    @Bean(typed = [Z::class])
    fun method(): X {
        return Y()
    }
}

interface Z
interface X
class Y: X
''')

        then:
        def e = thrown(RuntimeException)
        e.message.contains("Bean defines an exposed type [limittypes.Z] that is not implemented by the bean type")
    }

    void 'test order annotation'() {
        given:
        def definition = buildBeanDefinition('test.TestOrder', '''
package test

import io.micronaut.core.annotation.*
import io.micronaut.context.annotation.*
import jakarta.inject.*

@Singleton
@Order(value = 10)
class TestOrder
''')
        expect:

        definition.intValue(Order).getAsInt() == 10
    }

    void 'test qualifier for named only'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test

@jakarta.inject.Named("foo")
class Test
''')
        expect:
        definition.getDeclaredQualifier() == Qualifiers.byName("foo")
    }

    void 'test no qualifier / only scope'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test;

@jakarta.inject.Singleton
class Test
''')
        expect:
        definition.getDeclaredQualifier() == null
    }

    void 'test named via alias'() {
        given:
        def definition = buildBeanDefinition('aliastest.Test', '''
package aliastest

import io.micronaut.context.annotation.*

@MockBean(named="foo")
class Test

@Bean
annotation class MockBean(

    @get:Aliases(AliasFor(annotation = Replaces::class, member = "named"), AliasFor(annotation = jakarta.inject.Named::class, member = "value"))
    val named: String = ""
)
''')
        expect:
        definition.getDeclaredQualifier() == Qualifiers.byName("foo")
        definition.getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).get() == AnnotationUtil.NAMED
    }

    void 'test qualifier annotation'() {
        given:
        def definition = buildBeanDefinition('test.Test', '''
package test

import io.micronaut.context.annotation.*

@MyQualifier
class Test

@jakarta.inject.Qualifier
annotation class MyQualifier (

    @get:Aliases(AliasFor(annotation = Replaces::class, member = "named"), AliasFor(annotation = jakarta.inject.Named::class, member = "value"))
    val named: String = ""
)
''')

        expect:
        definition.getDeclaredQualifier() == Qualifiers.byAnnotation(definition.getAnnotationMetadata(), "test.MyQualifier")
        definition.getAnnotationNameByStereotype(AnnotationUtil.QUALIFIER).get() == "test.MyQualifier"
    }

  /*  @Issue("https://github.com/micronaut-projects/micronaut-core/issues/5001")
    void "test building a bean with generics that dont have a type"() {
        when:
        def definition = buildBeanDefinition('test.NumberThingManager', '''
package test;

import jakarta.inject.Singleton

interface Thing<T>

interface NumberThing<T: Number, Comparable<T>> extends Thing<T> {}

class AbstractThingManager<T extends Thing<?>> {}

@Singleton
public class NumberThingManager extends AbstractThingManager<NumberThing<?>> {}
''')

        then:
        noExceptionThrown()
        definition != null
        definition.getTypeArguments("test.AbstractThingManager")[0].getTypeVariables().get("T").getType() == Number.class
    }*/

    void "test a bean definition in a package with uppercase letters"() {
        when:
        def definition = buildBeanDefinition('test.A', 'TestBean', '''
package test.A

@jakarta.inject.Singleton
class TestBean
''')
        then:
        noExceptionThrown()
        definition != null
    }

    void "test a bean definition inner static class"() {
        when:
        def definition = buildBeanDefinition('test.TestBean$TestBeanInner', '''
package test

class TestBean {

    @jakarta.inject.Singleton
    class TestBeanInner {

    }
}
''')
        then:
        noExceptionThrown()
        definition != null
    }

    void "test a bean definition is not created for inner class"() {
        when:
        def definition = buildBeanDefinition('test.TestBean$TestBeanInner', '''
package test

class TestBean {

    @jakarta.inject.Singleton
    inner class TestBeanInner {

    }
}
''')
        then:
        noExceptionThrown()
        definition == null
    }

    void "test nullable constructor arg"() {
        when:
        def definition = buildBeanDefinition('test.TestBean', '''
package test

@jakarta.inject.Singleton
class TestBean(private val other: Other?) {
}

class Other
''')
        then:
        noExceptionThrown()
        definition.constructor.arguments[0].isDeclaredNullable()
    }
}
