package io.micronaut.kotlin.processing.elementapi

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.beans.BeanIntrospection
import io.micronaut.core.beans.BeanIntrospectionReference
import io.micronaut.core.beans.BeanIntrospector
import io.micronaut.core.beans.BeanMethod
import io.micronaut.core.beans.BeanProperty
import io.micronaut.core.convert.ConversionContext
import io.micronaut.core.reflect.InstantiationUtils
import io.micronaut.core.reflect.exception.InstantiationException
import io.micronaut.core.type.Argument
import io.micronaut.inject.ExecutableMethod
import spock.lang.Specification

import javax.validation.Constraint
import javax.validation.constraints.Min
import javax.validation.constraints.NotBlank
import java.lang.reflect.Field

class BeanIntrospectionSpec extends Specification {

    void "test basic introspection"() {
        when:
        def introspection = Compiler.buildBeanIntrospection("test.Test", """
package test

import io.micronaut.core.annotation.Introspected

@Introspected
class Test {

}
""")

        then:
        noExceptionThrown()
        introspection != null
        introspection.instantiate().class.name == "test.Test"
    }

    void "test generics in arrays don't stack overflow"() {
        given:
        def introspection = Compiler.buildBeanIntrospection('arraygenerics.Test', '''
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
        introspection.getRequiredProperty("array", CharSequence[].class).type == CharSequence[].class
        introspection.getRequiredProperty("starArray", Object[].class).type == Object[].class
        introspection.getRequiredProperty("stringArray", String[].class).type == String[].class
        introspection.beanMethods.first().returnType.type == CharSequence[].class
    }

    void 'test favor method access'() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('fieldaccess.Test','''\
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
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess;

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
''');
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
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('fieldaccess.Test','''\
package fieldaccess

import io.micronaut.core.annotation.*

@Introspected(accessKind=[Introspected.AccessKind.FIELD])
open class Test(val two: Integer?) {  // read-only
    var one: String? = null // read/write
    internal var three: String? = null // package protected
    protected var four: String? = null // not included since protected
    private var five: String? = null // not included since private
}
''');
        when:
        def properties = introspection.getBeanProperties()

        then: 'all fields are private in Kotlin'
        properties.isEmpty()
    }

    void 'test bean constructor'() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('beanctor.Test','''\
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
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.MethodTest', '''
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
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('customwith.CopyMe', '''\
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
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.CopyMe','''\
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
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.Foo', '''
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
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.Foo', '''
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
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.Foo', '''
package test

import io.micronaut.core.annotation.Creator
import javax.validation.constraints.Min

@io.micronaut.core.annotation.Introspected
data class Foo(val value: List<@Min(10) Long>)
''')

        when:
        BeanProperty<?, ?> property = introspection.getRequiredProperty("value", List)
        def genericTypeArg = property.asArgument().getTypeParameters()[0]

        then:
        property != null
        genericTypeArg.annotationMetadata.hasStereotype(Constraint)
        genericTypeArg.annotationMetadata.hasAnnotation(Min)
        genericTypeArg.annotationMetadata.intValue(Min).getAsInt() == 10
    }

    void 'test annotations on generic type arguments'() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.Foo', '''
package test

import javax.validation.constraints.Min
import kotlin.annotation.AnnotationTarget.*

@io.micronaut.core.annotation.Introspected
class Foo {
    var value : List<@Min(10) @SomeAnn Long>? = null
}

@MustBeDocumented
@Retention(AnnotationRetention.RUNTIME)
@Target(FUNCTION, PROPERTY, ANNOTATION_CLASS, CONSTRUCTOR, VALUE_PARAMETER, TYPE)
annotation class SomeAnn()
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
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.Foo', '''
package test

@io.micronaut.core.annotation.Introspected
data class Foo(@javax.validation.constraints.NotBlank val name: String, val age: Int)
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
        ClassLoader classLoader = Compiler.buildClassLoader('test.Foo', '''
package test

import io.micronaut.core.annotation.*
import io.micronaut.kotlin.processing.elementapi.OuterBean

@Introspected(classes=[OuterBean.InnerBean::class])
class Test
''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$IntrospectionRef0')
        BeanIntrospectionReference reference = clazz.newInstance()
        String className = "io.micronaut.kotlin.processing.elementapi.OuterBean\$InnerBean"

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
        noExceptionThrown()
        o.class.name == className
    }

    void "test create bean introspection for external inner interface"() {
        given:
        ClassLoader classLoader = Compiler.buildClassLoader('test.Foo', '''
package test

import io.micronaut.core.annotation.*
import io.micronaut.kotlin.processing.elementapi.OuterBean

@Introspected(classes=[OuterBean.InnerInterface::class])
class Test
''')

        when:"the reference is loaded"
        def clazz = classLoader.loadClass('test.$Test$IntrospectionRef0')
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
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.Foo', '''
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
        e.message =='Cannot mutate property [name] that is not mutable via a setter method or constructor argument for type: test.Foo'
    }

    void "test bean introspection with property of generic superclass"() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.Foo', '''
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
        beanProperties[0].type == String
        beanProperties[1].type == String
        introspection.getRequiredProperty("name", String)
                .get(test) == 'test'
        introspection.getRequiredProperty("other", String)
                .get(test) == 'other'
    }

    void "test bean introspection with argument of generic interface"() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.Foo', '''
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
        bp.set(test, 5L)

        then:
        bp.get(test) == 5L

        when:
        def returnedBean = bp.withValue(test, 10L)

        then:
        returnedBean.is(test)
        bp.get(test) == 10L
    }

    void "test bean introspection with property with static creator method on interface"() {
        given:
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.Foo', '''
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
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.Foo', '''
package test;

import io.micronaut.core.annotation.Creator;

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
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.Test', '''
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
        BeanIntrospection introspection = Compiler.buildBeanIntrospection('test.Test','''\
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
        def classLoader = Compiler.buildClassLoader('test.Address', '''
package test

import javax.validation.constraints.*

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
        def clazz = classLoader.loadClass('test.$Address$IntrospectionRef')
        BeanIntrospectionReference reference = clazz.newInstance()

        expect:
        reference != null
        reference.load()
    }

    void "test primary constructor is preferred"() {
        given:
        def classLoader = Compiler.buildClassLoader('test.Book', '''
package test

@io.micronaut.core.annotation.Introspected
class Book(val title: String) {

    private var author: String? = null

    constructor(title: String, author: String) : this(title) {
        this.author = author
    }
}
''')
        Class clazz = classLoader.loadClass('test.$Book$IntrospectionRef')
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
        introspectionMapField.set(introspector, new HashMap<String, BeanIntrospectionReference<Object>>());
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
        def classLoader = Compiler.buildClassLoader('test.Book', '''
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
        Class clazz = classLoader.loadClass('test.$Book$IntrospectionRef')
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
        introspectionMapField.set(introspector, new HashMap<String, BeanIntrospectionReference<Object>>());
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

    void "test default constructor "() {
        given:
        def classLoader = Compiler.buildClassLoader('test.Book', '''
package test

@io.micronaut.core.annotation.Introspected
class Book {
    var title: String? = null
}
''')
        Class clazz = classLoader.loadClass('test.$Book$IntrospectionRef')
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
        introspectionMapField.set(introspector, new HashMap<String, BeanIntrospectionReference<Object>>());
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
        def classLoader = Compiler.buildClassLoader('test.Test', '''
package test

import io.micronaut.core.annotation.*
import javax.validation.constraints.*
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
        def clazz = classLoader.loadClass('test.$Test$IntrospectionRef')
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
        def classLoader = Compiler.buildClassLoader('test.Test', '''
package test

import io.micronaut.core.annotation.*
import javax.validation.constraints.*
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
        def clazz = classLoader.loadClass('test.$Test$IntrospectionRef')
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
        def classLoader = Compiler.buildClassLoader('test.Test', '''
package test

import io.micronaut.core.annotation.*
import javax.validation.constraints.*
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
        def clazz = classLoader.loadClass('test.$Test$IntrospectionRef')
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
}
