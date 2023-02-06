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
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
 * @author Denis Stepanov
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
        return newClassElement(type, genericsSpec, new HashSet<>(), false, false);
    }

    @NonNull
    protected final ClassElement newClassElement(@NonNull GenericsType genericsType) {
        return newClassElement(genericsType, genericsType, Collections.emptyMap(), new HashSet<>(), false);
    }

    @NonNull
    protected final ClassElement newClassElement(@NonNull ClassNode type) {
        return newClassElement(type, Collections.emptyMap(), new HashSet<>(), false, false);
    }

    @NonNull
    private ClassElement newClassElement(GenericsType genericsType,
                                         GenericsType redirectType,
                                         Map<String, ClassElement> parentTypeArguments,
                                         Set<Object> visitedTypes,
                                         boolean isRawType) {
        if (parentTypeArguments == null) {
            parentTypeArguments = Collections.emptyMap();
        }
        if (genericsType.isWildcard()) {
            return resolveWildcard(genericsType, redirectType, parentTypeArguments, visitedTypes);
        }
        if (genericsType.isPlaceholder()) {
            return resolvePlaceholder(genericsType, redirectType, parentTypeArguments, visitedTypes, isRawType);
        }
        return newClassElement(genericsType.getType(), parentTypeArguments, visitedTypes, true, isRawType);
    }

    @NonNull
    private ClassElement newClassElement(ClassNode classNode,
                                         Map<String, ClassElement> parentTypeArguments,
                                         Set<Object> visitedTypes,
                                         boolean isTypeVariable,
                                         boolean isRawTypeParameter) {
        return newClassElement(classNode, parentTypeArguments, visitedTypes, isTypeVariable, isRawTypeParameter, false);
    }

    @NonNull
    private ClassElement newClassElement(ClassNode classNode,
                                         Map<String, ClassElement> parentTypeArguments,
                                         Set<Object> visitedTypes,
                                         boolean isTypeVariable,
                                         boolean isRawTypeParameter,
                                         boolean stripTypeArguments) {
        if (parentTypeArguments == null) {
            parentTypeArguments = Collections.emptyMap();
        }
        if (classNode.isArray()) {
            ClassNode componentType = classNode.getComponentType();
            return newClassElement(componentType, parentTypeArguments, visitedTypes, isTypeVariable, isRawTypeParameter)
                    .toArray();
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
                    redirectType = new GenericsType(classNode.redirect());
                }
            } else {
                // Bypass Groovy compiler weirdness
                genericsType = new GenericsType(classNode.redirect());
                redirectType = genericsType;
            }
            return newClassElement(genericsType, redirectType, parentTypeArguments, visitedTypes, isRawTypeParameter);
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
        Map<String, ClassElement> newTypeArguments;
        if (stripTypeArguments) {
            newTypeArguments = resolveTypeArgumentsToObject(classNode);
        } else {
            newTypeArguments = resolveTypeArguments(classNode, parentTypeArguments, visitedTypes);
        }
        return new GroovyClassElement(visitorContext, classNode, elementAnnotationMetadataFactory, newTypeArguments, 0, isTypeVariable);
    }

    private ClassElement resolvePlaceholder(GenericsType genericsType,
                                            GenericsType redirectType,
                                            Map<String, ClassElement> parentTypeArguments,
                                            Set<Object> visitedTypes,
                                            boolean isRawType) {
        PlaceholderEntry entry = new PlaceholderEntry(getNativeType(), genericsType.getName());
        String variableName = genericsType.getName();
        ClassElement boundVariable = parentTypeArguments.get(variableName);
        if (boundVariable != null) {
            if (boundVariable instanceof WildcardElement wildcardElement) {
                if (wildcardElement.isBounded()) {
                    return wildcardElement;
                }
            } else {
                return boundVariable;
            }
        }
        List<ClassNode> classNodeBounds = new ArrayList<>();
        addBounds(genericsType, classNodeBounds);
        if (genericsType != redirectType) {
            addBounds(redirectType, classNodeBounds);
        }

        boolean alreadyVisitedPlaceholder = visitedTypes.contains(entry);
        if (!alreadyVisitedPlaceholder) {
            visitedTypes.add(entry);
        }

        List<GroovyClassElement> bounds = classNodeBounds
                .stream()
                .map(classNode -> {
                    if (alreadyVisitedPlaceholder && classNode.isGenericsPlaceHolder()) {
                        classNode = classNode.redirect();
                    }
                    return classNode;
                })
                .filter(classNode -> !alreadyVisitedPlaceholder || !classNode.isGenericsPlaceHolder())
                .map(classNode -> {
                    // Strip declared type arguments and replace with an Object to prevent recursion
                    boolean stripTypeArguments = alreadyVisitedPlaceholder;
                    return (GroovyClassElement) newClassElement(classNode, parentTypeArguments, visitedTypes, true, isRawType, stripTypeArguments);
                })
                .toList();

        if (bounds.isEmpty()) {
            bounds = Collections.singletonList((GroovyClassElement) getObjectClassElement());
        }
        return new GroovyGenericPlaceholderElement(visitorContext, genericsType.getType(), bounds, isRawType);
    }

    private static void addBounds(GenericsType genericsType, List<ClassNode> classNodeBounds) {
        if (genericsType.getUpperBounds() != null) {
            for (ClassNode ub : genericsType.getUpperBounds()) {
                if (!classNodeBounds.contains(ub)) {
                    classNodeBounds.add(ub);
                }
            }
        } else {
            ClassNode type = genericsType.getType();
            if (!classNodeBounds.contains(type)) {
                classNodeBounds.add(type);
            }
        }
    }

    private ClassElement getObjectClassElement() {
        return visitorContext.getClassElement("java.lang.Object").get();
    }

    private ClassElement resolveWildcard(GenericsType genericsType,
                                         GenericsType redirectType,
                                         Map<String, ClassElement> parentTypeArguments,
                                         Set<Object> visitedTypes) {
        Stream<ClassNode> lowerBounds = Stream.ofNullable(genericsType.getLowerBound());
        Stream<ClassNode> upperBounds;
        ClassNode[] genericsUpperBounds = genericsType.getUpperBounds();
        if (genericsUpperBounds == null || genericsUpperBounds.length == 0) {
            upperBounds = Stream.empty();
        } else {
            upperBounds = Arrays.stream(genericsUpperBounds);
        }
        List<ClassElement> upperBoundsAsElements = upperBounds
                .map(classNode -> newClassElement(classNode, parentTypeArguments, visitedTypes, true, false))
                .toList();
        List<ClassElement> lowerBoundsAsElements = lowerBounds
                .map(classNode ->newClassElement(classNode, parentTypeArguments, visitedTypes, true, false))
                .toList();
        if (upperBoundsAsElements.isEmpty()) {
            upperBoundsAsElements = Collections.singletonList(getObjectClassElement());
        }
        ClassElement upperType = WildcardElement.findUpperType(upperBoundsAsElements, lowerBoundsAsElements);
        if (upperType.getType().getName().equals("java.lang.Object")) {
            // Not bounded wildcard: <?>
            if (redirectType != null && redirectType != genericsType) {
                ClassElement definedTypeBound = newClassElement(redirectType, redirectType, parentTypeArguments, visitedTypes, false);
                // Use originating parameter to extract the bound defined
                if (definedTypeBound instanceof GroovyGenericPlaceholderElement groovyGenericPlaceholderElement) {
                    upperType = WildcardElement.findUpperType(groovyGenericPlaceholderElement.getBounds(), Collections.emptyList());
                }
            }
        }
        if (upperType.isPrimitive()) {
            // TODO: Support primitives for wildcards (? extends byte[])
            return upperType;
        }
        return new GroovyWildcardElement(
                (GroovyClassElement) upperType,
                upperBoundsAsElements.stream().map(GroovyClassElement.class::cast).toList(),
                lowerBoundsAsElements.stream().map(GroovyClassElement.class::cast).toList(),
                elementAnnotationMetadataFactory
        );
    }

    @NonNull
    protected final Map<String, ClassElement> resolveTypeArguments(MethodNode methodNode,
                                                                   @Nullable Map<String, ClassElement> parentTypeArguments) {
        if (parentTypeArguments == null) {
            parentTypeArguments = Collections.emptyMap();
        }
        Set<Object> visitedTypes = new HashSet<>();
        GenericsType[] genericsTypes = methodNode.getGenericsTypes();
        if (ArrayUtils.isEmpty(genericsTypes)) {
            return parentTypeArguments;
        }
        Map<String, ClassElement> newTypeArguments = new LinkedHashMap<>(parentTypeArguments);
        for (GenericsType genericsType : genericsTypes) {
            String variableName = genericsType.getName();
            ClassNode classNode;
            if (genericsType.isPlaceholder()) {
                ClassNode[] upperBounds = genericsType.getUpperBounds();
                ClassNode lowerBound = genericsType.getLowerBound();
                if (ArrayUtils.isNotEmpty(upperBounds)) {
                    classNode = upperBounds[0];
                } else if (lowerBound != null) {
                    classNode = lowerBound;
                } else {
                    classNode = ClassHelper.OBJECT_TYPE;
                }
            } else {
                classNode = genericsType.getType();
            }
            newTypeArguments.put(
                    variableName,
                    newClassElement(classNode, parentTypeArguments, visitedTypes, true, false)
            );
        }
        return newTypeArguments;
    }

    @NonNull
    protected final Map<String, ClassElement> resolveTypeArguments(ClassNode classNode,
                                                                   @Nullable Map<String, ClassElement> parentTypeArguments,
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
                ClassElement classElement = newClassElement(genericsType, redirectType, parentTypeArguments, visitedTypes, false);
                resolved.put(redirectType.getName(), classElement);
            }
        } else {
            boolean isRaw = genericsTypes == null;
            for (GenericsType redirectType : redirectTypes) {
                String variableName = redirectType.getName();
                resolved.put(
                        variableName,
                        newClassElement(redirectType, redirectType, parentTypeArguments, visitedTypes, isRaw)
                );
            }
        }
        return resolved;
    }

    @NonNull
    protected final Map<String, ClassElement> resolveTypeArgumentsToObject(ClassNode classNode) {
        GenericsType[] redirectTypes = classNode.redirect().getGenericsTypes();
        if (redirectTypes == null || redirectTypes.length == 0) {
            return Collections.emptyMap();
        }
        ClassElement objectClassElement = getObjectClassElement();
        Map<String, ClassElement> resolved = CollectionUtils.newLinkedHashMap(redirectTypes.length);
        for (GenericsType redirectType : redirectTypes) {
            String variableName = redirectType.getName();
            resolved.put(variableName, objectClassElement);
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

    record PlaceholderEntry(AnnotatedNode owner, String placeholderName) {
    }
}

