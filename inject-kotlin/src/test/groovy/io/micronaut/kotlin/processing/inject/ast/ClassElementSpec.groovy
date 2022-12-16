package io.micronaut.kotlin.processing.inject.ast

import io.micronaut.annotation.processing.test.AbstractKotlinCompilerSpec
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement

class ClassElementSpec extends AbstractKotlinCompilerSpec {

    void "test class element"() {
        expect:
        buildClassElement('ast.test.Test', '''
package ast.test

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
            assert !methodMap['openFunc'].isFinal()
            assert !methodMap['publicFunc'].isPackagePrivate()
            assert !methodMap['publicFunc'].isPrivate()
            assert !methodMap['publicFunc'].isStatic()
            assert !methodMap['publicFunc'].isReflectionRequired()
            assert methodMap['publicFunc'].hasParameters()
            assert methodMap['publicFunc'].isPublic()
            assert methodMap['publicFunc'].owningType.name == 'ast.test.Test'
            assert methodMap['publicFunc'].declaringType.name == 'ast.test.Test'
            assert !methodMap['publicFunc'].isFinal() // should be final? But apparently not
            assert overridden != null
            assert methodMap['publicFunc'].overrides(overridden)
        }
    }
}
