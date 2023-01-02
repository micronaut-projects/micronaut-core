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
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.kotlin.processing.getBinaryName
import io.micronaut.kotlin.processing.getClassDeclaration
import java.util.*
import java.util.function.Function
import java.util.stream.Stream
import kotlin.collections.LinkedHashMap

open class KotlinClassElement(val kotlinType: KSType,
                              protected val classDeclaration: KSClassDeclaration,
                              private val annotationInfo: KSAnnotated,
                              protected val elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
                              visitorContext: KotlinVisitorContext,
                              private val arrayDimensions: Int = 0,
                              private val typeVariable: Boolean = false): AbstractKotlinElement<KSAnnotated>(annotationInfo, elementAnnotationMetadataFactory, visitorContext), ArrayableClassElement {

    constructor(
        ref: KSAnnotated,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext,
        arrayDimensions: Int = 0,
        typeVariable: Boolean = false
    ) : this(getType(ref, visitorContext), ref.getClassDeclaration(visitorContext), ref, elementAnnotationMetadataFactory, visitorContext, arrayDimensions, typeVariable)

    constructor(
        type: KSType,
        elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
        visitorContext: KotlinVisitorContext,
        arrayDimensions: Int = 0,
        typeVariable: Boolean = false
    ) : this(type, type.declaration as KSClassDeclaration, type.declaration as KSClassDeclaration, elementAnnotationMetadataFactory, visitorContext, arrayDimensions, typeVariable)


    val outerType: KSType? by lazy {
        val outerDecl = classDeclaration.parentDeclaration as? KSClassDeclaration
        outerDecl?.asType(kotlinType.arguments.subList(classDeclaration.typeParameters.size, kotlinType.arguments.size))
    }

    private val resolvedProperties : List<PropertyElement> by lazy {
        getBeanProperties(PropertyElementQuery.of(this))
    }
    private val enclosedElementsQuery = KotlinEnclosedElementsQuery()
    private val nativeProperties  : List<PropertyElement> by lazy {
        classDeclaration.getAllProperties()
            .filter { !it.isInternal() && !it.isPrivate() }
            .map { KotlinPropertyElement(
                this,
                visitorContext.elementFactory.newClassElement(it.type.resolve(), elementAnnotationMetadataFactory),
                it,
                elementAnnotationMetadataFactory, visitorContext
            ) }
            .toList()
    }
    private val internalGenerics : Map<String, Map<String, KSType>> by lazy {
        val boundMirrors : Map<String, KSType> = getBoundTypeMirrors()
        val data = mutableMapOf<String, Map<String, KSType>>()
        if (boundMirrors.isNotEmpty()) {
            data[this.name] = boundMirrors
        }
        val classDeclaration = classDeclaration
        populateGenericInfo(classDeclaration, data, boundMirrors)
        data
    }
    private val internalCanonicalName : String by lazy {
        classDeclaration.qualifiedName!!.asString()
    }

    private var overrideBoundGenericTypes: MutableList<out ClassElement>? = null
    private var resolvedTypeArguments : MutableMap<String, ClassElement>? = null

    private val nt : KSAnnotated =  if (annotationInfo is KSTypeArgument) annotationInfo else KSClassReference(classDeclaration)
    override fun getNativeType(): KSAnnotated {
        return nt
    }

    companion object Helper {
        fun getType(ref: KSAnnotated, visitorContext: KotlinVisitorContext) : KSType {
            if (ref is KSType) {
                return ref
            } else if (ref is KSTypeReference) {
                return ref.resolve()
            } else if (ref is KSTypeParameter) {
                return ref.bounds.firstOrNull()?.resolve() ?: visitorContext.resolver.builtIns.anyType
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



    }

    @OptIn(KspExperimental::class)
    override fun getName(): String {
        return classDeclaration.getBinaryName(visitorContext.resolver)
    }

    override fun getCanonicalName(): String {
        return internalCanonicalName
    }

    override fun getPackageName(): String {
        return classDeclaration.packageName.asString()
    }

    override fun isDeclaredNullable(): Boolean {
        return kotlinType.isMarkedNullable
    }

    override fun isNullable(): Boolean {
        return kotlinType.isMarkedNullable
    }

    override fun getSyntheticBeanProperties(): List<PropertyElement> {
        return nativeProperties
    }

    override fun getAccessibleStaticCreators(): MutableList<MethodElement> {
        val staticCreators: MutableList<MethodElement> = mutableListOf()
        staticCreators.addAll(super.getAccessibleStaticCreators())
        return staticCreators.ifEmpty {
            val companion = classDeclaration.declarations.filter {
                it is KSClassDeclaration && it.isCompanionObject
            }.map { it as KSClassDeclaration }
             .map { visitorContext.elementFactory.newClassElement(it, elementAnnotationMetadataFactory, false) }
             .firstOrNull()

            if (companion != null) {
                return companion.getEnclosedElements(
                    ElementQuery.ALL_METHODS
                        .annotated { it.hasStereotype(
                            Creator::class.java
                        )}
                        .modifiers { it.isEmpty() || it.contains(ElementModifier.PUBLIC) }
                        .filter { method ->
                            method.returnType.isAssignable(this)
                        }
                )

            } else {
                return mutableListOf()
            }
        }
    }

    override fun getBeanProperties(): List<PropertyElement> {
        return resolvedProperties
    }

    override fun getDeclaredGenericPlaceholders(): MutableList<out GenericPlaceholderElement> {
        return kotlinType.declaration.typeParameters.map {
            KotlinGenericPlaceholderElement(it, annotationMetadataFactory, visitorContext)
        }.toMutableList()
    }

    override fun withBoundGenericTypes(typeArguments: MutableList<out ClassElement>?): ClassElement {
        val copy = copyThis()
        copy.overrideBoundGenericTypes = typeArguments
        if (typeArguments != null && typeArguments.size == kotlinType.declaration.typeParameters.size) {
            val i = typeArguments.iterator()
            copy.resolvedTypeArguments = kotlinType.declaration.typeParameters.associate {
                it.name.asString() to i.next()
            }.toMutableMap()
        }
        return copy
    }

    override fun getBoundGenericTypes(): MutableList<out ClassElement> {
        if (overrideBoundGenericTypes == null) {
            val arguments = kotlinType.arguments
            if (arguments.isEmpty()) {
                return mutableListOf()
            } else {
                val elementFactory = visitorContext.elementFactory
                this.overrideBoundGenericTypes = arguments.map { arg ->
                    when(arg.variance) {
                        Variance.STAR, Variance.COVARIANT, Variance.CONTRAVARIANT -> KotlinWildcardElement( // example List<*>
                            resolveUpperBounds(arg, elementFactory, visitorContext),
                            resolveLowerBounds(arg, elementFactory),
                            elementAnnotationMetadataFactory, visitorContext
                        )
                        else -> elementFactory.newClassElement( // other cases
                            arg,
                            elementAnnotationMetadataFactory,
                            false
                        )
                    }
                }.toMutableList()
            }
        }
        return overrideBoundGenericTypes!!
    }

    fun getGenericTypeInfo() : Map<String, Map<String, KSType>> {
        return this.internalGenerics
    }

    private fun populateGenericInfo(
        classDeclaration: KSClassDeclaration,
        data: MutableMap<String, Map<String, KSType>>,
        boundMirrors: Map<String, KSType>?
    ) {
        classDeclaration.superTypes.forEach {
            val superType = it.resolve()
            if (superType != visitorContext.resolver.builtIns.anyType) {
                val declaration = superType.declaration
                val name = declaration.qualifiedName?.asString()
                val binaryName = declaration.getBinaryName(visitorContext.resolver)
                if (name != null && !data.containsKey(name)) {
                    val typeParameters = declaration.typeParameters
                    if (typeParameters.isEmpty()) {
                        data[binaryName] = emptyMap()
                    } else {
                        val ksTypeArguments = superType.arguments
                        if (typeParameters.size == ksTypeArguments.size) {
                            val resolved = LinkedHashMap<String, KSType>()
                            var i = 0
                            typeParameters.forEach { typeParameter ->
                                val parameterName = typeParameter.name.asString()
                                val typeArgument = ksTypeArguments[i]
                                val argumentType = typeArgument.type?.resolve()
                                val argumentName = argumentType?.declaration?.simpleName?.asString()
                                val bound = if (argumentName != null ) boundMirrors?.get(argumentName) else null
                                if (bound != null) {
                                    resolved[parameterName] = bound
                                } else {
                                    resolved[parameterName] = argumentType ?: typeParameter.bounds.firstOrNull()?.resolve()
                                            ?: visitorContext.resolver.builtIns.anyType
                                }
                                i++
                            }
                            data[binaryName] = resolved
                        }
                    }
                    if (declaration is KSClassDeclaration) {
                        val newBounds = data[binaryName]
                        populateGenericInfo(
                            declaration,
                            data,
                            newBounds
                        )
                    }
                }
            }

        }
    }

    private fun getBoundTypeMirrors(): Map<String, KSType> {
        val typeParameters: List<KSTypeArgument> = kotlinType.arguments
        val parameterIterator = classDeclaration.typeParameters.iterator()
        val tpi = typeParameters.iterator()
        val map: MutableMap<String, KSType> = LinkedHashMap()
        while (tpi.hasNext() && parameterIterator.hasNext()) {
            val tpe = tpi.next()
            val parameter = parameterIterator.next()
            val resolvedType = tpe.type?.resolve()
            if (resolvedType != null) {
                map[parameter.name.asString()] = resolvedType
            } else {
                map[parameter.name.asString()] = visitorContext.resolver.builtIns.anyType
            }
        }
        return Collections.unmodifiableMap(map)
    }

    private fun resolveLowerBounds(arg: KSTypeArgument, elementFactory: KotlinElementFactory): List<KotlinClassElement?> {
        return if (arg.variance == Variance.CONTRAVARIANT) {
            listOf(
                elementFactory.newClassElement(arg.type?.resolve()!!, elementAnnotationMetadataFactory, false) as KotlinClassElement
            )
        } else {
            return emptyList()
        }
    }

    private fun resolveUpperBounds(
        arg: KSTypeArgument,
        elementFactory: KotlinElementFactory,
        visitorContext: KotlinVisitorContext
    ): List<KotlinClassElement?> {
        return if (arg.variance == Variance.COVARIANT) {
            listOf(
                elementFactory.newClassElement(arg.type?.resolve()!!, elementAnnotationMetadataFactory, false) as KotlinClassElement
            )
        } else {
            val objectType = visitorContext.resolver.getClassDeclarationByName(Object::class.java.name)!!
            listOf(
                elementFactory.newClassElement(objectType.asStarProjectedType(), elementAnnotationMetadataFactory, false) as KotlinClassElement
            )
        }
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
                    elementAnnotationMetadataFactory
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
            val resolved = it.resolve()
            if (resolved == visitorContext.resolver.builtIns.anyType) {
                false
            } else {
                val declaration = resolved.declaration
                declaration is KSClassDeclaration && declaration.classKind != ClassKind.INTERFACE
            }
        }
        return Optional.ofNullable(superType)
            .map {
                visitorContext.elementFactory.newClassElement(it.resolve())
            }
    }

    override fun getInterfaces(): Collection<ClassElement> {
        return classDeclaration.superTypes.map { it.resolve() }
        .filter {
            it != visitorContext.resolver.builtIns.anyType
        }
        .filter {
            val declaration = it.declaration
            declaration is KSClassDeclaration && declaration.classKind == ClassKind.INTERFACE
        }.map {
            visitorContext.elementFactory.newClassElement(it)
        }.toList()
    }

    override fun isStatic(): Boolean {
        return if (isInner) {
            // inner classes in Kotlin are by default static unless
            // the 'inner' keyword is used
            !classDeclaration.modifiers.contains(Modifier.INNER)
        } else {
            super<AbstractKotlinElement>.isStatic()
        }
    }

    override fun isInterface(): Boolean {
        return classDeclaration.classKind == ClassKind.INTERFACE
    }

    override fun isTypeVariable(): Boolean = typeVariable

    @OptIn(KspExperimental::class)
    override fun isAssignable(type: String): Boolean {
        var ksType = visitorContext.resolver.getClassDeclarationByName(type)?.asStarProjectedType()
        if (ksType != null) {
            if (ksType.isAssignableFrom(kotlinType)) {
                return true
            }
            val kotlinName = visitorContext.resolver.mapJavaNameToKotlin(
                visitorContext.resolver.getKSNameFromString(type))
            if (kotlinName != null) {
                ksType = visitorContext.resolver.getKotlinClassByName(kotlinName)?.asStarProjectedType()
                if (ksType != null) {
                    if (kotlinType.starProjection().isAssignableFrom(ksType)) {
                        return true
                    }
                }
            }
        }
        return ksType?.isAssignableFrom(kotlinType) ?: false
    }

    override fun isAssignable(type: ClassElement): Boolean {
        if (type is KotlinClassElement) {
            return type.kotlinType.isAssignableFrom(kotlinType)
        }
        return super.isAssignable(type)
    }

    override fun copyThis(): KotlinClassElement {
        val copy = KotlinClassElement(
            kotlinType, classDeclaration, annotationInfo, elementAnnotationMetadataFactory, visitorContext, arrayDimensions, typeVariable
        )
        copy.resolvedTypeArguments = resolvedTypeArguments
        return copy
    }

    override fun withTypeArguments(typeArguments: MutableMap<String, ClassElement>?): ClassElement {
        val copy = copyThis()
        copy.resolvedTypeArguments = typeArguments
        return copy
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
        return KotlinClassElement(kotlinType, classDeclaration, annotationInfo, elementAnnotationMetadataFactory, visitorContext, arrayDimensions, typeVariable)
    }

    override fun isInner(): Boolean {
        return outerType != null
    }

    override fun getPrimaryConstructor(): Optional<MethodElement> {
        val primaryConstructor = super.getPrimaryConstructor()
        return if (primaryConstructor.isPresent) {
            primaryConstructor
        } else {
            Optional.ofNullable(classDeclaration.primaryConstructor)
                .filter { !it.isInternal() && !it.isPrivate() }
                .map { visitorContext.elementFactory.newConstructorElement(
                    this,
                    it,
                    elementAnnotationMetadataFactory
                ) }
        }
    }

    override fun getDefaultConstructor(): Optional<MethodElement> {
        val defaultConstructor = super.getDefaultConstructor()
        return if (defaultConstructor.isPresent) {
            defaultConstructor
        } else {
            Optional.ofNullable(classDeclaration.primaryConstructor)
                .filter { !it.isInternal() && !it.isPrivate() && it.parameters.isEmpty() }
                .map { visitorContext.elementFactory.newConstructorElement(
                    this,
                    it,
                    elementAnnotationMetadataFactory
                ) }
        }
    }

    override fun getTypeArguments(): Map<String, ClassElement> {
        if (resolvedTypeArguments == null) {
            val typeArguments = mutableMapOf<String, ClassElement>()
            val elementFactory = visitorContext.elementFactory
            val typeParameters = kotlinType.declaration.typeParameters
            if (kotlinType.arguments.isEmpty()) {
                typeParameters.forEach {
                    typeArguments[it.name.asString()] = KotlinGenericPlaceholderElement(it, annotationMetadataFactory, visitorContext)
                }
            } else {
                kotlinType.arguments.forEachIndexed { i, argument ->
                    val typeElement = elementFactory.newClassElement(
                        argument,
                        annotationMetadataFactory,
                        false
                    )
                    typeArguments[typeParameters[i].name.asString()] = typeElement
                }
            }
            resolvedTypeArguments = typeArguments
        }
        return resolvedTypeArguments!!
    }

    override fun getTypeArguments(type: String): Map<String, ClassElement> {
        return allTypeArguments.getOrElse(type) { emptyMap() }
    }

    override fun getAllTypeArguments(): Map<String, Map<String, ClassElement>> {
        val genericInfo = getGenericTypeInfo()
        return genericInfo.mapValues { entry ->
            entry.value.mapValues { data ->
                visitorContext.elementFactory.newClassElement(data.value, elementAnnotationMetadataFactory, false)
            }
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
        return enclosedElementsQuery.getEnclosedElements(classElementToInspect, query)

    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false

        other as KotlinClassElement

        if (arrayDimensions != other.arrayDimensions) return false
        if (typeVariable != other.typeVariable) return false
        if (internalCanonicalName != other.internalCanonicalName) return false
        if (overrideBoundGenericTypes != other.overrideBoundGenericTypes) return false

        return true
    }

    override fun hashCode(): Int {
        var result = arrayDimensions
        result = 31 * result + typeVariable.hashCode()
        result = 31 * result + internalCanonicalName.hashCode()
        result = 31 * result + (overrideBoundGenericTypes?.hashCode() ?: 0)
        return result
    }

    private inner class KotlinEnclosedElementsQuery :
        EnclosedElementsQuery<KSClassDeclaration, KSNode>() {
        override fun getExcludedNativeElements(result: ElementQuery.Result<*>): Set<KSNode> {
            if (result.isExcludePropertyElements) {
                val excludeElements: MutableSet<KSNode> = HashSet()
                for (excludePropertyElement in beanProperties) {
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

        override fun getCacheKey(element: KSNode): KSNode {
            return when(element) {
                is KSFunctionDeclaration -> KSFunctionReference(element)
                is KSPropertyDeclaration -> KSPropertyReference(element)
                is KSClassDeclaration -> KSClassReference(element)
                is KSValueParameter -> KSValueParameterReference(element)
                is KSPropertyGetter -> KSPropertyGetterReference(element)
                is KSPropertySetter -> KSPropertySetterReference(element)
                else -> element
            }
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

        override fun getInterfaces(classDeclaration: KSClassDeclaration): Collection<KSClassDeclaration> {
            val superTypes = classDeclaration.superTypes
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
                    val result = classNode.getDeclaredFunctions()
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
                    classNode.getDeclaredProperties()
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
            val t = classNode.asStarProjectedType()
            val builtIns = visitorContext.resolver.builtIns
            return t == builtIns.anyType ||
                    t == builtIns.nothingType ||
                    t == builtIns.unitType ||
                    classNode.qualifiedName.toString() == Enum::class.java.name
        }

        override fun toAstElement(enclosedElement: KSNode): Element {
            var ee = enclosedElement
            if (ee is KSAnnotatedReference) {
                ee = ee.node
            }
            val elementFactory: KotlinElementFactory = visitorContext.elementFactory
            return when (ee) {
                is KSFunctionDeclaration -> {
                    if (ee.isConstructor()) {
                        return elementFactory.newConstructorElement(
                            this@KotlinClassElement,
                            ee,
                            elementAnnotationMetadataFactory
                        )
                    } else {
                        return elementFactory.newMethodElement(
                            this@KotlinClassElement,
                            ee,
                            elementAnnotationMetadataFactory
                        )
                    }
                }

                is KSPropertyDeclaration -> {
                    return elementFactory.newFieldElement(
                        this@KotlinClassElement,
                        ee,
                        elementAnnotationMetadataFactory
                    )
                }

                is KSType -> elementFactory.newClassElement(
                    ee,
                    elementAnnotationMetadataFactory
                )

                is KSClassDeclaration -> elementFactory.newClassElement(
                    ee,
                    elementAnnotationMetadataFactory,
                    false
                )

                else -> throw ProcessingException(this@KotlinClassElement, "Unknown element: $ee")
            }
        }
    }


}
