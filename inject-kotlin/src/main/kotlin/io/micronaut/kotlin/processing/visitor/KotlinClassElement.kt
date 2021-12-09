package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.outerType
import com.google.devtools.ksp.symbol.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.inject.ast.*
import java.util.*
import java.util.function.Predicate

open class KotlinClassElement(protected val classType: KSType,
                         annotationMetadata: AnnotationMetadata,
                         visitorContext: KotlinVisitorContext,
                         private val arrayDimensions: Int = 0): AbstractKotlinElement(classType.declaration, annotationMetadata, visitorContext), ArrayableClassElement {

    val classDeclaration: KSDeclaration = classType.declaration

    @OptIn(KspExperimental::class)
    override fun getName(): String {
        val qualifiedName = classDeclaration.qualifiedName!!
        return (visitorContext.resolver.mapKotlinNameToJava(qualifiedName) ?: qualifiedName).asString()
    }

    override fun isAssignable(type: String): Boolean {
        val ksType = visitorContext.resolver.getClassDeclarationByName(type)?.asStarProjectedType()
        return if (ksType != null && classDeclaration is KSClassDeclaration) {
            classDeclaration.asStarProjectedType().isAssignableFrom(ksType)
        } else {
            false
        }
    }

    override fun isArray(): Boolean {
        return arrayDimensions > 0
    }

    override fun getArrayDimensions(): Int {
        return arrayDimensions
    }

    override fun withArrayDimensions(arrayDimensions: Int): ClassElement {
        return KotlinClassElement(classType, annotationMetadata, visitorContext, arrayDimensions)
    }

    override fun isInner(): Boolean {
        return classType.outerType != null
    }

    override fun getTypeArguments(): Map<String, ClassElement> {
        val typeArguments = mutableMapOf<String, ClassElement>()
        val elementFactory = visitorContext.elementFactory
        classType.declaration.typeParameters.forEach {
            typeArguments[it.name.asString()] = elementFactory.newClassElement(it.bounds.first().resolve())
        }
        return typeArguments
    }

    override fun getTypeArguments(type: String): Map<String, ClassElement> {
        return emptyMap()
    }

    override fun getAllTypeArguments(): Map<String, Map<String, ClassElement>> {
        return emptyMap()
    }

    override fun getDefaultConstructor(): Optional<MethodElement> {
        if (classDeclaration !is KSClassDeclaration) {
            return Optional.empty()
        }
        val constructors = classDeclaration.getConstructors()
            .filter {
                it.parameters.isEmpty()
            }.toList()

        if (constructors.isEmpty()) {
            return Optional.empty()
        }

        val constructor = if (constructors.size == 1) {
           constructors.get(0)
        } else {
            constructors.filter {
                it.modifiers.contains(Modifier.PUBLIC)
            }.firstOrNull()
        }

        return Optional.ofNullable(constructor)
            .map { ctor ->
                visitorContext.elementFactory.newConstructorElement(
                    this,
                    ctor,
                    visitorContext.getAnnotationUtils().getAnnotationMetadata(ctor)
                )
            }
    }

    override fun getPrimaryConstructor(): Optional<MethodElement> {
        return Optional.ofNullable((classDeclaration as KSClassDeclaration).primaryConstructor)
            .map { ctor ->
                visitorContext.elementFactory.newConstructorElement(
                    this,
                    ctor,
                    visitorContext.getAnnotationUtils().getAnnotationMetadata(ctor)
                )
            }
    }

    override fun getEnclosingType(): Optional<ClassElement> {
        if (isInner) {
            val parentDeclaration = classDeclaration.parentDeclaration as KSClassDeclaration
            return Optional.of(
                visitorContext.elementFactory.newClassElement(
                    classType.outerType!!,
                    visitorContext.getAnnotationUtils().getAnnotationMetadata(parentDeclaration)
                )
            )
        }
        return Optional.empty()
    }

    override fun <T : Element> getEnclosedElements(query: ElementQuery<T>): MutableList<T> {
        val result = query.result()
        val kind = getElementKind(result.elementType)
        var enclosedElements = (classDeclaration as KSClassDeclaration).declarations.filter { kind.test(it) }

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
                if (enclosingElement is KSPropertyDeclaration) {
                    // the backing fields of properties are always private
                    if (result.elementType == FieldElement::class.java) {
                        continue
                    }
                }
                if (enclosingElement.modifiers.contains(Modifier.PRIVATE)) {
                    continue
                }
                val onlyAccessibleFrom = result.onlyAccessibleFromType.orElse(this)
                val accessibleFrom = onlyAccessibleFrom.nativeType
                // if the outer element of the enclosed element is not the current class
                // we need to check if it package private and within a different package so it can be excluded
                if (enclosingElement !== accessibleFrom && enclosingElement.modifiers.contains(Modifier.INTERNAL)) {
                    TODO("how to determine if element is in module")
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
                    enclosingElement.asType(classDeclaration.asStarProjectedType().arguments),
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
                Predicate { declaration ->  declaration is KSPropertyDeclaration && declaration.hasBackingField }
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
        val annotationUtils = visitorContext.getAnnotationUtils()
        val elementFactory = visitorContext.elementFactory
        val typeArguments = typeArguments
        return (classDeclaration as KSClassDeclaration).getAllProperties().map {
            val type = it.type.resolve()

            KotlinPropertyElement(this,
                elementFactory.newClassElement(type, typeArguments),
                it,
                annotationUtils.getAnnotationMetadata(it),
                visitorContext)
        }.toMutableList()
    }
}
