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

import groovy.transform.CompileStatic;
import groovy.transform.PackageScope;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.ElementAnnotationMetadata;
import io.micronaut.inject.ast.ElementAnnotationMetadataFactory;
import io.micronaut.inject.ast.ElementModifier;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.FieldNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.ast.MethodNode;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

import java.lang.annotation.Annotation;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.regex.Pattern;

/**
 * Abstract Groovy element.
 *
 * @author Graeme Rocher
 * @since 1.1
 */

public abstract class AbstractGroovyElement implements AnnotationMetadataDelegate, Element {

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

    private ElementAnnotationMetadata getElementAnnotationMetadata() {
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
     * @return the copy
     */
    protected abstract AbstractGroovyElement copyThis();

    /**
     * @param element the values to be copied to
     */
    protected void copyValues(AbstractGroovyElement element) {
        element.presetAnnotationMetadata = presetAnnotationMetadata;
    }

    protected final AbstractGroovyElement makeCopy() {
        AbstractGroovyElement element = copyThis();
        copyValues(element);
        return element;
    }

    @Override
    public io.micronaut.inject.ast.Element withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        AbstractGroovyElement element = makeCopy();
        element.presetAnnotationMetadata = annotationMetadata;
        return element;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return getElementAnnotationMetadata().get();
    }

    @Override
    public AnnotatedNode getNativeType() {
        return annotatedNode;
    }

    @Override
    public boolean isPackagePrivate() {
        return hasDeclaredAnnotation(PackageScope.class);
    }

