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

import groovy.transform.PackageScope;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementModifier;
import io.micronaut.inject.ast.PrimitiveElement;
import io.micronaut.inject.ast.WildcardElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.annotation.ElementMutableAnnotationMetadataDelegate;
import io.micronaut.inject.ast.annotation.MutableAnnotationMetadataDelegate;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Abstract Groovy element.
 *
 * @author Graeme Rocher
 * @since 1.1
 */

public abstract class AbstractGroovyElement implements Element, ElementMutableAnnotationMetadataDelegate<Element> {

    private static final Pattern JAVADOC_PATTERN = Pattern.compile("(/\\s*\\*\\*)|\\s*\\*|(\\s*[*/])");

    protected final SourceUnit sourceUnit;
    protected final CompilationUnit compilationUnit;
    protected final GroovyVisitorContext visitorContext;
    protected final ElementAnnotationMetadataFactory elementAnnotationMetadataFactory;
    protected AnnotationMetadata presetAnnotationMetadata;
    private ElementAnnotationMetadata elementAnnotationMetadata;
    private final AnnotatedNode annotatedNode;

    /**
     * Default constructor.
     *
     * @param visitorContext            The groovy visitor context
     * @param annotatedNode             The annotated node
     * @param annotationMetadataFactory The annotation metadata factory
     */
    protected AbstractGroovyElement(GroovyVisitorContext visitorContext,
                                    AnnotatedNode annotatedNode,
                                    ElementAnnotationMetadataFactory annotationMetadataFactory) {
        this.visitorContext = visitorContext;
        this.compilationUnit = visitorContext.getCompilationUnit();
        this.annotatedNode = annotatedNode;
        this.elementAnnotationMetadataFactory = annotationMetadataFactory;
        this.sourceUnit = visitorContext.getSourceUnit();
    }

    @Override
    public <T extends Annotation> Element annotate(String annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        return ElementMutableAnnotationMetadataDelegate.super.annotate(annotationType, consumer);
    }

    @Override
    public Element removeAnnotation(String annotationType) {
        return ElementMutableAnnotationMetadataDelegate.super.removeAnnotation(annotationType);
    }

    @Override
    public <T extends Annotation> Element removeAnnotation(Class<T> annotationType) {
        return ElementMutableAnnotationMetadataDelegate.super.removeAnnotation(annotationType);
    }

    @Override
    public <T extends Annotation> Element removeAnnotationIf(Predicate<AnnotationValue<T>> predicate) {
        return ElementMutableAnnotationMetadataDelegate.super.removeAnnotationIf(predicate);
    }

    @Override
    public Element removeStereotype(String annotationType) {
        return ElementMutableAnnotationMetadataDelegate.super.removeStereotype(annotationType);
    }

    @Override
    public <T extends Annotation> Element removeStereotype(Class<T> annotationType) {
        return ElementMutableAnnotationMetadataDelegate.super.removeStereotype(annotationType);
    }

    @Override
    public Element annotate(String annotationType) {
        return ElementMutableAnnotationMetadataDelegate.super.annotate(annotationType);
    }

    @Override
    public <T extends Annotation> Element annotate(Class<T> annotationType, Consumer<AnnotationValueBuilder<T>> consumer) {
        return ElementMutableAnnotationMetadataDelegate.super.annotate(annotationType, consumer);
    }

    @Override
    public <T extends Annotation> Element annotate(Class<T> annotationType) {
        return ElementMutableAnnotationMetadataDelegate.super.annotate(annotationType);
    }

    @Override
    public <T extends Annotation> Element annotate(AnnotationValue<T> annotationValue) {
        return ElementMutableAnnotationMetadataDelegate.super.annotate(annotationValue);
    }

    @Override
    public Element getReturnInstance() {
        return this;
    }

    @Override
    public MutableAnnotationMetadataDelegate<?> getAnnotationMetadata() {
        if (elementAnnotationMetadata == null) {
            if (presetAnnotationMetadata == null) {
                elementAnnotationMetadata = elementAnnotationMetadataFactory.build(this);
            } else {
                elementAnnotationMetadata = elementAnnotationMetadataFactory.build(this, presetAnnotationMetadata);
            }
        }
        return elementAnnotationMetadata;
    }

