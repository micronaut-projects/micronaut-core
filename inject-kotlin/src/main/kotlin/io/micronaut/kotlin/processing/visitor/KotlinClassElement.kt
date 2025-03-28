/*
 * Copyright 2017-2024 original authors
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

import com.google.devtools.ksp.KspExperimental
import com.google.devtools.ksp.getClassDeclarationByName
import com.google.devtools.ksp.getConstructors
import com.google.devtools.ksp.getDeclaredFunctions
import com.google.devtools.ksp.getDeclaredProperties
import com.google.devtools.ksp.getKotlinClassByName
import com.google.devtools.ksp.isAbstract
import com.google.devtools.ksp.isConstructor
import com.google.devtools.ksp.isJavaPackagePrivate
import com.google.devtools.ksp.isPrivate
import com.google.devtools.ksp.symbol.ClassKind
import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSClassDeclaration
import com.google.devtools.ksp.symbol.KSDeclaration
import com.google.devtools.ksp.symbol.KSFunctionDeclaration
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSPropertyDeclaration
import com.google.devtools.ksp.symbol.KSType
import com.google.devtools.ksp.symbol.KSTypeAlias
import com.google.devtools.ksp.symbol.KSTypeArgument
import com.google.devtools.ksp.symbol.KSTypeParameter
import com.google.devtools.ksp.symbol.KSTypeReference
import com.google.devtools.ksp.symbol.Modifier
import com.google.devtools.ksp.symbol.Origin
import io.micronaut.context.annotation.BeanProperties
import io.micronaut.context.annotation.ConfigurationBuilder
import io.micronaut.context.annotation.ConfigurationReader
import io.micronaut.core.annotation.AnnotationMetadata
import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.naming.NameUtils
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy
import io.micronaut.inject.ast.ArrayableClassElement
import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.ConstructorElement
import io.micronaut.inject.ast.Element
import io.micronaut.inject.ast.ElementModifier
import io.micronaut.inject.ast.ElementQuery
import io.micronaut.inject.ast.FieldElement
import io.micronaut.inject.ast.GenericPlaceholderElement
import io.micronaut.inject.ast.MemberElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.PropertyElement
import io.micronaut.inject.ast.PropertyElementQuery
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate
import io.micronaut.inject.ast.utils.AstBeanPropertiesUtils
import io.micronaut.inject.ast.utils.EnclosedElementsQuery
import io.micronaut.inject.processing.ProcessingException
import io.micronaut.kotlin.processing.getBinaryName
import java.util.Optional
import java.util.function.Function
import java.util.stream.Stream

internal open class KotlinClassElement(
    private val nativeType: KotlinClassNativeElement,
    elementAnnotationMetadataFactory: ElementAnnotationMetadataFactory,
    var resolvedTypeArguments: Map<String, ClassElement>?,
    visitorContext: KotlinVisitorContext,
    private val internalArrayDimensions: Int = 0,
    private val typeVariable: Boolean = false
) : AbstractKotlinElement<KotlinClassNativeElement>(
    nativeType,
    elementAnnotationMetadataFactory,
    visitorContext
),
    ArrayableClassElement {

    private val definedType: KSType? by lazy {
        nativeType.type
    }

    val declaration: KSClassDeclaration by lazy {
        nativeType.declaration
    }

    val kotlinType: KSType by lazy {
        definedType ?: declaration.asStarProjectedType()
    }

    val plainKotlinType: KSType by lazy {
        kotlinType.starProjection().makeNullable()
    }

    open val asType: KotlinClassElement by lazy {
        if (definedType == null) {
            this
        } else {
            KotlinClassElement(
                KotlinClassNativeElement(declaration), // Strip the kotlin type and the owner
                elementAnnotationMetadataFactory,
                resolvedTypeArguments,
                visitorContext,
                arrayDimensions,
                typeVariable
            )
        }
    }

    private val outerType: KSType? by lazy {
        val outerDecl = declaration.parentDeclaration as? KSClassDeclaration
        outerDecl?.asType(
            kotlinType.arguments.subList(
                declaration.typeParameters.size,
                kotlinType.arguments.size
            )
        )
    }

    private val resolvedProperties: List<PropertyElement> by lazy {
        getBeanProperties(PropertyElementQuery.of(this))
    }

    private val internalDeclaredGenericPlaceholders: List<GenericPlaceholderElement> by lazy {
        kotlinType.declaration.typeParameters.map {
            resolveTypeParameter(nativeType, it, emptyMap()) as GenericPlaceholderElement
        }.toList()
    }

    private val internalFields: List<FieldElement> by lazy {
        super.getFields()
    }

    private val internalMethods: List<MethodElement> by lazy {
        super.getMethods()
    }

    private val enclosedElementsQuery = KotlinEnclosedElementsQuery()

    private val nativeProperties: List<PropertyElement> by lazy {
        val properties: MutableList<PropertyElement> = ArrayList()
        var clazz: KotlinClassElement? = this
        while (clazz != null) {
            // We need to aggregate all the hierarchy properties because
            // getAllProperties doesn't return correct parent of the property
            properties.addAll(clazz.getDeclaredSyntheticBeanProperties())
            clazz.interfaces.forEach {
                if (it is KotlinClassElement) {
                    properties.addAll(it.getDeclaredSyntheticBeanProperties())
                }
            }

            clazz = clazz.superType.orElse(null) as KotlinClassElement?
        }
        properties
    }

    private val declaredNativeProperties: List<PropertyElement> by lazy {
        declaration.getDeclaredProperties()
            .filter { !it.isPrivate() && !hasAnnotation(it, JvmField::class.java) }
            .map {
                KotlinPropertyElement(
                    this,
                    it,
                    elementAnnotationMetadataFactory,
                    visitorContext
                )
            }
            .toList()
    }

    @OptIn(KspExperimental::class)
    private val internalCanonicalName: String by lazy {
        val javaName = visitorContext.resolver.mapKotlinNameToJava(declaration.qualifiedName!!)
        javaName?.asString() ?: declaration.qualifiedName!!.asString()
    }

    private val internalName: String by lazy {
        declaration.getBinaryName(visitorContext.resolver, visitorContext)
    }

    private val resolvedInterfaces: Collection<ClassElement> by lazy {
        declaration.superTypes.map { it.resolve() }
            .filter {
                it != visitorContext.resolver.builtIns.anyType
            }
            .filter {
                val declaration = it.declaration
                declaration is KSClassDeclaration && declaration.classKind == ClassKind.INTERFACE
            }.map {
                newClassElement(nativeType, it, typeArguments)
            }.toList()
    }

    private val resolvedSuperType: Optional<ClassElement> by lazy {
        val superType = declaration.superTypes.firstOrNull {
            val resolved = it.resolve()
            if (resolved == visitorContext.resolver.builtIns.anyType) {
                false
            } else {
                val declaration = resolved.declaration
                declaration is KSClassDeclaration && declaration.classKind != ClassKind.INTERFACE
            }
        }
        Optional.ofNullable(superType)
            .map {
                newClassElement(nativeType, it.resolve(), typeArguments)
            }
    }

    private val resolvedPrimaryConstructor: Optional<MethodElement> by lazy {
        val primaryConstructor = super.getPrimaryConstructor()
        if (primaryConstructor.isPresent) {
            primaryConstructor
        } else {
            Optional.ofNullable(declaration.primaryConstructor)
                .filter { !it.isPrivate() }
                .map {
                    visitorContext.elementFactory.newConstructorElement(
                        this,
                        it,
                        elementAnnotationMetadataFactory
                    )
                }
        }
    }

    private val resolvedDefaultConstructor: Optional<MethodElement> by lazy {
        val defaultConstructor = super.getDefaultConstructor()
        if (defaultConstructor.isPresent) {
            defaultConstructor
        } else {
            Optional.ofNullable(declaration.primaryConstructor)
                .filter { !it.isPrivate() && it.parameters.isEmpty() }
                .map {
                    visitorContext.elementFactory.newConstructorElement(
                        this,
                        it,
                        elementAnnotationMetadataFactory
                    )
                }
        }
    }

    private val resolvedAnnotationMetadataToWrite: MutableAnnotationMetadataDelegate<*> by lazy {
        if (definedType != null) {
            resolvedTypeAnnotationMetadata
        } else {
            super.getAnnotationMetadataToWrite()
        }
    }

    private val resolvedTypeAnnotationMetadata: MutableAnnotationMetadataDelegate<AnnotationMetadata> by lazy {
        if (definedType != null) {
            elementAnnotationMetadataFactory.buildTypeAnnotations(this)
        } else {
            MutableAnnotationMetadataDelegate.EMPTY as MutableAnnotationMetadataDelegate<AnnotationMetadata>
        }
    }

    private val resolvedAnnotationMetadata: AnnotationMetadata by lazy {
        if (presetAnnotationMetadata != null) {
            presetAnnotationMetadata
        } else {
            if (definedType != null) {
                AnnotationMetadataHierarchy(
                    true,
                    super<AbstractKotlinElement>.getAnnotationMetadata(),
                    typeAnnotationMetadata
                )
            } else {
                super<AbstractKotlinElement>.getAnnotationMetadata()
            }
        }
    }

    override fun getType() = asType

    companion object Helper {
        fun getType(ref: KSAnnotated, visitorContext: KotlinVisitorContext): KSType {
            when (ref) {
                is KSType -> {
                    return ref
                }

                is KSTypeReference -> {
                    return ref.resolve()
                }

                is KSTypeParameter -> {
                    return ref.bounds.firstOrNull()?.resolve()
                        ?: visitorContext.resolver.builtIns.anyType
                }

                is KSClassDeclaration -> {
                    return ref.asStarProjectedType()
                }

                is KSTypeArgument -> {
                    val ksType = ref.type?.resolve()
                    if (ksType != null) {
                        return ksType
                    } else {
                        throw IllegalArgumentException("Unresolvable type argument $ref")
                    }
                }

                is KSTypeAlias -> {
                    return ref.type.resolve()
                }

                else -> {
                    throw IllegalArgumentException("Not a type $ref")
                }
            }
        }

        private fun hasAnnotation(
            element: KSAnnotated,
            annotation: Class<out Annotation>
        ) = element.annotations.any {
            it.shortName.getShortName() == annotation.simpleName && it.annotationType.resolve().declaration
                .qualifiedName?.asString() == annotation.name
        }

        private fun hasAnnotation(
            element: KSAnnotated,
            annotation: String
        ) = element.annotations.any {
            it.annotationType.resolve().declaration.qualifiedName?.asString() == annotation
        }
    }

    override fun getName() = internalName

    override fun getCanonicalName() = internalCanonicalName

    override fun getPackageName() = declaration.packageName.asString()

    override fun isDeclaredNullable() = kotlinType.isMarkedNullable

    override fun isNullable() = kotlinType.isMarkedNullable

    override fun getSyntheticBeanProperties() = nativeProperties

    private fun getDeclaredSyntheticBeanProperties() = declaredNativeProperties

    override fun getAccessibleStaticCreators(): List<MethodElement> {
        val staticCreators: MutableList<MethodElement> = mutableListOf()
        staticCreators.addAll(super.getAccessibleStaticCreators())
        return staticCreators.ifEmpty {
            val companion = declaration.declarations
                .filter { it is KSClassDeclaration && it.isCompanionObject }
                .map { it as KSClassDeclaration }
                .map { newKotlinClassElement(it, emptyMap()) }
                .firstOrNull() ?: return emptyList()

            return companion.getEnclosedElements(
                ElementQuery.ALL_METHODS
                    .annotated {
                        it.hasStereotype(
                            Creator::class.java
                        )
                    }
                    .modifiers { it.isEmpty() || it.contains(ElementModifier.PUBLIC) }
                    .filter { method ->
                        method.returnType.isAssignable(this)
                    }
            )
        }
    }

    override fun getBeanProperties() = resolvedProperties

    override fun getDeclaredGenericPlaceholders() = internalDeclaredGenericPlaceholders

    override fun getFields() = internalFields

    override fun findField(name: String) = Optional.ofNullable(
        internalFields.firstOrNull { it.name == name }
    )

    override fun getMethods() = internalMethods

    override fun findMethod(name: String?) = Optional.ofNullable(
        internalMethods.firstOrNull { it.name == name }
    )

    override fun getBeanProperties(propertyElementQuery: PropertyElementQuery): MutableList<PropertyElement> {
        val customReaderPropertyNameResolver =
            Function<MethodElement, Optional<String>> { Optional.empty() }
        val customWriterPropertyNameResolver =
            Function<MethodElement, Optional<String>> { Optional.empty() }
        val accessKinds = propertyElementQuery.accessKinds
        val fieldAccess =
            accessKinds.contains(BeanProperties.AccessKind.FIELD) && !propertyElementQuery.accessKinds.contains(
                BeanProperties.AccessKind.METHOD
            )
        if (fieldAccess) {
            // all kotlin fields are private
            return mutableListOf()
        }

        val eq = ElementQuery.of(PropertyElement::class.java)
            .filter { el ->
                val kotlinNativeElement = el.nativeType as KotlinNativeElement
                val nativeEl = kotlinNativeElement.element
                val excludedAnnotations = propertyElementQuery.excludedAnnotations
                if (hasAnnotation(nativeEl, JvmField::class.java) || excludedAnnotations.any { hasAnnotation(nativeEl, it) }) {
                    false
                } else {
                    !propertyElementQuery.excludes.contains(el.name)
                            && (propertyElementQuery.includes.isEmpty() || propertyElementQuery.includes.contains(el.name))
                }
            }
            .modifiers {
                if (!propertyElementQuery.isAllowStaticProperties && it.contains(ElementModifier.STATIC)) {
                    return@modifiers false
                }
                val visibility = propertyElementQuery.visibility
                if (visibility == BeanProperties.Visibility.PUBLIC) {
                    it.contains(ElementModifier.PUBLIC)
                } else {
                    !it.contains(ElementModifier.PRIVATE)
                }
            }.annotated { prop ->
                val excludedAnnotations = propertyElementQuery.excludedAnnotations
                excludedAnnotations.isEmpty() || !excludedAnnotations.any {
                    prop.hasAnnotation(
                        it
                    )
                }
            }

        val allProperties: MutableList<PropertyElement> = mutableListOf()
        allProperties.addAll(enclosedElementsQuery.getEnclosedElements(this, eq))
        // unfortunate hack since these are not excluded?
        if (hasDeclaredStereotype(ConfigurationReader::class.java)) {
            val configurationBuilderQuery = ElementQuery.of(PropertyElement::class.java)
                .annotated { it.hasDeclaredAnnotation(ConfigurationBuilder::class.java) }
                .onlyInstance()
                .onlyAccessible(this)
            enclosedElementsQuery.getEnclosedElements(this, configurationBuilderQuery)
                .forEach { e ->
                    if (!allProperties.contains(e)) {
                        allProperties.add(e)
                    }
                }
        }
        val propertyNames = allProperties.map { it.name }.toMutableSet()
        val resolvedProperties: MutableList<PropertyElement> = mutableListOf()
        val methods = ArrayList(getEnclosedElements(ElementQuery.ALL_METHODS))
        if (isJavaRecord(nativeType.declaration)) {
            propertyElementQuery.readPrefixes("")
            propertyElementQuery.writePrefixes(emptyArray())
        }
        allProperties.forEach { prop ->
            methods.removeIf { m ->
                prop.name == NameUtils.getPropertyNameForGetter(
                    m.name,
                    propertyElementQuery.readPrefixes
                ) || prop.name == NameUtils.getPropertyNameForSetter(
                    m.name,
                    propertyElementQuery.writePrefixes
                )
            }
        }
        val fields = ArrayList(getEnclosedElements(ElementQuery.ALL_FIELDS))
        fields.removeIf { f -> allProperties.stream().anyMatch { p -> p.name == f.name }}
        val methodProperties = AstBeanPropertiesUtils.resolveBeanProperties(propertyElementQuery,
            this,
            {
                methods
            },
            {
                fields
            },
            false,
            propertyNames,
            customReaderPropertyNameResolver,
            customWriterPropertyNameResolver,
            { value: AstBeanPropertiesUtils.BeanPropertyData ->
                if (!value.isExcluded) {
                    this.mapToPropertyElement(
                        value
                    )
                } else {
                    null
                }
            })
        resolvedProperties.addAll(methodProperties)
        resolvedProperties.addAll(allProperties)
        return resolvedProperties
    }

    private fun mapToPropertyElement(value: AstBeanPropertiesUtils.BeanPropertyData) =
        KotlinSimplePropertyElement(
            this@KotlinClassElement,
            value.type,
            value.propertyName,
            value.field,
            value.getter,
            value.setter,
            elementAnnotationMetadataFactory,
            visitorContext,
            value.isExcluded
        )

    @OptIn(KspExperimental::class)
    override fun getSimpleName(): String {
        var parentDeclaration = declaration.parentDeclaration
        return if (parentDeclaration == null) {
            val qualifiedName = declaration.qualifiedName
            if (qualifiedName != null) {
                visitorContext.resolver.mapKotlinNameToJava(qualifiedName)?.getShortName()
                    ?: declaration.simpleName.asString()
            } else
                declaration.simpleName.asString()
        } else {
            val builder = StringBuilder(declaration.simpleName.asString())
            while (parentDeclaration != null) {
                builder.insert(0, '$')
                    .insert(0, parentDeclaration.simpleName.asString())
                parentDeclaration = parentDeclaration.parentDeclaration
            }
            builder.toString()
        }
    }

    override fun getSuperType() = resolvedSuperType

    override fun getInterfaces() = resolvedInterfaces

    override fun isStatic() = if (isInner) {
        // inner classes in Kotlin are by default static unless
        // the 'inner' keyword is used
        !declaration.modifiers.contains(Modifier.INNER)
    } else {
        super<AbstractKotlinElement>.isStatic()
    }

    override fun isInterface() = declaration.classKind == ClassKind.INTERFACE

    override fun isTypeVariable() = typeVariable

    override fun isAssignable(type: String): Boolean {
        if (internalName == type) {
            return true // Same type
        }
        val otherDeclaration = visitorContext.resolver.getClassDeclarationByName(type)
        if (otherDeclaration != null) {
            if (declaration == otherDeclaration) {
                return true
            }
            val thisFullName = declaration.getBinaryName(
                visitorContext.resolver,
                visitorContext
            )
            val otherFullName = otherDeclaration.getBinaryName(
                visitorContext.resolver,
                visitorContext
            )
            if (thisFullName == otherFullName) {
                return true
            }
            val otherKotlinType = otherDeclaration.asStarProjectedType().makeNullable()
            val kotlinTypeNullable = kotlinType.makeNullable()
            if (otherKotlinType == kotlinTypeNullable) {
                return true
            }
            if (otherKotlinType.isAssignableFrom(kotlinTypeNullable)) {
                return true
            }
        }
        return isAssignable2(type)
    }

    // Second attempt to check if the class is assignable, the method is public for testing
    @OptIn(KspExperimental::class)
    fun isAssignable2(type: String): Boolean {
        val kotlinName = visitorContext.resolver.mapJavaNameToKotlin(
            visitorContext.resolver.getKSNameFromString(type)
        ) ?: return false
        val kotlinClassByName = visitorContext.resolver.getKotlinClassByName(kotlinName) ?: return false
        return kotlinClassByName.asStarProjectedType().makeNullable().isAssignableFrom(plainKotlinType)
    }

    override fun isAssignable(type: ClassElement): Boolean {
        if (equals(type)) {
            return true // Same type
        }
        if (type is KotlinClassElement) {
            return type.plainKotlinType.isAssignableFrom(plainKotlinType)
        }
        return super.isAssignable(type)
    }

    override fun copyThis() = KotlinClassElement(
        nativeType,
        elementAnnotationMetadataFactory,
        resolvedTypeArguments,
        visitorContext,
        arrayDimensions,
        typeVariable
    )

    override fun withTypeArguments(typeArguments: Map<String, ClassElement>) = KotlinClassElement(
        nativeType,
        elementAnnotationMetadataFactory,
        typeArguments,
        visitorContext,
        arrayDimensions,
        typeVariable
    )

    @NonNull
    override fun withTypeArguments(@NonNull typeArguments: Collection<ClassElement>): ClassElement? {
        if (getTypeArguments() == typeArguments) {
            return this
        }
        if (typeArguments.isEmpty()) {
            return withTypeArguments(emptyMap())
        }
        val boundByName: MutableMap<String, ClassElement> = LinkedHashMap()
        val keys = getTypeArguments().keys
        val variableNames: Iterator<String> = keys.iterator()
        val args = typeArguments.iterator()
        while (variableNames.hasNext() && args.hasNext()) {
            var next = args.next()
            val nativeType = next.nativeType
            if (nativeType is Class<*>) {
                next = visitorContext.getClassElement(nativeType).orElse(next)
            }
            if (nativeType is String) {
                next = visitorContext.getClassElement(nativeType).orElse(next)
            }
            boundByName[variableNames.next()] = next
        }
        return withTypeArguments(boundByName)
    }

    override fun isAbstract(): Boolean = declaration.isAbstract()

    override fun withAnnotationMetadata(annotationMetadata: AnnotationMetadata) =
        super<AbstractKotlinElement>.withAnnotationMetadata(annotationMetadata) as ClassElement

    override fun isArray() = arrayDimensions > 0

    override fun getArrayDimensions() = internalArrayDimensions

    override fun withArrayDimensions(arrayDimensions: Int) = KotlinClassElement(
        nativeType,
        elementAnnotationMetadataFactory,
        resolvedTypeArguments,
        visitorContext,
        arrayDimensions,
        typeVariable
    )

    override fun isInner() = outerType != null

    override fun getPrimaryConstructor() = resolvedPrimaryConstructor

    override fun getDefaultConstructor() = resolvedDefaultConstructor

    override fun getTypeArguments(): Map<String, ClassElement> {
        if (resolvedTypeArguments == null) {
            val ksDeclaration = kotlinType.declaration
            resolvedTypeArguments = if (ksDeclaration is KSTypeParameter) {
                resolveTypeArguments(
                    nativeType,
                    ksDeclaration.bounds.toList()[0].resolve(),
                    emptyMap()
                )
            } else if (definedType != null) {
                resolveTypeArguments(nativeType, definedType!!, emptyMap())
            } else {
                resolveTypeArguments(nativeType, declaration, emptyMap())
            }
        }
        return resolvedTypeArguments!!
    }

    override fun getEnclosingType(): Optional<ClassElement> {
        if (isInner) {
            return Optional.of(
                newClassElement(nativeType, outerType!!, emptyMap())
            )
        }
        return Optional.empty()
    }

    override fun getAnnotationMetadataToWrite() = resolvedAnnotationMetadataToWrite

    override fun getAnnotationMetadata() = resolvedAnnotationMetadata

    override fun getTypeAnnotationMetadata() = resolvedTypeAnnotationMetadata

    override fun <T : Element> getEnclosedElements(query: ElementQuery<T>): List<T> =
        enclosedElementsQuery.getEnclosedElements(this, query)

    private inner class KotlinEnclosedElementsQuery :
        EnclosedElementsQuery<KSClassDeclaration, KSNode>() {

        override fun hasAnnotation(element: KSNode, annotation: Class<out Annotation>): Boolean {
            if (element is KSAnnotated) {
                return KotlinClassElement.hasAnnotation(element, annotation)
            }
            return false
        }

        @OptIn(KspExperimental::class)
        override fun getElementName(element: KSNode): String {
            if (element is KSPropertyDeclaration) {
                return element.simpleName.asString()
            }
            if (element is KSFunctionDeclaration) {
                return visitorContext.resolver.getJvmName(element)!!
            }
            if (element is KSDeclaration) {
                return element.getBinaryName(visitorContext.resolver, visitorContext)
            }
            return ""
        }

        override fun getNativeClassType(classElement: ClassElement): KSClassDeclaration {
            return (classElement as KotlinClassElement).nativeType.declaration
        }

        override fun getNativeType(element: Element): KSNode {
            return (element as AbstractKotlinElement<*>).nativeType.element
        }

        override fun getExcludedNativeElements(result: ElementQuery.Result<*>): Set<KSNode> {
            if (result.isExcludePropertyElements) {
                val excludeElements: MutableSet<KSNode> = HashSet()
                for (excludePropertyElement in beanProperties) {
                    excludePropertyElement.readMethod.ifPresent { methodElement: MethodElement ->
                        excludeElements.add(
                            getNativeType(methodElement)
                        )
                    }
                    excludePropertyElement.writeMethod.ifPresent { methodElement: MethodElement ->
                        excludeElements.add(
                            getNativeType(methodElement)
                        )
                    }
                    excludePropertyElement.field.ifPresent { fieldElement: FieldElement ->
                        excludeElements.add(
                            getNativeType(fieldElement)
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
            result: ElementQuery.Result<*>,
            includeAbstract: Boolean
        ): List<KSNode> {
            val elementType: Class<*> = result.elementType
            return getEnclosedElements(classNode, result, elementType, includeAbstract)
        }

        private fun getEnclosedElements(
            classNode: KSClassDeclaration,
            result: ElementQuery.Result<*>,
            elementType: Class<*>,
            includeAbstract: Boolean
        ): List<KSNode> {
            return when (elementType) {
                MemberElement::class.java -> {
                    Stream.concat(
                        getEnclosedElements(classNode, result, FieldElement::class.java, includeAbstract).stream(),
                        getEnclosedElements(classNode, result, MethodElement::class.java, includeAbstract).stream()
                    ).toList()
                }

                MethodElement::class.java -> {
                    val functions = if (isJavaRecord(classNode)) {
                        classNode.getAllFunctions().filter {
                            !listOf(
                                "hashCode",
                                "toString",
                                "equals"
                            ).contains(it.simpleName.asString())
                        }
                    } else {
                        classNode.getDeclaredFunctions()
                    }

                    functions
                        .filter { func: KSFunctionDeclaration ->
                            !func.isConstructor() &&
                                    func.origin != Origin.SYNTHETIC &&
                                    (includeAbstract || !func.isAbstract || !classNode.isAbstract())
                        }
                        .toList()
                }

                FieldElement::class.java -> {
                    classNode.getDeclaredProperties()
                        .filter {
                            it.hasBackingField && it.origin != Origin.SYNTHETIC
                        }
                        .toList()
                }

                PropertyElement::class.java -> {
                    classNode.getDeclaredProperties().filter {
                        !it.isJavaPackagePrivate() && !it.annotations.any { ann -> ann.shortName.asString() == JvmField::class.java.simpleName }
                    }.toList()
                }

                ConstructorElement::class.java -> {
                    classNode.getConstructors().toList()
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
                    (classNode.qualifiedName != null && (
                            classNode.qualifiedName!!.asString() == Enum::class.java.name ||
                                    classNode.qualifiedName!!.asString() == Record::class.java.name
                            ))
        }

        override fun isAbstractClass(classNode: KSClassDeclaration) = classNode.isAbstract()

        override fun isInterface(classNode: KSClassDeclaration) = classNode.classKind == ClassKind.INTERFACE

        override fun toAstElement(
            nativeType: KSNode,
            elementType: Class<*>
        ): Element {
            val elementFactory: KotlinElementFactory = visitorContext.elementFactory
            val owningClass = this@KotlinClassElement
            return when (nativeType) {
                is KSFunctionDeclaration -> {
                    if (nativeType.isConstructor()) {
                        return elementFactory.newConstructorElement(
                            owningClass,
                            nativeType,
                            elementAnnotationMetadataFactory
                        )
                    } else {
                        return elementFactory.newMethodElement(
                            owningClass,
                            nativeType,
                            elementAnnotationMetadataFactory
                        )
                    }
                }

                is KSPropertyDeclaration -> {
                    return if (elementType == PropertyElement::class.java) {
                        KotlinPropertyElement(
                            owningClass,
                            nativeType,
                            elementAnnotationMetadataFactory, visitorContext
                        )
                    } else {
                        elementFactory.newFieldElement(
                            owningClass,
                            nativeType,
                            elementAnnotationMetadataFactory
                        )
                    }
                }

                is KSClassDeclaration -> newKotlinClassElement(
                    nativeType,
                    emptyMap()
                )

                else -> throw ProcessingException(owningClass, "Unexpected element: $nativeType")
            }
        }
    }

    private fun isJavaRecord(classNode: KSClassDeclaration) =
        classNode.origin == Origin.JAVA && classNode.superTypes.filter { Record::class.java.name == it.resolve().declaration.qualifiedName?.asString() }
            .any()

}