    @CompileStatic
    @Override
    public <T extends Annotation> Element annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
        getElementAnnotationMetadata().annotate(annotationType, consumer);
        return this;
    }

    @Override
    public <T extends Annotation> Element annotate(AnnotationValue<T> annotationValue) {
        getElementAnnotationMetadata().annotate(annotationValue);
        return this;
    }

    @Override
    public Element removeAnnotation(@NonNull String annotationType) {
        getElementAnnotationMetadata().removeAnnotation(annotationType);
        return this;
    }

    @Override
    public <T extends Annotation> Element removeAnnotationIf(@NonNull Predicate<AnnotationValue<T>> predicate) {
        getElementAnnotationMetadata().removeAnnotationIf(predicate);
        return this;
    }

    @Override
    public Element removeStereotype(@NonNull String annotationType) {
        getElementAnnotationMetadata().removeStereotype(annotationType);
        return this;
    }

    /**
     * Align the given generic types.
     *
     * @param genericsTypes The generic types
     * @param redirectTypes The redirect types
     * @param genericsSpec  The current generics spec
     * @return The new generic spec
     */
    protected Map<String, ClassNode> alignNewGenericsInfo(
        @NonNull GenericsType[] genericsTypes,
        @NonNull GenericsType[] redirectTypes,
        @NonNull Map<String, ClassNode> genericsSpec) {
        if (redirectTypes == null || redirectTypes.length != genericsTypes.length) {
            return Collections.emptyMap();
        } else {

            Map<String, ClassNode> newSpec = new HashMap<>(genericsSpec.size());
            for (int i = 0; i < genericsTypes.length; i++) {
                GenericsType genericsType = genericsTypes[i];
                GenericsType redirectType = redirectTypes[i];
                String name = genericsType.getName();
                if (genericsType.isWildcard()) {
                    ClassNode[] upperBounds = genericsType.getUpperBounds();
                    if (ArrayUtils.isNotEmpty(upperBounds)) {
                        name = upperBounds[0].getUnresolvedName();
                    } else {
                        ClassNode lowerBound = genericsType.getLowerBound();
                        if (lowerBound != null) {
                            name = lowerBound.getUnresolvedName();
                        }
                    }
                    ClassNode cn = resolveGenericPlaceholder(genericsSpec, name);
                    toNewGenericSpec(genericsSpec, newSpec, redirectType.getName(), cn);
                } else {
                    ClassNode classNode = genericsType.getType();
                    GenericsType[] typeParameters = classNode.getGenericsTypes();

                    if (ArrayUtils.isNotEmpty(typeParameters)) {
                        GenericsType[] redirectParameters = classNode.redirect().getGenericsTypes();
                        if (redirectParameters != null && typeParameters.length == redirectParameters.length) {
                            List<ClassNode> resolvedTypes = new ArrayList<>(typeParameters.length);
                            for (int j = 0; j < redirectParameters.length; j++) {
                                ClassNode type = typeParameters[j].getType();
                                if (type.isGenericsPlaceHolder()) {
                                    String unresolvedName = type.getUnresolvedName();
                                    ClassNode resolvedType = resolveGenericPlaceholder(genericsSpec, unresolvedName);
                                    if (resolvedType != null) {
                                        resolvedTypes.add(resolvedType);
                                    } else {
                                        resolvedTypes.add(type);
                                    }
                                } else {
                                    resolvedTypes.add(type);
                                }
                            }

                            ClassNode plainNodeReference = classNode.getPlainNodeReference();
                            plainNodeReference.setUsingGenerics(true);
                            plainNodeReference.setGenericsTypes(resolvedTypes.stream().map(GenericsType::new).toArray(GenericsType[]::new));
                            newSpec.put(redirectType.getName(), plainNodeReference);
                        } else {
                            ClassNode cn = resolveGenericPlaceholder(genericsSpec, name);
                            if (cn != null) {
                                newSpec.put(redirectType.getName(), cn);
                            } else {
                                newSpec.put(redirectType.getName(), classNode);
                            }
                        }
                    } else {
                        ClassNode cn = resolveGenericPlaceholder(genericsSpec, name);
                        toNewGenericSpec(genericsSpec, newSpec, redirectType.getName(), cn);
                    }
                }
            }
            return newSpec;
        }
    }

    private @Nullable ClassNode resolveGenericPlaceholder(@NonNull Map<String, ClassNode> genericsSpec, String name) {
        ClassNode classNode = genericsSpec.get(name);
        while (classNode != null && classNode.isGenericsPlaceHolder()) {
            ClassNode cn = genericsSpec.get(classNode.getUnresolvedName());
            if (cn == classNode) {
                break;
            }
            if (cn != null) {
                classNode = cn;
            } else {
                break;
            }
        }
        return classNode;
    }

    private void toNewGenericSpec(Map<String, ClassNode> genericsSpec, Map<String, ClassNode> newSpec, String name, ClassNode cn) {
        if (cn != null) {

            if (cn.isGenericsPlaceHolder()) {
                String n = cn.getUnresolvedName();
                ClassNode resolved = resolveGenericPlaceholder(genericsSpec, n);
                if (resolved == cn) {
                    newSpec.put(name, cn);
                } else {
                    toNewGenericSpec(genericsSpec, newSpec, name, resolved);
                }
            } else {
                newSpec.put(name, cn);
            }
        }
    }

    /**
     * Get a generic element for the given element and data.
     *
     * @param sourceUnit   The source unit
     * @param type         The type
     * @param rawElement   A raw element to fall back to
     * @param genericsSpec The generics spec
     * @return The element, never null.
     */
    @NonNull
    protected ClassElement getGenericElement(
        @NonNull SourceUnit sourceUnit,
        @NonNull ClassNode type,
        @NonNull ClassElement rawElement,
        @NonNull Map<String, ClassNode> genericsSpec) {
        if (CollectionUtils.isNotEmpty(genericsSpec)) {
            ClassElement classNode = resolveGenericType(genericsSpec, type);
            if (classNode != null) {
                return classNode;
            } else {
                GenericsType[] genericsTypes = type.getGenericsTypes();
                GenericsType[] redirectTypes = type.redirect().getGenericsTypes();
                if (genericsTypes != null && redirectTypes != null) {
                    genericsSpec = alignNewGenericsInfo(genericsTypes, redirectTypes, genericsSpec);
                    return new GroovyClassElement(visitorContext, type, elementAnnotationMetadataFactory, Collections.singletonMap(
                        type.getName(),
                        genericsSpec
                    ), 0);
                }
            }
        }
        return rawElement;
    }

    private ClassElement resolveGenericType(Map<String, ClassNode> typeGenericInfo, ClassNode returnType) {
        if (returnType.isGenericsPlaceHolder()) {
            String unresolvedName = returnType.getUnresolvedName();
            ClassNode classNode = resolveGenericPlaceholder(typeGenericInfo, unresolvedName);
            if (classNode != null) {
                if (classNode.isGenericsPlaceHolder() && classNode != returnType) {
                    return resolveGenericType(typeGenericInfo, classNode);
                } else {
                    return this.visitorContext.getElementFactory().newClassElement(
                        classNode, resolveElementAnnotationMetadataFactory(classNode)
                    );
                }
            }
        } else if (returnType.isArray()) {
            ClassNode componentType = returnType.getComponentType();
            if (componentType.isGenericsPlaceHolder()) {
                String unresolvedName = componentType.getUnresolvedName();
                ClassNode classNode = resolveGenericPlaceholder(typeGenericInfo, unresolvedName);
                if (classNode != null) {
                    if (classNode.isGenericsPlaceHolder() && classNode != returnType) {
                        return resolveGenericType(typeGenericInfo, classNode);
                    } else {
                        ClassNode cn = classNode.makeArray();
                        return this.visitorContext.getElementFactory().newClassElement(
                            cn, resolveElementAnnotationMetadataFactory(cn)
                        );
                    }
                }
            }
        }
        return null;
    }

    @Override
    public Optional<String> getDocumentation() {
        if (annotatedNode.getGroovydoc() == null || annotatedNode.getGroovydoc().getContent() == null) {
            return Optional.empty();
        }
        return Optional.of(JAVADOC_PATTERN.matcher(annotatedNode.getGroovydoc().getContent()).replaceAll(StringUtils.EMPTY_STRING).trim());
    }

    protected final @NonNull ElementAnnotationMetadataFactory resolveElementAnnotationMetadataFactory(Object nativeType) {
        if (visitorContext.getConfiguration().includeTypeLevelAnnotationsInGenericArguments()) {
            return elementAnnotationMetadataFactory;
        }
        return emptyAnnotationsForNativeType(nativeType);
    }

    protected final ElementAnnotationMetadataFactory emptyAnnotationsForNativeType(Object nativeType) {
        return elementAnnotationMetadataFactory.overrideForNativeType(nativeType, element -> elementAnnotationMetadataFactory.build(element, AnnotationMetadata.EMPTY_METADATA));
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

