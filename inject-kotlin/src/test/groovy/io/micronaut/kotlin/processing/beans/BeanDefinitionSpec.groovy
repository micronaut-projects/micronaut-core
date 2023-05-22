package io.micronaut.kotlin.processing.beans

import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.context.exceptions.NoSuchBeanException
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.Order
import io.micronaut.core.bind.annotation.Bindable
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.HttpMethodMapping
import io.micronaut.http.client.annotation.Client
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.qualifiers.Qualifiers
import io.micronaut.inject.writer.BeanDefinitionVisitor
import spock.lang.PendingFeature
import spock.lang.Specification

import static io.micronaut.annotation.processing.test.KotlinCompiler.buildBeanDefinition
import static io.micronaut.annotation.processing.test.KotlinCompiler.buildBeanDefinitionReference
import static io.micronaut.annotation.processing.test.KotlinCompiler.buildContext
import static io.micronaut.annotation.processing.test.KotlinCompiler.getBean
import static io.micronaut.annotation.processing.test.KotlinCompiler.getBeanDefinition

class BeanDefinitionSpec extends Specification {

    void "test bean annotated with @MicronautTest"() {
        when:
        def context = buildContext('''
package test

import io.micronaut.runtime.EmbeddedApplication
import io.micronaut.test.extensions.kotest5.annotation.MicronautTest
import io.kotest.core.spec.style.StringSpec

@MicronautTest
class ExampleTest(private val application: EmbeddedApplication<*>): StringSpec({

        "test the server is running" {
            assert(application.isRunning)
        }
})
''')

        then:
        context != null

        when:
        getBean(context, 'test.ExampleTest')

        then:
        def e = thrown(NoSuchBeanException)
        e.message.contains("Bean of type [test.ExampleTest] is disabled")
    }

    void "test jvm field"() {
        given:
        def definition = KotlinCompiler.buildBeanDefinition('test.JvmFieldTest', '''
package test

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class JvmFieldTest {
    @JvmField
    @Inject
    var f : F? = null
}

@Singleton
class F
''')

        expect:
        definition.injectedMethods.size() == 0
        definition.injectedFields.size() == 1
    }

    @PendingFeature(reason = "difficult to achieve with current design without a significant rewrite or how native properties are handled")
    void "test injection order for inheritance"() {
        given:
        def context = KotlinCompiler.buildContext('''
package inherit

import jakarta.inject.*
import jakarta.inject.Inject

@Singleton
class Child : Parent() {

    var parentMethodInjectBeforeChildMethod : Boolean = false
    var parentMethodInjectBeforeChildField : Boolean = false
    var childFieldInjectedBeforeChildMethod : Boolean = false

    @Inject
    var childProp : Other? = Other()
        set(value) {
           if (parentProp != null && parentMethod != null) {
                parentMethodInjectBeforeChildField = true
           }
        }
    lateinit var childMethod : Other

    @Inject
    fun antherMethod(other : Other)  {
        if (parentProp != null && parentMethod != null) {
            parentMethodInjectBeforeChildMethod = true
        }
        if (childProp != null) {
            childFieldInjectedBeforeChildMethod = true
        }
        childMethod = other
    }
}

open class Parent {
    var parentPropInjectedBeforeParentMethod : Boolean = false

    @Inject
    lateinit var parentProp : Other
    lateinit var parentMethod : Other

    @Inject
    fun someMethod(other : Other)  {
        if (parentProp != null) {
            parentPropInjectedBeforeParentMethod = true
        }
        parentMethod = other
    }
}

@Singleton
class Other
''')
        def bean = KotlinCompiler.getBean(context, 'inherit.Child')

        expect:"The parent property was injected before the parent method"
        bean.parentPropInjectedBeforeParentMethod

        and:"All injection points of the parent were injected before the child method"
        bean.parentInjectBeforeChildMethod

        and:"All injection points of the parent were injected before the child field"
        bean.parentInjectBeforeChildField

        and:"The child property was injected before the child method"
        bean.childFieldInjectedBeforeChildMethod

        cleanup:
        context.close()
    }

    void "test suspend function with executable"() {
        given:
        def definition = buildBeanDefinition('test.SuspendTest', '''
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
class SuspendTest {
    @Executable
    suspend fun test() {
        TODO()
    }
}

@Singleton
class A
''')
        expect:
        definition != null
        definition.executableMethods.size() == 1
    }

