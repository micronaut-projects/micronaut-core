/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.ast.groovy.visitor;

import groovy.lang.GroovyObject;
import groovy.lang.GroovyObjectSupport;
import groovy.lang.Script;
import io.micronaut.ast.groovy.utils.AstClassUtils;
import io.micronaut.context.annotation.BeanProperties;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.reflect.ClassUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ArrayableClassElement;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.ast.PackageElement;
import io.micronaut.inject.ast.ParameterElement;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.PropertyElement;
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import io.micronaut.inject.ast.utils.AstBeanPropertiesUtils;
import io.micronaut.inject.ast.utils.EnclosedElementsQuery;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.AnnotationNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.ConstructorNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.InnerClassNode;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.ast.PackageNode;
import org.codehaus.groovy.ast.PropertyNode;
import org.codehaus.groovy.ast.stmt.BlockStatement;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.OptionalDouble;
import java.util.OptionalInt;
import java.util.OptionalLong;
import java.util.Set;
import java.util.Spliterator;
import java.util.Spliterators;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

/**
 * A class element returning data from a {@link ClassNode}.
 *
 * @author James Kleeh
 * @author Denis Stepanov
 * @since 1.0
 */
@Internal
public class GroovyClassElement extends AbstractGroovyElement implements ArrayableClassElement {

    private static final int ACC_SYNTHETIC = 0x1000; // class, field, method, parameter, module *

    private static final Predicate<MethodNode> JUNK_METHOD_FILTER = m -> {
        String methodName = m.getName();

        return m.isStaticConstructor() ||
            methodName.startsWith("$") ||
            methodName.contains("trait$") ||
            methodName.startsWith("super$") ||
            methodName.equals("setMetaClass") ||
            m.getReturnType().getNameWithoutPackage().equals("MetaClass") ||
            m.getDeclaringClass().equals(ClassHelper.GROOVY_OBJECT_TYPE) ||
            m.getDeclaringClass().equals(ClassHelper.OBJECT_TYPE);
    };
    private static final Predicate<FieldNode> JUNK_FIELD_FILTER = m -> {
        String fieldName = m.getName();

        return fieldName.startsWith("__$") ||
            fieldName.contains("trait$") ||
            fieldName.equals("metaClass") ||
            m.getDeclaringClass().equals(ClassHelper.GROOVY_OBJECT_TYPE) ||
            m.getDeclaringClass().equals(ClassHelper.OBJECT_TYPE);
    };
    protected final ClassNode classNode;
    protected Map<String, ClassElement> resolvedTypeArguments;
    private final int arrayDimensions;
    private final boolean isTypeVar;
    private List<PropertyElement> properties;
    private List<PropertyElement> nativeProperties;
    @Nullable
    private ElementAnnotationMetadata elementTypeAnnotationMetadata;
    @Nullable
    private ClassElement theType;
    private final GroovyEnclosedElementsQuery groovyEnclosedElementsQuery = new GroovyEnclosedElementsQuery(false);
    private final GroovyEnclosedElementsQuery groovySourceEnclosedElementsQuery = new GroovyEnclosedElementsQuery(true);
    @Nullable
    private AnnotationMetadata annotationMetadata;
    private List<PropertyNode> propertyElements;

    /**
     * @param visitorContext The visitor context
     * @param nativeElement The native element
     * @param annotationMetadataFactory The annotation metadata
     */
    public GroovyClassElement(GroovyVisitorContext visitorContext,
                              GroovyNativeElement nativeElement,
                              ElementAnnotationMetadataFactory annotationMetadataFactory) {
        this(visitorContext, nativeElement, annotationMetadataFactory, null, 0);
    }

    /**
     * @param visitorContext The visitor context
     * @param nativeElement The native element
     * @param annotationMetadataFactory The annotation metadata factory
     * @param resolvedTypeArguments The resolved type arguments
     * @param arrayDimensions The number of array dimensions
     */
    GroovyClassElement(GroovyVisitorContext visitorContext,
                       GroovyNativeElement nativeElement,
                       ElementAnnotationMetadataFactory annotationMetadataFactory,
                       Map<String, ClassElement> resolvedTypeArguments,
                       int arrayDimensions) {
        this(visitorContext, nativeElement, annotationMetadataFactory, resolvedTypeArguments, arrayDimensions, false);
    }

