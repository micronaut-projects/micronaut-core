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
import io.micronaut.ast.groovy.utils.AstGenericUtils;
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
import io.micronaut.inject.ast.PropertyElementQuery;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ConstructorElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
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
import io.micronaut.inject.ast.utils.AstBeanPropertiesUtils;
import io.micronaut.inject.ast.utils.EnclosedElementsQuery;
import org.codehaus.groovy.ast.AnnotatedNode;
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
import org.objectweb.asm.Opcodes;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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

        return fieldName.startsWith("$") ||
            fieldName.startsWith("__$") ||
            fieldName.contains("trait$") ||
            fieldName.equals("metaClass") ||
            m.getDeclaringClass().equals(ClassHelper.GROOVY_OBJECT_TYPE) ||
            m.getDeclaringClass().equals(ClassHelper.OBJECT_TYPE);
    };
    protected final ClassNode classNode;
    private final int arrayDimensions;
    private final boolean isTypeVar;
    private List<? extends ClassElement> overrideBoundGenericTypes;
    private Map<String, Map<String, ClassNode>> genericInfo;
    private List<PropertyElement> properties;
    private List<PropertyElement> nativeProperties;
    private Map<String, ClassElement> resolvedTypeArguments;
    private final GroovyEnclosedElementsQuery groovyEnclosedElementsQuery = new GroovyEnclosedElementsQuery(false);
    private final GroovyEnclosedElementsQuery groovySourceEnclosedElementsQuery = new GroovyEnclosedElementsQuery(true);

    /**
     * @param visitorContext            The visitor context
     * @param classNode                 The {@link ClassNode}
     * @param annotationMetadataFactory The annotation metadata
     */
    public GroovyClassElement(GroovyVisitorContext visitorContext,
                              ClassNode classNode,
                              ElementAnnotationMetadataFactory annotationMetadataFactory) {
        this(visitorContext, classNode, annotationMetadataFactory, null, 0);
    }

    /**
     * @param visitorContext            The visitor context
     * @param classNode                 The {@link ClassNode}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param genericInfo               The generic info
     * @param arrayDimensions           The number of array dimensions
     */
    GroovyClassElement(
        GroovyVisitorContext visitorContext,
        ClassNode classNode,
        ElementAnnotationMetadataFactory annotationMetadataFactory,
        Map<String, Map<String, ClassNode>> genericInfo,
        int arrayDimensions) {
        this(visitorContext, classNode, annotationMetadataFactory, genericInfo, arrayDimensions, false);
    }

    /**
     * @param visitorContext            The visitor context
     * @param classNode                 The {@link ClassNode}
     * @param annotationMetadataFactory The annotation metadata factory
     * @param genericInfo               The generic info
     * @param arrayDimensions           The number of array dimensions
     * @param isTypeVar                 Is the element a type variable
     */
    GroovyClassElement(
        GroovyVisitorContext visitorContext,
        ClassNode classNode,
        ElementAnnotationMetadataFactory annotationMetadataFactory,
        Map<String, Map<String, ClassNode>> genericInfo,
        int arrayDimensions,
        boolean isTypeVar) {
        super(visitorContext, classNode, annotationMetadataFactory);
        this.classNode = classNode;
        this.genericInfo = genericInfo;
        this.arrayDimensions = arrayDimensions;
        if (classNode.isArray()) {
            classNode.setName(classNode.getComponentType().getName());
        }
        this.isTypeVar = isTypeVar;
    }

    @Override
    protected GroovyClassElement copyConstructor() {
        return new GroovyClassElement(visitorContext, classNode, elementAnnotationMetadataFactory, genericInfo, arrayDimensions, isTypeVar);
    }

    @Override
    protected void copyValues(AbstractGroovyElement element) {
        super.copyValues(element);
        ((GroovyClassElement) element).resolvedTypeArguments = resolvedTypeArguments;
    }

    @Override
    public ClassElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (ClassElement) super.withAnnotationMetadata(annotationMetadata);
    }

    @Override
    public ClassElement withTypeArguments(Map<String, ClassElement> typeArguments) {
        GroovyClassElement groovyClassElement = new GroovyClassElement(visitorContext, classNode, elementAnnotationMetadataFactory, genericInfo, arrayDimensions);
        groovyClassElement.resolvedTypeArguments = typeArguments;
        return groovyClassElement;
    }

    @Override
    public boolean isTypeVariable() {
        return isTypeVar;
    }

    @Override
    public <T extends Element> List<T> getEnclosedElements(@NonNull ElementQuery<T> query) {
        return groovyEnclosedElementsQuery.getEnclosedElements(this, query);
    }

    /**
     * This method will produce th elements just like {@link #getEnclosedElements(ElementQuery)}
     * but the elements are constructed as the source ones.
     * {@link io.micronaut.inject.ast.ElementFactory#newSourceMethodElement(ClassElement, Object, ElementAnnotationMetadataFactory)}.
     *
     * @param query The query
     * @param <T>   The element type
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
            return Optional.ofNullable(classNode.getOuterClass()).map(this::toGroovyClassElement);
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
            return Arrays.stream(interfaces).map(this::toGroovyClassElement).collect(Collectors.toList());
        }
        return Collections.emptyList();
    }

    @Override
    public Optional<ClassElement> getSuperType() {
        final ClassNode superClass = classNode.getUnresolvedSuperClass(false);
        if (superClass != null && !superClass.equals(ClassHelper.OBJECT_TYPE)) {
            return Optional.of(
                toGroovyClassElement(superClass)
            );
        }
        return Optional.empty();
    }

    private ClassElement toGroovyClassElement(ClassNode superClass) {
        return visitorContext.getElementFactory().newClassElement(superClass, elementAnnotationMetadataFactory);
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

    /**
     * Builds and returns the generic type information.
     *
     * @return The generic type info
     */
    public Map<String, Map<String, ClassNode>> getGenericTypeInfo() {
        if (genericInfo == null) {
            genericInfo = AstGenericUtils.buildAllGenericElementInfo(classNode, new GroovyVisitorContext(sourceUnit, compilationUnit));
        }
        return genericInfo;
    }

    @NonNull
    @Override
    public Map<String, ClassElement> getTypeArguments(@NonNull String type) {
        Map<String, Map<String, ClassNode>> allData = getGenericTypeInfo();
        Map<String, ClassNode> thisSpec = allData.get(getName());
        Map<String, ClassNode> forType = allData.get(type);
        if (forType != null) {
            Map<String, ClassElement> typeArgs = new LinkedHashMap<>(forType.size());
            for (Map.Entry<String, ClassNode> entry : forType.entrySet()) {
                ClassNode classNode = entry.getValue();
                ClassElement rawElement = new GroovyClassElement(visitorContext, classNode, elementAnnotationMetadataFactory);
                rawElement = adjustTypeAnnotationMetadata(rawElement);
                if (thisSpec != null) {
                    rawElement = getGenericElement(sourceUnit, classNode, rawElement, thisSpec);
                }
                typeArgs.put(entry.getKey(), rawElement);
            }
            return Collections.unmodifiableMap(typeArgs);
        }
        return Collections.emptyMap();
    }

    @NonNull
    @Override
    public Map<String, Map<String, ClassElement>> getAllTypeArguments() {
        Map<String, Map<String, ClassNode>> genericInfo =
            AstGenericUtils.buildAllGenericElementInfo(classNode, new GroovyVisitorContext(sourceUnit, compilationUnit));
        Map<String, Map<String, ClassElement>> results = new LinkedHashMap<>(genericInfo.size());

        genericInfo.forEach((name, generics) -> {
            Map<String, ClassElement> resolved = new LinkedHashMap<>(generics.size());
            generics.forEach((variable, type) -> {
                ClassElement classElement = new GroovyClassElement(visitorContext, type, elementAnnotationMetadataFactory);
                classElement = adjustTypeAnnotationMetadata(classElement);
                resolved.put(variable, classElement);
            });
            results.put(name, resolved);
        });
        results.put(getName(), getTypeArguments());
        return results;
    }

    @Override
    @NonNull
    public Map<String, ClassElement> getTypeArguments() {
        if (resolvedTypeArguments == null) {
            Map<String, Map<String, ClassNode>> genericInfo = getGenericTypeInfo();
            Map<String, ClassNode> info = genericInfo.get(classNode.getName());
            resolvedTypeArguments = resolveGenericMap(info);
        }
        return resolvedTypeArguments;
    }

    @NonNull
    private Map<String, ClassElement> resolveGenericMap(Map<String, ClassNode> info) {
        if (info != null) {
            Map<String, ClassElement> typeArgumentMap = new LinkedHashMap<>(info.size());
            GenericsType[] genericsTypes = classNode.getGenericsTypes();
            GenericsType[] redirectTypes = classNode.redirect().getGenericsTypes();
            if (genericsTypes != null && redirectTypes != null && genericsTypes.length == redirectTypes.length) {
                for (int i = 0; i < genericsTypes.length; i++) {
                    GenericsType gt = genericsTypes[i];
                    GenericsType redirectType = redirectTypes[i];
                    if (gt.isPlaceholder()) {
                        ClassNode cn = resolveTypeArgument(info, redirectType.getName());
                        if (cn != null) {
                            Map<String, ClassNode> newInfo = alignNewGenericsInfo(genericsTypes, redirectTypes, info);
                            typeArgumentMap.put(redirectType.getName(), adjustTypeAnnotationMetadata(new GroovyClassElement(
                                visitorContext,
                                cn,
                                elementAnnotationMetadataFactory,
                                Collections.singletonMap(cn.getName(), newInfo),
                                cn.isArray() ? computeDimensions(cn) : 0,
                                true
                            )));
                        }
                    } else {
                        ClassNode type;
                        String unresolvedName = redirectType.getType().getUnresolvedName();
                        ClassNode cn = info.get(unresolvedName);
                        if (cn != null) {
                            type = cn;
                        } else {
                            type = gt.getType();
                        }
                        typeArgumentMap.put(redirectType.getName(), adjustTypeAnnotationMetadata(new GroovyClassElement(
                            visitorContext,
                            type,
                            elementAnnotationMetadataFactory,
                            Collections.emptyMap(),
                            type.isArray() ? computeDimensions(type) : 0
                        )));
                    }
                }
            } else if (redirectTypes != null) {
                for (GenericsType gt : redirectTypes) {
                    String name = gt.getName();
                    ClassNode cn = resolveTypeArgument(info, name);
                    if (cn != null) {
                        Map<String, ClassNode> newInfo = Collections.emptyMap();
                        if (genericsTypes != null) {
                            newInfo = alignNewGenericsInfo(genericsTypes, redirectTypes, info);
                        }
                        typeArgumentMap.put(gt.getName(), adjustTypeAnnotationMetadata(new GroovyClassElement(
                            visitorContext,
                            cn,
                            elementAnnotationMetadataFactory,
                            Collections.singletonMap(cn.getName(), newInfo),
                            cn.isArray() ? computeDimensions(cn) : 0,
                                true
                        )));
                    }
                }
            }
            if (CollectionUtils.isNotEmpty(typeArgumentMap)) {
                return typeArgumentMap;
            }
        }
        Map<String, ClassNode> spec = AstGenericUtils.createGenericsSpec(classNode);
        if (!spec.isEmpty()) {
            Map<String, ClassElement> map = new LinkedHashMap<>(spec.size());
            for (Map.Entry<String, ClassNode> entry : spec.entrySet()) {
                ClassNode cn = entry.getValue();
                ClassElement classElement = visitorContext.getElementFactory().newClassElement(cn, elementAnnotationMetadataFactory);
                classElement = adjustTypeAnnotationMetadata(classElement);
                map.put(entry.getKey(), classElement);
            }
            return Collections.unmodifiableMap(map);
        }
        return Collections.emptyMap();
    }

    @Nullable
    private ClassNode resolveTypeArgument(Map<String, ClassNode> info, String name) {
        ClassNode cn = info.get(name);
        while (cn != null && cn.isGenericsPlaceHolder()) {
            name = cn.getUnresolvedName();
            ClassNode next = info.get(name);
            if (next == cn) {
                break;
            }
            cn = next;
        }
        return cn;
    }

    private int computeDimensions(ClassNode cn) {
        ClassNode componentType = cn.getComponentType();
        int i = 1;
        while (componentType != null && componentType.isArray()) {
            i++;
            componentType = componentType.getComponentType();
        }
        return i;
    }

    @Override
    public List<PropertyElement> getSyntheticBeanProperties() {
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
    public List<PropertyElement> getBeanProperties(PropertyElementQuery propertyElementQuery) {
        Set<String> nativeProps = getPropertyNodes().stream().map(PropertyNode::getName).collect(Collectors.toCollection(LinkedHashSet::new));
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
    public List<PropertyElement> getBeanProperties() {
        if (properties == null) {
            properties = getBeanProperties(PropertyElementQuery.of(this));
        }
        return properties;
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
            AnnotationMetadataProvider annotationMetadataProvider = new AnnotationMetadataProvider() {
                @Override
                public AnnotationMetadata getAnnotationMetadata() {
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
                    annotationMetadataProvider,
                    visitorContext.getAnnotationMetadataBuilder(),
                    PrimitiveElement.VOID,
                    PrimitiveElement.VOID,
                    NameUtils.setterNameFor(value.propertyName),
                    value.field.isStatic(),
                    value.field.isFinal(),
                    ParameterElement.of(value.field.getGenericType(), value.propertyName, annotationMetadataProvider, visitorContext.getAnnotationMetadataBuilder())
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
        GroovyPropertyElement propertyElement = new GroovyPropertyElement(
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
            value.isExcluded);
        ref.set(propertyElement);
        return propertyElement;
    }

    @Override
    public boolean isArray() {
        return arrayDimensions > 0;
    }

    @Override
    public ClassElement withArrayDimensions(int arrayDimensions) {
        return new GroovyClassElement(visitorContext, classNode, elementAnnotationMetadataFactory, getGenericTypeInfo(), arrayDimensions);
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
    public String getName() {
        return classNode.getName();
    }

    @Override
    public String getSimpleName() {
        return classNode.getNameWithoutPackage();
    }

    @Override
    public String getPackageName() {
        return classNode.getPackageName();
    }

    @Override
    public PackageElement getPackage() {
        final PackageNode aPackage = classNode.getPackage();
        if (aPackage != null) {
            return new GroovyPackageElement(
                visitorContext,
                aPackage,
                elementAnnotationMetadataFactory
            );
        } else {
            return PackageElement.DEFAULT_PACKAGE;
        }
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
    public ClassNode getNativeType() {
        return classNode;
    }

    @Override
    public boolean isAssignable(String type) {
        return AstClassUtils.isSubclassOfOrImplementsInterface(classNode, type);
    }

    @Override
    public boolean isAssignable(ClassElement type) {
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
        if (overrideBoundGenericTypes == null) {
            overrideBoundGenericTypes = getBoundGenericTypes(classNode);
        }
        return overrideBoundGenericTypes;
    }

    @NonNull
    private List<? extends ClassElement> getBoundGenericTypes(ClassNode classNode) {
        GenericsType[] genericsTypes = classNode.getGenericsTypes();
        if (genericsTypes == null) {
            return Collections.emptyList();
        } else {
            return Arrays.stream(genericsTypes)
                .map(cn -> {
                    if (cn.isWildcard()) {
                        List<GroovyClassElement> upperBounds;
                        if (cn.getUpperBounds() != null && cn.getUpperBounds().length > 0) {
                            upperBounds = Arrays.stream(cn.getUpperBounds())
                                .map(bound -> (GroovyClassElement) toClassElement(bound))
                                .collect(Collectors.toList());
                        } else {
                            upperBounds = Collections.singletonList((GroovyClassElement) visitorContext.getClassElement(Object.class).get());
                        }
                        List<GroovyClassElement> lowerBounds;
                        if (cn.getLowerBound() == null) {
                            lowerBounds = Collections.emptyList();
                        } else {
                            lowerBounds = Collections.singletonList((GroovyClassElement) toClassElement(cn.getLowerBound()));
                        }
                        return new GroovyWildcardElement(
                            upperBounds,
                            lowerBounds,
                            elementAnnotationMetadataFactory
                        );
                    } else {
                        return toClassElement(cn.getType());
                    }
                })
                .collect(Collectors.toList());
        }
    }

    @NonNull
    @Override
    public List<? extends GenericPlaceholderElement> getDeclaredGenericPlaceholders() {
        //noinspection unchecked
        return (List<? extends GenericPlaceholderElement>) getBoundGenericTypes(classNode.redirect());
    }

    protected final ClassElement toClassElement(ClassNode classNode) {
        return visitorContext.getElementFactory().newClassElement(classNode, elementAnnotationMetadataFactory)
            .withAnnotationMetadata(AnnotationMetadata.EMPTY_METADATA);
    }

    @NonNull
    @Override
    public ClassElement withBoundGenericTypes(@NonNull List<? extends ClassElement> typeArguments) {
        // we can't create a new ClassNode, so we have to go this route.
        GroovyClassElement copy = new GroovyClassElement(visitorContext, classNode, elementAnnotationMetadataFactory);
        copy.overrideBoundGenericTypes = typeArguments;
        return copy;
    }

    private List<PropertyNode> getPropertyNodes() {
        List<PropertyNode> propertyNodes = new ArrayList<>();
        ClassNode classNode = this.classNode;
        while (classNode != null && !classNode.equals(ClassHelper.OBJECT_TYPE) && !classNode.equals(ClassHelper.Enum_Type)) {
            propertyNodes.addAll(classNode.getProperties());
            classNode = classNode.getSuperClass();
        }
        List<PropertyNode> propertyElements = new ArrayList<>();
        for (PropertyNode propertyNode : propertyNodes) {
            if (propertyNode.isPublic()) {
                propertyElements.add(propertyNode);
            }
        }
        return propertyElements;
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
        protected Set<AnnotatedNode> getExcludedNativeElements(ElementQuery.Result<?> result) {
            if (result.isExcludePropertyElements()) {
                Set<AnnotatedNode> excluded = new HashSet<>();
                for (PropertyElement excludePropertyElement : getBeanProperties()) {
                    excludePropertyElement.getReadMethod()
                        .filter(m -> !m.isSynthetic())
                        .ifPresent(methodElement -> excluded.add((AnnotatedNode) methodElement.getNativeType()));
                    excludePropertyElement.getWriteMethod()
                        .filter(m -> !m.isSynthetic())
                        .ifPresent(methodElement -> excluded.add((AnnotatedNode) methodElement.getNativeType()));
                    excludePropertyElement.getField().ifPresent(fieldElement -> excluded.add((AnnotatedNode) fieldElement.getNativeType()));
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
        protected Collection<ClassNode> getInterfaces(ClassNode classNode) {
            return Arrays.stream(classNode.getInterfaces())
                .filter(interfaceNode -> !interfaceNode.getName().equals(GroovyObject.class.getName()))
                .toList();
        }

        @Override
        protected List<AnnotatedNode> getEnclosedElements(ClassNode classNode,
                                                          ElementQuery.Result<?> result) {
            Class<?> elementType = result.getElementType();
            return getEnclosedElements(classNode, result, elementType);
        }

        private List<AnnotatedNode> getEnclosedElements(ClassNode classNode, ElementQuery.Result<?> result, Class<?> elementType) {
            if (elementType == MemberElement.class) {
                return Stream.concat(
                    getEnclosedElements(classNode, result, FieldElement.class).stream(),
                    getEnclosedElements(classNode, result, MethodElement.class).stream()
                ).toList();
            } else if (elementType == MethodElement.class) {
                return classNode.getMethods()
                    .stream()
                    .filter(methodNode -> !JUNK_METHOD_FILTER.test(methodNode) && (methodNode.getModifiers() & Opcodes.ACC_SYNTHETIC) == 0)
                    .<AnnotatedNode>map(m -> m)
                    .toList();
            } else if (elementType == FieldElement.class) {
                return classNode.getFields().stream()
                    .filter(fieldNode -> (!fieldNode.isEnum() || result.isIncludeEnumConstants()) && !JUNK_FIELD_FILTER.test(fieldNode) && (fieldNode.getModifiers() & Opcodes.ACC_SYNTHETIC) == 0)
                    .<AnnotatedNode>map(m -> m)
                    .toList();

            } else if (elementType == ConstructorElement.class) {
                return classNode.getDeclaredConstructors()
                    .stream()
                    .filter(methodNode -> !JUNK_METHOD_FILTER.test(methodNode) && (methodNode.getModifiers() & Opcodes.ACC_SYNTHETIC) == 0)
                    .<AnnotatedNode>map(m -> m)
                    .toList();
            } else if (elementType == ClassElement.class) {
                return StreamSupport.stream(
                        Spliterators.spliteratorUnknownSize(classNode.getInnerClasses(), Spliterator.ORDERED),
                        false)
                    .filter(innerClassNode -> (innerClassNode.getModifiers() & Opcodes.ACC_SYNTHETIC) == 0)
                    .<AnnotatedNode>map(m -> m)
                    .toList();
            } else {
                throw new IllegalStateException("Unknown result type: " + elementType);
            }
        }

        @Override
        protected boolean excludeClass(ClassNode classNode) {
            String packageName = Objects.requireNonNullElse(classNode.getPackageName(), "");
            if (packageName.startsWith("org.spockframework.lang") || packageName.startsWith("spock.mock") || packageName.startsWith("spock.lang")) {
                // Performance optimization to exclude Spock;s deep hierarchy
                return true;
            }
            String className = classNode.getName();
            return Object.class.getName().equals(className)
                || Enum.class.getName().equals(className)
                || GroovyObjectSupport.class.getName().equals(className)
                || Script.class.getName().equals(className);
        }

        @Override
        protected Element toAstElement(AnnotatedNode enclosedElement) {
            final GroovyElementFactory elementFactory = visitorContext.getElementFactory();
            if (isSource) {
                if (!(enclosedElement instanceof ConstructorNode) && enclosedElement instanceof MethodNode methodNode) {
                    return elementFactory.newSourceMethodElement(
                        GroovyClassElement.this,
                        methodNode,
                        elementAnnotationMetadataFactory
                    );
                }
                if (enclosedElement instanceof ClassNode cn) {
                    return elementFactory.newSourceClassElement(
                        cn,
                        elementAnnotationMetadataFactory
                    );
                }
            }
            if (enclosedElement instanceof ConstructorNode constructorNode) {
                return elementFactory.newConstructorElement(
                    GroovyClassElement.this,
                    constructorNode,
                    elementAnnotationMetadataFactory
                );
            }
            if (enclosedElement instanceof MethodNode methodNode) {
                return elementFactory.newMethodElement(
                    GroovyClassElement.this,
                    methodNode,
                    elementAnnotationMetadataFactory
                );
            }
            if (enclosedElement instanceof FieldNode fieldNode) {
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
            if (enclosedElement instanceof ClassNode cn) {
                return elementFactory.newClassElement(
                    cn,
                    elementAnnotationMetadataFactory
                );
            }
            throw new IllegalStateException("Unknown element: " + enclosedElement);
        }
    }

}
