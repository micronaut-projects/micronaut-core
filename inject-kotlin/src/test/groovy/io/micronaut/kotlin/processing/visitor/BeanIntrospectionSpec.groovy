package io.micronaut.kotlin.processing.visitor

import com.fasterxml.jackson.annotation.JsonClassDescription
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.beans.BeanMethod
import io.micronaut.core.beans.BeanProperty
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.convert.TypeConverter
import io.micronaut.core.reflect.InstantiationUtils
import io.micronaut.core.reflect.exception.InstantiationException
import io.micronaut.core.type.Argument
import io.micronaut.core.type.GenericPlaceholder
import io.micronaut.inject.ExecutableMethod
import io.micronaut.kotlin.processing.elementapi.SomeEnum
import io.micronaut.kotlin.processing.elementapi.TestClass
import spock.lang.Issue

import javax.persistence.Column
import javax.persistence.Entity
import javax.persistence.Id
import javax.persistence.Version
import jakarta.validation.Constraint
import jakarta.validation.constraints.Min
import jakarta.validation.constraints.NotBlank
import jakarta.validation.constraints.Size
import java.lang.reflect.Field

class BeanIntrospectionSpec extends AbstractKotlinCompilerSpec {

    void "test basic introspection"() {
        when:
        def introspection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected
class Test
""")

        then:
        noExceptionThrown()
        introspection != null
        introspection.instantiate().class.name == "test.Test"
    }

    void "test enum introspection with a creator"() {
        given:
        def introspection = buildBeanIntrospection("test.StateEnum", """
package test

import com.fasterxml.jackson.annotation.JsonCreator
import io.micronaut.core.annotation.Introspected

@Introspected
enum class StateEnum (
    val value: String
) {

    STARTING("starting"),
    RUNNING("running"),
    STOPPED("stopped"),
    DELETED("deleted");

    override fun toString(): String {
        return value
    }

    companion object {

        @JvmField
        val VALUE_MAPPING = entries.associateBy { it.value }

        @JsonCreator
        @JvmStatic
        fun fromValue(value: String): StateEnum {
            require(VALUE_MAPPING.containsKey(value)) { "Unexpected value " + value }
            return VALUE_MAPPING[value]!!
        }
    }
}
""")


        when:
            def instance = introspection.instantiate("starting")
        then:
            instance.class.name == "test.StateEnum"
        when:
            introspection.instantiate("STARTING")
        then:
            def e = thrown(Exception)
            e.message == 'Unexpected value STARTING'
    }

    void "test default introspection"() {
        when:
            def introspection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected
data class Test(val firstName: String = "Denis",
               val lastName: String,
               val job: String? = "IT",
               val age: Int)
""")