    /**
     * @param visitorContext The visitor context
     * @param nativeElement The native element
     * @param annotationMetadataFactory The annotation metadata factory
     * @param resolvedTypeArguments The resolved type arguments
     * @param arrayDimensions The number of array dimensions
     * @param isTypeVar Is the element a type variable
     */
    GroovyClassElement(GroovyVisitorContext visitorContext,
                       GroovyNativeElement nativeElement,
                       ElementAnnotationMetadataFactory annotationMetadataFactory,
                       Map<String, ClassElement> resolvedTypeArguments,
                       int arrayDimensions,
                       boolean isTypeVar) {
        super(visitorContext, nativeElement, annotationMetadataFactory);
        this.resolvedTypeArguments = resolvedTypeArguments;
        this.arrayDimensions = arrayDimensions;
        classNode = (ClassNode) nativeElement.annotatedNode();
        if (classNode.isArray()) {
            classNode.setName(classNode.getComponentType().getName());
        }
        this.isTypeVar = isTypeVar;
    }

    @Override
    protected @NonNull GroovyClassElement copyConstructor() {
        return new GroovyClassElement(visitorContext, getNativeType(), elementAnnotationMetadataFactory, resolvedTypeArguments, arrayDimensions, isTypeVar);
    }

    @Override
    public @NonNull AnnotationMetadata getAnnotationMetadata() {
        if (presetAnnotationMetadata != null) {
            return presetAnnotationMetadata;
        }
        if (annotationMetadata == null) {
            if (getNativeType() instanceof GroovyNativeElement.ClassWithOwner) {
                annotationMetadata = new AnnotationMetadataHierarchy(true, super.getAnnotationMetadata(), getTypeAnnotationMetadata());
            } else {
                annotationMetadata = super.getAnnotationMetadata();
            }
        }
        return annotationMetadata;
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        if (getNativeType() instanceof GroovyNativeElement.ClassWithOwner) {
            return getTypeAnnotationMetadata();
        }
        return super.getAnnotationMetadataToWrite();
    }

