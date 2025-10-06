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

import groovy.lang.groovydoc.Groovydoc;
import groovy.transform.PackageScope;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
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
import io.micronaut.inject.ast.annotation.AbstractAnnotationElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Abstract Groovy element.
 *
 * @author Graeme Rocher
 * @author Denis Stepanov
 * @since 1.1
 */
@Internal
public abstract class AbstractGroovyElement extends AbstractAnnotationElement {

    private static final Pattern JAVADOC_PATTERN = Pattern.compile("(/\\s*\\*\\*)|\\s*\\*|(\\s*[*/])");

    protected final SourceUnit sourceUnit;
    protected final CompilationUnit compilationUnit;
    protected final GroovyVisitorContext visitorContext;
    private final GroovyNativeElement nativeElement;

    /**
     * Default constructor.
     *
     * @param visitorContext The groovy visitor context
     * @param nativeElement The native element
     * @param annotationMetadataFactory The annotation metadata factory
     */
    protected AbstractGroovyElement(GroovyVisitorContext visitorContext,
                                    GroovyNativeElement nativeElement,
                                    ElementAnnotationMetadataFactory annotationMetadataFactory) {
        super(annotationMetadataFactory);
        this.visitorContext = visitorContext;
        this.compilationUnit = visitorContext.getCompilationUnit();
        this.nativeElement = nativeElement;
        this.sourceUnit = visitorContext.getSourceUnit();
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
    public @NonNull GroovyNativeElement getNativeType() {
        return nativeElement;
    }

    @Override
    public boolean isPackagePrivate() {
        return hasDeclaredAnnotation(PackageScope.class);
    }

    @NonNull
    protected final ClassElement newClassElement(@NonNull ClassNode type,
                                                 @Nullable Map<String, ClassElement> genericsSpec) {
        if (genericsSpec == null) {
            return newClassElement(type);
        }
        return newClassElement(getNativeType(), type, genericsSpec, new HashSet<>(), false, false);
    }

    @NonNull
    protected final ClassElement newClassElement(GenericsType genericsType) {
        return newClassElement(getNativeType(), getNativeType().annotatedNode(), genericsType, genericsType, Collections.emptyMap(), new HashSet<>(), false);
    }

    @NonNull
    protected final ClassElement newClassElement(ClassNode type) {
        return newClassElement(getNativeType(), type, Collections.emptyMap(), new HashSet<>(), false, false);
    }

    @NonNull
    private ClassElement newClassElement(@Nullable GroovyNativeElement declaredElement,
                                         AnnotatedNode genericsOwner,
                                         GenericsType genericsType,
                                         GenericsType redirectType,
                                         Map<String, ClassElement> parentTypeArguments,
                                         Set<Object> visitedTypes,
                                         boolean isRawType) {
        if (parentTypeArguments == null) {
            parentTypeArguments = Collections.emptyMap();
        }
        if (genericsType.isWildcard()) {
            return resolveWildcard(declaredElement, genericsOwner, genericsType, redirectType, parentTypeArguments, visitedTypes);
        }
        if (genericsType.isPlaceholder()) {
            return resolvePlaceholder(declaredElement, genericsOwner, genericsType, redirectType, parentTypeArguments, visitedTypes, isRawType);
        }
        return newClassElement(declaredElement, genericsType.getType(), parentTypeArguments, visitedTypes, genericsType.isPlaceholder(), isRawType);
    }

    @NonNull
    private ClassElement newClassElement(@Nullable GroovyNativeElement declaredElement,
                                         ClassNode classNode,
                                         Map<String, ClassElement> parentTypeArguments,
                                         Set<Object> visitedTypes,
                                         boolean isTypeVariable,
                                         boolean isRawTypeParameter) {
        return newClassElement(declaredElement, classNode, parentTypeArguments, visitedTypes, isTypeVariable, isRawTypeParameter, false);
    }

    @NonNull
    private ClassElement newClassElement(@Nullable GroovyNativeElement declaredElement,
                                         ClassNode classNode,
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
            return newClassElement(declaredElement, componentType, parentTypeArguments, visitedTypes, isTypeVariable, isRawTypeParameter)
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
            return newClassElement(declaredElement, getNativeType().annotatedNode(), genericsType, redirectType, parentTypeArguments, visitedTypes, isRawTypeParameter);
        }
        if (ClassHelper.isPrimitiveType(classNode)) {
            return PrimitiveElement.valueOf(classNode.getName());
        }
        if (classNode.isEnum()) {
            return new GroovyEnumElement(visitorContext, new GroovyNativeElement.Class(classNode), elementAnnotationMetadataFactory);
        }
        if (classNode.isAnnotationDefinition()) {
            return new GroovyAnnotationElement(visitorContext, new GroovyNativeElement.Class(classNode), elementAnnotationMetadataFactory);
        }
        Map<String, ClassElement> newTypeArguments;
        GroovyNativeElement groovyNativeElement;
        if (declaredElement == null) {
            groovyNativeElement = new GroovyNativeElement.Class(classNode);
        } else {
            groovyNativeElement = new GroovyNativeElement.ClassWithOwner(classNode, declaredElement);
        }
        if (stripTypeArguments) {
            newTypeArguments = resolveTypeArgumentsToObject(classNode);
        } else {
            newTypeArguments = resolveClassTypeArguments(groovyNativeElement, classNode, parentTypeArguments, visitedTypes);
        }
        return new GroovyClassElement(visitorContext, groovyNativeElement, elementAnnotationMetadataFactory, newTypeArguments, 0, isTypeVariable);
    }