    void "test @Inject on set of Kotlin property"() {
        given:
        def definition = buildBeanDefinition('test.SetterInjectBean', '''
package test

import jakarta.inject.Inject
import jakarta.inject.Named
import jakarta.inject.Singleton

@Singleton
class SetterInjectBean {
    internal var _a: A? = null
    internal var a: A
        get() = _a!!
        @Inject set(value) { _a = value; }
}

@Singleton
class A
''')
        expect:
        definition != null
        definition.injectedMethods.size() == 1
    }

    void "test requires validation adds bean introspection"() {
        given:
        def definition = buildBeanDefinition('test.EngineConfig', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.convert.format.MapFormat
import jakarta.validation.constraints.Min
// end::imports[]

// tag::class[]
@ConfigurationProperties("my.engine")
class EngineConfig {

    @Min(1L)
    var cylinders: Int = 0

    @MapFormat(transformation = MapFormat.MapTransformation.FLAT) //<1>
    var sensors: Map<Int, String>? = null
}
''')
        expect:
        definition.hasAnnotation(Introspected)
    }

    void "test repeated annotations - auto unwrap"() {
        given:
        def definition = buildBeanDefinition('test.RepeatedTest', '''
package test

import io.micronaut.context.annotation.Executable
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.Headers
import jakarta.inject.Singleton

@Singleton
@Headers(
    Header(name="Foo"),
    Header(name="Bar")
)

class RepeatedTest {
    @Executable
    @Headers(
        Header(name="Baz"),
        Header(name="Stuff")
    )
    fun test() : String {
        return "Ok"
    }
}
''')
        expect:
        definition.getRequiredMethod("test").getAnnotationValuesByType(Header).size() == 4
        definition.getRequiredMethod("test").getAnnotationNamesByStereotype(Bindable) == [Header.class.name]
    }

    void "test repeated annotations"() {
        given:
        def definition = buildBeanDefinition('test.RepeatedTest', '''
package test

import io.micronaut.context.annotation.Executable
import io.micronaut.http.annotation.Header
import jakarta.inject.Singleton

@Singleton
@Header(name="Foo")
@Header(name="Bar")
class RepeatedTest {
    @Executable
    @Header(name="Baz")
    @Header(name="Stuff")
    fun test() : String {
        return "Ok"
    }
}
''')
        expect:
        definition.getRequiredMethod("test").getAnnotationValuesByType(Header).size() == 4
    }

    void "test annotation defaults"() {
        given:
        def definition = KotlinCompiler.buildBeanDefinition('test.TestClient' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client

@Client("/")
interface TestClient {
    @Post
    fun save(str : String) : String
}
''')
        expect:
        definition.getRequiredMethod("save", String)
                .getAnnotation(HttpMethodMapping)
                .getRequiredValue(String) == '/'
    }

    void "test annotation defaults - inherited"() {
        given:
        def definition = KotlinCompiler.buildBeanDefinition('test.TestClient' + BeanDefinitionVisitor.PROXY_SUFFIX, '''
package test

import io.micronaut.http.annotation.Post
import io.micronaut.http.client.annotation.Client

@Client("/")
interface TestClient : TestOperations {
    override fun save(str : String) : String
}

interface TestOperations {
    @Post
    fun save(str : String) : String
}
''')
        expect:
        definition.getRequiredMethod("save", String)
                .getAnnotation(HttpMethodMapping)
                .getRequiredValue(String) == '/'
    }