        then:
            noExceptionThrown()
            introspection != null
            introspection.getConstructorArguments()[0].isDeclaredNullable()
            introspection.getConstructorArguments()[0].isNullable()
            !introspection.getConstructorArguments()[1].isDeclaredNullable()
            introspection.getConstructorArguments()[2].isDeclaredNullable()
            !introspection.getConstructorArguments()[3].isDeclaredNullable()
            !introspection.getProperty("firstName").get().isNullable()
            !introspection.getProperty("lastName").get().isNullable()
            introspection.getProperty("job").get().isNullable()
            !introspection.getProperty("age").get().isNullable()
    }

    void "test default introspection instantiate failure"() {
        when:
            def introspection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected
data class Test(val firstName: String = "Denis",
               val lastName: String,
               val job: String? = "IT",
               val age: Int)
""")
            introspection.instantiate()
        then:
            def e = thrown(Exception)
            e.message == "No default constructor exists"
    }

    void "test default introspection instantiate no failure"() {
        when:
            def introspection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected
data class Test(val firstName: String = "Denis",
               val lastName: String = "Stepanov",
               val job: String? = "IT",
               val age: Int = 99)
""")
            def bean = introspection.instantiate()
        then:
            bean.getFirstName() == "Denis"
    }

    void "test data class introspection"() {
        when:
        def introspection = buildBeanIntrospection("test.ContactEntity", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected
data class ContactEntity(var id: Long? = null, val firstName: String, val lastName: String)
""")


        then:
        noExceptionThrown()
        introspection != null
        introspection.beanProperties.size() == 3
    }

    void "test non-null and null introspection"() {
        when:
        def introspection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected
data class Test(
    val name: String,
    val description: String? = null)
""")

        then:
        noExceptionThrown()
        introspection != null

        introspection.constructorArguments.size() == 2
        introspection.constructorArguments[0].isNonNull()
        introspection.constructorArguments[1].isNullable()
    }

    void 'test inner annotation'() {
        when:
        def introspection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@MyAnn
class Test

@Introspected
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS)
@MyAnn.InnerAnn
annotation class MyAnn {
    @Retention(AnnotationRetention.RUNTIME)
    @Target(AnnotationTarget.CLASS)
    annotation class InnerAnn
}
""")

        then:
        noExceptionThrown()
        introspection != null
        introspection.instantiate().class.name == "test.Test"
        introspection.hasAnnotation('test.MyAnn')
        introspection.hasStereotype('test.MyAnn$InnerAnn')

    }

    void "test generics in arrays don't stack overflow"() {
        given:
        def introspection = buildBeanIntrospection('arraygenerics.Test', '''
package arraygenerics

import io.micronaut.core.annotation.Introspected
import io.micronaut.context.annotation.Executable

@Introspected
class Test<T : CharSequence> {

    lateinit var array: Array<T>
    lateinit var starArray: Array<*>
    lateinit var stringArray: Array<String>

    @Executable
    fun myMethod(): Array<T> = array
}
''')
        expect:
        introspection.beanProperties.size() == 3
        introspection.getRequiredProperty("array", CharSequence[].class).type == CharSequence[].class
        introspection.getRequiredProperty("starArray", Object[].class).type == Object[].class
        introspection.getRequiredProperty("stringArray", String[].class).type == String[].class
        introspection.beanMethods.first().returnType.type == CharSequence[].class
    }

    void 'test favor method access'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess

import io.micronaut.core.annotation.*

@Introspected(accessKind=[Introspected.AccessKind.METHOD, Introspected.AccessKind.FIELD])
class Test {
    var one: String? = null
        private set
        get() {
            invoked = true
            return field
        }
    var invoked = false
}
''')

        when:
        def properties = introspection.getBeanProperties()
        def instance = introspection.instantiate()

        then:
        properties.size() == 2

        when:
        def one = introspection.getRequiredProperty("one", String)
        instance.one = 'test'


        then:
        one.get(instance) == 'test'
        instance.invoked
    }

    void 'test favor field access'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess

import io.micronaut.core.annotation.*


@Introspected(accessKind = [Introspected.AccessKind.FIELD, Introspected.AccessKind.METHOD])
class Test {
    var one: String? = null
        private set
        get() {
            invoked = true
            return field
        }
    var invoked = false
}
''')
        when:
        def properties = introspection.getBeanProperties()
        def instance = introspection.instantiate()

        then:
        properties.size() == 2

        when:
        def one = introspection.getRequiredProperty("one", String)
        instance.one = 'test'

        then:
        one.get(instance) == 'test'
        instance.invoked // fields are always private in kotlin so the method will always be referenced
    }

    void 'test field access only'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess

import io.micronaut.core.annotation.*

@Introspected(accessKind=[Introspected.AccessKind.FIELD])
open class Test(val two: Integer?) {  // read-only
    var one: String? = null // read/write
    internal var three: String? = null // package protected
    protected var four: String? = null // not included since protected
    private var five: String? = null // not included since private
}
''')
        when:
        def properties = introspection.getBeanProperties()

        then: 'all fields are private in Kotlin'
        properties.isEmpty()
    }

    void 'test bean constructor'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('beanctor.Test','''\
package beanctor

import java.net.URL

@io.micronaut.core.annotation.Introspected
class Test @com.fasterxml.jackson.annotation.JsonCreator constructor(private val another: String)
''')


        when:
        def constructor = introspection.getConstructor()
        def newInstance = constructor.instantiate("test")

        then:
        newInstance != null
        newInstance.another == "test"
        !introspection.getAnnotationMetadata().hasDeclaredAnnotation(com.fasterxml.jackson.annotation.JsonCreator)
        constructor.getAnnotationMetadata().hasDeclaredAnnotation(com.fasterxml.jackson.annotation.JsonCreator)
        !constructor.getAnnotationMetadata().hasDeclaredAnnotation(Introspected)
        !constructor.getAnnotationMetadata().hasAnnotation(Introspected)
        !constructor.getAnnotationMetadata().hasStereotype(Introspected)
        constructor.arguments.length == 1
        constructor.arguments[0].type == String
    }

    void "test generate bean method for introspected class"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.MethodTest', '''
package test

import io.micronaut.core.annotation.Introspected
import io.micronaut.context.annotation.Executable

@Introspected
class MethodTest : SuperType(), SomeInt {

    fun nonAnnotated() = true

    @Executable
    override fun invokeMe(str: String): String {
        return str
    }

    @Executable
    fun invokePrim(i: Int): Int {
        return i
    }
}

open class SuperType {

    @Executable
    fun superMethod(str: String): String {
        return str
    }

    @Executable
    open fun invokeMe(str: String): String {
        return str
    }
}

interface SomeInt {

    @Executable
    fun ok() = true

    fun getName() = "ok"
}
''')
        when:
        def properties = introspection.getBeanProperties()
        Collection<BeanMethod> beanMethods = introspection.getBeanMethods()

        then:
        properties.size() == 1
        beanMethods*.name as Set == ['invokeMe', 'invokePrim', 'superMethod', 'ok'] as Set
        beanMethods.every({it.annotationMetadata.hasAnnotation(Executable)})
        beanMethods.every { it.declaringBean == introspection}

        when:

        def invokeMe = beanMethods.find { it.name == 'invokeMe' }
        def invokePrim = beanMethods.find { it.name == 'invokePrim' }
        def itfeMethod = beanMethods.find { it.name == 'ok' }
        def bean = introspection.instantiate()

        then:
        invokeMe instanceof ExecutableMethod
        invokeMe.invoke(bean, "test") == 'test'
        invokePrim.invoke(bean, 10) == 10
        itfeMethod.invoke(bean) == true
    }

    void "test custom with prefix"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('customwith.CopyMe', '''\
package customwith

import java.net.URL
import java.util.Locale

@io.micronaut.core.annotation.Introspected(withPrefix = "alter")
class CopyMe(val another: String) {

    fun alterAnother(another: String): CopyMe {
        return if (another == this.another) {
            this
        } else {
            CopyMe(another.uppercase(Locale.getDefault()))
        }
    }
}
''')
        when:
        def another = introspection.getRequiredProperty("another", String)
        def newInstance = introspection.instantiate("test")

        then:
        newInstance.another == "test"

        when:"An explicit with method is used"
        def result = another.withValue(newInstance, "changed")

        then:"It was invoked"
        !result.is(newInstance)
        result.another == 'CHANGED'
    }

    void "test copy constructor via mutate method"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.CopyMe','''\
package test

import java.net.URL
import java.util.Locale

@io.micronaut.core.annotation.Introspected
class CopyMe(val name: String,
             val another: String) {

    var url: URL? = null

    fun withAnother(a: String): CopyMe {
        return if (this.another == a) {
            this
        } else {
            CopyMe(this.name, a.uppercase(Locale.getDefault()))
        }
    }
}
''')
        when:
        def copyMe = introspection.instantiate("Test", "Another")
        def expectUrl = new URL("http://test.com")
        copyMe.url = expectUrl

        then:
        copyMe.name == 'Test'
        copyMe.another == "Another"
        copyMe.url == expectUrl


        when:
        def property = introspection.getRequiredProperty("name", String)
        def another = introspection.getRequiredProperty("another", String)
        def newInstance = property.withValue(copyMe, "Changed")

        then:
        !newInstance.is(copyMe)
        newInstance.name == 'Changed'
        newInstance.url == expectUrl
        newInstance.another == "Another"

        when:"the instance is changed with the same value"
        def result = property.withValue(newInstance, "Changed")

        then:"The existing instance is returned"
        newInstance.is(result)

        when:"An explicit with method is used"
        result = another.withValue(newInstance, "changed")

        then:"It was invoked"
        !result.is(newInstance)
        result.another == 'CHANGED'
    }

    void "test secondary constructor for data classes"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test

@io.micronaut.core.annotation.Introspected
data class Foo(val x: Int, val y: Int) {

    constructor(x: Int) : this(x, 20)

    constructor() : this(20, 20)
}
''')
        when:
        def obj = introspection.instantiate(5, 10)

        then:
        obj.getX() == 5
        obj.getY() == 10
    }

    void "test secondary constructor with @Creator for data classes"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test

import io.micronaut.core.annotation.Creator

@io.micronaut.core.annotation.Introspected
data class Foo(val x: Int, val y: Int) {

    @Creator
    constructor(x: Int) : this(x, 20)

    constructor() : this(20, 20)
}
''')
        when:
        def obj = introspection.instantiate(5)

        then:
        obj.getX() == 5
        obj.getY() == 20
    }

    void "test annotations on generic type arguments for data classes"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test

import io.micronaut.core.annotation.Creator
import jakarta.validation.constraints.Min

@io.micronaut.core.annotation.Introspected
data class Foo(val value: List<@Min(10) Long>)
''')

        when:
        BeanProperty<?, ?> property = introspection.getRequiredProperty("value", List)
        def genericTypeArg = property.asArgument().getTypeParameters()[0]

        then:
        property != null
        genericTypeArg.type == Long
        genericTypeArg.annotationMetadata.hasStereotype(Constraint)
        genericTypeArg.annotationMetadata.hasAnnotation(Min)
        genericTypeArg.annotationMetadata.intValue(Min).getAsInt() == 10
    }

    void 'test annotations on generic type arguments'() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test

import jakarta.validation.constraints.Min
import kotlin.annotation.AnnotationTarget.*

@io.micronaut.core.annotation.Introspected
class Foo {
    var value : List<@Min(10) @SomeAnn Long>? = null
}

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(FUNCTION, PROPERTY, ANNOTATION_CLASS, CONSTRUCTOR, VALUE_PARAMETER, TYPE)
annotation class SomeAnn
''')
        when:
        BeanProperty<?, ?> property = introspection.getRequiredProperty("value", List)
        def genericTypeArg = property.asArgument().getTypeParameters()[0]

        then:
        property != null
        genericTypeArg.annotationMetadata.hasAnnotation(Min)
        genericTypeArg.annotationMetadata.intValue(Min).getAsInt() == 10
    }

    void "test bean introspection on a data class"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test

@io.micronaut.core.annotation.Introspected
data class Foo(@jakarta.validation.constraints.NotBlank val name: String, val age: Int)
''')
        when:
        def test = introspection.instantiate("test", 20)
        def property = introspection.getRequiredProperty("name", String)
        def argument = introspection.getConstructorArguments()[0]

        then:
        argument.name == 'name'
        argument.getAnnotationMetadata().hasStereotype(Constraint)
        argument.getAnnotationMetadata().hasAnnotation(NotBlank)
        test.name == 'test'
        test.getName() == 'test'
        introspection.propertyNames.length == 2
        introspection.propertyNames == ['name', 'age'] as String[]
        property.hasAnnotation(NotBlank)
        property.isReadOnly()
        property.hasSetterOrConstructorArgument()
        property.name == 'name'
        property.get(test) == 'test'

        when:"a mutation is applied"
        def newTest = property.withValue(test, "Changed")

        then:"a new instance is returned"
        !newTest.is(test)
        newTest.getName() == 'Changed'
        newTest.getAge() == 20
    }

    void "test create bean introspection for external inner class"() {
        given:
        ClassLoader classLoader = buildClassLoader('test.Foo', '''
package test

import io.micronaut.core.annotation.*
import io.micronaut.kotlin.processing.elementapi.OuterBean

@Introspected(classes=[OuterBean.InnerBean::class])
class Test
''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$io_micronaut_kotlin_processing_elementapi_OuterBean$InnerBean$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()
        String className = "io.micronaut.kotlin.processing.elementapi.OuterBean\$InnerBean"
        def innerType = classLoader.loadClass(className)

        then:"The reference is valid"
        reference != null
        reference.getBeanType().name == className

        when:
        BeanIntrospection i = reference.load()

        then:
        i.propertyNames.length == 1
        i.propertyNames[0] == 'name'

        when:
        innerType.newInstance()

        then:
        noExceptionThrown()

        when:
        def o = i.instantiate()

        then:
        noExceptionThrown()
        o.class.name == className
        innerType.isInstance(o)
    }

    void "test create bean introspection for external inner interface"() {
        given:
        ClassLoader classLoader = buildClassLoader('test.Foo', '''
package test

import io.micronaut.core.annotation.*
import io.micronaut.kotlin.processing.elementapi.OuterBean

@Introspected(classes=[OuterBean.InnerInterface::class])
class Test
''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$io_micronaut_kotlin_processing_elementapi_OuterBean$InnerInterface$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()
        String className = "io.micronaut.kotlin.processing.elementapi.OuterBean\$InnerInterface"

        then:"The reference is valid"
        reference != null
        reference.getBeanType().name == className

        when:
        BeanIntrospection i = reference.load()

        then:
        i.propertyNames.length == 1
        i.propertyNames[0] == 'name'

        when:
        def o = i.instantiate()

        then:
        def e = thrown(InstantiationException)
        e.message == 'No default constructor exists'
    }

    void "test bean introspection with property of generic interface"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test

@io.micronaut.core.annotation.Introspected
class Foo : GenBase<String> {
    override fun getName() = "test"
}

interface GenBase<T> {
    fun getName(): T
}
''')
        when:
        def test = introspection.instantiate()
        def property = introspection.getRequiredProperty("name", String)

        then:
        introspection.beanProperties.first().type == String
        property.get(test) == 'test'
        !property.hasSetterOrConstructorArgument()

        when:
        property.withValue(test, 'try change')

        then:
        def e = thrown(UnsupportedOperationException)
        e.message =='Cannot mutate property [name] that is not mutable via a setter method, field or constructor argument for type: test.Foo'
    }

    void "test bean introspection with property of generic superclass"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test

@io.micronaut.core.annotation.Introspected
class Foo: GenBase<String>() {
    override fun getName() = "test"
}

abstract class GenBase<T> {
    abstract fun getName(): T

    fun getOther(): T {
        return "other" as T
    }
}
''')
        when:
        def test = introspection.instantiate()

        def beanProperties = introspection.beanProperties.toList()
        then:
        beanProperties.size() == 2
        beanProperties[0].type == String
        beanProperties[1].type == String
        introspection.getRequiredProperty("name", String)
                .get(test) == 'test'
        introspection.getRequiredProperty("other", String)
                .get(test) == 'other'
    }

    void "test bean introspection with argument of generic interface"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test

@io.micronaut.core.annotation.Introspected
class Foo: GenBase<Long?> {
    override var value: Long? = null
}

interface GenBase<T> {
    var value: T
}

''')
        when:
        def test = introspection.instantiate()
        BeanProperty bp = introspection.getRequiredProperty("value", Long)
        bp.set(test, Long.valueOf(5))

        then:
        bp.get(test) == Long.valueOf(5)

        when:
        def returnedBean = bp.withValue(test, Long.valueOf(10))

        then:
        returnedBean.is(test)
        bp.get(test) == Long.valueOf(10)
    }

    void "test bean introspection with property with static creator method on interface"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test

import io.micronaut.core.annotation.Creator

@io.micronaut.core.annotation.Introspected
fun interface Foo {

    fun getName(): String

    companion object {
        @Creator
        fun create(name: String): Foo {
            return Foo { name }
        }
    }
}

''')
        when:
        def test = introspection.instantiate("test")

        then:
        introspection.constructorArguments.length == 1
        introspection.getRequiredProperty("name", String)
                .get(test) == 'test'
    }

    void "test bean introspection with property with static creator method on interface with generic type arguments"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Foo', '''
package test

import io.micronaut.core.annotation.Creator

@io.micronaut.core.annotation.Introspected
fun interface Foo<T> {

    fun getName(): String

    companion object {
        @Creator
        fun <T1> create(name: String): Foo<T1> {
            return Foo { name }
        }
    }
}

''')
        when:
        def test = introspection.instantiate("test")

        then:
        introspection.constructorArguments.length == 1
        introspection.getRequiredProperty("name", String)
                .get(test) == 'test'
    }

    void "test bean introspection with property from default interface method"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

@io.micronaut.core.annotation.Introspected
class Test: Foo

interface Foo {
    fun getBar(): String = "good"
}

''')
        when:
        def test = introspection.instantiate()

        then:
        introspection.getRequiredProperty("bar", String)
                .get(test) == 'good'
    }

    void "test generate bean introspection for interface"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test','''\
package test

@io.micronaut.core.annotation.Introspected
interface Test : io.micronaut.core.naming.Named {
    fun setName(name: String)
}
''')
        then:
        introspection != null
        introspection.propertyNames.length == 1
        introspection.propertyNames[0] == 'name'

        when:
        introspection.instantiate()

        then:
        def e = thrown(InstantiationException)
        e.message == 'No default constructor exists'

        when:
        def property = introspection.getRequiredProperty("name", String)
        String setNameValue
        def named = [getName:{-> "test"}, setName:{String n -> setNameValue= n }].asType(introspection.beanType)

        property.set(named, "test")

        then:
        property.get(named) == 'test'
        setNameValue == 'test'
    }

    void "test build introspection"() {
        given:
        def classLoader = buildClassLoader('test.Address', '''
package test

import jakarta.validation.constraints.*

@io.micronaut.core.annotation.Introspected
class Address {

    @NotBlank(groups = [GroupOne::class])
    @NotBlank(groups = [GroupThree::class], message = "different message")
    @Size(min = 5, max = 20, groups = [GroupTwo::class])
    private var street: String? = null
}

interface GroupOne
interface GroupTwo
interface GroupThree
''')
        def clazz = classLoader.loadClass('test.$Address$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        expect:
        reference != null
        reference.load()
    }

    void "test primary constructor is preferred"() {
        given:
        def classLoader = buildClassLoader('test.Book', '''
package test

@io.micronaut.core.annotation.Introspected
class Book(val title: String) {

    private var author: String? = null

    constructor(title: String, author: String) : this(title) {
        this.author = author
    }
}
''')
        Class clazz = classLoader.loadClass('test.$Book$Introspection')
        BeanIntrospectionReference reference = (BeanIntrospectionReference) clazz.newInstance()

        expect:
        reference != null

        when:
        BeanIntrospection introspection = reference.load()

        then:
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 1

        when:
        introspection.instantiate()

        then:
        thrown(InstantiationException)

        when: "update introspectionMap"
        BeanIntrospector introspector = BeanIntrospector.SHARED
        Field introspectionMapField = introspector.getClass().getDeclaredField("introspectionMap")
        introspectionMapField.setAccessible(true)
        introspectionMapField.set(introspector, new HashMap<String, BeanIntrospectionReference<Object>>())
            Map map = (Map) introspectionMapField.get(introspector)
        map.put(reference.getName(), reference)

        and:
        def book = InstantiationUtils.tryInstantiate(introspection.getBeanType(), ["title": "The Stand"], ConversionContext.of(Argument.of(introspection.beanType)))
        def prop = introspection.getRequiredProperty("title", String)

        then:
        prop.get(book.get()) == "The Stand"

        cleanup:
        introspectionMapField.set(introspector, null)
    }

    void "test multiple constructors with primary constructor marked as @Creator"() {
        given:
        def classLoader = buildClassLoader('test.Book', '''
package test

import io.micronaut.core.annotation.Creator

@io.micronaut.core.annotation.Introspected
class Book {

    private var author: String? = null
    val title: String

    constructor(title: String, author: String) : this(title) {
        this.author = author
    }

    @Creator
    constructor(title: String) {
        this.title = title
    }
}
''')
        Class clazz = classLoader.loadClass('test.$Book$Introspection')
        BeanIntrospectionReference reference = (BeanIntrospectionReference) clazz.newInstance()

        expect:
        reference != null

        when:
        BeanIntrospection introspection = reference.load()

        then:
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 1

        when:
        introspection.instantiate()

        then:
        thrown(InstantiationException)

        when: "update introspectionMap"
        BeanIntrospector introspector = BeanIntrospector.SHARED
        Field introspectionMapField = introspector.getClass().getDeclaredField("introspectionMap")
        introspectionMapField.setAccessible(true)
        introspectionMapField.set(introspector, new HashMap<String, BeanIntrospectionReference<Object>>())
            Map map = (Map) introspectionMapField.get(introspector)
        map.put(reference.getName(), reference)

        and:
        def book = InstantiationUtils.tryInstantiate(introspection.getBeanType(), ["title": "The Stand"], ConversionContext.of(Argument.of(introspection.beanType)))
        def prop = introspection.getRequiredProperty("title", String)

        then:
        prop.get(book.get()) == "The Stand"

        cleanup:
        introspectionMapField.set(introspector, null)
    }

    void "test default constructor"() {
        given:
        def classLoader = buildClassLoader('test.Book', '''
package test

@io.micronaut.core.annotation.Introspected
class Book {
    var title: String? = null
}
''')
        Class clazz = classLoader.loadClass('test.$Book$Introspection')
        BeanIntrospectionReference reference = (BeanIntrospectionReference) clazz.newInstance()

        expect:
        reference != null

        when:
        BeanIntrospection introspection = reference.load()

        then:
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 1

        when: "update introspectionMap"
        BeanIntrospector introspector = BeanIntrospector.SHARED
        Field introspectionMapField = introspector.getClass().getDeclaredField("introspectionMap")
        introspectionMapField.setAccessible(true)
        introspectionMapField.set(introspector, new HashMap<String, BeanIntrospectionReference<Object>>())
            Map map = (Map) introspectionMapField.get(introspector)
        map.put(reference.getName(), reference)

        and:
        def book = InstantiationUtils.tryInstantiate(introspection.getBeanType(), ["title": "The Stand"], ConversionContext.of(Argument.of(introspection.beanType)))
        def prop = introspection.getRequiredProperty("title", String)

        then:
        prop.get(book.get()) == null

        cleanup:
        introspectionMapField.set(introspector, null)
    }

    void "test multiple constructors with @JsonCreator"() {
        given:
        def classLoader = buildClassLoader('test.Test', '''
package test

import io.micronaut.core.annotation.*
import jakarta.validation.constraints.*
import java.util.*
import com.fasterxml.jackson.annotation.*

@Introspected
class Test {
    private var name: String? = null
    var age: Int = 0

    @JsonCreator
    constructor(@JsonProperty("name") name: String) {
        this.name = name
    }

    constructor(age: Int) {
        this.age = age
    }

    fun getName(): String? = name

    fun setName(n: String): Test {
        this.name = n
        return this
    }
}

''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getAnnotationMetadata().hasAnnotation(Introspected)
        reference.isPresent()
        reference.beanType.name == 'test.Test'

        when:"the introspection is loaded"
        BeanIntrospection introspection = reference.load()

        then:"The introspection is valid"
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 2

        when:
        def test = introspection.instantiate("Fred")
        def prop = introspection.getRequiredProperty("name", String)

        then:
        prop.get(test) == 'Fred'
    }

    void "test write bean introspection with builder style properties"() {
        given:
        def classLoader = buildClassLoader('test.Test', '''
package test

import io.micronaut.core.annotation.*
import jakarta.validation.constraints.*
import java.util.*

@Introspected
class Test {
    private var name: String? = null

    fun getName(): String? = name
    fun setName(n: String): Test {
        this.name = n
        return this
    }
}

''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getAnnotationMetadata().hasAnnotation(Introspected)
        reference.isPresent()
        reference.beanType.name == 'test.Test'

        when:"the introspection is loaded"
        BeanIntrospection introspection = reference.load()

        then:"The introspection is valid"
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 1

        when:
        def test = introspection.instantiate()
        def prop = introspection.getRequiredProperty("name", String)
        prop.set(test, "Foo")

        then:
        prop.get(test) == 'Foo'
    }

    void "test write bean introspection with inner classes"() {
        given:
        def classLoader = buildClassLoader('test.Test', '''
package test

import io.micronaut.core.annotation.*
import jakarta.validation.constraints.*
import java.util.*

@Introspected
class Test {

    private var status: Status? = null

    fun setStatus(status: Status) {
        this.status = status
    }

    fun getStatus(): Status? {
        return this.status
    }

    enum class Status {
        UP, DOWN
    }
}

''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getAnnotationMetadata().hasAnnotation(Introspected)
        reference.isPresent()
        reference.beanType.name == 'test.Test'

        when:"the introspection is loaded"
        BeanIntrospection introspection = reference.load()

        then:"The introspection is valid"
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.propertyNames.length == 1
    }

    void "test bean introspection with constructor"() {
        given:
        def classLoader = buildClassLoader('test.Test', '''
package test

import jakarta.validation.constraints.*
import javax.persistence.*

@Entity
class Test(
    @Column(name="test_name") var name: String,
    @Size(max=100) var age: Int,
    primitiveArray: Array<Int>) {

    @Id
    @GeneratedValue
    var id: Long? = null

    @Version
    var version: Long? = null

    private var primitiveArray: Array<Int>? = null

    private var v: Long? = null

    @Version
    fun getAnotherVersion(): Long? {
        return v
    }

    fun setAnotherVersion(v: Long) {
        this.v = v
    }
}
''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null

        when:"The introspection is loaded"
        BeanIntrospection bi = reference.load()

        then:"it is correct"
        bi.getConstructorArguments().length == 3
        bi.getConstructorArguments()[0].name == 'name'
        bi.getConstructorArguments()[0].type == String
        bi.getConstructorArguments()[1].name == 'age'
        bi.getConstructorArguments()[1].getAnnotationMetadata().hasAnnotation(Size)
        bi.getIndexedProperties(Id).size() == 1
        bi.getIndexedProperty(Id).isPresent()
        !bi.getIndexedProperty(Column, null).isPresent()
        bi.getIndexedProperty(Column, "test_name").isPresent()
        bi.getIndexedProperty(Column, "test_name").get().name == 'name'
        bi.getProperty("version").get().hasAnnotation(Version)
        bi.getProperty("anotherVersion").get().hasAnnotation(Version)
        // should not inherit metadata from class
        !bi.getProperty("anotherVersion").get().hasAnnotation(Entity)

        when:
        BeanProperty idProp = bi.getIndexedProperties(Id).first()

        then:
        idProp.name == 'id'
        !idProp.hasAnnotation(Entity)
        !idProp.hasStereotype(Entity)


        when:
        def object = bi.instantiate("test", 10, [20] as Integer[])

        then:
        object.name == 'test'
        object.age == 10
    }

    void "test write bean introspection data for entity"() {
        given:
        def classLoader = buildClassLoader('test.Test', '''
package test

import jakarta.validation.constraints.*
import javax.persistence.*

@Entity
class Test {

    @Id
    @GeneratedValue
    var id: Long? = null

    @Version
    var version: Long? = null

    var name: String? = null

    @Size(max=100)
    var age: Int? = null
}
''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null

        when:"The introspection is loaded"
        BeanIntrospection bi = reference.load()

        then:"it is correct"
        bi.instantiate()
        bi.getIndexedProperties(Id).size() == 1
        bi.getIndexedProperties(Id).first().name == 'id'
    }

    void "test write bean introspection data for class in another package"() {
        given:
        def classLoader = buildClassLoader('test.Test', '''
package test

import io.micronaut.core.annotation.Introspected
import io.micronaut.kotlin.processing.elementapi.OtherTestBean

@Introspected(classes=[OtherTestBean::class])
class Test
''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$io_micronaut_kotlin_processing_elementapi_OtherTestBean$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getBeanType().name == "io.micronaut.kotlin.processing.elementapi.OtherTestBean"

        when:
        def introspection = reference.load()

        then: "the introspection is under the reference package"
        noExceptionThrown()
        introspection.class.name == "test.\$io_micronaut_kotlin_processing_elementapi_OtherTestBean\$Introspection"
        introspection.instantiate()
    }

    void "test write bean introspection data for class already introspected"() {
        given:
        def classLoader = buildClassLoader('test.Test', '''
package test

import io.micronaut.core.annotation.Introspected
import io.micronaut.kotlin.processing.elementapi.TestBean

@Introspected(classes=[TestBean::class])
class Test
''')

        when:"the reference is loaded"
        classLoader.loadClass('test.$Test$IntrospectionRef0')

        then:"The reference is not written"
        thrown(ClassNotFoundException)
    }

    void "test write bean introspection data for package with sources"() {
        given:
        def classLoader = buildClassLoader('test.Test', '''
package test

import io.micronaut.core.annotation.*
import io.micronaut.kotlin.processing.elementapi.MarkerAnnotation

@Introspected(packages = ["io.micronaut.kotlin.processing.elementapi"], includedAnnotations = [MarkerAnnotation::class])
class Test
''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$io_micronaut_kotlin_processing_elementapi_OtherTestBean$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is generated"
        reference != null
    }

    void "test write bean introspection data for package with compiled classes"() {
        given:
        def classLoader = buildClassLoader('test.Test', '''
package test

import io.micronaut.core.annotation.*

@Introspected(packages=["io.micronaut.inject.beans.visitor"], includedAnnotations=[Internal::class])
class Test
''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$io_micronaut_inject_beans_visitor_MappedSuperClassIntrospectionMapper$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getBeanType() == io.micronaut.inject.beans.visitor.MappedSuperClassIntrospectionMapper
    }

    void "test write bean introspection data"() {
        given:
        def classLoader = buildClassLoader('test.Test', '''
package test

import io.micronaut.core.annotation.Introspected
import io.micronaut.core.convert.TypeConverter
import jakarta.validation.constraints.Size

@Introspected
class Test: ParentBean() {
    val readOnly: String = "test"
    var name: String? = null

    @Size(max=100)
    var age: Int = 0

    var list: List<Number>? = null
    var stringArray: Array<String>? = null
    var primitiveArray: Array<Int>? = null
    var flag: Boolean = false
    val genericsTest: TypeConverter<String, Collection<*>>? = null
    val genericsArrayTest: TypeConverter<String, Array<Any>>? = null
}

open class ParentBean {
    var listOfBytes: List<ByteArray>? = null
}
''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()

        then:"The reference is valid"
        reference != null
        reference.getAnnotationMetadata().hasAnnotation(Introspected)
        reference.isPresent()
        reference.beanType.name == 'test.Test'

        when:"the introspection is loaded"
        BeanIntrospection introspection = reference.load()

        then:"The introspection is valid"
        introspection != null
        introspection.hasAnnotation(Introspected)
        introspection.instantiate().getClass().name == 'test.Test'
        introspection.getBeanProperties().size() == 10
        introspection.getProperty("name").isPresent()
        introspection.getProperty("name", String).isPresent()
        !introspection.getProperty("name", Integer).isPresent()

        when:
        BeanProperty nameProp = introspection.getProperty("name", String).get()
        BeanProperty boolProp = introspection.getProperty("flag", boolean.class).get()
        BeanProperty ageProp = introspection.getProperty("age", int.class).get()
        BeanProperty listProp = introspection.getProperty("list").get()
        BeanProperty primitiveArrayProp = introspection.getProperty("primitiveArray").get()
        BeanProperty stringArrayProp = introspection.getProperty("stringArray").get()
        BeanProperty listOfBytes = introspection.getProperty("listOfBytes").get()
        BeanProperty genericsTest = introspection.getProperty("genericsTest").get()
        BeanProperty genericsArrayTest = introspection.getProperty("genericsArrayTest").get()
        def readOnlyProp = introspection.getProperty("readOnly", String).get()
        def instance = introspection.instantiate()

        then:
        readOnlyProp.isReadOnly()
        nameProp != null
        !nameProp.isReadOnly()
        !nameProp.isWriteOnly()
        nameProp.isReadWrite()
        boolProp.get(instance) == false
        nameProp.get(instance) == null
        ageProp.get(instance) == 0
        genericsTest != null
        genericsTest.type == TypeConverter
        genericsTest.asArgument().typeParameters.size() == 2
        genericsTest.asArgument().typeParameters[0].type == String
        genericsTest.asArgument().typeParameters[1].type == Collection
        genericsTest.asArgument().typeParameters[1].typeParameters.length == 1
        genericsArrayTest.type == TypeConverter
        genericsArrayTest.asArgument().typeParameters.size() == 2
        genericsArrayTest.asArgument().typeParameters[0].type == String
        genericsArrayTest.asArgument().typeParameters[1].type == Object[].class
        stringArrayProp.get(instance) == null
        stringArrayProp.type == String[]
        primitiveArrayProp.get(instance) == null
        ageProp.hasAnnotation(Size)
        listOfBytes.asArgument().getFirstTypeVariable().get().type == byte[].class
        listProp.asArgument().getFirstTypeVariable().isPresent()
        listProp.asArgument().getFirstTypeVariable().get().type == Number

        when:
        boolProp.set(instance, true)
        nameProp.set(instance, "foo")
        ageProp.set(instance, 10)
        primitiveArrayProp.set(instance, [10] as Integer[])
        stringArrayProp.set(instance, ['foo'] as String[])


        then:
        boolProp.get(instance) == true
        nameProp.get(instance) == 'foo'
        ageProp.get(instance) == 10
        stringArrayProp.get(instance) == ['foo'] as String[]
        primitiveArrayProp.get(instance) == [10] as Integer[]

        when:
        ageProp.convertAndSet(instance, "20")
        nameProp.set(instance, "100" )

        then:
        ageProp.get(instance) == 20
        nameProp.get(instance, Integer, null) == 100

        when:
        introspection.instantiate("blah") // illegal argument

        then:
        def e = thrown(InstantiationException)
        e.message == 'Argument count [1] doesn\'t match required argument count: 0'
    }

    void "test constructor argument generics"() {
        given:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.*

@Introspected
class Test(var properties: Map<String, String>)
''')
        expect:
        introspection.constructorArguments[0].getTypeVariable("K").get().getType() == String
        introspection.constructorArguments[0].getTypeVariable("V").get().getType() == String
    }

    void "test static creator"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.*

@Introspected
class Test private constructor(val name: String) {

    companion object {
        @Creator
        fun forName(name: String): Test {
            return Test(name)
        }
    }
}
''')

        expect:
        introspection != null

        when:
        def instance = introspection.instantiate("Sally")

        then:
        introspection.getRequiredProperty("name", String).get(instance) == "Sally"

        when:
        introspection.instantiate(new Object[0])

        then:
        thrown(InstantiationException)

        when:
        introspection.instantiate()

        then:
        thrown(InstantiationException)
    }

    void "test static creator with no args"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.*

@Introspected
class Test private constructor(val name: String) {

    companion object {
        @Creator
        fun forName(): Test {
            return Test("default")
        }
    }
}
''')
        expect:
        introspection != null

        when:
        def instance = introspection.instantiate("Sally")

        then:
        thrown(InstantiationException)

        when:
        instance = introspection.instantiate(new Object[0])

        then:
        introspection.getRequiredProperty("name", String).get(instance) == "default"

        when:
        instance = introspection.instantiate()

        then:
        introspection.getRequiredProperty("name", String).get(instance) == "default"
    }

    void "test static creator multiple"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.*

@Introspected
class Test private constructor(val name: String) {

    companion object {
        @Creator
        fun forName(): Test {
            return Test("default")
        }

        @Creator
        fun forName(name: String): Test {
            return Test(name)
        }
    }
}
''')

        expect:
        introspection != null

        when:
        def instance = introspection.instantiate("Sally")

        then:
        introspection.getRequiredProperty("name", String).get(instance) == "Sally"

        when:
        instance = introspection.instantiate(new Object[0])

        then:
        introspection.getRequiredProperty("name", String).get(instance) == "default"

        when:
        instance = introspection.instantiate()

        then:
        introspection.getRequiredProperty("name", String).get(instance) == "default"
    }

    void "test introspections are not created for super classes"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.*

@Introspected
class Test: Foo()

open class Foo
''')

        expect:
        introspection != null

        when:
        introspection.getClass().getClassLoader().loadClass("test.\$Foo\$Introspection")

        then:
        thrown(ClassNotFoundException)
    }

    void "test enum bean properties"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.*

@Introspected
enum class Test(val number: Int) {
    A(0), B(1), C(2);
}
''')

        expect:
        introspection != null
        introspection.beanProperties.size() == 1
        introspection.getProperty("number").isPresent()

        when:
        def instance = introspection.instantiate("A")

        then:
        instance.name() == "A"
        introspection.getRequiredProperty("number", int).get(instance) == 0

        when:
        introspection.instantiate()

        then:
        thrown(InstantiationException)

        when:
        introspection.getClass().getClassLoader().loadClass("java.lang.\$Enum\$Introspection")

        then:
        thrown(ClassNotFoundException)
    }

    void "test basic enum bean properties"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.MyEnum', '''
package test

import io.micronaut.core.annotation.*

@Introspected
enum class MyEnum {
    A, B, C;
}
''')

        expect:
            introspection != null
            introspection.beanProperties.size() == 0

        when:
            introspection.instantiate("A")

        then:
            noExceptionThrown()
    }

    void "test instantiating an enum"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.Introspected

@Introspected
enum class Test {
    A, B, C;
}
''')

        expect:
        introspection != null

        when:
        def instance = introspection.instantiate("A")

        then:
        instance.name() == "A"

        when:
        introspection.instantiate()

        then:
        thrown(InstantiationException)
    }

    void "test constructor argument nested generics"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.Introspected
import java.util.List
import java.util.Map

@Introspected
class Test(map: Map<String, List<Action>>)

class Action
''')

        expect:
        introspection != null
        introspection.constructorArguments[0].typeParameters.size() == 2
        introspection.constructorArguments[0].typeParameters[0].typeName == 'java.lang.String'
        introspection.constructorArguments[0].typeParameters[1].typeName == 'java.util.List<test.Action>'
        introspection.constructorArguments[0].typeParameters[1].typeParameters.size() == 1
        introspection.constructorArguments[0].typeParameters[1].typeParameters[0].typeName == 'test.Action'
    }

    void "test primitive multi-dimensional arrays"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.Introspected

@Introspected
class Test {
    var oneDimension: IntArray? = null
    var twoDimensions: Array<IntArray>? = null
    var threeDimensions: Array<Array<IntArray>>? = null
}
''')

        then:
        noExceptionThrown()
        introspection != null

        when:
        def instance = introspection.instantiate()
        def property = introspection.getRequiredProperty("oneDimension", int[].class)
        int[] level1 = [1, 2, 3] as int[]
        property.set(instance, level1)

        then:
        property.get(instance) == level1

        when:
        property = introspection.getRequiredProperty("twoDimensions", int[][].class)
        int[] level2 = [4, 5, 6] as int[]
        int[][] twoDimensions = [level1, level2] as int[][]
        property.set(instance, twoDimensions)

        then:
        property.get(instance) == twoDimensions

        when:
        property = introspection.getRequiredProperty("threeDimensions", int[][][].class)
        int[][][] threeDimensions = [[level1], [level2]] as int[][][]
        property.set(instance, threeDimensions)

        then:
        property.get(instance) == threeDimensions
    }

    void "test class multi-dimensional arrays"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.Introspected

@Introspected
class Test {
    var oneDimension: Array<String>? = null
    var twoDimensions: Array<Array<String>>? = null
    var threeDimensions: Array<Array<Array<String>>>? = null
}
''')

        then:
        noExceptionThrown()
        introspection != null

        when:
        def instance = introspection.instantiate()
        def property = introspection.getRequiredProperty("oneDimension", String[].class)
        String[] level1 = ["1", "2", "3"] as String[]
        property.set(instance, level1)

        then:
        property.get(instance) == level1

        when:
        property = introspection.getRequiredProperty("twoDimensions", String[][].class)
        String[] level2 = ["4", "5", "6"] as String[]
        String[][] twoDimensions = [level1, level2] as String[][]
        property.set(instance, twoDimensions)

        then:
        property.get(instance) == twoDimensions

        when:
        property = introspection.getRequiredProperty("threeDimensions", String[][][].class)
        String[][][] threeDimensions = [[level1], [level2]] as String[][][]
        property.set(instance, threeDimensions)

        then:
        property.get(instance) == threeDimensions
    }

    void "test enum multi-dimensional arrays"() {
        when:
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.Introspected
import io.micronaut.kotlin.processing.elementapi.SomeEnum

@Introspected
class Test {
    var oneDimension: Array<SomeEnum>? = null
    var twoDimensions: Array<Array<SomeEnum>>? = null
    var threeDimensions: Array<Array<Array<SomeEnum>>>? = null
}
''')

        then:
        noExceptionThrown()
        introspection != null

        when:
        def instance = introspection.instantiate()
        def property = introspection.getRequiredProperty("oneDimension", SomeEnum[].class)
        SomeEnum[] level1 = [SomeEnum.A, SomeEnum.B, SomeEnum.A] as SomeEnum[]
        property.set(instance, level1)

        then:
        property.get(instance) == level1

        when:
        property = introspection.getRequiredProperty("twoDimensions", SomeEnum[][].class)
        SomeEnum[] level2 = [SomeEnum.B, SomeEnum.A, SomeEnum.B] as SomeEnum[]
        SomeEnum[][] twoDimensions = [level1, level2] as SomeEnum[][]
        property.set(instance, twoDimensions)

        then:
        property.get(instance) == twoDimensions

        when:
        property = introspection.getRequiredProperty("threeDimensions", SomeEnum[][][].class)
        SomeEnum[][][] threeDimensions = [[level1], [level2]] as SomeEnum[][][]
        property.set(instance, threeDimensions)

        then:
        property.get(instance) == threeDimensions
    }

    void "test superclass methods are read before interface methods"() {
        BeanIntrospection introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.Introspected
import jakarta.validation.constraints.NotNull

interface IEmail {
    fun getEmail(): String?
}

@Introspected
open class SuperClass: IEmail {
    @NotNull
    override fun getEmail(): String? = null
}

@Introspected
class SubClass: SuperClass()

@Introspected
class Test: SuperClass(), IEmail

''')
        expect:
        introspection != null
        introspection.getProperty("email").isPresent()
        introspection.getIndexedProperties(Constraint).size() == 1
    }

    void "test introspection on abstract class"() {
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected
abstract class Test {
    var name: String? = null
    var author: String? = null
}
""")

        expect:
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 2
    }

    void "test targeting abstract class with @Introspected(classes = "() {
        ClassLoader classLoader = buildClassLoader("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected(classes = [io.micronaut.kotlin.processing.elementapi.TestClass::class])
class MyConfig
""")

        when:
        BeanIntrospector beanIntrospector = BeanIntrospector.forClassLoader(classLoader)

        then:
        BeanIntrospection beanIntrospection = beanIntrospector.getIntrospection(TestClass)
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 2
    }

    void "test introspection on abstract class with extra getter"() {
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected
abstract class Test {
    var name: String? = null
    var author: String? = null

    fun getAge(): Int = 0
}
""")

        expect:
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 3
    }

    void "test class loading is not shared between the introspection and the ref"() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected
import java.util.Set

@Introspected(excludedAnnotations = [Deprecated::class]) class Test {
    var authors: Set<Author>? = null
}

@Introspected(excludedAnnotations = [Deprecated::class])
class Author {
    var name: String? = null
}
""")

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
    }

    void "test annotation on setter"() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected

@Introspected
class Test {
    @set:JsonProperty
    var foo: String = "bar"
}
""")

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
        beanIntrospection.getBeanProperties()[0].annotationMetadata.hasAnnotation(JsonProperty)
    }

    void "test annotation on field"() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected

@Introspected
class Test {
    @field:JsonProperty
    var foo: String = "bar"
}
""")

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
        beanIntrospection.getBeanProperties()[0].annotationMetadata.hasAnnotation(JsonProperty)
    }

    void "test field annotation overrides getter and setter"() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected

@Introspected
class Test {
    @field:JsonProperty("field")
    @get:JsonProperty("getter")
    @set:JsonProperty("setter")
    var foo: String? = null
}
""")

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
        beanIntrospection.getBeanProperties()[0].annotationMetadata.getAnnotation(JsonProperty).stringValue().get() == 'field'
    }

    void "test getter annotation overrides setter"() {
        when:
        BeanIntrospection beanIntrospection = buildBeanIntrospection("test.Test", """
package test

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.core.annotation.Introspected

@Introspected
class Test {
    @get:JsonProperty("getter")
    @set:JsonProperty("setter")
    var foo: String? = null
}
""")

        then:
        noExceptionThrown()
        beanIntrospection != null
        beanIntrospection.getBeanProperties().size() == 1
        beanIntrospection.getBeanProperties()[0].annotationMetadata.getAnnotation(JsonProperty).stringValue().get() == 'getter'
    }

    void "test create bean introspection for interface"() {
        given:
        def classLoader = buildClassLoader('itfcetest.MyInterface','''
package itfcetest

import com.fasterxml.jackson.annotation.JsonClassDescription
import io.micronaut.core.annotation.Introspected
import io.micronaut.context.annotation.Executable

@Introspected(classes = [MyInterface::class])
class Test

@JsonClassDescription interface MyInterface {
    fun getName(): String

    @Executable
    fun name(): String = getName()
}

class MyImpl: MyInterface {
    override fun getName(): String = "ok"
}
''')
        when:"the reference is loaded"
        def clazz = classLoader.loadClass('itfcetest.$itfcetest_MyInterface$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()
        BeanIntrospection introspection = reference.load()

        then:
        introspection.getBeanType().isInterface()
        introspection.beanProperties.size() == 1
        introspection.beanMethods.size() == 1
        introspection.hasAnnotation(JsonClassDescription)
    }

    void "test create bean introspection for interface - only methods"() {
        given:
        def classLoader = buildClassLoader('itfcetest.MyInterface','''
package itfcetest

import io.micronaut.core.annotation.Introspected
import io.micronaut.context.annotation.Executable

@Introspected(classes = [MyInterface::class])
class Test

interface MyInterface {
    @Executable
    fun name(): String
}

class MyImpl: MyInterface {
    override fun name(): String = "ok"
}
''')
        when:"the reference is loaded"
        def clazz = classLoader.loadClass('itfcetest.$itfcetest_MyInterface$Introspection')
        BeanIntrospectionReference reference = clazz.newInstance()
        BeanIntrospection introspection = reference.load()

        then:
        introspection.getBeanType().isInterface()
        introspection.beanProperties.size() == 0
        introspection.beanMethods.size() == 1
    }

    void "test type_use annotations"() {
        given:
            def introspection = buildBeanIntrospection('test.Test', '''
package test
import io.micronaut.core.annotation.Introspected
import io.micronaut.kotlin.processing.visitor.*

@Introspected
class Test(val name:  @TypeUseRuntimeAnn String, val secondName: @TypeUseClassAnn String)
''')
            def nameField = introspection.getProperty("name").orElse(null)
            def secondNameField = introspection.getProperty("secondName").orElse(null)

        expect:
            nameField
            secondNameField

            nameField.hasStereotype(TypeUseRuntimeAnn.name)
            !secondNameField.hasStereotype(TypeUseClassAnn.name)
    }

    void "test subtypes"() {
        given:
            BeanIntrospection introspection = buildBeanIntrospection('test.Holder', '''
package test
import io.micronaut.core.annotation.Introspected

@Introspected
open class Animal
@Introspected
class Cat(val lives: Int) : Animal()

@Introspected
class Holder<A : Animal>(
    var animalNonGeneric: Animal,
    var animalsNonGeneric: List<Animal>,
    var animal: A,
    var animals: List<A>
) {

    constructor(animal: A) : this(animal, listOf(animal), animal, listOf(animal))
}
        ''')

        expect:
            def animalListArgument = introspection.getProperty("animals").get().asArgument().getTypeParameters()[0]
            animalListArgument instanceof GenericPlaceholder
            animalListArgument.isTypeVariable()

            def animal = introspection.getProperty("animal").get().asArgument()
            animal instanceof GenericPlaceholder
            animal.isTypeVariable()
    }

    void "test package private property introspection"() {
        when:
        def introspection = buildBeanIntrospection('test.Test', '''
package test

import io.micronaut.core.annotation.Introspected
import io.micronaut.kotlin.processing.visitor.MySuperclass

@Introspected
class Test(val name: String) : MySuperclass()
''')
        then: 'the property in this class is introspected'
        introspection.getProperty("name").orElse(null)

        and: 'the public property in the java superclass is introspected'
        introspection.getProperty("publicProperty").orElse(null)

        and: 'the private property in the java superclass is not introspected'
        !introspection.getProperty("privateProperty").orElse(null)

        and: 'the package private superclass property is not introspected'
        !introspection.getProperty("packagePrivateProperty").orElse(null)
    }

    void "test package private property introspection in same package"() {
        when:
        def introspection = buildBeanIntrospection('io.micronaut.kotlin.processing.visitor.Test', '''
package io.micronaut.kotlin.processing.visitor

import io.micronaut.core.annotation.Introspected

@Introspected
class Test(val name: String) : MySuperclass()
''')
        then: 'the property in this class is introspected'
        introspection.getProperty("name").orElse(null)

        and: 'the public property in the java superclass is introspected'
        introspection.getProperty("publicProperty").orElse(null)

        and: 'the private property in the java superclass is not introspected'
        !introspection.getProperty("privateProperty").orElse(null)

        and: 'the package private superclass property is introspected, as we are in the same package'
        introspection.getProperty("packagePrivateProperty").orElse(null)
    }

    void "test list property"() {
        given:
            BeanIntrospection introspection = buildBeanIntrospection('test.Cart', '''
package test
import io.micronaut.core.annotation.Introspected

@io.micronaut.core.annotation.Introspected
data class CartItem(
        val id: Long?,
        val name: String,
        val cart: Cart?
) {
    constructor(name: String) : this(null, name, null)
}

@io.micronaut.core.annotation.Introspected
data class Cart(
        val id: Long?,
        val items: List<CartItem>?
) {

    constructor(items: List<CartItem>) : this(null, items)

    fun cartItemsNotNullable() : List<CartItem> = listOf()
}
        ''')
            def bean = introspection.instantiate(1L, new ArrayList())
            bean = introspection.getProperty("items").get().withValue(bean, new ArrayList())
        expect:
            bean
    }

    void "test ignored problem"() {
        given:
            BeanIntrospection introspection = buildBeanIntrospection('test.ClassificationAndStats', '''
package test

import com.fasterxml.jackson.annotation.JsonGetter
import com.fasterxml.jackson.annotation.JsonIgnore
import com.fasterxml.jackson.annotation.JsonValue
import io.micronaut.core.annotation.Introspected

@Introspected
enum class Aggregation(@get:JsonValue val fieldName: String) {
    FIELD_1("SomeField1"),
    FIELD_2("SomeField2");
}

interface StatsEntry {
    val shouldNotAppearInJson: MutableMap<Aggregation, Int>
}

@Introspected
data class ClassificationVars(
    val regionKode: String
)

@Introspected
data class ClassificationAndStats<out T : StatsEntry>(
    val klassifisering: ClassificationVars,
    /** Ignore field to avoid double wrapping of values in resulting JSON */
    @JsonIgnore
    val stats: T
) {
    @JsonGetter("stats")
    fun getValues(): Map<Aggregation, Int> = stats.shouldNotAppearInJson
}

        ''')
            def statsProp = introspection.getProperty("stats").get()
        expect:
            statsProp.hasAnnotation(JsonIgnore)
    }

    @Issue("https://github.com/micronaut-projects/micronaut-core/issues/10647")
    void 'handles generic definitions as generated by protobuf'() {
        when:
        buildBeanIntrospection('test.MyMessage', '''
package test

import io.micronaut.core.annotation.Introspected
import io.micronaut.kotlin.processing.visitor.Message

@Introspected
class MyMessage: Message()
''')
        then:
        noExceptionThrown()
    }
}