    @NonNull
    private ClassElement resolvePlaceholder(GroovyNativeElement owner,
                                            AnnotatedNode genericsOwner,
                                            GenericsType genericsType,
                                            GenericsType redirectType,
                                            Map<String, ClassElement> parentTypeArguments,
                                            Set<Object> visitedTypes,
                                            boolean isRawType) {
        ClassNode placeholderClassNode = genericsType.getType();
        String variableName = genericsType.getName();

        ClassElement resolvedBound = parentTypeArguments.get(variableName);
        List<GroovyClassElement> bounds = null;
        Element declaredElement = this;
        GroovyClassElement resolved = null;
        int arrayDimensions = 0;
        if (resolvedBound != null) {
            if (resolvedBound instanceof WildcardElement wildcardElement) {
                if (wildcardElement.isBounded()) {
                    return wildcardElement;
                }
            } else if (resolvedBound instanceof GroovyGenericPlaceholderElement groovyGenericPlaceholderElement) {
                bounds = groovyGenericPlaceholderElement.getBounds();
                declaredElement = groovyGenericPlaceholderElement.getRequiredDeclaringElement();
                resolved = groovyGenericPlaceholderElement.getResolvedInternal();
                arrayDimensions = groovyGenericPlaceholderElement.getArrayDimensions();
                isRawType = groovyGenericPlaceholderElement.isRawType();
            } else if (resolvedBound instanceof GroovyClassElement resolvedClassElement) {
                resolved = resolvedClassElement;
                arrayDimensions = resolved.getArrayDimensions();
                isRawType = resolved.isRawType();
            } else {
                // Most likely primitive array
                return resolvedBound;
            }
        }
        GroovyNativeElement groovyPlaceholderNativeElement = new GroovyNativeElement.Placeholder(placeholderClassNode, owner, variableName);
        if (bounds == null) {
            List<ClassNode> classNodeBounds = new ArrayList<>();
            addBounds(genericsType, classNodeBounds);
            if (genericsType != redirectType) {
                addBounds(redirectType, classNodeBounds);
            }

            PlaceholderEntry placeholderEntry = new PlaceholderEntry(genericsOwner, variableName);
            boolean alreadyVisitedPlaceholder = visitedTypes.contains(placeholderEntry);
            if (!alreadyVisitedPlaceholder) {
                visitedTypes.add(placeholderEntry);
            }
            boolean finalIsRawType = isRawType;
            bounds = classNodeBounds
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

                    return (GroovyClassElement) newClassElement(groovyPlaceholderNativeElement, classNode, parentTypeArguments, visitedTypes, true, finalIsRawType, stripTypeArguments);
                })
                .toList();
            if (bounds.isEmpty()) {
                bounds = Collections.singletonList((GroovyClassElement) getObjectClassElement());
            }
        }
        return new GroovyGenericPlaceholderElement(visitorContext, declaredElement, groovyPlaceholderNativeElement, resolved, bounds, arrayDimensions, isRawType, variableName);
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

    @NonNull
    private ClassElement getObjectClassElement() {
        return visitorContext.getClassElement(Object.class)
            .orElseThrow(() -> new IllegalStateException("java.lang.Object element not found"));
    }

    @NonNull
    private ClassElement resolveWildcard(GroovyNativeElement declaredElement,
                                         AnnotatedNode genericsOwner,
                                         GenericsType genericsType,
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
            .map(classNode -> newClassElement(declaredElement, classNode, parentTypeArguments, visitedTypes, true, false))
            .toList();
        List<ClassElement> lowerBoundsAsElements = lowerBounds
            .map(classNode -> newClassElement(declaredElement, classNode, parentTypeArguments, visitedTypes, true, false))
            .toList();
        if (upperBoundsAsElements.isEmpty()) {
            upperBoundsAsElements = Collections.singletonList(getObjectClassElement());
        }
        ClassElement upperType = WildcardElement.findUpperType(upperBoundsAsElements, lowerBoundsAsElements);
        if (upperType.getType().getName().equals("java.lang.Object")) {
            // Not bounded wildcard: <?>
            if (redirectType != null && redirectType != genericsType) {
                ClassElement definedTypeBound = newClassElement(declaredElement, genericsOwner, redirectType, redirectType, parentTypeArguments, visitedTypes, false);
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
        GroovyNativeElement wildcardNativeElement = new GroovyNativeElement.ClassWithOwner(genericsType.getType(), declaredElement);
        return new GroovyWildcardElement(
            wildcardNativeElement, upperBoundsAsElements.stream().map(GroovyClassElement.class::cast).toList(), lowerBoundsAsElements.stream().map(GroovyClassElement.class::cast).toList(), elementAnnotationMetadataFactory, (GroovyClassElement) upperType
        );
    }

    @NonNull
    protected final Map<String, ClassElement> resolveMethodTypeArguments(GroovyNativeElement declaredElement,
                                                                         MethodNode methodNode,
                                                                         @Nullable Map<String, ClassElement> parentTypeArguments) {
        if (parentTypeArguments == null) {
            parentTypeArguments = Collections.emptyMap();
        }
        return resolveTypeArguments(declaredElement, methodNode, methodNode.getGenericsTypes(), methodNode.getGenericsTypes(), parentTypeArguments, new HashSet<>());
    }

    @NonNull
    protected final Map<String, ClassElement> resolveClassTypeArguments(GroovyNativeElement declaredElement,
                                                                        ClassNode classNode,
                                                                        @Nullable Map<String, ClassElement> parentTypeArguments,
                                                                        Set<Object> visitedTypes) {
        return resolveTypeArguments(declaredElement, classNode, classNode.getGenericsTypes(), classNode.redirect().getGenericsTypes(), parentTypeArguments, visitedTypes);
    }

    @NonNull
    private Map<String, ClassElement> resolveTypeArguments(GroovyNativeElement declaredElement,
                                                           AnnotatedNode genericsOwner,
                                                           GenericsType[] genericsTypes,
                                                           GenericsType[] redirectTypes,
                                                           @Nullable Map<String, ClassElement> parentTypeArguments,
                                                           Set<Object> visitedTypes) {
        if (redirectTypes == null || redirectTypes.length == 0) {
            return Collections.emptyMap();
        }
        Map<String, ClassElement> resolved = CollectionUtils.newLinkedHashMap(redirectTypes.length);
        if (genericsTypes != null && genericsTypes.length == redirectTypes.length) {
            for (int i = 0; i < genericsTypes.length; i++) {
                GenericsType genericsType = genericsTypes[i];
                GenericsType redirectType = redirectTypes[i];
                ClassElement classElement = newClassElement(declaredElement, genericsOwner, genericsType, redirectType, parentTypeArguments, visitedTypes, false);
                resolved.put(redirectType.getName(), classElement);
            }
        } else {
            boolean isRaw = genericsTypes == null;
            for (GenericsType redirectType : redirectTypes) {
                String variableName = redirectType.getName();
                resolved.put(
                    variableName,
                    newClassElement(declaredElement, genericsOwner, redirectType, redirectType, parentTypeArguments, visitedTypes, isRaw)
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
    public Optional<String> getDocumentation(boolean parse) {
        GroovyNativeElement nativeType = getNativeType();
        AnnotatedNode annotatedNode = nativeType.annotatedNode();
        Groovydoc groovydoc = annotatedNode.getGroovydoc();
        if (groovydoc == null || groovydoc.getContent() == null) {
            return Optional.empty();
        }
        if (parse) {
            return Optional.of(JAVADOC_PATTERN.matcher(annotatedNode.getGroovydoc().getContent()).replaceAll(StringUtils.EMPTY_STRING).trim());
        }
        return Optional.of(annotatedNode.getGroovydoc().getContent());
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null) {
            return false;
        }
        if (!(o instanceof AbstractGroovyElement that)) {
            return false;
        }
        // Allow to match GroovyClassElement / GroovyGenericPlaceholderElement / GroovyWildcardElement
        return nativeElement.annotatedNode().equals(that.nativeElement.annotatedNode());
    }

    @Override
    public int hashCode() {
        return nativeElement.annotatedNode().hashCode();
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

