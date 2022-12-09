/*
 * Copyright 2017-2022 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.*
import com.google.devtools.ksp.symbol.*
import io.micronaut.context.annotation.BeanProperties
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.NonNull
import io.micronaut.inject.ast.*
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.ast.utils.AstBeanPropertiesUtils
import io.micronaut.inject.ast.utils.EnclosedElementsQuery
import io.micronaut.kotlin.processing.toClassName
import java.lang.IllegalArgumentException
import java.util.*
import java.util.function.Function
import java.util.stream.Stream

open class KotlinClassElement(protected val classType: KSType,
                              protected val classDeclaration: KSClassDeclaration,
                              private val annotationInfo: KSAnnotated,
                              private val elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                              visitorContext: KotlinVisitorContext,
                              protected val resolvedGenerics: Map<String, ClassElement>,
                              private val arrayDimensions: Int = 0,
                              private val typeVariable: Boolean = false): AbstractKotlinElement<KSAnnotated>(annotationInfo, elementAnnotationMetadataFactory, visitorContext), ArrayableClassElement {

    constructor(
        ref: KSAnnotated,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext,
        resolvedGenerics: Map<String, ClassElement>,
        arrayDimensions: Int = 0,
        typeVariable: Boolean = false
    ) : this(getType(ref), getDeclaration(ref), ref, elementAnnotationMetadataFactory, visitorContext, resolvedGenerics, arrayDimensions, typeVariable)

    constructor(
        type: KSType,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext,
        resolvedGenerics: Map<String, ClassElement>,
        arrayDimensions: Int = 0,
        typeVariable: Boolean = false
    ) : this(type, type.declaration as KSClassDeclaration, type.declaration as KSClassDeclaration, elementAnnotationMetadataFactory, visitorContext, resolvedGenerics, arrayDimensions, typeVariable)

    val outerType: KSType?
    private var resolvedProperties : List<PropertyElement>? = null
    private val enclosedElementsQuery = KotlinEnclosedElementsQuery()
    private var nativeProperties  : List<PropertyElement>? = null

    init {
        val outerDecl = classDeclaration.parentDeclaration as? KSClassDeclaration
        outerType = outerDecl?.asType(classType.arguments.subList(classDeclaration.typeParameters.size, classType.arguments.size))
    }

    companion object Helper {
        fun getType(ref: KSAnnotated) : KSType {
            if (ref is KSType) {
                return ref
            } else if (ref is KSTypeReference) {
                return ref.resolve()
            } else if (ref is KSTypeParameter) {
                return ref.bounds.first().resolve()
            } else if (ref is KSClassDeclaration) {
                return ref.asStarProjectedType()
            } else if (ref is KSTypeArgument) {
                val ksType = ref.type?.resolve()
                if (ksType != null) {
                    return ksType
                } else {
                    throw IllegalArgumentException("Unresolvable type argument $ref")
                }
            } else if (ref is KSTypeAlias) {
                return ref.type.resolve()
            } else {
                throw IllegalArgumentException("Not a type $ref")
            }
        }

        fun getDeclaration(ref: KSAnnotated) : KSClassDeclaration {
            if (ref is KSType) {
                return ref.declaration as KSClassDeclaration
            } else if (ref is KSTypeReference) {
                return ref.resolve().declaration as KSClassDeclaration
            } else if (ref is KSTypeParameter) {
                return ref.bounds.first().resolve().declaration as KSClassDeclaration
            } else if (ref is KSClassDeclaration) {
                return ref
            } else if (ref is KSTypeArgument) {
                val ksType = ref.type?.resolve()
                if (ksType != null) {
                    return ksType.declaration as KSClassDeclaration
                } else {
                    throw IllegalArgumentException("Unresolvable type argument $ref")
                }
            } else if (ref is KSTypeAlias) {
                return ref.type.resolve().declaration as KSClassDeclaration
            } else {
                throw IllegalArgumentException("Not a type $ref")
            }
        }
    }

    @OptIn(KspExperimental::class)
    override fun getName(): String {
        return visitorContext.resolver.mapKotlinNameToJava(classDeclaration.qualifiedName!!)?.asString() ?: classDeclaration.toClassName()
    }

    override fun getPackageName(): String {
        return classDeclaration.packageName.asString()
    }

    override fun getSyntheticBeanProperties(): List<PropertyElement> {
        // Native properties should be composed of field + synthetic getter/setter
        if (nativeProperties == null) {
            nativeProperties = classDeclaration.getAllProperties()
                .filter { !it.isInternal() && !it.isPrivate() }
                .map { KotlinPropertyElement(
                    this,
                    visitorContext.elementFactory.newClassElement(it.type.resolve(), elementAnnotationMetadataFactory, resolvedGenerics),
                    it,
                    elementAnnotationMetadataFactory, visitorContext
                ) }
                .toList()
        }
        return nativeProperties!!
    }

    override fun getAccessibleStaticCreators(): MutableList<MethodElement> {
        val staticCreators: MutableList<MethodElement> = mutableListOf()
        staticCreators.addAll(super.getAccessibleStaticCreators())
        return if (staticCreators.isNotEmpty()) {
            staticCreators
        } else visitorContext.getClassElement("$name\$Companion", elementAnnotationMetadataFactory)
            .filter { it.isStatic }
            .flatMap { typeElement: ClassElement ->
                typeElement.getEnclosedElements(
                    ElementQuery.ALL_METHODS
                        .annotated { annotationMetadata: AnnotationMetadata ->
                            annotationMetadata.hasStereotype(
                                Creator::class.java
                            )
                        }
                ).stream().findFirst()
            }
            .filter { method: MethodElement -> !method.isPrivate && method.returnType == this }
            .map { o: MethodElement ->
                mutableListOf(
                    o
                )
            }.orElse(mutableListOf())
    }

    override fun getBeanProperties(): List<PropertyElement> {
        if (resolvedProperties == null) {
            resolvedProperties = getBeanProperties(PropertyElementQuery.of(this))
        }
        return Collections.unmodifiableList(resolvedProperties!!)
    }

    override fun getBeanProperties(propertyElementQuery: PropertyElementQuery): MutableList<PropertyElement> {
        val customReaderPropertyNameResolver =
            Function<MethodElement, Optional<String>> { Optional.empty() }
        val customWriterPropertyNameResolver =
            Function<MethodElement, Optional<String>> { Optional.empty() }
        val accessKinds = propertyElementQuery.accessKinds
        val fieldAccess = accessKinds.contains(BeanProperties.AccessKind.FIELD) && !propertyElementQuery.accessKinds.contains(BeanProperties.AccessKind.METHOD)
        if (fieldAccess) {
            // all kotlin fields are private
            return mutableListOf()
        }

        val allProperties = classDeclaration.getAllProperties()
            .filter { !it.isInternal() }
            .filter { !propertyElementQuery.excludes.contains(it.simpleName.asString()) }
            .filter { propertyElementQuery.includes.isEmpty() || propertyElementQuery.includes.contains(it.simpleName.asString()) }
            .filter {
                val visibility = propertyElementQuery.visibility
                if (visibility == BeanProperties.Visibility.PUBLIC) {
                    it.isPublic()
                } else {
                    !it.isPrivate()
                }
            }
        val propertyNames = allProperties.map { it.simpleName.asString() }.toSet()

        val resolvedProperties : MutableList<PropertyElement> = mutableListOf()
        val methodProperties = AstBeanPropertiesUtils.resolveBeanProperties(propertyElementQuery,
            this,
            {
                getEnclosedElements(
                    ElementQuery.ALL_METHODS
                )
            },
            {
                emptyList()
            },
            false, propertyNames,
            customReaderPropertyNameResolver,
            customWriterPropertyNameResolver,
            { value: AstBeanPropertiesUtils.BeanPropertyData ->
                this.mapToPropertyElement(
                    value
                )
            })
        resolvedProperties.addAll(methodProperties)
        val kotlinPropertyElements = toKotlinProperties(allProperties, propertyElementQuery)
        resolvedProperties.addAll(kotlinPropertyElements)
        return resolvedProperties
    }

    private fun toKotlinProperties(
        allProperties: Sequence<KSPropertyDeclaration>,
        propertyElementQuery: PropertyElementQuery
    ): Sequence<KotlinPropertyElement> {
        val kotlinPropertyElements = allProperties.map {
            KotlinPropertyElement(
                this,
                visitorContext.elementFactory.newClassElement(
                    it.type.resolve(),
                    elementAnnotationMetadataFactory,
                    resolvedGenerics
                ),
                it,
                elementAnnotationMetadataFactory, visitorContext
            )
        }.filter { prop ->
            val excludedAnnotations = propertyElementQuery.excludedAnnotations
            excludedAnnotations.isEmpty() || !excludedAnnotations.any { prop.hasAnnotation(it) }
        }
        return kotlinPropertyElements
    }

    private fun mapToPropertyElement(value: AstBeanPropertiesUtils.BeanPropertyData): KotlinPropertyElement {
        return KotlinPropertyElement(
            this@KotlinClassElement,
            value.type,
            value.propertyName,
            value.field,
            value.getter,
            value.setter,
            elementAnnotationMetadataFactory,
            visitorContext
        )
    }

    override fun getSimpleName(): String {
        var parentDeclaration = classDeclaration.parentDeclaration
        return if (parentDeclaration == null) {
            classDeclaration.simpleName.asString()
        } else {
            val builder = StringBuilder(classDeclaration.simpleName.asString())
            while (parentDeclaration != null) {
                builder.insert(0, '$')
                    .insert(0, parentDeclaration.simpleName.asString())
                parentDeclaration = parentDeclaration.parentDeclaration
            }
            builder.toString()
        }
    }

    override fun getSuperType(): Optional<ClassElement> {
        val superType = classDeclaration.superTypes.firstOrNull {
            val declaration = it.resolve().declaration
            declaration is KSClassDeclaration && declaration.classKind != ClassKind.INTERFACE
        }
        return Optional.ofNullable(superType)
            .map {
                visitorContext.elementFactory.newClassElement(it.resolve())
            }
    }

    override fun getInterfaces(): Collection<ClassElement> {
        return classDeclaration.superTypes.map { it.resolve() }.filter {
            val declaration = it.declaration
            declaration is KSClassDeclaration && declaration.classKind == ClassKind.INTERFACE
        }.map {
            visitorContext.elementFactory.newClassElement(it)
        }.toList()
    }

    override fun isInterface(): Boolean {
        return classDeclaration.classKind == ClassKind.INTERFACE
    }

    override fun isTypeVariable(): Boolean = typeVariable

    @OptIn(KspExperimental::class)
    override fun isAssignable(type: String): Boolean {
        var ksType = visitorContext.resolver.getClassDeclarationByName(type)?.asStarProjectedType()
        if (ksType != null) {
            if (ksType.isAssignableFrom(classType)) {
                return true
            }
            val kotlinName = visitorContext.resolver.mapJavaNameToKotlin(
                visitorContext.resolver.getKSNameFromString(type))
            if (kotlinName != null) {
                ksType = visitorContext.resolver.getKotlinClassByName(kotlinName)?.asStarProjectedType()
                if (ksType != null) {
                    if (classType.starProjection().isAssignableFrom(ksType)) {
                        return true
                    }
                }
            }
        }
        return ksType?.isAssignableFrom(classType) ?: false
    }

    override fun isAssignable(type: ClassElement): Boolean {
        if (type is KotlinClassElement) {
            return type.classType.isAssignableFrom(classType)
        }
        return super.isAssignable(type)
    }

    override fun copyThis(): KotlinClassElement {
        return KotlinClassElement(
            classType, classDeclaration, annotationInfo, elementAnnotationMetadataFactory, visitorContext, resolvedGenerics)
    }

    override fun isAbstract(): Boolean {
        return classDeclaration.isAbstract()
    }

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata): ClassElement {
        return super<AbstractKotlinElement>.withAnnotationMetadata(annotationMetadata) as ClassElement
    }

    override fun isArray(): Boolean {
        return arrayDimensions > 0
    }

    override fun getArrayDimensions(): Int {
        return arrayDimensions
    }

    override fun withArrayDimensions(arrayDimensions: Int): ClassElement {
        return KotlinClassElement(classType, classDeclaration, annotationInfo, elementAnnotationMetadataFactory, visitorContext, resolvedGenerics, arrayDimensions, typeVariable)
    }

    override fun isInner(): Boolean {
        return outerType != null
    }

    override fun getTypeArguments(): Map<String, ClassElement> {
        val typeArguments = mutableMapOf<String, ClassElement>()
        val elementFactory = visitorContext.elementFactory
        val typeParameters = classType.declaration.typeParameters
        if (classType.arguments.isEmpty()) {
            typeParameters.forEach {
                typeArguments[it.name.asString()] = KotlinGenericPlaceholderElement(it, annotationMetadataFactory, visitorContext)
            }
        } else {
            classType.arguments.forEachIndexed { i, argument ->
                val typeElement = elementFactory.newClassElement(
                    argument,
                    annotationMetadataFactory,
                    resolvedGenerics,
                    false
                )
                typeArguments[typeParameters[i].name.asString()] = typeElement
            }
        }
        return typeArguments
    }

    override fun getTypeArguments(type: String): Map<String, ClassElement> {
        return allTypeArguments.getOrElse(type) { emptyMap() }
    }

    override fun getAllTypeArguments(): Map<String, Map<String, ClassElement>> {
        val allTypeArguments = mutableMapOf<String, Map<String, ClassElement>>()
        val resolvedArguments = mutableMapOf<String, ClassElement>()
        populateTypeArguments(allTypeArguments, resolvedArguments, this)
        var superType = this.superType.orElse(null)
        while (superType != null) {
            populateTypeArguments(allTypeArguments, resolvedArguments, superType)
            superType.interfaces.forEach {
                populateTypeArguments(allTypeArguments, resolvedArguments, it)
            }
            superType = superType.superType.orElse(null)
        }
        interfaces.forEach {
            populateTypeArguments(allTypeArguments, resolvedArguments, it)
        }
        return allTypeArguments
    }

    private fun populateTypeArguments(allTypeArguments: MutableMap<String, Map<String, ClassElement>>,
                                      resolvedArguments: MutableMap<String, ClassElement>,
                                      classElement: ClassElement) {
        var typeArguments = classElement.typeArguments
        if (typeArguments.isNotEmpty()) {
            typeArguments = typeArguments.mapValues { entry ->
                if (entry.value is GenericPlaceholderElement) {
                    resolvedArguments.getOrDefault(entry.key, entry.value)
                } else {
                    resolvedArguments.putIfAbsent(entry.key, entry.value)
                    entry.value
                }
            }
            allTypeArguments[classElement.name] = typeArguments
        }
    }

    override fun getEnclosingType(): Optional<ClassElement> {
        if (isInner) {
            return Optional.of(
                visitorContext.elementFactory.newClassElement(
                    outerType!!,
                    visitorContext.elementAnnotationMetadataFactory
                )
            )
        }
        return Optional.empty()
    }

    override fun <T : Element> getEnclosedElements(@NonNull query: ElementQuery<T>): MutableList<T> {
        val classElementToInspect: ClassElement = if (this is GenericPlaceholderElement) {
            val bounds: List<ClassElement> = this.bounds
            if (bounds.isEmpty()) {
                return mutableListOf()
            }
            bounds[0]
        } else {
            this
        }
        return enclosedElementsQuery.getEnclosedElements<T>(classElementToInspect, query)

    }

    private inner class KotlinEnclosedElementsQuery :
        EnclosedElementsQuery<KSClassDeclaration, KSNode>() {
        override fun getExcludedNativeElements(result: ElementQuery.Result<*>): Set<KSNode> {
            if (result.isExcludePropertyElements) {
                val excludeElements: MutableSet<KSNode> = HashSet()
                for (excludePropertyElement in getBeanProperties()) {
                    excludePropertyElement.readMethod.ifPresent { methodElement: MethodElement ->
                        excludeElements.add(
                            methodElement.nativeType as KSNode
                        )
                    }
                    excludePropertyElement.writeMethod.ifPresent { methodElement: MethodElement ->
                        excludeElements.add(
                            methodElement.nativeType as KSNode
                        )
                    }
                    excludePropertyElement.field.ifPresent { fieldElement: FieldElement ->
                        excludeElements.add(
                            fieldElement.nativeType as KSNode
                        )
                    }
                }
                return excludeElements
            }
            return emptySet()
        }

        override fun getSuperClass(classNode: KSClassDeclaration): KSClassDeclaration? {
            val superTypes = classNode.superTypes
            for (superclass in superTypes) {
                val resolved = superclass.resolve()
                val declaration = resolved.declaration
                if (declaration is KSClassDeclaration) {
                    if (declaration.classKind == ClassKind.CLASS && declaration.qualifiedName?.asString() != Any::class.qualifiedName) {
                        return declaration
                    }
                }
            }

            return null
        }

        override fun getInterfaces(classNode: KSClassDeclaration): Collection<KSClassDeclaration> {
            val superTypes = classNode.superTypes
            val result: MutableCollection<KSClassDeclaration> = ArrayList()
            for (superclass in superTypes) {
                val resolved = superclass.resolve()
                val declaration = resolved.declaration
                if (declaration is KSClassDeclaration) {
                    if (declaration.classKind == ClassKind.INTERFACE) {
                        result.add(declaration)
                    }
                }
            }
            return result
        }

        override fun getEnclosedElements(
            classNode: KSClassDeclaration,
            result: ElementQuery.Result<*>
        ): List<KSNode> {
            val elementType: Class<*> = result.elementType
            return getEnclosedElements(classNode, result, elementType)
        }

        private fun getEnclosedElements(
            classNode: KSClassDeclaration,
            result: ElementQuery.Result<*>,
            elementType: Class<*>
        ): List<KSNode> {
            return when (elementType) {
                MemberElement::class.java -> {
                    Stream.concat(
                        getEnclosedElements(classNode, result, FieldElement::class.java).stream(),
                        getEnclosedElements(classNode, result, MethodElement::class.java).stream()
                    ).toList()
                }
                MethodElement::class.java -> {
                    val result = classNode.getAllFunctions()
                        .filter { func: KSFunctionDeclaration ->
                            !func.isInternal() &&
                            !func.isConstructor() &&
                            func.origin != Origin.SYNTHETIC &&
                            // this is a hack but no other way it seems
                            !listOf("hashCode", "toString", "equals").contains(func.simpleName.asString())
                        }
                        .toList()
                    result
                }
                FieldElement::class.java -> {
                    classNode.getAllProperties()
                        .filter {
                            !it.isInternal() &&
                            it.hasBackingField &&
                            it.origin != Origin.SYNTHETIC
                        }
                        .toList()
                }
                ConstructorElement::class.java -> {
                    classNode.getConstructors()
                        .filter { methodNode: KSFunctionDeclaration ->
                            !methodNode.isInternal()
                        }
                        .toList()
                }
                ClassElement::class.java -> {
                    classNode.declarations.filter {
                        it is KSClassDeclaration
                    }.toList()
                }
                else -> {
                    throw java.lang.IllegalStateException("Unknown result type: $elementType")
                }
            }
        }

        override fun excludeClass(classNode: KSClassDeclaration): Boolean {
            return classNode.qualifiedName.toString() == Any::class.java.name || classNode.qualifiedName.toString() == Enum::class.java.name
        }

        override fun toAstElement(enclosedElement: KSNode): Element {
            val elementFactory: KotlinElementFactory = visitorContext.elementFactory
            return when (enclosedElement) {
                is KSFunctionDeclaration -> {
                    if (enclosedElement.isConstructor()) {
                        return elementFactory.newConstructorElement(
                            this@KotlinClassElement,
                            enclosedElement,
                            elementAnnotationMetadataFactory
                        )
                    } else {
                        return elementFactory.newMethodElement(
                            this@KotlinClassElement,
                            enclosedElement,
                            elementAnnotationMetadataFactory
                        )
                    }
                }

                is KSPropertyDeclaration -> {
                    return elementFactory.newFieldElement(
                        this@KotlinClassElement,
                        enclosedElement,
                        elementAnnotationMetadataFactory
                    )
                }

                is KSType -> elementFactory.newClassElement(
                    enclosedElement,
                    elementAnnotationMetadataFactory
                )

                else -> throw IllegalStateException("Unknown element: $enclosedElement")
            }
        }

    }
}