    void "test @Inject internal var"() {
        given:
        def context = KotlinCompiler.buildContext('''
package test

import io.micronaut.context.event.ApplicationEventPublisher
import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class SampleEventEmitterBean {

    @Inject
    internal var eventPublisher: ApplicationEventPublisher<SampleEvent>? = null

    fun publishSampleEvent() {
        eventPublisher!!.publishEvent(SampleEvent())
    }

}

class SampleEvent

''')

        def bean = getBean(context, 'test.SampleEventEmitterBean')
        def definition = KotlinCompiler.getBeanDefinition(context, 'test.SampleEventEmitterBean')
        expect:
        definition.injectedFields.size() == 0
        definition.injectedMethods.size() == 1

        bean.eventPublisher

        cleanup:
        context.close()
    }
    void "test @Property targeting field"() {
        given:
        def context = buildContext('''
package test

import io.micronaut.context.annotation.Property

import jakarta.inject.Inject
import jakarta.inject.Singleton

@Singleton
class Engine {

    @field:Property(name = "my.engine.cylinders") // <1>
    protected var cylinders: Int = 0 // <2>

    @set:Inject
    @setparam:Property(name = "my.engine.manufacturer") // <3>
    var manufacturer: String? = null

    @Inject
    @Property(name = "my.engine.manufacturer") // <3>
    var manufacturer2: String? = null

    @Property(name = "my.engine.manufacturer") // <3>
    var manufacturer3: String? = null

    fun cylinders(): Int {
        return cylinders
    }
}
''', false, ['my.engine.cylinders': 8, 'my.engine.manufacturer':'Ford'])
        def definition = getBeanDefinition(context, 'test.Engine')
        def bean = getBean(context, 'test.Engine')

        expect:"field targeting injects fields"
        definition.injectedMethods.size() == 4
        definition.injectedFields.size() == 0
        bean.cylinders() == 8
        bean.manufacturer == 'Ford'
        bean.manufacturer2 == 'Ford'
        bean.manufacturer3 == 'Ford'
    }

    void "test non-binding qualifier"() {
        given:
        def definition = KotlinCompiler.buildBeanDefinition('test.V8Engine', '''
package test

import io.micronaut.context.annotation.NonBinding
import jakarta.inject.Qualifier
import jakarta.inject.Singleton
import kotlin.annotation.Retention

@Cylinders(value = 8, description = "test")
@Singleton
class V8Engine

@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class Cylinders(
    val value: Int,
    @get:NonBinding // <2>
    val description: String = ""
)
''')
        expect:"the non-binding member is not there"
        definition.declaredQualifier.qualifierAnn.memberNames == ["value"] as Set
    }

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

    void "test isTypeVariable"() {
        given:
            BeanDefinition definition = buildBeanDefinition('test', 'Test', '''
package test;
import jakarta.validation.constraints.*;
import java.util.List;

@jakarta.inject.Singleton
class Test : Serde<Object> {
}

interface Serde<T> : Serializer<T>, Deserializer<T> {
}

interface Serializer<T> {
}

interface Deserializer<T> {
}


        ''')

        when: "Micronaut Serialization use-case"
            def serdeTypeParam = definition.getTypeArguments("test.Serde")[0]
            def serializerTypeParam = definition.getTypeArguments("test.Serializer")[0]
            def deserializerTypeParam = definition.getTypeArguments("test.Deserializer")[0]

        then: "The first is a placeholder"
            serdeTypeParam.isTypeVariable() //
            (serdeTypeParam instanceof GenericPlaceholder)
        and: "threat resolved placeholder as not a type variable"
            serializerTypeParam.isTypeVariable()
            (serializerTypeParam instanceof GenericPlaceholder)
            deserializerTypeParam.isTypeVariable()
            (deserializerTypeParam instanceof GenericPlaceholder)
    }

    void "test isTypeVariable array"() {
        given:
            BeanDefinition definition = buildBeanDefinition('test', 'Test', '''
package test;

import jakarta.validation.constraints.*;
import java.util.List

@jakarta.inject.Singleton
class Test : Serde<Array<String>> {
}

interface Serde<T> : Serializer<T>, Deserializer<T> {
}

interface Serializer<T> {
}

interface Deserializer<T> {
}


        ''')

        when: "Micronaut Serialization use-case"
            def serdeTypeParam = definition.getTypeArguments("test.Serde")[0]
            def serializerTypeParam = definition.getTypeArguments("test.Serializer")[0]
            def deserializerTypeParam = definition.getTypeArguments("test.Deserializer")[0]
            // Arrays are not resolved as KotlinClassElements or placeholders
        then: "The first is a placeholder"
            serdeTypeParam.simpleName == "String[]"
            serdeTypeParam.isTypeVariable()
            (serdeTypeParam instanceof GenericPlaceholder)
        and: "threat resolved placeholder as not a type variable"
            serializerTypeParam.simpleName == "String[]"
            serializerTypeParam.isTypeVariable()
            (serializerTypeParam instanceof GenericPlaceholder)
            deserializerTypeParam.simpleName == "String[]"
            deserializerTypeParam.isTypeVariable()
            (deserializerTypeParam instanceof GenericPlaceholder)
    }
}
