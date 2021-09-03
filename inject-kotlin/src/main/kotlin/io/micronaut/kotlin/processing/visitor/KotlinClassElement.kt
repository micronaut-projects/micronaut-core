package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.*
import io.micronaut.inject.ast.*
import java.util.*
import java.util.function.Predicate
import javax.lang.model.element.ElementKind
import javax.lang.model.element.ExecutableElement
import javax.lang.model.element.TypeElement
import javax.lang.model.element.VariableElement

class KotlinClassElement(private val classDeclaration: KSClassDeclaration,
                         private val visitorContext: KotlinVisitorContext,
                         private val arrayDimensions: Int = 0): AbstractKotlinElement(classDeclaration), ArrayableClassElement {

    override fun getName(): String {
        return classDeclaration.qualifiedName!!.asString()
    }

    override fun isAssignable(type: String?): Boolean {
        TODO("Not yet implemented")
    }

    override fun isArray(): Boolean {
        return arrayDimensions > 0
    }

    override fun getArrayDimensions(): Int {
        return arrayDimensions
    }

    override fun withArrayDimensions(arrayDimensions: Int): ClassElement {
        return KotlinClassElement(classDeclaration, visitorContext, arrayDimensions);
    }

    override fun isInner(): Boolean {
        return classDeclaration.parentDeclaration is KSClassDeclaration
    }

    override fun getEnclosingType(): Optional<ClassElement> {
        if (isInner) {
            val parentDeclaration = classDeclaration.parentDeclaration as KSClassDeclaration
            return Optional.of(
                visitorContext.elementFactory.newClassElement(
                    parentDeclaration,
                    visitorContext.getAnnotationUtils().getAnnotationMetadata(parentDeclaration)
                )
            )
        }
        return Optional.empty()
    }

    override fun <T : Element> getEnclosedElements(query: ElementQuery<T>): MutableList<T> {
        val result = query.result()
        val kind = getElementKind(result.elementType)
        var enclosedElements = classDeclaration.declarations.filter { kind.test(it) }

        if (result.isOnlyDeclared) {
            enclosedElements = enclosedElements.filter { declaration ->  declaration.parentDeclaration == classDeclaration }
        }
        if (result.isOnlyAbstract) {
            enclosedElements = enclosedElements.filter { declaration -> declaration.modifiers.contains(Modifier.ABSTRACT) }
        } else if (result.isOnlyConcrete) {
            enclosedElements = enclosedElements.filter { declaration -> !declaration.modifiers.contains(Modifier.ABSTRACT) }
        } else if (result.isOnlyInstance) {
            enclosedElements = enclosedElements.filter { declaration ->
                val parent = declaration.parentDeclaration
                return@filter if (parent is KSClassDeclaration) {
                    parent.isCompanionObject
                } else {
                    false
                }
            }
        }

        val modifierPredicates = result.modifierPredicates
        val namePredicates = result.namePredicates
        val annotationPredicates = result.annotationPredicates
        val typePredicates = result.typePredicates
        val hasNamePredicates = namePredicates.isNotEmpty()
        val hasModifierPredicates = modifierPredicates.isNotEmpty()
        val hasAnnotationPredicates = annotationPredicates.isNotEmpty()
        val hasTypePredicates = typePredicates.isNotEmpty()

        val elements = ArrayList<T>()

        elementLoop@ for (enclosingElement in enclosedElements) {

            if (result.isOnlyAccessible) {
                if (enclosingElement.modifiers.contains(Modifier.PRIVATE)) {
                    continue
                } else {
                    val onlyAccessibleFrom = result.onlyAccessibleFromType.orElse(this)
                    val accessibleFrom = onlyAccessibleFrom.nativeType
                    // if the outer element of the enclosed element is not the current class
                    // we need to check if it package private and within a different package so it can be excluded
                    if (enclosingElement !== accessibleFrom && enclosingElement.modifiers.contains(Modifier.INTERNAL)) {
                        TODO("how to determine if element is in module")
                    }
                }
            }

            if (hasModifierPredicates) {
                val modifiers: Set<ElementModifier> = enclosingElement.modifiers.mapNotNull { m ->
                    var name = m.name
                    if (m.name.startsWith("JAVA_")) {
                        name = m.name.substring(4)
                    }
                    return@mapNotNull try {
                        ElementModifier.valueOf(name)
                    } catch (e: IllegalArgumentException) {
                        null
                    }
                }.toSet()
                for (modifierPredicate in modifierPredicates) {
                    if (!modifierPredicate.test(modifiers)) {
                        continue@elementLoop
                    }
                }
            }

            if (hasNamePredicates) {
                for (namePredicate in namePredicates) {
                    if (!namePredicate.test(enclosingElement.simpleName.asString())) {
                        continue@elementLoop
                    }
                }
            }

            val metadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(enclosingElement)
            if (hasAnnotationPredicates) {
                for (annotationPredicate in annotationPredicates) {
                    if (!annotationPredicate.test(metadata)) {
                        continue@elementLoop
                    }
                }
            }

            var element: T?
            val elementFactory = visitorContext.elementFactory

            if (enclosingElement is KSFunctionDeclaration) {
                if (result.elementType == ConstructorElement::class) {
                    element = elementFactory.newConstructorElement(
                        this,
                        enclosingElement,
                        metadata
                    ) as T
                } else {
                    element = elementFactory.newMethodElement(
                        this,
                        enclosingElement,
                        metadata
                    ) as T
                }
            } else if (enclosingElement is KSPropertyDeclaration) {
                element =  elementFactory.newFieldElement(
                    this,
                    enclosingElement,
                    metadata
                ) as T
            } else if (enclosingElement is KSClassDeclaration) {
                element = elementFactory.newClassElement(
                    enclosingElement,
                    metadata
                ) as T
            } else {
                element = null
            }

            if (element != null) {
                elements.add(element)
            }
        }

        return elements
    }

    private fun <T : Element?> getElementKind(elementType: Class<T>): Predicate<KSDeclaration> {
        return when (elementType) {
            MethodElement::class.java -> {
                Predicate { declaration ->  declaration is KSFunctionDeclaration }
            }
            FieldElement::class.java -> {
                Predicate { declaration ->  declaration is KSPropertyDeclaration }
            }
            ConstructorElement::class.java -> {
                Predicate { declaration ->  declaration is KSFunctionDeclaration && declaration.functionKind == FunctionKind.TOP_LEVEL }
            }
            ClassElement::class.java -> {
                Predicate { declaration -> declaration is KSClassDeclaration }
            }
            else -> throw IllegalArgumentException("Unsupported element type for query: $elementType")
        }
    }

    override fun getBeanProperties(): MutableList<PropertyElement> {
        TODO("not yet implemented")
    }
}