    @Override
    public ClassElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (ClassElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    public @NonNull ClassElement withTypeArguments(Map<String, ClassElement> typeArguments) {
        return new GroovyClassElement(visitorContext, getNativeType(), elementAnnotationMetadataFactory, typeArguments, arrayDimensions);
    }

    @Override
    @NonNull
    public final ClassElement withTypeArguments(@NonNull Collection<ClassElement> typeArguments) {
        if (typeArguments.isEmpty()) {
            // Allow to eliminate all arguments
            return withTypeArguments(Collections.emptyMap());
        }
        Set<String> genericNames = getTypeArguments().keySet();
        if (genericNames.size() != typeArguments.size()) {
            throw new IllegalStateException("Expected to have: " + genericNames.size() + " type arguments! Got: " + typeArguments.size());
        }
        Map<String, ClassElement> boundByName = CollectionUtils.newLinkedHashMap(typeArguments.size());
        Iterator<String> keys = genericNames.iterator();
        Iterator<? extends ClassElement> args = typeArguments.iterator();
        while (keys.hasNext() && args.hasNext()) {
            ClassElement next = args.next();
            Object nativeType = next.getNativeType();
            if (nativeType instanceof Class<?> aClass) {
                next = visitorContext.getClassElement(aClass).orElse(next);
            }
            boundByName.put(keys.next(), next);
        }
        return withTypeArguments(boundByName);
    }

    @Override
    public boolean isTypeVariable() {
        return isTypeVar;
    }

    @Override
    public boolean isRecord() {
        return classNode.isRecord();
    }

    @Override
    public <T extends Element> @NonNull List<T> getEnclosedElements(@NonNull ElementQuery<T> query) {
        return groovyEnclosedElementsQuery.getEnclosedElements(this, query);
    }

    /**
     * This method will produce th elements just like {@link #getEnclosedElements(ElementQuery)}
     * but the elements are constructed as the source ones.
     * {@link io.micronaut.inject.ast.ElementFactory#newSourceMethodElement(ClassElement, Object, ElementAnnotationMetadataFactory)}.
     *
     * @param query The query
     * @param <T> The element type
     * @return The list of elements
     */
    public final <T extends Element> List<T> getSourceEnclosedElements(@NonNull ElementQuery<T> query) {
        return groovySourceEnclosedElementsQuery.getEnclosedElements(this, query);
    }

    @Override
    public Set<ElementModifier> getModifiers() {
        return resolveModifiers(this.classNode);
    }

    @Override
    public boolean isInner() {
        return classNode instanceof InnerClassNode;
    }

    @Override
    public Optional<ClassElement> getEnclosingType() {
        if (isInner()) {
            return Optional.ofNullable(classNode.getOuterClass()).map(inner -> newClassElement(inner, getTypeArguments()));
        }
        return Optional.empty();
    }

    @Override
    public boolean isInterface() {
        return classNode.isInterface();
    }

    @Override
    public boolean isPrimitive() {
        return classNode.isArray() && ClassUtils.getPrimitiveType(classNode.getComponentType().getName()).isPresent();
    }

    @Override
    public Collection<ClassElement> getInterfaces() {
        final ClassNode[] interfaces = classNode.getInterfaces();
        if (ArrayUtils.isNotEmpty(interfaces)) {
            return Arrays.stream(interfaces)
                .filter(inf -> !inf.getName().equals("groovy.lang.GroovyObject"))
                .map(inf -> newClassElement(inf, getTypeArguments()))
                .toList();
        }
        return Collections.emptyList();
    }

    @Override
    public Optional<ClassElement> getSuperType() {
        final ClassNode superClass = classNode.getUnresolvedSuperClass();
        if (superClass != null && !superClass.equals(ClassHelper.OBJECT_TYPE)) {
            return Optional.of(newClassElement(superClass, getTypeArguments()));
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public Optional<MethodElement> getPrimaryConstructor() {
        Optional<MethodElement> primaryConstructor = ArrayableClassElement.super.getPrimaryConstructor();
        if (primaryConstructor.isPresent()) {
            return primaryConstructor;
        }
        return possibleDefaultEmptyConstructor();
    }

    @Override
    public Optional<MethodElement> getDefaultConstructor() {
        Optional<MethodElement> defaultConstructor = ArrayableClassElement.super.getDefaultConstructor();
        if (defaultConstructor.isPresent()) {
            return defaultConstructor;
        }
        return possibleDefaultEmptyConstructor();
    }

    private Optional<MethodElement> possibleDefaultEmptyConstructor() {
        List<ConstructorNode> constructors = classNode.getDeclaredConstructors();
        if (CollectionUtils.isEmpty(constructors) && !classNode.isAbstract() && !classNode.isEnum()) {
            // empty default constructor
            return createMethodElement(new ConstructorNode(Modifier.PUBLIC, new BlockStatement()));
        }
        return Optional.empty();
    }

    private Optional<MethodElement> createMethodElement(MethodNode method) {
        return Optional.ofNullable(method).map(executableElement -> {
            if (executableElement instanceof ConstructorNode) {
                return visitorContext.getElementFactory().newConstructorElement(this, executableElement, elementAnnotationMetadataFactory);
            } else {
                return visitorContext.getElementFactory().newMethodElement(this, executableElement, elementAnnotationMetadataFactory);
            }
        });
    }

    @Override
    @NonNull
    public Map<String, ClassElement> getTypeArguments() {
        if (resolvedTypeArguments == null) {
            resolvedTypeArguments = resolveClassTypeArguments(getNativeType(), classNode, Collections.emptyMap(), new HashSet<>());
        }
        return resolvedTypeArguments;
    }

    @Override
    public @NonNull List<PropertyElement> getSyntheticBeanProperties() {
        // Native properties should be composed of field + synthetic getter/setter
        if (nativeProperties == null) {
            PropertyElementQuery configuration = new PropertyElementQuery();
            configuration.allowStaticProperties(true);
            Set<String> nativeProps = getPropertyNodes().stream().map(PropertyNode::getName).collect(Collectors.toCollection(LinkedHashSet::new));
            nativeProperties = AstBeanPropertiesUtils.resolveBeanProperties(configuration,
                this,
                () -> getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance()),
                () -> getPropertyNodes().stream().map(propertyNode -> visitorContext.getElementFactory().newFieldElement(this, propertyNode.getField(), elementAnnotationMetadataFactory)).collect(Collectors.toList()),
                true,
                nativeProps,
                methodElement -> Optional.empty(),
                methodElement -> Optional.empty(),
                value -> mapPropertyElement(nativeProps, value, configuration, true));
        }
        return nativeProperties;
    }

    @Override
    public @NonNull List<PropertyElement> getBeanProperties(@NonNull PropertyElementQuery propertyElementQuery) {
        if (isRecord()) {
            return AstBeanPropertiesUtils.resolveBeanProperties(propertyElementQuery,
                this,
                this::getRecordMethods,
                this::getRecordFields,
                true,
                Collections.emptySet(),
                methodElement -> Optional.empty(),
                methodElement -> Optional.empty(),
                this::mapToPropertyElement);
        }
        Set<String> nativeProps = getPropertyNodes().stream()
            .map(PropertyNode::getName)
            .collect(Collectors.toCollection(LinkedHashSet::new));
        return AstBeanPropertiesUtils.resolveBeanProperties(propertyElementQuery,
            this,
            () -> getEnclosedElements(ElementQuery.ALL_METHODS.onlyInstance()),
            () -> getEnclosedElements(ElementQuery.ALL_FIELDS),
            true,
            nativeProps,
            methodElement -> Optional.empty(),
            methodElement -> Optional.empty(),
            value -> mapPropertyElement(nativeProps, value, propertyElementQuery, false));
    }

    @Override
    public @NonNull List<PropertyElement> getBeanProperties() {
        if (properties == null) {
            properties = getBeanProperties(PropertyElementQuery.of(this));
        }
        return properties;
    }

    private GroovyPropertyElement mapToPropertyElement(AstBeanPropertiesUtils.BeanPropertyData value) {
        return new GroovyPropertyElement(
            visitorContext,
            GroovyClassElement.this,
            value.type,
            value.readAccessKind == null ? null : value.getter,
            value.writeAccessKind == null ? null : value.setter,
            value.field,
            elementAnnotationMetadataFactory,
            value.propertyName,
            value.readAccessKind == null ? PropertyElement.AccessKind.METHOD : PropertyElement.AccessKind.valueOf(value.readAccessKind.name()),
            value.writeAccessKind == null ? PropertyElement.AccessKind.METHOD : PropertyElement.AccessKind.valueOf(value.writeAccessKind.name()),
            value.isExcluded
        );
    }

    private List<MethodElement> getRecordMethods() {
        var methodElements = new ArrayList<MethodElement>();
        for (var recordComponentNode : classNode.getRecordComponents()) {
            var method = classNode.getMethods(recordComponentNode.getName()).get(0);
            methodElements.add(
                new GroovyMethodElement(
                    GroovyClassElement.this,
                    visitorContext,
                    new GroovyNativeElement.Method(method),
                    method,
                    elementAnnotationMetadataFactory
                )
            );
        }
        return methodElements;
    }

    private List<FieldElement> getRecordFields() {
        var fieldElements = new ArrayList<FieldElement>();
        for (var recordComponentNode : classNode.getRecordComponents()) {
            fieldElements.add(
                new GroovyFieldElement(
                    visitorContext,
                    GroovyClassElement.this,
                    classNode.getField(recordComponentNode.getName()),
                    elementAnnotationMetadataFactory
                )
            );
        }
        return fieldElements;
    }

    @Nullable
    private GroovyPropertyElement mapPropertyElement(Set<String> nativeProps,
                                                     AstBeanPropertiesUtils.BeanPropertyData value,
                                                     PropertyElementQuery conf,
                                                     boolean nativePropertiesOnly) {
        if (value.type == null) {
            // withSomething() builder setter
            value.type = PrimitiveElement.VOID;
        }
        AtomicReference<AnnotationMetadataProvider> ref = new AtomicReference<>();
        if (conf.getAccessKinds().contains(BeanProperties.AccessKind.METHOD) && nativeProps.remove(value.propertyName)) {
            AnnotationMetadataProvider methodAnnotationMetadataProvider = new AnnotationMetadataProvider() {
                @Override
                public @NonNull AnnotationMetadata getAnnotationMetadata() {
                    return ref.get().getAnnotationMetadata();
                }
            };
            AnnotationMetadataProvider annotationMetadataProvider = new AnnotationMetadataProvider() {
                @Override
                public @NonNull AnnotationMetadata getAnnotationMetadata() {
                    return new AnnotationMetadataHierarchy(GroovyClassElement.this, ref.get().getAnnotationMetadata());
                }
            };
            if (nativePropertiesOnly && value.field == null) {
                return null;
            }
            if (value.field != null && value.readAccessKind != BeanProperties.AccessKind.METHOD) {
                String getterName = NameUtils.getterNameFor(
                    value.propertyName,
                    value.type.equals(PrimitiveElement.BOOLEAN)
                );
                value.getter = MethodElement.of(
                    this,
                    value.field.getDeclaringType(),
                    methodAnnotationMetadataProvider,
                    annotationMetadataProvider,
                    visitorContext.getAnnotationMetadataBuilder(),
                    value.field.getGenericType(),
                    value.field.getGenericType(),
                    getterName,
                    value.field.isStatic(),
                    value.field.isFinal()
                );
                value.readAccessKind = BeanProperties.AccessKind.METHOD;
            } else if (nativePropertiesOnly) {
                value.getter = null;
                value.readAccessKind = null;
            }
            if (value.field != null && !value.field.isFinal() && value.writeAccessKind != BeanProperties.AccessKind.METHOD) {
                value.setter = MethodElement.of(
                    this,
                    value.field.getDeclaringType(),
                    methodAnnotationMetadataProvider,
                    annotationMetadataProvider,
                    visitorContext.getAnnotationMetadataBuilder(),
                    PrimitiveElement.VOID,
                    PrimitiveElement.VOID,
                    NameUtils.setterNameFor(value.propertyName),
                    value.field.isStatic(),
                    value.field.isFinal(),
                    ParameterElement.of(value.field.getGenericType(), value.propertyName, methodAnnotationMetadataProvider, visitorContext.getAnnotationMetadataBuilder())
                );
                value.writeAccessKind = BeanProperties.AccessKind.METHOD;
            } else if (nativePropertiesOnly) {
                value.setter = null;
                value.writeAccessKind = null;
            }
        } else if (nativePropertiesOnly) {
            return null;
        }
        // Skip not accessible setters / getters
        if (value.writeAccessKind != BeanProperties.AccessKind.METHOD) {
            value.setter = null;
        }
        if (value.readAccessKind != BeanProperties.AccessKind.METHOD) {
            value.getter = null;
        }
        var propertyElement = new GroovyPropertyElement(
            visitorContext,
            this,
            value.type,
            value.getter,
            value.setter,
            value.field,
            elementAnnotationMetadataFactory,
            value.propertyName,
            value.readAccessKind == null ? PropertyElement.AccessKind.METHOD : PropertyElement.AccessKind.valueOf(value.readAccessKind.name()),
            value.writeAccessKind == null ? PropertyElement.AccessKind.METHOD : PropertyElement.AccessKind.valueOf(value.writeAccessKind.name()),
            value.isExcluded
        );
        ref.set(propertyElement);
        return propertyElement;
    }

    @Override
    public boolean isArray() {
        return arrayDimensions > 0;
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new GroovyClassElement(visitorContext, getNativeType(), elementAnnotationMetadataFactory, resolvedTypeArguments, arrayDimensions);
    }

    @Override
    public int getArrayDimensions() {
        return arrayDimensions;
    }

    @Override
    public String toString() {
        return classNode.getName();
    }

    @Override
    public @NonNull String getName() {
        return classNode.getName();
    }

    @Override
    public @NonNull String getSimpleName() {
        return classNode.getNameWithoutPackage();
    }

    @Override
    public String getPackageName() {
        return classNode.getPackageName();
    }

    @Override
    public PackageElement getPackage() {
        final PackageNode aPackage = classNode.getPackage();
        if (aPackage == null) {
            return PackageElement.DEFAULT_PACKAGE;
        }
        return new GroovyPackageElement(
            visitorContext,
            aPackage,
            elementAnnotationMetadataFactory
        );
    }

    @Override
    public boolean isAbstract() {
        return classNode.isAbstract();
    }

    @Override
    public boolean isStatic() {
        // I assume Groovy can decide not to make the class static internally
        // and isStaticClass will be false even if the class has a static modifier
        return classNode.isStaticClass() || Modifier.isStatic(classNode.getModifiers());
    }

    @Override
    public boolean isPublic() {
        return (classNode.isSyntheticPublic() || Modifier.isPublic(classNode.getModifiers())) && !isPackagePrivate();
    }

    @Override
    public boolean isPrivate() {
        return Modifier.isPrivate(classNode.getModifiers());
    }

    @Override
    public boolean isFinal() {
        return Modifier.isFinal(classNode.getModifiers());
    }

    @Override
    public boolean isProtected() {
        return Modifier.isProtected(classNode.getModifiers());
    }

    @Override
    public boolean isAssignable(String type) {
        return AstClassUtils.isSubclassOfOrImplementsInterface(classNode, type);
    }

    @Override
    public boolean isAssignable(ClassElement type) {
        if (equals(type)) {
            return true; // Same type
        }
        return AstClassUtils.isSubclassOfOrImplementsInterface(classNode, type.getName());
    }

    @Override
    public Optional<ClassElement> getOptionalValueType() {
        if (isAssignable(Optional.class)) {
            return getFirstTypeArgument().or(() -> visitorContext.getClassElement(Object.class));
        }
        if (isAssignable(OptionalLong.class)) {
            return visitorContext.getClassElement(Long.class);
        }
        if (isAssignable(OptionalDouble.class)) {
            return visitorContext.getClassElement(Double.class);
        }
        if (isAssignable(OptionalInt.class)) {
            return visitorContext.getClassElement(Integer.class);
        }
        return Optional.empty();
    }

    @NonNull
    @Override
    public List<? extends ClassElement> getBoundGenericTypes() {
        GenericsType[] genericsTypes = classNode.getGenericsTypes();
        if (genericsTypes == null) {
            return Collections.emptyList();
        }
        return Arrays.stream(genericsTypes).map(this::newClassElement).toList();
    }

    @NonNull
    @Override
    public List<? extends GenericPlaceholderElement> getDeclaredGenericPlaceholders() {
        //noinspection unchecked
        return (List<? extends GenericPlaceholderElement>) getBoundGenericTypes();
    }

    private List<PropertyNode> getPropertyNodes() {
        if (propertyElements != null) {
            return propertyElements;
        }
        var propertyNodes = new ArrayList<PropertyNode>();
        ClassNode classNode = this.classNode;
        while (classNode != null && !classNode.equals(ClassHelper.OBJECT_TYPE) && !classNode.equals(ClassHelper.Enum_Type)) {
            propertyNodes.addAll(classNode.getProperties());
            classNode = classNode.getSuperClass();
        }
        var propertyElements = new ArrayList<PropertyNode>();
        for (PropertyNode propertyNode : propertyNodes) {
            if (propertyNode.isPublic()) {
                propertyElements.add(propertyNode);
            }
        }
        this.propertyElements = propertyElements;
        return propertyElements;
    }

    @Override
    public @NonNull ClassElement getType() {
        if (theType == null) {
            GroovyNativeElement nativeType = getNativeType();
            var thisClassNode = (ClassNode) nativeType.annotatedNode();
            ClassNode redirect = thisClassNode.redirect();
            // This should eliminate type annotations
            theType = new GroovyClassElement(visitorContext, new GroovyNativeElement.Class(redirect), elementAnnotationMetadataFactory, resolvedTypeArguments, arrayDimensions, isTypeVar);
        }
        return theType;
    }

    @Override
    public @NonNull MutableAnnotationMetadataDelegate<AnnotationMetadata> getTypeAnnotationMetadata() {
        if (elementTypeAnnotationMetadata == null) {
            elementTypeAnnotationMetadata = elementAnnotationMetadataFactory.buildTypeAnnotations(this);
        }
        return elementTypeAnnotationMetadata;
    }

    /**
     * The groovy elements query helper.
     */
    private final class GroovyEnclosedElementsQuery extends EnclosedElementsQuery<ClassNode, AnnotatedNode> {

        private final boolean isSource;

        private GroovyEnclosedElementsQuery(boolean isSource) {
            this.isSource = isSource;
        }

        @Override
        protected boolean hasAnnotation(AnnotatedNode element, Class<? extends Annotation> annotation) {
            if (element.getAnnotations() != null) {
                return element.getAnnotations().stream().anyMatch(a -> a.getClassNode().getName().equals(annotation.getName()));
            }
            return false;
        }

        @Override
        protected ClassNode getNativeClassType(ClassElement classElement) {
            return (ClassNode) ((GroovyClassElement) classElement).getNativeType().annotatedNode();
        }

        @Override
        protected String getElementName(AnnotatedNode element) {
            if (element instanceof ClassNode cn) {
                return cn.getName();
            }
            if (element instanceof MethodNode methodNode) {
                return methodNode.getName();
            }
            if (element instanceof FieldNode fieldNode) {
                return fieldNode.getName();
            }
            return "";
        }

        @Override
        protected Set<AnnotatedNode> getExcludedNativeElements(ElementQuery.Result<?> result) {
            if (result.isExcludePropertyElements()) {
                var excluded = new HashSet<AnnotatedNode>();
                for (PropertyElement excludePropertyElement : getBeanProperties()) {
                    excludePropertyElement.getReadMethod()
                        .filter(m -> !m.isSynthetic())
                        .ifPresent(methodElement -> excluded.add(((GroovyNativeElement) methodElement.getNativeType()).annotatedNode()));
                    excludePropertyElement.getWriteMethod()
                        .filter(m -> !m.isSynthetic())
                        .ifPresent(methodElement -> excluded.add(((GroovyNativeElement) methodElement.getNativeType()).annotatedNode()));
                    excludePropertyElement.getField().ifPresent(fieldElement -> excluded.add(((GroovyNativeElement) fieldElement.getNativeType()).annotatedNode()));
                }
                return excluded;
            }
            return super.getExcludedNativeElements(result);
        }

        @Override
        protected ClassNode getSuperClass(ClassNode classNode) {
            return classNode.getSuperClass();
        }

        @Override
        protected @NonNull Collection<ClassNode> getInterfaces(ClassNode classNode) {
            return Arrays.stream(classNode.getInterfaces())
                .filter(interfaceNode -> !interfaceNode.getName().equals(GroovyObject.class.getName()))
                .toList();
        }

        @Override
        protected @NonNull List<AnnotatedNode> getEnclosedElements(ClassNode classNode,
                                                                   ElementQuery.Result<?> result,
                                                                   boolean includeAbstract) {
            Class<?> elementType = result.getElementType();
            return getEnclosedElements(classNode, result, elementType, includeAbstract);
        }

        private List<AnnotatedNode> getEnclosedElements(ClassNode classNode,
                                                        ElementQuery.Result<?> result,
                                                        Class<?> elementType,
                                                        boolean includeAbstract) {
            if (elementType == MemberElement.class) {
                return Stream.concat(
                    getEnclosedElements(classNode, result, FieldElement.class, includeAbstract).stream(),
                    getEnclosedElements(classNode, result, MethodElement.class, includeAbstract).stream()
                ).toList();
            } else if (elementType == MethodElement.class) {
                return classNode.getMethods()
                    .stream()
                    .filter(methodNode -> !JUNK_METHOD_FILTER.test(methodNode) && (methodNode.getModifiers() & ACC_SYNTHETIC) == 0 && (includeAbstract || isNonAbstract(classNode, methodNode)))
                    .<AnnotatedNode>map(m -> m)
                    .toList();
            } else if (elementType == FieldElement.class) {
                return classNode.getFields().stream()
                    .filter(fieldNode -> (!fieldNode.isEnum() || result.isIncludeEnumConstants()) && !JUNK_FIELD_FILTER.test(fieldNode) && (fieldNode.getModifiers() & ACC_SYNTHETIC) == 0)
                    .<AnnotatedNode>map(m -> m)
                    .toList();

            } else if (elementType == ConstructorElement.class) {
                return classNode.getDeclaredConstructors()
                    .stream()
                    .filter(methodNode -> !JUNK_METHOD_FILTER.test(methodNode) && (methodNode.getModifiers() & ACC_SYNTHETIC) == 0)
                    .<AnnotatedNode>map(m -> m)
                    .toList();
            } else if (elementType == ClassElement.class) {
                return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(classNode.getInnerClasses(), Spliterator.ORDERED),
                        false)
                    .filter(innerClassNode -> (innerClassNode.getModifiers() & ACC_SYNTHETIC) == 0)
                    .<AnnotatedNode>map(m -> m)
                    .toList();
            } else {
                throw new IllegalStateException("Unknown result type: " + elementType);
            }
        }

        private boolean isNonAbstract(ClassNode classNode, MethodNode methodNode) {
            if (methodNode.isDefault()) {
                return false;
            }
            if (methodNode.isPrivate() && classNode.isInterface()) {
                return true;
            }
            if (!methodNode.isAbstract() && classNode.isInterface()) {
                return false;
            }
            return !methodNode.isAbstract();
        }

        @Override
        protected boolean excludeClass(ClassNode classNode) {
            String packageName = Objects.requireNonNullElse(classNode.getPackageName(), "");
            if (packageName.startsWith("org.spockframework.lang") || packageName.startsWith("spock.mock") || packageName.startsWith("spock.lang")) {
                // Performance optimization to exclude Spock's deep hierarchy
                return true;
            }
            String className = classNode.getName();
            return Object.class.getName().equals(className)
                || Enum.class.getName().equals(className)
                || GroovyObjectSupport.class.getName().equals(className)
                || Script.class.getName().equals(className);
        }

        @Override
        protected boolean isAbstractClass(ClassNode classNode) {
            return classNode.isAbstract();
        }

        @Override
        protected boolean isInterface(ClassNode classNode) {
            if (classNode.isInterface()) {
                return true;
            }
            List<AnnotationNode> annotations = classNode.getAnnotations();
            if (annotations != null) {
                return annotations.stream().anyMatch(a -> a.getClassNode().getName().equals(groovy.transform.Trait.class.getName()));
            }
            return false;
        }

        @Override
        protected @NonNull Element toAstElement(AnnotatedNode nativeType, Class<?> elementType) {
            final GroovyElementFactory elementFactory = visitorContext.getElementFactory();
            if (isSource) {
                if (!(nativeType instanceof ConstructorNode) && nativeType instanceof MethodNode methodNode) {
                    return elementFactory.newSourceMethodElement(
                        GroovyClassElement.this,
                        methodNode,
                        elementAnnotationMetadataFactory
                    );
                }
                if (nativeType instanceof ClassNode cn) {
                    return elementFactory.newSourceClassElement(
                        cn,
                        elementAnnotationMetadataFactory
                    );
                }
            }
            if (nativeType instanceof ConstructorNode constructorNode) {
                return elementFactory.newConstructorElement(
                    GroovyClassElement.this,
                    constructorNode,
                    elementAnnotationMetadataFactory
                );
            }
            if (nativeType instanceof MethodNode methodNode) {
                return elementFactory.newMethodElement(
                    GroovyClassElement.this,
                    methodNode,
                    elementAnnotationMetadataFactory
                );
            }
            if (nativeType instanceof FieldNode fieldNode) {
                if (fieldNode.isEnum()) {
                    return elementFactory.newEnumConstantElement(
                        GroovyClassElement.this,
                        fieldNode,
                        elementAnnotationMetadataFactory
                    );
                }
                return elementFactory.newFieldElement(
                    GroovyClassElement.this,
                    fieldNode,
                    elementAnnotationMetadataFactory
                );
            }
            if (nativeType instanceof ClassNode cn) {
                return elementFactory.newClassElement(
                    cn,
                    elementAnnotationMetadataFactory
                );
            }
            throw new IllegalStateException("Unknown element: " + nativeType);
        }
    }

}
