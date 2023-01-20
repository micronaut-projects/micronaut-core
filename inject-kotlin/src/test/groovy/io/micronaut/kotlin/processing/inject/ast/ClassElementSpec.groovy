package io.micronaut.kotlin.processing.inject.ast

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement
import spock.lang.PendingFeature

class ClassElementSpec extends AbstractKotlinCompilerSpec {

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
            assert methodMap.keySet() == ['publicFunc',  'parentFunc', 'openFunc', 'privateFunc', 'protectedFunc', 'suspendFunc', 'getConventionProp', 'setConventionProp'] as Set
            assert declaredMethodMap.keySet()  == ['publicFunc', 'openFunc', 'privateFunc', 'protectedFunc', 'suspendFunc', 'getConventionProp', 'setConventionProp'] as Set
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

    void "test class element generics"() {
        expect:
        buildClassElement('ast.test.Test', '''
package ast.test

class Test(
    val constructorProp : String) : Parent<String>(constructorProp), One<String> {

    val publicReadOnlyProp : Boolean = true
    override val size: Int = 10
    override fun get(index: Int): String {
        return "ok"
    }

    open fun openFunc(name : String) : String {
        return "ok"
    }

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
            assert methodMap['parentFunc'].returnType.simpleName == 'CharSequence'
            assert methodMap['parentFunc'].genericReturnType.simpleName == 'String'
            assert methodMap['parentFunc'].parameters[0].type.simpleName == 'CharSequence'
            assert methodMap['parentFunc'].parameters[0].genericType.simpleName == 'String'
        }
    }
}
