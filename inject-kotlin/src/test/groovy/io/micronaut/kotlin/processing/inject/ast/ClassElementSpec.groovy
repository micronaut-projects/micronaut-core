package io.micronaut.kotlin.processing.inject.ast

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.annotation.processing.test.KotlinCompiler
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.util.CollectionUtils
import io.micronaut.inject.BeanDefinition
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.EnumElement
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.GenericElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MemberElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.ast.WildcardElement
import io.micronaut.kotlin.processing.visitor.AllElementsVisitor
import io.micronaut.kotlin.processing.visitor.KotlinClassElement
import io.micronaut.kotlin.processing.visitor.KotlinEnumElement
import io.micronaut.runtime.context.env.ConfigurationAdvice
import jakarta.validation.Valid
import spock.lang.PendingFeature

class ClassElementSpec extends AbstractKotlinCompilerSpec {

    void "test Java Record compile"() {
        def ce = buildClassElementJava('test.Product2', '''
package test;

record Product2(Double price, String name) {
}
''')
        def methods = ce.getMethods()
        def properties = ce.getBeanProperties()
        expect:
            methods.size() == 2
            methods.get(0).name == "price"
            !methods.get(0).getReturnType().isPrimitive()
            methods.get(1).name == "name"
            properties.size() == 2
            properties.get(0).name == "price"
            !properties.get(0).getType().isPrimitive()
            properties.get(1).name == "name"
    }

    void "test Java annotations"() {
        def ce = buildClassElementJava('test.Product', '''
package test;

class Product {

    private final Double price;
    @javax.persistence.Id
    private final String name;

    public Product(Double price, String name) {
        this.price = price;
        this.name = name;
    }

    public Double getPrice() {
        return price;
    }

    public String getName() {
        return name;
    }
}
''')
        def properties = ce.getBeanProperties()
        expect:
            properties.size() == 2
            properties.get(0).name == "price"
            !properties.get(0).getType().isPrimitive()
            properties.get(1).name == "name"
            properties.get(1).hasStereotype(javax.persistence.Id)
    }

    void "test Java Bean compile"() {
        def ce = buildClassElementJava('test.Product', '''
package test;

class Product {

    public Product(Double price, String name) {
        this.price = price;
        this.name = name;
    }

    private final Double price;
    private final String name;

    public Double getPrice() {
        return price;
    }

    public String getName() {
        return name;
    }
}
''')
        def methods = ce.getMethods()
        def properties = ce.getBeanProperties()
        expect:
            methods.size() == 2
            methods.get(0).name == "getPrice"
            !methods.get(0).getReturnType().isPrimitive()
            methods.get(1).name == "getName"
            properties.size() == 2
            properties.get(0).name == "price"
            !properties.get(0).getType().isPrimitive()
            properties.get(1).name == "name"
    }

    void "test visit enum"() {

        given:
            AllElementsVisitor.VISITED_CLASS_ELEMENTS.clear()
            AllElementsVisitor.VISITED_ELEMENTS.clear()
            AllElementsVisitor.VISITED_METHOD_ELEMENTS.clear()
            AllElementsVisitor.WRITE_FILE = true
            AllElementsVisitor.WRITE_IN_METAINF = true

        when:
            def definition = buildBeanDefinition('test.MyBean', '''
package test

import io.micronaut.core.annotation.*
import io.micronaut.http.annotation.*
import io.micronaut.http.*

@Controller("/hello")
class HelloController {

    @Get
    @Produces(MediaType.TEXT_PLAIN)
    fun index(@QueryValue("channels") channels: Channel?) = ""

    @Introspected
    enum class Channel {
        SYSTEM1,
        SYSTEM2
    }
}

@jakarta.inject.Singleton
class MyBean {}
''')
        then:
            definition

            AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 3
            def enumEl = AllElementsVisitor.VISITED_CLASS_ELEMENTS.find {
                it.name == 'test.HelloController$Channel'
            }

            enumEl
            def enmConsts = ((KotlinEnumElement) enumEl).elements()
            enmConsts
            enmConsts.size() == 2
            enmConsts.find {
                it.name == "SYSTEM1"
            } != null
            enmConsts.find {
                it.name == "SYSTEM2"
            } != null
    }