    /**
     * Constructs this element by invoking the constructor.
     *
     * @return the copy
     */
    @NonNull
    protected abstract AbstractGroovyElement copyConstructor();

    /**
     * Copies additional values after the element was constructed by {@link #copyConstructor()}.
     *
     * @param element the values to be copied to
     */
    protected void copyValues(@NonNull AbstractGroovyElement element) {
        element.presetAnnotationMetadata = presetAnnotationMetadata;
    }

    /**
     * Makes a copy of this element.
     *
     * @return a copy
     */
    @NonNull
    protected final AbstractGroovyElement copy() {
        AbstractGroovyElement element = copyConstructor();
        copyValues(element);
        return element;
    }

    @Override
    public io.micronaut.inject.ast.Element withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        AbstractGroovyElement element = copy();
        element.presetAnnotationMetadata = annotationMetadata;
        return element;
    }

    @Override
    public AnnotatedNode getNativeType() {
        return annotatedNode;
    }

    @Override
    public boolean isPackagePrivate() {
        return hasDeclaredAnnotation(PackageScope.class);
    }

    @NonNull
    protected final ClassElement newClassElement(@NonNull ClassNode type, @Nullable Map<String, ClassElement> genericsSpec) {
        if (genericsSpec == null) {
            return newClassElement(type);
        }
        return newClassElement(type, genericsSpec, new HashSet<>(), false);
    }

    @NonNull
    protected final ClassElement newClassElement(@NonNull GenericsType genericsType) {
        return newClassElement(genericsType, genericsType, Collections.emptyMap(), new HashSet<>(), false);
    }

    @NonNull
    protected final ClassElement newClassElement(@NonNull ClassNode type) {
        return newClassElement(type, Collections.emptyMap(), new HashSet<>(), false);
    }

    @NonNull
    private ClassElement newClassElement(GenericsType genericsType,
                                         GenericsType redirectType,
                                         Map<String, ClassElement> declaredTypeArguments,
                                         Set<Object> visitedTypes,
                                         boolean isRawType) {
        if (declaredTypeArguments == null) {
            declaredTypeArguments = Collections.emptyMap();
        }
        if (genericsType.isWildcard()) {
            return resolveWildcard(genericsType, redirectType, declaredTypeArguments, visitedTypes);
        }
        if (genericsType.isPlaceholder()) {
            return resolvePlaceholder(genericsType, declaredTypeArguments, visitedTypes, isRawType);
        }
        return newClassElement(genericsType.getType(), declaredTypeArguments, visitedTypes, isRawType);
    }

    @NonNull
    private ClassElement newClassElement(ClassNode classNode,
                                         Map<String, ClassElement> declaredTypeArguments,
                                         Set<Object> visitedTypes,
                                         boolean isRawTypeParameter) {
        if (declaredTypeArguments == null) {
            declaredTypeArguments = Collections.emptyMap();
        }
        if (classNode.isGenericsPlaceHolder()) {
            GenericsType genericsType;
            GenericsType redirectType;
            GenericsType[] genericsTypes = classNode.getGenericsTypes();
            if (ArrayUtils.isNotEmpty(genericsTypes)) {
                genericsType = genericsTypes[0];
                GenericsType[] redirectTypes = classNode.redirect().getGenericsTypes();
                if (ArrayUtils.isNotEmpty(redirectTypes)) {
                    redirectType = redirectTypes[0];
                } else {
                    redirectType = genericsType;
                }
            } else {
                // Bypass Groovy compiler weirdness
                genericsType = new GenericsType(classNode, new ClassNode[]{classNode.redirect()}, null);
                genericsType.setName(classNode.getUnresolvedName());
                genericsType.setPlaceholder(true);
                redirectType = genericsType;
            }
            return newClassElement(genericsType, redirectType, declaredTypeArguments, visitedTypes, isRawTypeParameter);
        }
        if (classNode.isArray()) {
            ClassNode componentType = classNode.getComponentType();
            return newClassElement(componentType, declaredTypeArguments, visitedTypes, isRawTypeParameter)
                    .toArray();
        }
        if (ClassHelper.isPrimitiveType(classNode)) {
            return PrimitiveElement.valueOf(classNode.getName());
        }
        if (classNode.isEnum()) {
            return new GroovyEnumElement(visitorContext, classNode, elementAnnotationMetadataFactory);
        }
        if (classNode.isAnnotationDefinition()) {
            return new GroovyAnnotationElement(visitorContext, classNode, elementAnnotationMetadataFactory);
        }
        Map<String, ClassElement> newTypeArguments = resolveTypeArguments(classNode, declaredTypeArguments, visitedTypes);
        return new GroovyClassElement(visitorContext, classNode, elementAnnotationMetadataFactory, newTypeArguments, 0);
    }

    private ClassElement resolvePlaceholder(GenericsType genericsType,
                                            Map<String, ClassElement> declaredTypeArguments,
                                            Set<Object> visitedTypes,
                                            boolean isRawType) {
        ClassNode type = genericsType.getType();
        List<GroovyClassElement> bounds = Collections.emptyList();
        if (!visitedTypes.contains(type) && !getNativeType().equals(type)) {
            visitedTypes.add(type);
            String variableName = genericsType.getName();
            ClassElement b = declaredTypeArguments.get(variableName);
            if (b != null) {
                if (b instanceof WildcardElement wildcardElement) {
                    if (wildcardElement.isBounded()) {
                        return wildcardElement;
                    }
                } else {
                    return b;
                }
            }
            Stream<ClassNode> classNodeBounds = genericsType.getUpperBounds() == null ? Stream.empty() : Arrays.stream(genericsType.getUpperBounds());
            bounds = classNodeBounds
                    .map(classNode -> (GroovyClassElement) newClassElement(classNode, declaredTypeArguments, visitedTypes, isRawType))
                    .toList();
        }
        if (bounds.isEmpty()) {
            bounds = Collections.singletonList((GroovyClassElement) getObjectClassElement());
        }
        return new GroovyGenericPlaceholderElement(visitorContext, genericsType.getType(), elementAnnotationMetadataFactory, Collections.emptyMap(), 0, bounds, isRawType);
    }

    private ClassElement getObjectClassElement() {
        return visitorContext.getClassElement("java.lang.Object").get();
    }

    private ClassElement resolveWildcard(GenericsType genericsType,
                                         GenericsType redirectType,
                                         Map<String, ClassElement> declaredTypeArguments,
                                         Set<Object> visitedTypes) {
        Stream<ClassNode> lowerBounds = Stream.ofNullable(genericsType.getLowerBound());
        Stream<ClassNode> upperBounds;
        ClassNode[] genericsUpperBounds = genericsType.getUpperBounds();
        if (genericsUpperBounds == null || genericsUpperBounds.length == 0) {
            upperBounds = Stream.empty();
        } else {
            upperBounds = Arrays.stream(genericsUpperBounds);
        }
        List<GroovyClassElement> upperBoundsAsElements = upperBounds
                .map(classNode -> (GroovyClassElement) newClassElement(classNode, declaredTypeArguments, visitedTypes, false))
                .toList();
        List<GroovyClassElement> lowerBoundsAsElements = lowerBounds
                .map(classNode -> (GroovyClassElement) newClassElement(classNode, declaredTypeArguments, visitedTypes, false))
                .toList();
        if (upperBoundsAsElements.isEmpty()) {
            upperBoundsAsElements = Collections.singletonList((GroovyClassElement) getObjectClassElement());
        }
        GroovyClassElement upperType = WildcardElement.findUpperType(upperBoundsAsElements, lowerBoundsAsElements);
        if (upperType.getType().getName().equals("java.lang.Object")) {
            // Not bounded wildcard: <?>
            if (redirectType != null && redirectType != genericsType) {
                GroovyClassElement definedTypeBound = (GroovyClassElement) newClassElement(redirectType, redirectType, declaredTypeArguments, visitedTypes, false);
                // Use originating parameter to extract the bound defined
                if (definedTypeBound instanceof GroovyGenericPlaceholderElement groovyGenericPlaceholderElement) {
                    upperType = WildcardElement.findUpperType(groovyGenericPlaceholderElement.getBounds(), Collections.emptyList());
                }
            }
        }
        return new GroovyWildcardElement(
                upperType,
                upperBoundsAsElements,
                lowerBoundsAsElements,
                elementAnnotationMetadataFactory
        );
    }

    @NonNull
    protected final Map<String, ClassElement> resolveTypeArguments(ClassNode classNode,
                                                                   @Nullable Map<String, ClassElement> info,
                                                                   Set<Object> visitedTypes) {
        GenericsType[] genericsTypes = classNode.getGenericsTypes();
        GenericsType[] redirectTypes = classNode.redirect().getGenericsTypes();
        if (redirectTypes == null || redirectTypes.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, ClassElement> resolved = CollectionUtils.newLinkedHashMap(redirectTypes.length);
        if (genericsTypes != null && genericsTypes.length == redirectTypes.length) {
            for (int i = 0; i < genericsTypes.length; i++) {
                GenericsType genericsType = genericsTypes[i];
                GenericsType redirectType = redirectTypes[i];
                String variableName = redirectType.getName();
                resolved.put(
                        variableName,
                        newClassElement(genericsType, redirectType, info, visitedTypes, false)
                );
            }
        } else {
            // Not null means raw type definition: "List myMethod()"
            // Null value means a class definition: "class List<T> {}"
            boolean isRaw = genericsTypes == null;
            for (GenericsType redirectType : redirectTypes) {
                String variableName = redirectType.getName();
                resolved.put(
                        variableName,
                        newClassElement(redirectType, redirectType, info, visitedTypes, isRaw)
                );
            }
        }
        return resolved;
    }

    @Override
    public Optional<String> getDocumentation() {
        if (annotatedNode.getGroovydoc() == null || annotatedNode.getGroovydoc().getContent() == null) {
            return Optional.empty();
        }
        return Optional.of(JAVADOC_PATTERN.matcher(annotatedNode.getGroovydoc().getContent()).replaceAll(StringUtils.EMPTY_STRING).trim());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractGroovyElement that = (AbstractGroovyElement) o;
        return annotatedNode.equals(that.annotatedNode);
    }

    @Override
    public int hashCode() {
        return Objects.hash(annotatedNode);
    }

    /**
     * Resolve modifiers for a method node.
     *
     * @param methodNode The method node
     * @return The modifiers
     */
    protected Set<ElementModifier> resolveModifiers(MethodNode methodNode) {
        return resolveModifiers(methodNode.getModifiers());
    }

    /**
     * Resolve modifiers for a field node.
     *
     * @param fieldNode The field node
     * @return The modifiers
     */
    protected Set<ElementModifier> resolveModifiers(FieldNode fieldNode) {
        return resolveModifiers(fieldNode.getModifiers());
    }

    /**
     * Resolve modifiers for a class node.
     *
     * @param classNode The class node
     * @return The modifiers
     */
    protected Set<ElementModifier> resolveModifiers(ClassNode classNode) {
        return resolveModifiers(classNode.getModifiers());
    }

    private Set<ElementModifier> resolveModifiers(int mod) {
        Set<ElementModifier> modifiers = new HashSet<>(5);
        if (Modifier.isPrivate(mod)) {
            modifiers.add(ElementModifier.PRIVATE);
        } else if (Modifier.isProtected(mod)) {
            modifiers.add(ElementModifier.PROTECTED);
        } else if (Modifier.isPublic(mod)) {
            modifiers.add(ElementModifier.PUBLIC);
        }
        if (Modifier.isAbstract(mod)) {
            modifiers.add(ElementModifier.ABSTRACT);
        } else if (Modifier.isStatic(mod)) {
            modifiers.add(ElementModifier.STATIC);
        }
        if (Modifier.isFinal(mod)) {
            modifiers.add(ElementModifier.FINAL);
        }
        return modifiers;
    }
}

