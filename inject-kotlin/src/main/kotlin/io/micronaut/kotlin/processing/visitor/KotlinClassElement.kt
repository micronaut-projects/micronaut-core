package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.*
import com.google.devtools.ksp.symbol.*
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.AnnotationUtil
import io.micronaut.core.annotation.Creator
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.ast.*
import io.micronaut.kotlin.processing.toClassName
import java.util.*
import java.util.function.Predicate
import javax.lang.model.element.NestingKind
import javax.lang.model.element.TypeElement

open class KotlinClassElement(val classType: KSType,
                              annotationMetadata: AnnotationMetadata,
                              visitorContext: KotlinVisitorContext,
                              private val arrayDimensions: Int = 0,
                              private val typeVariable: Boolean = false): AbstractKotlinElement<KSClassDeclaration>(classType.declaration as KSClassDeclaration, annotationMetadata, visitorContext), ArrayableClassElement {

    companion object {
        val CREATOR = "io.micronaut.core.annotation.Creator"
    }

    @OptIn(KspExperimental::class)
    override fun getName(): String {
        return visitorContext.resolver.mapKotlinNameToJava(declaration.qualifiedName!!)?.asString() ?: declaration.toClassName()
    }

    override fun getPackageName(): String {
        return declaration.packageName.asString()
    }

    override fun getSimpleName(): String {
        var parentDeclaration = declaration.parentDeclaration
        if (parentDeclaration == null) {
            return declaration.simpleName.asString()
        } else {
            val builder = StringBuilder(declaration.simpleName.asString())
            while (parentDeclaration != null) {
                builder.insert(0, '$')
                    .insert(0, parentDeclaration.simpleName.asString())
                parentDeclaration = parentDeclaration.parentDeclaration
            }
            return builder.toString()
        }
    }

    override fun getSuperType(): Optional<ClassElement> {
        val superType = declaration.superTypes.firstOrNull {
            val declaration = it.resolve().declaration
            declaration is KSClassDeclaration && declaration.classKind != ClassKind.INTERFACE
        }
        return Optional.ofNullable(superType)
            .map {
                visitorContext.elementFactory.newClassElement(it.resolve())
            }
    }

    override fun isInterface(): Boolean {
        return declaration.classKind == ClassKind.INTERFACE
    }

    override fun isTypeVariable(): Boolean = typeVariable

    override fun isAssignable(type: String): Boolean {
        val ksType = visitorContext.resolver.getClassDeclarationByName(type)?.asStarProjectedType()
        return ksType?.isAssignableFrom(classType) ?: false
    }

    override fun isAssignable(type: ClassElement): Boolean {
        if (type is KotlinClassElement) {
            return type.classType.isAssignableFrom(classType)
        }
        return super.isAssignable(type)
    }

    override fun isAbstract(): Boolean {
        return declaration.isAbstract()
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
        val annotationUtils = visitorContext.getAnnotationUtils()
        val typeParameters = classType.declaration.typeParameters
        if (classType.arguments.isEmpty()) {
            typeParameters.forEach {
                typeArguments[it.name.asString()] = KotlinGenericPlaceholderElement(it, annotationUtils.getAnnotationMetadata(it), visitorContext)
            }
        } else {
            classType.arguments.forEachIndexed { i, argument ->
                val type = argument.type!!
                val typeElement = elementFactory.newClassElement(
                    type.resolve(),
                    annotationUtils.getAnnotationMetadata(name, type),
                    emptyMap(),
                    false)
                typeArguments[typeParameters[i].name.asString()] = typeElement
            }
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
        var method = findDefaultStaticCreator()
        if (method == null) {
            method = findDefaultConstructor()
        }

        return createConstructor(method)
    }

    override fun getPrimaryConstructor(): Optional<MethodElement> {
        var method = findStaticCreator()
        if (method == null) {
            method = findConcreteConstructor()
        }

        return createConstructor(method)
    }

    private fun createConstructor(ctor: KSFunctionDeclaration?): Optional<MethodElement> {
        return Optional.ofNullable(ctor)
            .map { constructor ->
                if (constructor.isConstructor()) {
                    visitorContext.elementFactory.newConstructorElement(
                        this,
                        constructor,
                        visitorContext.getAnnotationUtils().getAnnotationMetadata(constructor)
                    )
                } else {
                    visitorContext.elementFactory.newMethodElement(
                        visitorContext.elementFactory.newClassElement(constructor.closestClassDeclaration()!!.asStarProjectedType()),
                        constructor,
                        visitorContext.getAnnotationUtils().getAnnotationMetadata(constructor)
                    )
                }
            }
    }

    private fun findDefaultConstructor(): KSFunctionDeclaration? {
        val constructors = declaration.getConstructors()
            .filter {
                it.parameters.isEmpty()
            }.toList()

        if (constructors.isEmpty()) {
            return null
        }

        return if (constructors.size == 1) {
            constructors.get(0)
        } else {
            constructors.filter {
                it.modifiers.contains(Modifier.PUBLIC)
            }.firstOrNull()
        }
    }

    private fun findConcreteConstructor(): KSFunctionDeclaration? {
        val nonPrivateConstructors = declaration.getConstructors()
            .filter { ctor -> !ctor.isPrivate() }
            .toList()
        val constructor = if (nonPrivateConstructors.size == 1) {
            nonPrivateConstructors[0]
        } else {
            nonPrivateConstructors.filter { ctor ->
                val annotationMetadata = visitorContext.getAnnotationUtils().getAnnotationMetadata(ctor)
                return@filter annotationMetadata.hasAnnotation(AnnotationUtil.INJECT) ||
                        annotationMetadata.hasAnnotation(Creator::class.java)
            }.firstOrNull() ?: declaration.primaryConstructor
        }
        return constructor
    }

    private fun findStaticCreator(): KSFunctionDeclaration? {
        var creators = findNonPrivateStaticCreators()

        if (creators.isEmpty()) {
            return null
        }
        if (creators.size == 1) {
            return creators[0]
        }

        //Can be multiple static @Creator methods. Prefer one with args here. The no arg method (if present) will
        //be picked up by staticDefaultCreatorFor
        val withArgs = creators.filter { method ->
            method.parameters.isNotEmpty()
        }.toList()

        if (withArgs.size == 1) {
            return withArgs[0]
        } else {
            creators = withArgs
        }

        return creators.firstOrNull(KSFunctionDeclaration::isPublic)
    }

    private fun findDefaultStaticCreator(): KSFunctionDeclaration? {
        val creators = findNonPrivateStaticCreators().filter { func ->
            func.parameters.isEmpty()
        }.toList()

        if (creators.isEmpty()) {
            return null
        }

        if (creators.size == 1) {
            return creators[0]
        }

        return creators.firstOrNull(KSFunctionDeclaration::isPublic)
    }

    private fun findNonPrivateStaticCreators(): List<KSFunctionDeclaration> {
        val companion = declaration.declarations.find { it is KSClassDeclaration && it.isCompanionObject }
        val creators = mutableListOf<KSFunctionDeclaration>()
        if (companion != null) {
            (companion as KSClassDeclaration).getDeclaredFunctions().forEach {
                if (!it.isPrivate() && it.returnType?.resolve()?.declaration == declaration) {
                    if (it.annotations.any { ann ->
                            ann.annotationType.resolve().declaration.qualifiedName!!.asString() == CREATOR
                        }) {
                        creators.add(it)
                    }
                }
            }
            return creators
        }
        return emptyList()
    }

    override fun getEnclosingType(): Optional<ClassElement> {
        if (isInner) {
            val parentDeclaration = declaration.closestClassDeclaration()!!
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
        val classDeclarationElements = mutableMapOf<KSClassDeclaration, ClassElement>(declaration to this)

        var enclosedElements = if (result.isOnlyDeclared) {
            declaration.declarations.toSet().filter { kind.test(it) }
        } else {
            getAllDeclarations().filter { kind.test(it) }
        }

        if (result.isOnlyAbstract) {
            enclosedElements = enclosedElements.filter { declaration -> declaration.modifiers.contains(Modifier.ABSTRACT) }
        } else if (result.isOnlyConcrete) {
            enclosedElements = enclosedElements.filter { declaration -> !declaration.modifiers.contains(Modifier.ABSTRACT) }
        } else if (result.isOnlyInstance) {
            enclosedElements = enclosedElements.filter { declaration ->
                val parent = declaration.parentDeclaration
                return@filter if (parent is KSClassDeclaration) {
                    !parent.isCompanionObject
                } else {
                    false
                }
            }
        }

        val modifierPredicates = result.modifierPredicates
        val namePredicates = result.namePredicates
        val annotationPredicates = result.annotationPredicates
        val typePredicates = result.typePredicates
        val elementPredicates = result.elementPredicates
        val hasNamePredicates = namePredicates.isNotEmpty()
        val hasModifierPredicates = modifierPredicates.isNotEmpty()
        val hasAnnotationPredicates = annotationPredicates.isNotEmpty()
        val hasTypePredicates = typePredicates.isNotEmpty()
        val hasElementPredicates = elementPredicates.isNotEmpty()

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
                val declaringDeclaration = enclosingElement.closestClassDeclaration()!!
                var declaringClass = classDeclarationElements[declaringDeclaration]
                if (declaringClass == null) {
                    declaringClass = elementFactory.newClassElement(declaringDeclaration.asStarProjectedType())
                    classDeclarationElements[declaringDeclaration] = declaringClass
                }
                if (result.elementType == ConstructorElement::class) {
                    element = elementFactory.newConstructorElement(
                        declaringClass,
                        enclosingElement,
                        metadata
                    ) as T
                } else {
                    element = elementFactory.newMethodElement(
                        declaringClass,
                        enclosingElement,
                        metadata
                    ) as T
                }
            } else if (enclosingElement is KSPropertyDeclaration) {
                val declaringDeclaration = enclosingElement.closestClassDeclaration()!!
                var declaringClass = classDeclarationElements[declaringDeclaration]
                if (declaringClass == null) {
                    declaringClass = elementFactory.newClassElement(declaringDeclaration.asStarProjectedType())
                    classDeclarationElements[declaringDeclaration] = declaringClass
                }
                if (result.elementType == PropertyElement::class.java) {
                    element = elementFactory.newPropertyElement(declaringClass, enclosingElement) as T
                } else {
                    element = elementFactory.newFieldElement(
                        declaringClass,
                        enclosingElement,
                        metadata
                    ) as T
                }
            } else if (enclosingElement is KSClassDeclaration) {
                val classElement = elementFactory.newClassElement(
                    enclosingElement.asType(declaration.asStarProjectedType().arguments),
                    metadata
                )

                if (hasTypePredicates) {
                    for (typePredicate in typePredicates) {
                        if (!typePredicate.test(classElement)) {
                            continue@elementLoop
                        }
                    }
                }
                element = classElement as T
            } else {
                element = null
            }

            if (element != null) {
                if (hasElementPredicates) {
                    if (elementPredicates.all { it.test(element) }) {
                        elements.add(element)
                    }
                } else {
                    elements.add(element)
                }
            }
        }

        return elements
    }

    private fun getAllDeclarations(): Set<KSDeclaration>  {
        return getAllDeclarations(declaration, mutableListOf())
    }

    private fun getAllDeclarations(declaration: KSClassDeclaration, excluded: MutableList<KSDeclaration>): Set<KSDeclaration>  {
        val declarations = declaration.declarations
            .filter { !excluded.contains(it) }
            .toMutableSet()
        declarations.forEach {
            if (it is KSFunctionDeclaration) {
                var overridee = it.findOverridee()
                while (overridee != null) {
                    excluded.add(overridee)
                    overridee = (overridee as KSFunctionDeclaration).findOverridee()
                }
            } else if (it is KSPropertyDeclaration) {
                var overridee = it.findOverridee()
                while (overridee != null) {
                    excluded.add(overridee)
                    overridee = overridee.findOverridee()
                }
            }
        }
        declaration.getAllSuperTypes().forEach { superType ->
            declarations.addAll(getAllDeclarations(superType.declaration as KSClassDeclaration, excluded))
        }
        return declarations
    }

    private fun <T : Element?> getElementKind(elementType: Class<T>): Predicate<KSDeclaration> {
        return when (elementType) {
            MethodElement::class.java -> {
                Predicate { declaration -> declaration is KSFunctionDeclaration }
            }
            FieldElement::class.java -> {
                Predicate { declaration -> declaration is KSPropertyDeclaration && declaration.hasBackingField }
            }
            ConstructorElement::class.java -> {
                Predicate { declaration -> declaration is KSFunctionDeclaration && declaration.isConstructor() }
            }
            ClassElement::class.java -> {
                Predicate { declaration -> declaration is KSClassDeclaration }
            }
            PropertyElement::class.java -> {
                Predicate { declaration -> declaration is KSPropertyDeclaration }
            }
            else -> throw IllegalArgumentException("Unsupported element type for query: $elementType")
        }
    }

    override fun getBeanProperties(): MutableList<PropertyElement> {
        val annotationUtils = visitorContext.getAnnotationUtils()
        val elementFactory = visitorContext.elementFactory
        val propertyList : MutableList<PropertyElement> = declaration.getAllProperties()
            .filter { !it.isPrivate() }
            .map {
                elementFactory.newPropertyElement(this, it)
            }
            .toMutableList()
        val functionBasedProperties: MutableMap<String, GetterAndSetter> = mutableMapOf()
        getAllDeclarations()
            .filterIsInstance<KSFunctionDeclaration>()
            .filter { func -> !func.isPrivate() }
            .forEach { func ->
            val name = func.simpleName.asString()
            val isGetter = NameUtils.isGetterName(name)
            val isSetter = NameUtils.isSetterName(name) && func.parameters.size == 1
            if (isGetter || isSetter) {
                val classDeclaration = func.closestClassDeclaration()!!

                val declaringType = if (declaration == classDeclaration) {
                    this
                } else {
                    val ksType = declaration.getAllSuperTypes()
                        .find { it.declaration == classDeclaration }
                    elementFactory.newClassElement(ksType!!)
                }

                val propertyName: String
                val type: ClassElement
                if (isGetter) {
                    type = elementFactory.newClassElement(func.returnType!!.resolve(), declaringType.typeArguments)
                    propertyName = NameUtils.getPropertyNameForGetter(name)
                } else {
                    type = elementFactory.newClassElement(func.parameters[0].type.resolve(), declaringType.typeArguments)
                    propertyName = NameUtils.getPropertyNameForSetter(name)
                }

                var getterAndSetter = functionBasedProperties[propertyName]
                if (getterAndSetter == null) {
                    getterAndSetter = GetterAndSetter(type, this)
                    functionBasedProperties[propertyName] = getterAndSetter
                }
                if (isGetter) {
                    getterAndSetter.getter = func
                } else {
                    getterAndSetter.setter = func
                }
            }
        }
        functionBasedProperties.forEach { (name, getterSetter) ->
            if (getterSetter.getter != null) {
                val parents: List<KSAnnotated> = if (getterSetter.setter != null) {
                    listOf(getterSetter.setter!!)
                } else {
                    emptyList()
                }
                val annotationMetadata = if (!parents.isEmpty()) {
                    annotationUtils.getAnnotationMetadata(
                        parents,
                        getterSetter.getter!!
                    )
                } else {
                    annotationUtils.getAnnotationMetadata(getterSetter.getter!!)
                }
                propertyList.add(KotlinPropertyElement(
                    getterSetter.declaringType,
                    getterSetter.type,
                    name,
                    getterSetter.getter!!,
                    getterSetter.setter,
                    annotationMetadata,
                    visitorContext))
            }
        }
        return propertyList
    }

    private class GetterAndSetter(val type: ClassElement,
                                  val declaringType: ClassElement) {
        var getter: KSFunctionDeclaration? = null
        var setter: KSFunctionDeclaration? = null
    }
}