    void "test interface native properties"() {

        when:
            def nativeProps = buildClassElementMapped('test.BaseConfiguration', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("base")
interface BaseConfiguration : Other {
    val port: Int
}

interface Other {
    val host: String
}
''', ce -> ce.syntheticBeanProperties)

        then:
            nativeProps.size() == 2
            nativeProps.collect { it.name } == ["port", "host"]
    }

    void "test visitGeneratedFile"() {
        given:
            AllElementsVisitor.VISITED_CLASS_ELEMENTS.clear()
            AllElementsVisitor.VISITED_ELEMENTS.clear()
            AllElementsVisitor.VISITED_METHOD_ELEMENTS.clear()
            AllElementsVisitor.WRITE_FILE = true
            AllElementsVisitor.WRITE_IN_METAINF = false

        when:
            def definition = buildBeanDefinition('test.visit.Test', '''
package test.visit

 import jakarta.inject.Singleton


@Singleton
class Test {
    fun myMethod() {}
}
''')

        then:
            AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
            AllElementsVisitor.VISITED_METHOD_ELEMENTS.size() == 1
            definition.getClass().getClassLoader().getResource("foo/bar.txt").text == 'All good'

        cleanup:
            AllElementsVisitor.WRITE_FILE == false
    }

    void "test visitGeneratedFile META-INF"() {
        given:
            AllElementsVisitor.VISITED_CLASS_ELEMENTS.clear()
            AllElementsVisitor.VISITED_ELEMENTS.clear()
            AllElementsVisitor.VISITED_METHOD_ELEMENTS.clear()
            AllElementsVisitor.WRITE_FILE = true
            AllElementsVisitor.WRITE_IN_METAINF = true

        when:
            def definition = buildBeanDefinition('test.visit.Test', '''
package test.visit

 import jakarta.inject.Singleton


@Singleton
class Test {
    fun myMethod() {}
}
''')

        then:
            AllElementsVisitor.VISITED_CLASS_ELEMENTS.size() == 1
            AllElementsVisitor.VISITED_METHOD_ELEMENTS.size() == 1
            definition.getClass().getClassLoader().getResource("META-INF/foo/bar.txt").text == 'All good'

        cleanup:
            AllElementsVisitor.WRITE_FILE == false
            AllElementsVisitor.WRITE_IN_METAINF = false
    }

    void "test class element"() {
        expect:
            buildClassElement('ast.test.Test', '''
package ast.test

import java.lang.IllegalStateException
import kotlin.jvm.Throws


class Test(
    val publicConstructorReadOnly : String,
    private val privateConstructorReadOnly : String,
    protected val protectedConstructorReadOnly : Boolean
) : Parent(), One, Two {

    val publicReadOnlyProp : Boolean = true
    protected val protectedReadOnlyProp : Boolean? = true
    private val privateReadOnlyProp : Boolean? = true
    var publicReadWriteProp : Boolean = true
    protected var protectedReadWriteProp : String? = "ok"
    private var privateReadWriteProp : String = "ok"
    private var conventionProp : String = "ok"

    private fun privateFunc(name : String) : String {
        return "ok"
    }

    open fun openFunc(name : String) : String {
        return "ok"
    }

    protected fun protectedFunc(name : String) : String {
        return "ok"
    }

    @Throws(IllegalStateException::class)
    override fun publicFunc(name : String) : String {
        return "ok"
    }

    suspend fun suspendFunc(name : String) : String {
        return "ok"
    }

    fun getConventionProp() : String {
        return conventionProp
    }

    fun setConventionProp(name : String) {
        this.conventionProp = name
    }


    companion object Helper {
        fun publicStatic() : String {
            return "ok"
        }

        private fun privateStatic() : String {
            return "ok"
        }
    }

    inner class InnerClass1

    class InnerClass2
}

open class Parent : Three {
    open fun publicFunc(name : String) : String {
        return "ok"
    }

    fun parentFunc() : Boolean {
        return true
    }

    companion object ParentHelper {
        fun publicStatic() : String {
            return "ok"
        }
    }
}

interface One
interface Two
interface Three
''') { ClassElement classElement ->
                List<ConstructorElement> constructorElements = classElement.getEnclosedElements(ElementQuery.CONSTRUCTORS)
                List<ClassElement> allInnerClasses = classElement.getEnclosedElements(ElementQuery.ALL_INNER_CLASSES)
                List<ClassElement> declaredInnerClasses = classElement.getEnclosedElements(ElementQuery.ALL_INNER_CLASSES.onlyDeclared())
                List<PropertyElement> propertyElements = classElement.getBeanProperties()
                List<PropertyElement> syntheticProperties = classElement.getSyntheticBeanProperties()
                List<MethodElement> methodElements = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)
                List<MethodElement> declaredMethodElements = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.onlyDeclared())
                List<MethodElement> includeOverridden = classElement.getEnclosedElements(ElementQuery.ALL_METHODS.includeOverriddenMethods())
                Map<String, MethodElement> methodMap = methodElements.collectEntries {
                    [it.name, it]
                }
                Map<String, MethodElement> declaredMethodMap = declaredMethodElements.collectEntries {
                    [it.name, it]
                }
                Map<String, PropertyElement> propMap = propertyElements.collectEntries {
                    [it.name, it]
                }
                Map<String, PropertyElement> synthPropMap = syntheticProperties.collectEntries {
                    [it.name, it]
                }
                Map<String, ClassElement> declaredInnerMap = declaredInnerClasses.collectEntries {
                    [it.simpleName, it]
                }
                Map<String, ClassElement> innerMap = allInnerClasses.collectEntries {
                    [it.simpleName, it]
                }

                def overridden = includeOverridden.find { it.declaringType.simpleName == 'Parent' && it.name == 'publicFunc' }

                assert classElement != null
                assert classElement.interfaces*.simpleName as Set == ['One', "Two"] as Set
                assert methodElements != null
                assert !classElement.isAbstract()
                assert classElement.name == 'ast.test.Test'
                assert !classElement.isPrivate()
                assert classElement.isPublic()
                assert classElement.modifiers == [ElementModifier.FINAL, ElementModifier.PUBLIC] as Set
                assert constructorElements.size() == 1
                assert constructorElements[0].parameters.size() == 3
                assert classElement.superType.isPresent()
                assert classElement.superType.get().simpleName == 'Parent'
                assert !classElement.superType.get().getSuperType().isPresent()
                assert propertyElements.size() == 7
                assert propMap.size() == 7
                assert synthPropMap.size() == 6
                assert methodElements.size() == 8
                assert includeOverridden.size() == 9
                assert declaredMethodElements.size() == 7
                assert propMap.keySet() == ['conventionProp', 'publicReadOnlyProp', 'protectedReadOnlyProp', 'publicReadWriteProp', 'protectedReadWriteProp', 'publicConstructorReadOnly', 'protectedConstructorReadOnly'] as Set
                assert synthPropMap.keySet() == ['publicReadOnlyProp', 'protectedReadOnlyProp', 'publicReadWriteProp', 'protectedReadWriteProp', 'publicConstructorReadOnly', 'protectedConstructorReadOnly'] as Set
                // inner classes
                assert allInnerClasses.size() == 4
                assert declaredInnerClasses.size() == 3
                assert !declaredInnerMap['Test$InnerClass1'].isStatic()
                assert declaredInnerMap['Test$InnerClass2'].isStatic()
                assert declaredInnerMap['Test$InnerClass1'].isPublic()
                assert declaredInnerMap['Test$InnerClass2'].isPublic()

                // read-only public
                assert propMap['publicReadOnlyProp'].isReadOnly()
                assert !propMap['publicReadOnlyProp'].isWriteOnly()
                assert propMap['publicReadOnlyProp'].isPublic()
                assert propMap['publicReadOnlyProp'].readMethod.isPresent()
                assert propMap['publicReadOnlyProp'].readMethod.get().isSynthetic()
                assert !propMap['publicReadOnlyProp'].writeMethod.isPresent()
                // read/write public property
                assert !propMap['publicReadWriteProp'].isReadOnly()
                assert !propMap['publicReadWriteProp'].isWriteOnly()
                assert propMap['publicReadWriteProp'].isPublic()
                assert propMap['publicReadWriteProp'].readMethod.isPresent()
                assert propMap['publicReadWriteProp'].readMethod.get().isSynthetic()
                assert propMap['publicReadWriteProp'].writeMethod.isPresent()
                assert propMap['publicReadWriteProp'].writeMethod.get().isSynthetic()
                // convention prop
                assert !propMap['conventionProp'].isReadOnly()
                assert !propMap['conventionProp'].isWriteOnly()
                assert propMap['conventionProp'].isPublic()
                assert propMap['conventionProp'].readMethod.isPresent()
                assert !propMap['conventionProp'].readMethod.get().isSynthetic()
                assert propMap['conventionProp'].writeMethod.isPresent()
                assert !propMap['conventionProp'].writeMethod.get().isSynthetic()

                // methods
                assert methodMap.keySet() == ['publicFunc', 'parentFunc', 'openFunc', 'privateFunc', 'protectedFunc', 'suspendFunc', 'getConventionProp', 'setConventionProp'] as Set
                assert declaredMethodMap.keySet() == ['publicFunc', 'openFunc', 'privateFunc', 'protectedFunc', 'suspendFunc', 'getConventionProp', 'setConventionProp'] as Set
                assert methodMap['suspendFunc'].isSuspend()
                assert methodMap['suspendFunc'].returnType.name == String.name
                assert methodMap['suspendFunc'].parameters.size() == 1
                assert methodMap['suspendFunc'].suspendParameters.size() == 2
                assert !methodMap['openFunc'].isFinal()
                assert !methodMap['publicFunc'].isPackagePrivate()
                assert !methodMap['publicFunc'].isPrivate()
                assert !methodMap['publicFunc'].isStatic()
                assert !methodMap['publicFunc'].isReflectionRequired()
                assert methodMap['publicFunc'].hasParameters()
                assert methodMap['publicFunc'].thrownTypes.size() == 1
                assert methodMap['publicFunc'].thrownTypes[0].name == IllegalStateException.name
                assert methodMap['publicFunc'].isPublic()
                assert methodMap['publicFunc'].owningType.name == 'ast.test.Test'
                assert methodMap['publicFunc'].declaringType.name == 'ast.test.Test'
                assert !methodMap['publicFunc'].isFinal() // should be final? But apparently not
                assert overridden != null
                assert methodMap['publicFunc'].overrides(overridden)
            }
    }

    @PendingFeature(reason = "https://github.com/google/ksp/issues/2422")
    void "test class element generics"() {
        expect:
            buildClassElement('ast.test.Test', '''
package ast.test

/**
* Class docs
*
* @param constructorProp construct prop
*/
class Test(val constructorProp : String) : Parent<String>(constructorProp), One<String> {
    /**
     * Property doc
     */
    val publicReadOnlyProp : Boolean = true
    override val size: Int = 10
    override fun get(index: Int): String {
        return "ok"
    }

    open fun openFunc(name : String) : String {
        return "ok"
    }

    /**
    * Method doc
    * @param name Param name
    */
    override fun publicFunc(name : String) : String {
        return "ok"
    }
}

open abstract class Parent<T : CharSequence>(val parentConstructorProp : T) : AbstractMutableList<T>() {

    var parentProp : T = parentConstructorProp
    private var conventionProp : T = parentConstructorProp

    fun getConventionProp() : T {
        return conventionProp
    }
    override fun add(index: Int, element: T){
        TODO("Not yet implemented")
    }
    override fun removeAt(index: Int): T{
        TODO("Not yet implemented")
    }
    override fun set(index: Int, element: T): T{
        TODO("Not yet implemented")
    }
    fun setConventionProp(name : T) {
        this.conventionProp = name
    }

    open fun publicFunc(name : T) : T {
        TODO("not yet implemented")
    }

    fun parentFunc(name :  T) : T {
        TODO("not yet implemented")
    }

    suspend fun suspendFunc(name : T) : T {
        TODO("not yet implemented")
    }
}

interface One<E>
interface Two
interface Three
''') { ClassElement classElement ->
                List<ConstructorElement> constructorElements = classElement.getEnclosedElements(ElementQuery.CONSTRUCTORS)
                List<PropertyElement> propertyElements = classElement.getBeanProperties()
                List<PropertyElement> syntheticProperties = classElement.getSyntheticBeanProperties()
                List<MethodElement> methodElements = classElement.getEnclosedElements(ElementQuery.ALL_METHODS)
                Map<String, MethodElement> methodMap = methodElements.collectEntries {
                    [it.name, it]
                }
                Map<String, PropertyElement> propMap = propertyElements.collectEntries {
                    [it.name, it]
                }
                Map<String, PropertyElement> syntheticPropMap = syntheticProperties.collectEntries {
                    [it.name, it]
                }

                assert classElement.documentation.isPresent()
                assert methodMap['add'].parameters[1].genericType.simpleName == 'String'
                assert methodMap['add'].parameters[1].type.simpleName == 'CharSequence'
                assert methodMap['iterator'].returnType.firstTypeArgument.get().simpleName == 'Object'
                assert methodMap['iterator'].genericReturnType.firstTypeArgument.get().simpleName == 'String'
                assert methodMap['stream'].returnType.firstTypeArgument.get().simpleName == 'Object'
                assert methodMap['stream'].genericReturnType.firstTypeArgument.get().simpleName == 'String'
                assert propMap['conventionProp'].type.simpleName == 'String'
                assert propMap['conventionProp'].genericType.simpleName == 'String'
                assert propMap['conventionProp'].genericType.simpleName == 'String'
                assert propMap['conventionProp'].readMethod.get().returnType.simpleName == 'CharSequence'
                assert propMap['conventionProp'].readMethod.get().genericReturnType.simpleName == 'String'
                assert propMap['conventionProp'].writeMethod.get().parameters[0].type.simpleName == 'CharSequence'
                assert propMap['conventionProp'].writeMethod.get().parameters[0].genericType.simpleName == 'String'

                assert propMap['parentConstructorProp'].type.simpleName == 'CharSequence'
                assert propMap['parentConstructorProp'].genericType.simpleName == 'String'

                assert syntheticPropMap['parentConstructorProp'].type.simpleName == 'CharSequence'
                assert syntheticPropMap['parentConstructorProp'].genericType.simpleName == 'String'

                assert propMap['constructorProp'].type.simpleName == 'String'
                assert propMap['constructorProp'].genericType.simpleName == 'String'

                assert syntheticPropMap['constructorProp'].type.simpleName == 'String'
                assert syntheticPropMap['constructorProp'].genericType.simpleName == 'String'

                assert propMap['parentProp'].type.simpleName == 'CharSequence'
                assert propMap['parentProp'].genericType.simpleName == 'String'

                assert syntheticPropMap['parentProp'].type.simpleName == 'CharSequence'
                assert syntheticPropMap['parentProp'].genericType.simpleName == 'String'

                assert methodMap['publicFunc'].documentation.isPresent()
                assert methodMap['parentFunc'].returnType.simpleName == 'CharSequence'
                assert methodMap['parentFunc'].genericReturnType.simpleName == 'String'
                assert methodMap['parentFunc'].parameters[0].type.simpleName == 'CharSequence'
                assert methodMap['parentFunc'].parameters[0].genericType.simpleName == 'String'
            }
    }

    void "test annotation metadata present on deep type parameters for field"() {
        ClassElement ce = buildClassElementTransformed('test.Test', '''
package test;
import io.micronaut.core.annotation.*;
import jakarta.validation.constraints.*;
import java.util.List;

class Test {
    var deepList: List<@Size(min=1, max=2) List<@NotEmpty List<@NotNull String>>>? = null
}
''') {
            def field = it.findField("deepList").get()
            initializeAllTypeArguments(field.getType())
            initializeAllTypeArguments(field.getGenericType())
            it
        }
        expect:
            def field = ce.findField("deepList").get()
            def fieldType = field.getGenericType()

            assertListGenericArgument(fieldType, { ClassElement listArg1 ->
                assert listArg1.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.Size$List')
                assert listArg1.getTypeAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.Size$List')
                assertListGenericArgument(listArg1, { ClassElement listArg2 ->
                    assert listArg2.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotEmpty$List')
                    assert listArg2.getTypeAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotEmpty$List')
                    assertListGenericArgument(listArg2, { ClassElement listArg3 ->
                        assert listArg3.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotNull$List')
                        assert listArg3.getTypeAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotNull$List')
                    })
                })
            })

            def level1 = fieldType.getTypeArguments()["E"]
            level1.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.Size$List')
            level1.getTypeAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.Size$List')
            def level2 = level1.getTypeArguments()["E"]
            level2.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotEmpty$List')
            level2.getTypeAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotEmpty$List')
            def level3 = level2.getTypeArguments()["E"]
            level3.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotNull$List')
            level3.getTypeAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotNull$List')
    }

    void "test annotation metadata present on deep type parameters for method"() {
        ClassElement ce = buildClassElementTransformed('test.Test', '''
package test
import jakarta.validation.constraints.*
import java.util.List

class Test {
    fun deepList(): List<@Size(min=1, max=2) List<@NotEmpty List<@NotNull String>>>? {
        return null
    }
}
''') {
            def method = it.findMethod("deepList").get()
            initializeAllTypeArguments(method.getReturnType())
            initializeAllTypeArguments(method.getGenericReturnType())
            it
        }
        expect:
            def method = ce.findMethod("deepList").get()
            def theType = method.getGenericReturnType()


            def level1 = theType.getTypeArguments()["E"]
            level1.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.Size$List')
            level1.getTypeAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.Size$List')
            def level2 = level1.getTypeArguments()["E"]
            level2.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotEmpty$List')
            level2.getTypeAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotEmpty$List')
            def level3 = level2.getTypeArguments()["E"]
            level3.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotNull$List')
            level3.getTypeAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotNull$List')

            assertListGenericArgument(theType, { ClassElement listArg1 ->
                assert listArg1.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.Size$List')
                assert listArg1.getTypeAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.Size$List')
                assertListGenericArgument(listArg1, { ClassElement listArg2 ->
                    assert listArg2.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotEmpty$List')
                    assert listArg2.getTypeAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotEmpty$List')
                    assertListGenericArgument(listArg2, { ClassElement listArg3 ->
                        assert listArg3.getTypeAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotNull$List')
                        assert listArg3.getAnnotationMetadata().getAnnotationNames().contains('jakarta.validation.constraints.NotNull$List')
                    })
                })
            })
    }

    void "test type annotations on a method and a field"() {
        ClassElement ce = buildClassElement('test.Test', '''
package test

import io.micronaut.kotlin.processing.inject.ast.*

class Test {
    var myField: @TypeUseRuntimeAnn @TypeUseClassAnn Str? = null

    fun myMethod(): @TypeUseRuntimeAnn @TypeUseClassAnn Str? {
        return null
    }
}

class Str
''')
        expect:
            def field = ce.findField("myField").get()
            def method = ce.findMethod("myMethod").get()

            // Type annotations shouldn't appear on the field
            field.getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.NULLABLE]
            field.getType().getAnnotationMetadata().getAnnotationNames().asList() == [TypeUseRuntimeAnn.name, TypeUseClassAnn.name]
            field.getGenericType().getAnnotationMetadata().getAnnotationNames().asList() == [TypeUseRuntimeAnn.name, TypeUseClassAnn.name]
            // Type annotations shouldn't appear on the method
            method.getAnnotationMetadata().getAnnotationNames().asList() == [AnnotationUtil.NULLABLE]
            method.getReturnType().getAnnotationMetadata().getAnnotationNames().asList() == [TypeUseRuntimeAnn.name, TypeUseClassAnn.name]
            method.getGenericReturnType().getAnnotationMetadata().getAnnotationNames().asList() == [TypeUseRuntimeAnn.name, TypeUseClassAnn.name]
    }

    void "test type annotations on a method and a field 2"() {
        ClassElement ce = buildClassElement('test.Test', '''
package test

import io.micronaut.kotlin.processing.inject.ast.*

class Test {
    @TypeFieldRuntimeAnn
    var myField: @TypeUseRuntimeAnn @TypeUseClassAnn Str? = null

    @TypeMethodRuntimeAnn
    fun myMethod(): @TypeUseRuntimeAnn @TypeUseClassAnn Str? {
        return null
    }
}

class Str
''')
        expect:
            def field = ce.findField("myField").get()
            def method = ce.findMethod("myMethod").get()

            // Type annotations shouldn't appear on the field
            field.getAnnotationMetadata().getAnnotationNames().asList() == [TypeFieldRuntimeAnn.name, AnnotationUtil.NULLABLE]
            field.getType().getAnnotationMetadata().getAnnotationNames().asList() == [TypeUseRuntimeAnn.name, TypeUseClassAnn.name]
            field.getGenericType().getAnnotationMetadata().getAnnotationNames().asList() == [TypeUseRuntimeAnn.name, TypeUseClassAnn.name]
            // Type annotations shouldn't appear on the method
            method.getAnnotationMetadata().getAnnotationNames().asList() == [TypeMethodRuntimeAnn.name, AnnotationUtil.NULLABLE]
            method.getReturnType().getAnnotationMetadata().getAnnotationNames().asList() == [TypeUseRuntimeAnn.name, TypeUseClassAnn.name]
            method.getGenericReturnType().getAnnotationMetadata().getAnnotationNames().asList() == [TypeUseRuntimeAnn.name, TypeUseClassAnn.name]
    }

    void "test recursive generic type parameter"() {
        expect:
            buildClassElement('test.TrackedSortedSet', '''\
package test

class TrackedSortedSet<T : Comparable<T>>

''') { ce ->
                def typeArguments = ce.getTypeArguments()
                assert typeArguments.size() == 1
                def typeArgument = typeArguments.get("T")
                assert typeArgument.name == "java.lang.Comparable"
                def nextTypeArguments = typeArgument.getTypeArguments()
                def nextTypeArgument = nextTypeArguments.get("T")
                assert nextTypeArgument.name == "java.lang.Comparable"
                def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
                def nextNextTypeArgument = nextNextTypeArguments.get("T")
                assert nextNextTypeArgument.name == "java.lang.Object"
            }
    }

    void "test annotation metadata present on deep type parameters for method 2"() {
        ClassElement ce = buildClassElementTransformed('test.Test', '''
package test
import io.micronaut.core.annotation.*
import jakarta.validation.constraints.*
import java.util.List
import io.micronaut.kotlin.processing.inject.ast.*

class Test {
    fun deepList() : Lst<Lst<Lst<@TypeUseRuntimeAnn @TypeUseClassAnn String>>>? {
        return null
    }
}

class Lst<E>
''') {
            def method = it.findMethod("deepList").get()
            def theType = method.getGenericReturnType()
            def level1 = theType.getTypeArguments()["E"]
            def level2 = level1.getTypeArguments()["E"]
            def level3 = level2.getTypeArguments()["E"]
            level3.getAnnotationNames()
            level3
        }
        expect:
            ce.getAnnotationMetadata().getAnnotationNames().asList() == [TypeUseRuntimeAnn.name, TypeUseClassAnn.name]
    }

    void "test annotations on recursive generic type parameter 1"() {
        given:
            ClassElement ce = buildClassElementTransformed('test.TrackedSortedSet', '''\
package test

import io.micronaut.kotlin.processing.inject.ast.TypeUseRuntimeAnn

class TrackedSortedSet<@TypeUseRuntimeAnn T : java.lang.Comparable<out T>> {
}

''') {
                initializeAllTypeArguments(it)
                it
            }
        expect:
            def typeArguments = ce.getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "java.lang.Comparable"
            typeArgument.getAnnotationNames().asList() == [TypeUseRuntimeAnn.name]
    }

    void "test recursive generic type parameter 2"() {
        when:
            buildClassElement('test.Test', '''\
package test

class Test<T : Test<*>>

''')
        then:
            def e = thrown(RuntimeException)
            e.message.contains "This type parameter violates the Finite Bound Restriction"
    }

    void "test nested configurations"() {
        when:
            def ce = (BeanDefinition) KotlinCompiler.buildAndLoad('test.ConfigurationLevel1', 'test.$ConfigurationLevel1$ConfigurationLevel2$ConfigurationLevel3$Intercepted$Definition', '''\
package test

import io.micronaut.context.annotation.ConfigurationProperties

@ConfigurationProperties("level1")
interface ConfigurationLevel1 {
    val foo: String
    @ConfigurationProperties("level2")
    interface ConfigurationLevel2 {
        val bar: String
        @ConfigurationProperties("level3")
        interface ConfigurationLevel3 {
            val baz: String
        }
    }
}
''')
        then:
            ce.hasDeclaredAnnotation(ConfigurationAdvice)
    }

    void "test recursive generic type parameter 3"() {
        given:
            ClassElement ce = buildClassElement('test.Test', '''\
package test

class Test<T : Test<T>>

''')
        expect:
            def typeArguments = ce.getTypeArguments()
            typeArguments.size() == 1
            def typeArgument = typeArguments.get("T")
            typeArgument.name == "test.Test"
            def nextTypeArguments = typeArgument.getTypeArguments()
            def nextTypeArgument = nextTypeArguments.get("T")
            nextTypeArgument.name == "test.Test"
            def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
            def nextNextTypeArgument = nextNextTypeArguments.get("T")
            nextNextTypeArgument.name == "java.lang.Object"
    }

    void "test recursive generic method return"() {
        expect:
            buildClassElement('test.MyFactory', '''\
package test

import org.hibernate.SessionFactory
import org.hibernate.engine.spi.SessionFactoryDelegatingImpl

class MyFactory {

    fun sessionFactory() : SessionFactory {
         return SessionFactoryDelegatingImpl(null)
    }
}

''') {
                def sessionFactoryMethod = it.findMethod("sessionFactory").get()
                def withOptionsMethod = sessionFactoryMethod.getReturnType().findMethod("withOptions").get()
                assert withOptionsMethod.getReturnType().getTypeArguments()
                def typeArguments = withOptionsMethod.getReturnType().getTypeArguments()
                assert typeArguments.size() == 1
                def typeArgument = typeArguments.get("T")
                assert typeArgument.name == "org.hibernate.SessionBuilder"
                def nextTypeArguments = typeArgument.getTypeArguments()
                def nextTypeArgument = nextTypeArguments.get("T")
                assert nextTypeArgument.name == "org.hibernate.SessionBuilder"
                def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
                def nextNextTypeArgument = nextNextTypeArguments.get("T")
                assert nextNextTypeArgument.name == "java.lang.Object"
            }
    }

    void "test recursive generic method return 2"() {
        when:
            buildClassElement('test.MyFactory', '''\
package test;

class MyFactory {

    fun myBean(): MyBean {
        return MyBean()
    }
}

interface MyBuilder<T : MyBuilder<*>> {
    fun build(): T
}

class MyBean {

   fun myBuilder() : MyBuilder<test.MyBuilder>? {
       return null
   }

}
''')
        then:
            def e = thrown(RuntimeException)
            e.message.contains "This type parameter violates the Finite Bound Restriction"
    }

    void "test recursive generic method return 3"() {
        expect:
            buildClassElement('test.MyFactory', '''\
package test

class MyFactory {

    fun myBean(): MyBean {
        return MyBean()
    }
}

interface MyBuilder<T : MyBuilder<T>> {
    fun build(): T
}

class MyBean {

   fun <K : MyBuilder<K>> myBuilder() : K? {
       return null
   }

}
''') { ce ->
                def myBeanMethod = ce.findMethod("myBean").get()
                def myBuilderMethod = myBeanMethod.getReturnType().findMethod("myBuilder").get()
                def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
                assert typeArguments.size() == 1
                def typeArgument = typeArguments.get("T")
                assert typeArgument.name == "test.MyBuilder"
                def nextTypeArguments = typeArgument.getTypeArguments()
                def nextTypeArgument = nextTypeArguments.get("T")
                assert nextTypeArgument.name == "java.lang.Object"
            }
    }

    void "test recursive generic method return 4"() {
        expect:
            buildClassElement('test.MyFactory', '''\
package test

class MyFactory {

    fun myBean(): MyBean {
        return MyBean()
    }
}

interface MyBuilder<T : MyBuilder<T>> {
    fun build(): T
}

class MyBean {

   fun myBuilder() : MyBuilder<*>? {
       return null
   }

}
''') { ce ->
                def myBeanMethod = ce.findMethod("myBean").get()
                def myBuilderMethod = myBeanMethod.getReturnType().findMethod("myBuilder").get()
                def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
                assert typeArguments.size() == 1
                def typeArgument = typeArguments.get("T")
                assert typeArgument.name == "test.MyBuilder"
                def nextTypeArguments = typeArgument.getTypeArguments()
                def nextTypeArgument = nextTypeArguments.get("T")
                assert nextTypeArgument.name == "test.MyBuilder"
                def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
                def nextNextTypeArgument = nextNextTypeArguments.get("T")
                assert nextNextTypeArgument.name == "java.lang.Object"
            }
    }

    void "test recursive generic method return 5"() {
        expect:
            buildClassElement('test.MyFactory', '''\
package test

class MyFactory {

    fun myBean(): MyBean<*> {
        return MyBean()
    }
}

interface MyBuilder<T : MyBuilder<T>> {
    fun build(): T
}

class MyBean<K : MyBuilder<K>> {

   fun myBuilder() : MyBuilder<K>? {
       return null
   }

}
''') { ce ->
                def myBeanMethod = ce.findMethod("myBean").get()
                def myBuilderMethod = myBeanMethod.getReturnType().findMethod("myBuilder").get()
                def typeArguments = myBuilderMethod.getReturnType().getTypeArguments()
                assert typeArguments.size() == 1
                def typeArgument = typeArguments.get("T")
                assert typeArgument.name == "test.MyBuilder"
                def nextTypeArguments = typeArgument.getTypeArguments()
                def nextTypeArgument = nextTypeArguments.get("T")
                assert nextTypeArgument.name == "test.MyBuilder"
                def nextNextTypeArguments = nextTypeArgument.getTypeArguments()
                def nextNextTypeArgument = nextNextTypeArguments.get("T")
                assert nextNextTypeArgument.name == "java.lang.Object"
            }
    }

    void "test how the annotations from the type are propagated"() {
        given:
            ClassElement ce = buildClassElementTransformed('test.MyBean', '''\
package test

import io.micronaut.inject.annotation.*
import io.micronaut.context.annotation.*
import java.util.List
import io.micronaut.kotlin.processing.inject.ast.*
import jakarta.inject.Singleton

@Singleton
class MyBean {
    @Executable
    fun saveAll(books: List<MyBook>) {
    }

    @Executable
    fun <T : MyBook> saveAll2(book: List<T>) {
    }

    @Executable
    fun <T : MyBook> saveAll3(book: List<T>) {
    }

    @Executable
    fun save2(book: MyBook) {
    }

    @Executable
    fun <T : MyBook> save3(book: T) {
    }

    @Executable
    fun get(): MyBook? {
        return null
    }
}


''') {
                it.getMethods().forEach { method ->
                    initializeAllTypeArguments(method.getReturnType())
                    initializeAllTypeArguments(method.getGenericReturnType())
                    method.getParameters().each { parameter ->
                        parameter.getAnnotationNames()
                        initializeAllTypeArguments(parameter.getType())
                        initializeAllTypeArguments(parameter.getGenericType())
                    }
                }
                it
            }
        when:
            def saveAll = ce.findMethod("saveAll").get()
            def listTypeArgument = saveAll.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
            if (!listTypeArgument.hasAnnotation(MyEntity.class)) {
                def kotlinClassElement = listTypeArgument as KotlinClassElement
                System.out.println("XXX1 "+ kotlinClassElement)
                System.out.println("XXX2 "+ kotlinClassElement.class)
                System.out.println("XXX3 "+ kotlinClassElement.nativeType)

                def iterator = kotlinClassElement.nativeType.type?.annotations?.iterator()
                System.out.println("XXX4 "+ (iterator == null ? [] : CollectionUtils.iteratorToSet(iterator)))
                System.out.println("XXX5 "+ CollectionUtils.iteratorToSet(kotlinClassElement.nativeType.declaration.annotations.iterator()))
                System.out.println("XXX6 "+ kotlinClassElement.nativeType.declaration)
                System.out.println("XXX7 "+ kotlinClassElement.nativeType.declaration.class)

            }
            listTypeArgument.hasAnnotation(MyEntity.class)
            listTypeArgument.hasAnnotation(Introspected.class)

        when:
            def saveAll2 = ce.findMethod("saveAll2").get()
            def listTypeArgument2 = saveAll2.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
            listTypeArgument2.hasAnnotation(MyEntity.class)
            listTypeArgument2.hasAnnotation(Introspected.class)

        when:
            def saveAll3 = ce.findMethod("saveAll3").get()
            def listTypeArgument3 = saveAll3.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
            listTypeArgument3.hasAnnotation(MyEntity.class)
            listTypeArgument3.hasAnnotation(Introspected.class)

        when:
            def save2 = ce.findMethod("save2").get()
            def parameter2 = save2.getParameters()[0].getType()
        then:
            parameter2.hasAnnotation(MyEntity.class)
            parameter2.hasAnnotation(Introspected.class)

        when:
            def save3 = ce.findMethod("save3").get()
            def parameter3 = save3.getParameters()[0].getType()
        then:
            parameter3.hasAnnotation(MyEntity.class)
            parameter3.hasAnnotation(Introspected.class)

        when:
            def get = ce.findMethod("get").get()
            def returnType = get.getReturnType()
        then:
            returnType.hasAnnotation(MyEntity.class)
            returnType.hasAnnotation(Introspected.class)
    }

    void "test how the type annotations from the type are propagated"() {
        given:
            ClassElement ce = buildClassElementTransformed('test.MyBean','''\
package test;

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton
import io.micronaut.kotlin.processing.inject.ast.*

@Singleton
class MyBean {
    @Executable
    fun saveAll(books: List<@TypeUseRuntimeAnn MyBook>) {
    }

    @Executable
    fun <@TypeUseRuntimeAnn T : MyBook> saveAll2(book: List<T>) {
    }

    @Executable
    fun <@TypeUseRuntimeAnn T : MyBook> saveAll3(book: List<out T>) {
    }

    @Executable
    fun <T : MyBook> saveAll4(book: List<out @TypeUseRuntimeAnn T>) {
    }

    @Executable
    fun <T : MyBook> saveAll5(book: List<@TypeUseRuntimeAnn T>) {
    }

    @Executable
    fun save2(book: @TypeUseRuntimeAnn MyBook) {
    }

    @Executable
    fun save2x(book: @TypeUseRuntimeAnn MyBook) {
    }

    @Executable
    fun save2xx(book: MyBook) {
    }

    @Executable
    fun <@TypeUseRuntimeAnn T : MyBook> save3(book: T) {
    }

    @Executable
    fun <T : @TypeUseRuntimeAnn MyBook> save4(book: T) {
    }

    @Executable
    fun <T : MyBook> save5(book: @TypeUseRuntimeAnn T) {
    }

    @Executable
    fun get(): @TypeUseRuntimeAnn MyBook? {
        return null
    }
}

'''){
                it.getMethods().forEach { method ->
                    initializeAllTypeArguments(method.getReturnType())
                    initializeAllTypeArguments(method.getGenericReturnType())
                    method.getParameters().each { parameter ->
                        parameter.getAnnotationNames()
                        initializeAllTypeArguments(parameter.getType())
                        initializeAllTypeArguments(parameter.getGenericType())
                    }
                }
                it
            }
        when:
            def saveAll = ce.findMethod("saveAll").get()
            def listTypeArgument = saveAll.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
            validateMyBookArgument(listTypeArgument)

        when:
            def saveAll2 = ce.findMethod("saveAll2").get()
            def listTypeArgument2 = saveAll2.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
            validateMyBookArgument(listTypeArgument2)

        when:
            def saveAll3 = ce.findMethod("saveAll3").get()
            def listTypeArgument3 = saveAll3.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
            validateMyBookArgument(listTypeArgument3)

        when:
            def saveAll4 = ce.findMethod("saveAll4").get()
            def listTypeArgument4 = saveAll4.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
            validateMyBookArgument(listTypeArgument4)

//        when:
//            def saveAll5 = ce.findMethod("saveAll5").get()
//            def listTypeArgument5 = saveAll5.getParameters()[0].getType().getTypeArguments(List).get("E")
//        then:
//            noExceptionThrown()
//            validateMyBookArgument(listTypeArgument5)

        when:
            def save2 = ce.findMethod("save2").get()
            def parameter2 = save2.getParameters()[0].getType()
        then:
            validateMyBookArgument(parameter2)

        when:
            def save3 = ce.findMethod("save3").get()
            def parameter3 = save3.getParameters()[0].getType()
        then:
            validateMyBookArgument(parameter3)

        when:
            def save4 = ce.findMethod("save4").get()
            def parameter4 = save4.getParameters()[0].getType()
        then:
            validateMyBookArgument(parameter4)

//        when:
//            def save5 = ce.findMethod("save5").get()
//            def parameter5 = save5.getParameters()[0].getType()
//        then:
//            validateMyBookArgument(parameter5)

        when:
            def get = ce.findMethod("get").get()
            def returnType = get.getReturnType()
        then:
            validateMyBookArgument(returnType)
    }

    @PendingFeature
    void "test how the type annotations from the type are propagated - pending 1"() {
        given:
            ClassElement ce = buildClassElementTransformed('test.MyBean','''\
package test;

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton
import io.micronaut.kotlin.processing.inject.ast.*

@Singleton
class MyBean {

    @Executable
    fun <T : MyBook> saveAll5(book: List<@TypeUseRuntimeAnn T>) {
    }

    @Executable
    fun save2(book: @TypeUseRuntimeAnn MyBook) {
    }

    @Executable
    fun save2x(book: @TypeUseRuntimeAnn MyBook) {
    }

    @Executable
    fun save2xx(book: MyBook) {
    }

    @Executable
    fun <@TypeUseRuntimeAnn T : MyBook> save3(book: T) {
    }

    @Executable
    fun <T : @TypeUseRuntimeAnn MyBook> save4(book: T) {
    }

    @Executable
    fun <T : MyBook> save5(book: @TypeUseRuntimeAnn T) {
    }

    @Executable
    fun get(): @TypeUseRuntimeAnn MyBook? {
        return null
    }
}

'''){
                it.getMethods().forEach { method ->
                    initializeAllTypeArguments(method.getReturnType())
                    initializeAllTypeArguments(method.getGenericReturnType())
                    method.getParameters().each { parameter ->
                        parameter.getAnnotationNames()
                        initializeAllTypeArguments(parameter.getType())
                        initializeAllTypeArguments(parameter.getGenericType())
                    }
                }
                it
            }
        when:
            def saveAll5 = ce.findMethod("saveAll5").get()
            def listTypeArgument5 = saveAll5.getParameters()[0].getType().getTypeArguments(List).get("E")
        then:
            noExceptionThrown()
            validateMyBookArgument(listTypeArgument5)
    }

    @PendingFeature
    void "test how the type annotations from the type are propagated - pending 2"() {
        given:
            ClassElement ce = buildClassElementTransformed('test.MyBean','''\
package test;

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton
import io.micronaut.kotlin.processing.inject.ast.*

@Singleton
class MyBean {

    @Executable
    fun <T : MyBook> save5(book: @TypeUseRuntimeAnn T) {
    }

}

'''){
                it.getMethods().forEach { method ->
                    initializeAllTypeArguments(method.getReturnType())
                    initializeAllTypeArguments(method.getGenericReturnType())
                    method.getParameters().each { parameter ->
                        parameter.getAnnotationNames()
                        initializeAllTypeArguments(parameter.getType())
                        initializeAllTypeArguments(parameter.getGenericType())
                    }
                }
                it
            }

        when:
            def save5 = ce.findMethod("save5").get()
            def parameter5 = save5.getParameters()[0].getType()
        then:
            validateMyBookArgument(parameter5)

    }

    void validateMyBookArgument(ClassElement classElement) {
        // The class element should have all the annotations present
        assert classElement.hasAnnotation(TypeUseRuntimeAnn.class)
        assert classElement.hasAnnotation(MyEntity.class)
        assert classElement.hasAnnotation(Introspected.class)

        def typeAnnotationMetadata = classElement.getTypeAnnotationMetadata()
        // The type annotations should have only type annotations
        assert typeAnnotationMetadata.hasAnnotation(TypeUseRuntimeAnn.class)
        assert !typeAnnotationMetadata.hasAnnotation(MyEntity.class)
        assert !typeAnnotationMetadata.hasAnnotation(Introspected.class)

        // Get the actual type -> the type shouldn't have any type annotations
        def type = classElement.getType()
        assert !type.hasAnnotation(TypeUseRuntimeAnn.class)
        assert type.hasAnnotation(MyEntity.class)
        assert type.hasAnnotation(Introspected.class)
        assert type.getTypeAnnotationMetadata().isEmpty()
    }

    void "test type annotations cache"() {
        given:
            ClassElement ce = buildClassElementTransformed('test.MyBean','''\
package test

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton
import io.micronaut.kotlin.processing.inject.ast.*

@Singleton
class MyBean {

    var field1: MyBook? = null
    var field2: @TypeUseRuntimeAnn MyBook? = null

    @Executable
    fun save1(book: MyBook) {
    }

    @Executable
    fun save2(book: @TypeUseRuntimeAnn MyBook) {
    }

    @Executable
    fun get1(): MyBook? {
        return null
    }

    @Executable
    fun get2(): @TypeUseRuntimeAnn MyBook? {
        return null
    }

}

'''){
                it.getMethods().forEach { method ->
                    initializeAllTypeArguments(method.getReturnType())
                    initializeAllTypeArguments(method.getGenericReturnType())
                    method.getParameters().each { parameter ->
                        parameter.getAnnotationNames()
                        initializeAllTypeArguments(parameter.getType())
                        initializeAllTypeArguments(parameter.getGenericType())
                    }
                }
                it.getFields().forEach { method ->
                    initializeAllTypeArguments(method.type)
                    initializeAllTypeArguments(method.genericType)
                }
                it
            }

        when:
            def save1 = ce.findMethod("save1").get()
            def parameter1 = save1.getParameters()[0]
            def save2 = ce.findMethod("save2").get()
            def parameter2 = save2.getParameters()[0]
        then:
            parameter1.nativeType != parameter2.nativeType
            parameter1.type.nativeType != parameter2.type.nativeType
            parameter1.genericType.nativeType != parameter2.genericType.nativeType
            parameter1.type.type.nativeType == parameter2.type.type.nativeType
            parameter1.genericType.type.nativeType == parameter2.genericType.type.nativeType

            !parameter1.hasAnnotation(TypeUseRuntimeAnn)
            !parameter1.type.hasAnnotation(TypeUseRuntimeAnn)
            !parameter1.genericType.hasAnnotation(TypeUseRuntimeAnn)

            parameter2.type.hasAnnotation(TypeUseRuntimeAnn)
            parameter2.genericType.hasAnnotation(TypeUseRuntimeAnn)
            !parameter2.hasAnnotation(TypeUseRuntimeAnn)

            !parameter2.type.type.hasAnnotation(TypeUseRuntimeAnn)
            !parameter2.genericType.type.hasAnnotation(TypeUseRuntimeAnn)
        when:
            def get1 = ce.findMethod("get1").get()
            def get2 = ce.findMethod("get2").get()
        then:
            get1.nativeType != get2.nativeType
            get1.returnType.nativeType != get2.returnType.nativeType
            get1.genericReturnType.nativeType != get2.genericReturnType.nativeType
            get1.returnType.type.nativeType == get2.returnType.type.nativeType
            get1.genericReturnType.type.nativeType == get2.genericReturnType.type.nativeType

        when:
            def field1 = ce.findField("field1").get()
            def field2 = ce.findField("field2").get()
        then:
            field1.nativeType != field2.nativeType
            field1.type.nativeType != field2.type.nativeType
            field1.genericType.nativeType != field2.genericType.nativeType
            field1.type.type.nativeType == field2.type.type.nativeType
            field1.genericType.type.nativeType == field2.genericType.type.nativeType


            !field1.hasAnnotation(TypeUseRuntimeAnn)
            !field1.type.hasAnnotation(TypeUseRuntimeAnn)
            !field1.genericType.hasAnnotation(TypeUseRuntimeAnn)

            !field2.hasAnnotation(TypeUseRuntimeAnn)
            field2.type.hasAnnotation(TypeUseRuntimeAnn)
            field2.genericType.hasAnnotation(TypeUseRuntimeAnn)

            !field2.type.type.hasAnnotation(TypeUseRuntimeAnn)
            !field2.genericType.type.hasAnnotation(TypeUseRuntimeAnn)
    }

    void "test generics model"() {
        ClassElement ce = buildClassElement('test.Test', '''
package test

class Test {
    fun method1() : Lst<Lst<Lst<String>>>? {
        return null
    }
}

class Lst<E>

''')
        expect:
            def method1 = ce.findMethod("method1").get()
            def genericType = method1.getGenericReturnType()
            def genericTypeLevel1 = genericType.getTypeArguments()["E"]
            !genericTypeLevel1.isGenericPlaceholder()
            !genericTypeLevel1.isWildcard()
            def genericTypeLevel2 = genericTypeLevel1.getTypeArguments()["E"]
            !genericTypeLevel2.isGenericPlaceholder()
            !genericTypeLevel2.isWildcard()
            def genericTypeLevel3 = genericTypeLevel2.getTypeArguments()["E"]
            !genericTypeLevel3.isGenericPlaceholder()
            !genericTypeLevel3.isWildcard()

            def type = method1.getReturnType()
            def typeLevel1 = type.getTypeArguments()["E"]
            !typeLevel1.isGenericPlaceholder()
            !typeLevel1.isWildcard()
            def typeLevel2 = typeLevel1.getTypeArguments()["E"]
            !typeLevel2.isGenericPlaceholder()
            !typeLevel2.isWildcard()
            def typeLevel3 = typeLevel2.getTypeArguments()["E"]
            !typeLevel3.isGenericPlaceholder()
            !typeLevel3.isWildcard()
    }

    void "test generics model for wildcard"() {
        expect:
        buildClassElement('test.Test', '''
package test

class Test<T> {

    fun method(): Lst<*>? {
        return null
    }
}

class Lst<E>

''') { ce ->
            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("method")).get()
            def genericTypeArgument = method.getGenericReturnType().getTypeArguments()["E"]
            assert !genericTypeArgument.isGenericPlaceholder()
            assert genericTypeArgument.isRawType()
            assert genericTypeArgument.isWildcard()

            def typeArgument = method.getReturnType().getTypeArguments()["E"]
            assert !typeArgument.isGenericPlaceholder()
            assert typeArgument.isRawType()
            assert typeArgument.isWildcard()
        }
    }

    void "test generics model for placeholder"() {
        expect:
        buildClassElement('test.Test', '''
package test

class Test<T> {

    fun method(): Lst<T>? {
        return null
    }
}

class Lst<E>

''') { ce ->
            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("method")).get()
            def genericTypeArgument = method.getGenericReturnType().getTypeArguments()["E"]
            assert genericTypeArgument.isGenericPlaceholder()
            assert !genericTypeArgument.isRawType()
            assert !genericTypeArgument.isWildcard()

            def typeArgument = method.getReturnType().getTypeArguments()["E"]
            assert typeArgument.isGenericPlaceholder()
            assert !typeArgument.isRawType()
            assert !typeArgument.isWildcard()
        }
    }

    void "test generics model for class placeholder wildcard"() {
        expect:
        buildClassElement('test.Test', '''
package test

class Test<T> {

    fun method() : Lst<out T>? {
        return null
    }
}

class Lst<E>

''') { ce ->

            def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("method")).get()
            def genericTypeArgument = method.getGenericReturnType().getTypeArguments()["E"]
            assert !genericTypeArgument.isGenericPlaceholder()
            assert !genericTypeArgument.isRawType()
            assert genericTypeArgument.isWildcard()

            def genericWildcard = genericTypeArgument as WildcardElement
            assert !genericWildcard.lowerBounds
            assert genericWildcard.upperBounds.size() == 1
            def genericUpperBound = genericWildcard.upperBounds[0]
            assert genericUpperBound.name == "java.lang.Object"
            assert genericUpperBound.isGenericPlaceholder()
            assert !genericUpperBound.isWildcard()
            assert !genericUpperBound.isRawType()
            def genericPlaceholderUpperBound = genericUpperBound as GenericPlaceholderElement
            assert genericPlaceholderUpperBound.variableName == "T"
            assert genericPlaceholderUpperBound.declaringElement.get() == ce

            def typeArgument = method.getReturnType().getTypeArguments()["E"]
            assert !typeArgument.isGenericPlaceholder()
            assert !typeArgument.isRawType()
            assert typeArgument.isWildcard()

            def wildcard = genericTypeArgument as WildcardElement
            assert !wildcard.lowerBounds
            assert wildcard.upperBounds.size() == 1
            def upperBound = wildcard.upperBounds[0]
            assert upperBound.name == "java.lang.Object"
            assert upperBound.isGenericPlaceholder()
            assert !upperBound.isWildcard()
            assert !upperBound.isRawType()
            def placeholderUpperBound = upperBound as GenericPlaceholderElement
            assert placeholderUpperBound.variableName == "T"
            assert placeholderUpperBound.declaringElement.get() == ce
        }
    }

    void "test generics model for method placeholder wildcard"() {
        expect:
            buildClassElement('test.Test', '''
package test

class Test {

    fun <T> method(): Lst<out T>? {
        return null
    }
}

class Lst<E>

''') { ce ->
                def method = ce.getEnclosedElement(ElementQuery.ALL_METHODS.named("method")).get()
                assert method.getDeclaredTypeVariables().size() == 1
                assert method.getDeclaredTypeVariables()[0].declaringElement.get() == method
                assert method.getDeclaredTypeVariables()[0].variableName == "T"
                assert method.getDeclaredTypeArguments().size() == 1
                def placeholder = method.getDeclaredTypeArguments()["T"] as GenericPlaceholderElement
                assert placeholder.declaringElement.get() == method
                assert placeholder.variableName == "T"
                def genericTypeArgument = method.getGenericReturnType().getTypeArguments()["E"]
                assert !genericTypeArgument.isGenericPlaceholder()
                assert !genericTypeArgument.isRawType()
                assert genericTypeArgument.isWildcard()

                def genericWildcard = genericTypeArgument as WildcardElement
                assert !genericWildcard.lowerBounds
                assert genericWildcard.upperBounds.size() == 1
                def genericUpperBound = genericWildcard.upperBounds[0]
                assert genericUpperBound.name == "java.lang.Object"
                assert genericUpperBound.isGenericPlaceholder()
                assert !genericUpperBound.isWildcard()
                assert !genericUpperBound.isRawType()
                def genericPlaceholderUpperBound = genericUpperBound as GenericPlaceholderElement
                assert genericPlaceholderUpperBound.variableName == "T"
                assert genericPlaceholderUpperBound.declaringElement.get() == method

                def typeArgument = method.getReturnType().getTypeArguments()["E"]
                assert !typeArgument.isGenericPlaceholder()
                assert !typeArgument.isRawType()
                assert typeArgument.isWildcard()

                def wildcard = genericTypeArgument as WildcardElement
                assert !wildcard.lowerBounds
                assert wildcard.upperBounds.size() == 1
                def upperBound = wildcard.upperBounds[0]
                assert upperBound.name == "java.lang.Object"
                assert upperBound.isGenericPlaceholder()
                assert !upperBound.isWildcard()
                assert !upperBound.isRawType()
                def placeholderUpperBound = upperBound as GenericPlaceholderElement
                assert placeholderUpperBound.variableName == "T"
                assert placeholderUpperBound.declaringElement.get() == method
            }
    }

//    void "test generics model for constructor placeholder wildcard"() {
//        ClassElement ce = buildClassElement('test.Test', '''
//package test;
//import java.util.List;
//
//class Test<T> {
//
//    Test(List<out T> list) {
//    }
//}
//''')
//        expect:
//            def method = ce.getPrimaryConstructor().get()
//            method.getDeclaredTypeVariables().size() == 1
//            method.getDeclaredTypeVariables()[0].declaringElement.get() == method
//            method.getDeclaredTypeVariables()[0].variableName == "T"
//            method.getDeclaredTypeArguments().size() == 1
//            def placeholder = method.getDeclaredTypeArguments()["T"] as GenericPlaceholderElement
//            placeholder.declaringElement.get() == method
//            placeholder.variableName == "T"
//            def genericTypeArgument = method.getParameters()[0].getGenericType().getTypeArguments()["E"]
//            !genericTypeArgument.isGenericPlaceholder()
//            !genericTypeArgument.isRawType()
//            genericTypeArgument.isWildcard()
//
//            def genericWildcard = genericTypeArgument as WildcardElement
//            !genericWildcard.lowerBounds
//            genericWildcard.upperBounds.size() == 1
//            def genericUpperBound = genericWildcard.upperBounds[0]
//            genericUpperBound.name == "java.lang.Object"
//            genericUpperBound.isGenericPlaceholder()
//            !genericUpperBound.isWildcard()
//            !genericUpperBound.isRawType()
//            def genericPlaceholderUpperBound = genericUpperBound as GenericPlaceholderElement
//            genericPlaceholderUpperBound.variableName == "T"
//            genericPlaceholderUpperBound.declaringElement.get() == method
//
//            def typeArgument = method.getParameters()[0].getType().getTypeArguments()["E"]
//            !typeArgument.isGenericPlaceholder()
//            !typeArgument.isRawType()
//            typeArgument.isWildcard()
//
//            def wildcard = genericTypeArgument as WildcardElement
//            !wildcard.lowerBounds
//            wildcard.upperBounds.size() == 1
//            def upperBound = wildcard.upperBounds[0]
//            upperBound.name == "java.lang.Object"
//            upperBound.isGenericPlaceholder()
//            !upperBound.isWildcard()
//            !upperBound.isRawType()
//            def placeholderUpperBound = upperBound as GenericPlaceholderElement
//            placeholderUpperBound.variableName == "T"
//            placeholderUpperBound.declaringElement.get() == method
//    }

    void "test generics equality"() {
        ClassElement ce = buildClassElementTransformed('test.Test', '''
package test

import java.util.List

class Test<T : Number?>(list: List<T>) {

    var number: Number? = null

    var obj: Any? = null

    fun <N : T?> method1(): List<N?>? {
        return null
    }

    fun method2(): List<T>? {
        return null
    }

    fun method3(): T? {
        return null
    }

    fun method4(): List<List<T>>? {
        return null
    }

    fun method5(): List<List<T>>? {
        return null
    }

    fun method6(): Test<T>? {
        return null
    }

    fun method7(): Test<*>? {
        return null
    }

    fun method8(): Test<*>? {
        return null
    }

    fun <N : T?> method9(): Test<out N?>? {
        return null
    }

    fun <N : T?> method10(): Test<in N?>? {
        return null
    }
}
''') {

            it.getPrimaryConstructor().get().getParameters().each {
                it.getGenericType().getAllTypeArguments().values().forEach { m -> m.values().forEach {it.getAllTypeArguments()}}
                it.getType().getAllTypeArguments().values().forEach { m -> m.values().forEach {it.getAllTypeArguments()}}
            }
            it.getMethods().forEach {
                it.getReturnType().getAllTypeArguments().values().forEach { m -> m.values().forEach {it.getAllTypeArguments()}}
                it.getGenericReturnType().getAllTypeArguments().values().forEach { m -> m.values().forEach {it.getAllTypeArguments()}}
            }
            it
        }
        expect:
            def numberType = ce.getFields()[0].getType()

            def constructor = ce.getPrimaryConstructor().get()
            constructor.getParameters()[0].getGenericType().getTypeArguments(List).get("E") == numberType
            constructor.getParameters()[0].getType().getTypeArguments(List).get("E") == numberType

            ce.findMethod("method1").get().getGenericReturnType().getTypeArguments(List).get("E") == numberType
            ce.findMethod("method1").get().getReturnType().getTypeArguments(List).get("E") == numberType

            ce.findMethod("method2").get().getGenericReturnType().getTypeArguments(List).get("E") == numberType
            ce.findMethod("method2").get().getReturnType().getTypeArguments(List).get("E") == numberType

            ce.findMethod("method3").get().getGenericReturnType() == numberType
            ce.findMethod("method3").get().getReturnType() == numberType

            ce.findMethod("method4").get().getGenericReturnType().getTypeArguments(List).get("E").getTypeArguments(List).get("E") == numberType
            ce.findMethod("method4").get().getReturnType().getTypeArguments(List).get("E").getTypeArguments(List).get("E") == numberType

            ce.findMethod("method5").get().getGenericReturnType().getTypeArguments(List).get("E").getTypeArguments(List).get("E") == numberType
            ce.findMethod("method5").get().getReturnType().getTypeArguments(List).get("E").getTypeArguments(List).get("E") == numberType

            ce.findMethod("method6").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
            ce.findMethod("method6").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType

            ce.findMethod("method7").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
            ce.findMethod("method7").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType

            ce.findMethod("method8").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
            ce.findMethod("method8").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType

            ce.findMethod("method9").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
            ce.findMethod("method9").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType

            ce.findMethod("method10").get().getGenericReturnType().getTypeArguments("test.Test").get("T") == numberType
            ce.findMethod("method10").get().getReturnType().getTypeArguments("test.Test").get("T") == numberType
    }

    void "test inherit parameter annotation"() {
        ClassElement ce = buildClassElement('test.UserController', '''
package test

import io.micronaut.kotlin.processing.inject.ast.MyParameter

interface MyApi {
    fun get(@MyParameter("X-username") username: String): String
}

class UserController : MyApi {
    override fun get(username: String): String {
        return "Hello"
    }
}

''')
        expect:
            ce.findMethod("get").get().getParameters()[0].hasAnnotation(MyParameter)
    }

    void "test interface placeholder"() {
        expect:
            buildClassElement('test.MyRepo', '''
package test
import io.micronaut.context.annotation.Prototype
import java.util.List

interface MyRepo : Repo<MyBean, Long>
interface Repo<E, ID> : GenericRepository<E, ID> {
    fun save(ent: E)
}
interface GenericRepository<E, ID>
@Prototype
class MyBean {
    var name: String? = null
}

''') { ce ->

            def repo = ce.getTypeArguments("test.Repo")
            assert repo.get("E").simpleName == "MyBean"
            assert repo.get("E").getSyntheticBeanProperties().size() == 1
            assert repo.get("E").getMethods().size() == 0
            assert repo.get("E").getFields().size() == 1
            assert repo.get("E").getFields().get(0).name == "name"

            def element = ce.findMethod("save").get().getParameters()[0]
            assert element.getGenericType().simpleName == "MyBean"
            assert element.getType().simpleName == "Object"

            def genRepo = ce.getTypeArguments("test.GenericRepository")
            assert genRepo.get("E").simpleName == "MyBean"
            assert genRepo.get("E").getSyntheticBeanProperties().size() == 1
            assert genRepo.get("E").getMethods().size() == 0
            assert genRepo.get("E").getFields().get(0).name == "name"
        }
    }

    void "test interface placeholder 2"() {
        expect:
            buildClassElement('test.MyRepoX', '''
package test
import io.micronaut.context.annotation.Prototype
import java.util.List

interface MyRepoX : RepoX<MyBeanX, Long>
interface RepoX<E, ID> : GenericRepository<E, ID> {
    fun <S : E> save(ent: S)
}
interface GenericRepository<E, ID>
@Prototype
class MyBeanX {
    var name: String? = null
}

''') { ce ->

            def repo = ce.getTypeArguments("test.RepoX")
            assert repo.get("E").simpleName == "MyBeanX"
            assert repo.get("E").getSyntheticBeanProperties().size() == 1
            assert repo.get("E").getMethods().size() == 0
            assert repo.get("E").getFields().size() == 1
            assert repo.get("E").getFields().get(0).name == "name"

            def element = ce.findMethod("save").get().getParameters()[0]
            assert element.getGenericType().simpleName == "MyBeanX"
            assert element.getType().simpleName == "Object"

            def genRepo = ce.getTypeArguments("test.GenericRepository")
            assert genRepo.get("E").simpleName == "MyBeanX"
            assert genRepo.get("E").getSyntheticBeanProperties().size() == 1
            assert genRepo.get("E").getMethods().size() == 0
            assert genRepo.get("E").getFields().get(0).name == "name"
        }
    }

    void "test interface placeholder 2 isAssignable"() {
        when:
        boolean isAssignable = buildClassElementMapped('test.MyRepoX', '''
package test
import io.micronaut.context.annotation.Prototype
import java.util.List

interface MyRepoX : RepoX<MyBeanX, Long>
interface RepoX<E, ID> : GenericRepository<E, ID> {
    fun <S : E> save(ent: S)
}
interface GenericRepository<E, ID>
@Prototype
class MyBeanX {
    var name: String? = null
}

''', { ce ->
            def element = ce.findMethod("save").get().getParameters()[0]
            element.getGenericType().simpleName == "MyBeanX"
            element.getType().simpleName == "Object"
            element.getGenericType().isAssignable("test.MyBeanX")
        })

        then:
            isAssignable
    }

    void "test abstract placeholder"() {
        expect:
        buildClassElement('test.MyRepo2', '''
package test
import io.micronaut.context.annotation.Prototype
import java.util.List

abstract class MyRepo2 : Repo<MyBean, Long>
interface Repo<E, ID> : GenericRepository<E, ID> {
    fun save(ent: E)
}
interface GenericRepository<E, ID>
@Prototype
@io.micronaut.kotlin.processing.inject.ast.MyEntity
class MyBean {
    var name: String? = null
}

''') { ce ->

            def repo = ce.getTypeArguments("test.Repo")
            assert repo.get("E").simpleName == "MyBean"
            assert repo.get("E").hasAnnotation(MyEntity)
            assert repo.get("E").getSyntheticBeanProperties().size() == 1
            assert repo.get("E").getMethods().size() == 0
            assert repo.get("E").getFields().size() == 1
            assert repo.get("E").getFields().get(0).name == "name"

            def element = ce.findMethod("save").get().getParameters()[0]
            assert element.getGenericType().simpleName == "MyBean"
            assert element.getType().simpleName == "Object"

            def genRepo = ce.getTypeArguments("test.GenericRepository")
            assert genRepo.get("E").simpleName == "MyBean"
            assert genRepo.get("E").hasAnnotation(MyEntity)
            assert genRepo.get("E").getSyntheticBeanProperties().size() == 1
            assert genRepo.get("E").getMethods().size() == 0
            assert genRepo.get("E").getFields().get(0).name == "name"
        }
    }

    void "test abstract placeholder 2"() {
        expect:
        buildClassElement('test.MyRepo2', '''
package test
import io.micronaut.context.annotation.Prototype
import java.util.List

abstract class MyRepo2 : Repo<MyBean, Long>()
abstract class Repo<E, ID> : GenericRepository<E, ID> {
    abstract fun save(ent: E)
}
interface GenericRepository<E, ID>
@Prototype
@io.micronaut.kotlin.processing.inject.ast.MyEntity
class MyBean {
    var name: String? = null
}

''') { ce ->

            def repo = ce.getTypeArguments("test.Repo")
            assert repo.get("E").simpleName == "MyBean"
            assert repo.get("E").hasAnnotation(MyEntity)
            assert repo.get("E").getSyntheticBeanProperties().size() == 1
            assert repo.get("E").getMethods().size() == 0
            assert repo.get("E").getFields().size() == 1
            assert repo.get("E").getFields().get(0).name == "name"

            def element = ce.findMethod("save").get().getParameters()[0]
            assert element.getGenericType().simpleName == "MyBean"
            assert element.getType().simpleName == "Object"

            def genRepo = ce.getTypeArguments("test.GenericRepository")
            assert genRepo.get("E").simpleName == "MyBean"
            assert genRepo.get("E").hasAnnotation(MyEntity)
            assert genRepo.get("E").getSyntheticBeanProperties().size() == 1
            assert genRepo.get("E").getMethods().size() == 0
            assert genRepo.get("E").getFields().get(0).name == "name"
            }
    }

    void "test abstract placeholder 3"() {
        expect:
        buildClassElement('test.MyRepo2', '''
package test
import io.micronaut.context.annotation.Prototype
import java.util.List

abstract class MyRepo2 : Repo<MyBean, Long>()
abstract class Repo<E, ID> : GenericRepository<E, ID> {
    abstract fun <S : E> save(ent: S)
}
interface GenericRepository<E, ID>
@Prototype
@io.micronaut.kotlin.processing.inject.ast.MyEntity
class MyBean {
    var name: String? = null
}

''') { ce ->

            def repo = ce.getTypeArguments("test.Repo")
            assert repo.get("E").simpleName == "MyBean"
            assert repo.get("E").hasAnnotation(MyEntity)
            assert repo.get("E").getSyntheticBeanProperties().size() == 1
            assert repo.get("E").getMethods().size() == 0
            assert repo.get("E").getFields().size() == 1
            assert repo.get("E").getFields().get(0).name == "name"

            def element = ce.findMethod("save").get().getParameters()[0]
            assert element.getGenericType().simpleName == "MyBean"
            assert element.getType().simpleName == "Object"

            def genRepo = ce.getTypeArguments("test.GenericRepository")
            assert genRepo.get("E").simpleName == "MyBean"
            assert genRepo.get("E").hasAnnotation(MyEntity)
            assert genRepo.get("E").getSyntheticBeanProperties().size() == 1
            assert genRepo.get("E").getMethods().size() == 0
            assert genRepo.get("E").getFields().get(0).name == "name"
            }
    }

    void "test internal methods"() {
        ClassElement ce = buildClassElement('test.Test', '''
package test
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Prototype
import jakarta.inject.Singleton
import java.util.List

@Prototype
class Test {
    @Executable
    internal fun helloWorld() {
    }
}

''')

        expect:
            ce.findMethod("helloWorld\$main").isPresent()
    }

    void "test type isAssignable"() {
        boolean isAssignable = buildClassElementMapped('test.Test', '''
package test
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Prototype
import jakarta.inject.Singleton
import java.util.List

@Prototype
class Test {
    @Executable
    fun method1() : kotlin.collections.List<String> {
        return listOf()
    }

    @Executable
    fun method2() : java.util.List<String>? {
        return null
    }

    @Executable
    fun method3() : kotlin.collections.List<String>? {
        return listOf()
    }

}

''', ce -> {
            return ce.findMethod("method1").get().getReturnType().isAssignable(Iterable.class)
                    && ce.findMethod("method2").get().getReturnType().isAssignable(Iterable.class)
                    && ce.findMethod("method3").get().getReturnType().isAssignable(Iterable.class)
                    && ((KotlinClassElement) ce.findMethod("method1").get().getReturnType()).isAssignable2(Iterable.class.name)
                    && ((KotlinClassElement) ce.findMethod("method2").get().getReturnType()).isAssignable2(Iterable.class.name)
                    && ((KotlinClassElement) ce.findMethod("method3").get().getReturnType()).isAssignable2(Iterable.class.name)
        })

        expect:
            isAssignable
    }

    void "test type isAssignable between nullable and not nullable"() {
        when:
            boolean isAssignable = buildClassElementMapped('test.Cart', '''
package test

data class CartItem(
        val id: Long?,
        val name: String,
        val cart: Cart?
) {
    constructor(name: String) : this(null, name, null)
}

data class Cart(
        val id: Long?,
        val items: List<CartItem>?
) {

    constructor(items: List<CartItem>) : this(null, items)

    fun cartItemsNotNullable() : List<CartItem> = listOf()
}

''', cl -> cl.getPrimaryConstructor().get().parameters[1].getType().isAssignable(cl.findMethod("cartItemsNotNullable").get().getReturnType()))
        then:
            isAssignable
    }

    void "test override and default methods"() {
        when:
            def result = buildClassElementMapped('test.MyBean', '''
package test

interface MyBean : Parent {

    fun test(): Int

    fun getName() : String {
        return "my-bean"
    }

    @Override
    override fun getDescription() : String {
        return "description"
    }
}

interface Parent {
     fun getParentName() : String {
        return "parent"
    }

    fun getDescription() : String
}

''', cl -> {

                if (!cl.getMethods().stream().map { me -> me.name }.toList().equals(["getParentName", "test", "getName", "getDescription"] as List)) {
                    throw new IllegalStateException("Doesn't match")
                }
                MethodElement getName = cl.getMethods()[2]
                if (getName.isAbstract()) {
                    throw new IllegalStateException("Expected not abstract!")
                }
                if (!getName.isDefault()) {
                    throw new IllegalStateException("Expected default!")
                }
                MethodElement getDescription = cl.getMethods()[3]
                if (getDescription.isAbstract()) {
                    throw new IllegalStateException("Expected not abstract!")
                }
                if (!getDescription.isDefault()) {
                    throw new IllegalStateException("Expected default!")
                }
                return true
            })
        then:
            result
    }

    void "test abstract and overridden methods"() {
        when:
            def result = buildClassElementMapped('test.MyBean2', '''
package test

import java.lang.annotation.*
import io.micronaut.aop.*
import jakarta.inject.*

abstract class MyBean2 : Parent() {

    abstract fun test() : Int

    fun getName() : String {
        return "my-bean"
    }

    @Override
    override fun getDescription() : String {
        return "description"
    }
}

abstract class Parent {
    fun getParentName() : String {
        return "parent"
    }

    abstract fun getDescription() : String
}

interface MyInterface {

    fun getDescription() : String
}


''', cl -> {

                if (!cl.getMethods().stream().map { me -> me.name }.toList().equals(["getParentName", "test", "getName", "getDescription"] as List)) {
                    throw new IllegalStateException("Doesn't match")
                }
                return true
            })
        then:
            result
    }

    void "test abstract and interface and overridden methods"() {
        when:
            def result = buildClassElementMapped('test.MyBean2', '''
package test

import java.lang.annotation.*
import io.micronaut.aop.*
import jakarta.inject.*

abstract class MyBean2 : Parent(), MyInterface {

    abstract fun test() : Int

    fun getName() : String {
        return "my-bean"
    }

    @Override
    override fun getDescription() : String {
        return "description"
    }
}

abstract class Parent {
    fun getParentName() : String {
        return "parent"
    }

    abstract fun getDescription() : String
}

interface MyInterface {

    fun getDescription() : String
}


''', cl -> {

                if (!cl.getMethods().stream().map { me -> me.name }.toList().equals(["getParentName", "test", "getName", "getDescription"] as List)) {
                    throw new IllegalStateException("Doesn't match")
                }
                return true
            })
        then:
            result
    }

    void "test interface type annotations"() {
        ClassElement ce = buildClassElement('test.MyRepo', '''
package test
import jakarta.validation.Valid
import java.util.List

interface MyRepo : Repo<@Valid MyBean, Long> {
}

interface Repo<E, ID> : GenericRepository<E, ID> {

    fun save(entity: E)

}

interface GenericRepository<E, ID>


class MyBean {
}

''')

        when:
            def method = ce.findMethod("save").get()
            def type = method.parameters[0].getGenericType()
        then:
            type.hasAnnotation(Valid)
    }

    void "test element canonicalName"() {
        def ce = buildClassElement('test.TestNamed', '''
package test
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Prototype
import jakarta.inject.Singleton
import java.util.List

@Prototype
class TestNamed {
    @Executable
    fun method1() : kotlin.Int {
        return 111
    }

    @Executable
    fun method2() : kotlin.Int? {
        return null
    }

}

''')

        expect:
            ce.findMethod("method1").get().getReturnType().canonicalName == "int"
            ce.findMethod("method2").get().getReturnType().canonicalName == Integer.class.name
    }

    void "test enum with inner companion"() {
        when:
        ClassElement enumEl = buildClassElement('test.ColorEnum', '''
package test

import com.fasterxml.jackson.annotation.JsonCreator
import com.fasterxml.jackson.annotation.JsonProperty
import com.fasterxml.jackson.annotation.JsonValue
import io.micronaut.core.annotation.Introspected

@Introspected
enum class ColorEnum (
    @get:JsonValue val value: String
) {

    @JsonProperty("red")
    RED("red"),
    @JsonProperty("blue")
    BLUE("blue"),
    @JsonProperty("green")
    GREEN("green"),
    @JsonProperty("light-blue")
    LIGHT_BLUE("light-blue"),
    @JsonProperty("dark-green")
    DARK_GREEN("dark-green");

    override fun toString(): String {
        return value
    }

    companion object {

        @JvmField
        val VALUE_MAPPING = entries.associateBy { it.value }

        @JsonCreator
        @JvmStatic
        fun fromValue(value: String): ColorEnum {
            require(VALUE_MAPPING.containsKey(value)) { "Unexpected value '$value'" }
            return VALUE_MAPPING[value]!!
        }
    }
}

''')
        then:
        ((EnumElement) enumEl).elements().size() == 5
    }

    void "test conf with inner companion"() {
        when:
        ClassElement ce = buildClassElement('test.StripeConfig', '''
package test

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.core.annotation.Introspected
import jakarta.validation.Valid
import jakarta.validation.constraints.NotNull
import io.micronaut.context.annotation.Context

@Context
@ConfigurationProperties(StripeConfig.PREFIX)
class StripeConfig {
    companion object {
        const val PREFIX = "stripe"
    }

    @Valid
    @NotNull
    var usa: UsaStripeClientConfig = UsaStripeClientConfig()

    @Valid
    @NotNull
    var canada: CanadaStripeClientConfig = CanadaStripeClientConfig()

    @Introspected
    interface StripeClientConfig {
        val apiKey: String
        val webhookSecret: String
    }

    @Context
    @ConfigurationProperties(UsaStripeClientConfig.PREFIX)
    @Introspected
    class UsaStripeClientConfig : StripeClientConfig {
        internal companion object {
            internal const val PREFIX = "usa"
        }

        override lateinit var apiKey: String
        override lateinit var webhookSecret: String
    }

    @Context
    @ConfigurationProperties(CanadaStripeClientConfig.PREFIX)
    @Introspected
    class CanadaStripeClientConfig : StripeClientConfig {
        internal companion object {
            internal const val PREFIX = "canada"
        }

        override lateinit var apiKey: String
        override lateinit var webhookSecret: String
    }
}


''')
        then:
            noExceptionThrown()
    }

    void "test enum collection"() {
        def ce = buildClassElement('test.TestNamed', '''
package test
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Prototype
import jakarta.inject.Singleton
import java.util.List

@Prototype
class TestNamed {
    @Executable
    fun method1(coll: Collection<MyType>) : kotlin.Int {
        return 111
    }

}


enum class MyType {
    A, B
}

''')

        def theType = ce.findMethod("method1").get().getParameters()[0].type.firstTypeArgument.get()
        expect:
            theType instanceof GenericElement
            (theType as GenericElement).resolved().isEnum()
    }

    void "test executable visitor"() {
        def result = KotlinCompiler.compile("test.MyClass", '''
package io.micronaut.core.beans

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected

@Introspected
data class TestEntity3(val firstName: String = "Denis",
                       val lastName: String,
                       val job: String? = "IT",
                       val age: Int) {

    @Executable
    fun test4(i: Int? = 88) : String {
        return "$i"
    }

    @Executable
    fun test3(i: Int = 88) : String {
        return "$i"
    }

    @Executable
    fun test2(a: String = "A") : String {
        return a
    }

    @Executable
    fun test1(a: String = "A", b: String, i: Int = 99) : String {
        return "$a $b $i"
    }

}
''', {})
        expect:
            !result.component2().component2().messages.contains("@Nullable on primitive types")
    }

    void "test bean properties interfaces"() {
        def properties = buildClassElementMapped('test.TestNamed', '''
package test
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Prototype
import io.micronaut.kotlin.processing.Pageable
import jakarta.inject.Singleton

class TestNamed {
    fun method() : Pageable? {
        return null
    }

}

''', {ce -> ce.findMethod("method").get().getReturnType().getBeanProperties()})
        expect:
            properties.stream().map {it.getName()}.toList() == [
                    "number",
                    "size",
                    "offset",
                    "sort",
                    "unpaged",
                    "sorted",
                    "orderBy"
            ]
    }

    void "test bean properties 2"() {
        def properties = buildClassElementMapped('test.TestNamed', '''
package test
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Prototype
import io.micronaut.kotlin.processing.Pageable
import io.micronaut.kotlin.processing.Sort.Order
import jakarta.inject.Singleton

class TestNamed {
    fun method() : Pageable? {
        return null
    }

}

''', {ce -> ce.findMethod("method").get().getReturnType().getBeanProperties()
                .stream()
                .filter {it.getName() == "orderBy"}.findFirst()
                .get()
                .getGenericType()
                .getFirstTypeArgument()
                .get()
                .getBeanProperties()})
        expect:
            properties.stream().map {it.getName()}.toList() == [
                    "ignoreCase",
                    "direction",
                    "property",
                    "ascending"
            ]
            properties.stream()
                    .filter { it.getName() == "direction" }
                    .findFirst()
                    .get()
                    .getType()
                    .isEnum()
            properties.stream()
                    .filter { it.getName() == "direction" }
                    .findFirst()
                    .get()
                    .getType() instanceof EnumElement
            properties.stream()
                    .filter { it.getName() == "direction" }
                    .findFirst()
                    .get()
                    .getGenericType() instanceof EnumElement
            properties.stream()
                    .filter { it.getName() == "direction" }
                    .findFirst()
                    .get()
                    .getGenericType()
                    .isEnum()
    }

    void "test nested enum"() {
        def returnType = buildClassElementMapped('test.TestNamed2', '''
package test
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Prototype
import io.micronaut.kotlin.processing.Pageable
import io.micronaut.kotlin.processing.Sort.Order
import io.micronaut.kotlin.processing.Sort.Order.Direction
import jakarta.inject.Singleton

class TestNamed2 {
    fun method() : Direction? {
        return null
    }

}

''', {ce -> ce.findMethod("method").get().getReturnType()})
        expect:
            returnType.isEnum()
            returnType instanceof EnumElement
            returnType.getType().isEnum()
            returnType.getType() instanceof EnumElement
            returnType.getGenericType().isEnum()
            returnType.getType() instanceof EnumElement
    }

    void "test overridden accessor in properties"() {
        def properties = buildClassElementMapped('test.EngineConfiguration', '''
package test

import io.micronaut.core.util.Toggleable

class EngineConfiguration : Toggleable {

    var enabled = true

    val cylinders: Int? = null

    override fun isEnabled(): Boolean {
        return enabled
    }
}

''', {ce -> ce.getBeanProperties()})
        expect:
            properties.size() == 2
    }

    void "test inner class not failing"() {
        def field = buildClassElementMapped('test.TestNamed3', '''
package test
import io.micronaut.context.annotation.Executable
import io.micronaut.context.annotation.Prototype
import io.micronaut.kotlin.processing.Pageable
import io.micronaut.kotlin.processing.Sort.Order
import io.micronaut.kotlin.processing.Sort.Order.Direction
import jakarta.inject.Singleton

class TestNamed3 {
    fun method() : SomeInner? {
        return null
    }

}

class SomeInner {
    private val xyz = object {}
}

''', {ce -> ce.findMethod("method").get().getReturnType().getFields().get(0)})
        expect:
            field.getType().getName() == "java.lang.Object"
            field.getGenericType().getName() == "java.lang.Object"
            field.getType().getType().getName() == "java.lang.Object"
            field.getGenericType().getType().getName() == "java.lang.Object"
    }

    private void assertListGenericArgument(ClassElement type, Closure cl) {
        def arg1 = type.getAllTypeArguments().get(List.class.name).get("E")
        def arg2 = type.getAllTypeArguments().get(Collection.class.name).get("E")
        def arg3 = type.getAllTypeArguments().get(Iterable.class.name).get("T")
        cl.call(arg1)
        cl.call(arg2)
        cl.call(arg3)
    }

    private void initializeAllTypeArguments(ClassElement type) {
        initializeAllTypeArguments0(type, 0)
    }

    private void initializeAllTypeArguments0(ClassElement type, int level) {
        if (level == 4) {
            return
        }
        type.getAnnotationNames()
        type.getTypeAnnotationMetadata().getAnnotationNames()
        initializeAllTypeArguments0(type.getType(), level + 1)
        initializeAllTypeArguments0(type.getGenericType(), level + 1)
        type.getAllTypeArguments().entrySet().forEach { e1 ->
            e1.value.entrySet().forEach { e2 ->
                initializeAllTypeArguments0(e2.value, level + 1)
            }
        }
    }

    private void assertMethodsByName(List<MethodElement> allMethods, String name, List<String> expectedDeclaringTypeSimpleNames) {
        Collection<MethodElement> methods = collectElements(allMethods, name)
        assert expectedDeclaringTypeSimpleNames.size() == methods.size()
        for (String expectedDeclaringTypeSimpleName : expectedDeclaringTypeSimpleNames) {
            assert oneElementPresentWithDeclaringType(methods, expectedDeclaringTypeSimpleName)
        }
    }

    private void assertFieldsByName(List<FieldElement> allFields, String name, List<String> expectedDeclaringTypeSimpleNames) {
        Collection<FieldElement> fields = collectElements(allFields, name)
        assert expectedDeclaringTypeSimpleNames.size() == fields.size()
        for (String expectedDeclaringTypeSimpleName : expectedDeclaringTypeSimpleNames) {
            assert oneElementPresentWithDeclaringType(fields, expectedDeclaringTypeSimpleName)
        }
    }

    private boolean oneElementPresentWithDeclaringType(Collection<MemberElement> elements, String declaringTypeSimpleName) {
        elements.stream()
                .filter { it -> it.getDeclaringType().getSimpleName() == declaringTypeSimpleName }
                .count() == 1
    }

    static <T extends Element> Collection<T> collectElements(List<T> allElements, String name) {
        return allElements.findAll { it.name == name }
    }
}
