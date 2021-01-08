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

import edu.umd.cs.findbugs.annotations.Nullable;
import groovy.transform.CompileStatic;
import groovy.transform.PackageScope;
import io.micronaut.ast.groovy.annotation.GroovyAnnotationMetadataBuilder;
import io.micronaut.ast.groovy.utils.AstAnnotationUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MemberElement;
import io.micronaut.inject.ast.PrimitiveElement;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassHelper;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Annotation;
import java.util.*;
import java.util.function.Consumer;

/**
 * Abstract Groovy element.
 *
 * @author Graeme Rocher
 * @since 1.1
 */

public abstract class AbstractGroovyElement implements AnnotationMetadataDelegate, Element {

    protected final SourceUnit sourceUnit;
    protected final CompilationUnit compilationUnit;
    protected final GroovyVisitorContext visitorContext;
    private final AnnotatedNode annotatedNode;
    private AnnotationMetadata annotationMetadata;

    /**
     * Default constructor.
     * @param visitorContext     The groovy visitor context
     * @param annotatedNode      The annotated node
     * @param annotationMetadata The annotation metadata
     */
    public AbstractGroovyElement(GroovyVisitorContext visitorContext, AnnotatedNode annotatedNode, AnnotationMetadata annotationMetadata) {
        this.visitorContext = visitorContext;
        this.compilationUnit = visitorContext.getCompilationUnit();
        this.annotatedNode = annotatedNode;
        this.annotationMetadata = annotationMetadata;
        this.sourceUnit = visitorContext.getSourceUnit();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
    }

    @Override
    public boolean isPackagePrivate() {
        return hasDeclaredAnnotation(PackageScope.class);
    }

    @CompileStatic
    @Override
    public <T extends Annotation> Element annotate(@NonNull String annotationType, @NonNull Consumer<AnnotationValueBuilder<T>> consumer) {
        ArgumentUtils.requireNonNull("annotationType", annotationType);
        AnnotationValueBuilder<T> builder = AnnotationValue.builder(annotationType);
        //noinspection ConstantConditions
        if (consumer != null) {

            consumer.accept(builder);
            AnnotationValue<T> av = builder.build();
            this.annotationMetadata = new GroovyAnnotationMetadataBuilder(sourceUnit, compilationUnit).annotate(
                    this.annotationMetadata,
                    av
            );
            String declaringTypeName = this instanceof MemberElement ? ((MemberElement) this).getOwningType().getName() : getName();
            AbstractAnnotationMetadataBuilder.addMutatedMetadata(
                    declaringTypeName,
                    annotatedNode,
                    this.annotationMetadata
            );
            AstAnnotationUtils.invalidateCache(annotatedNode);
        }
        return this;
    }

    /**
     * Align the given generic types.
     * @param genericsTypes The generic types
     * @param redirectTypes The redirect types
     * @param genericsSpec The current generics spec
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
     * @param sourceUnit The source unit
     * @param type The type
     * @param rawElement A raw element to fall back to
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
                    return new GroovyClassElement(visitorContext, type, annotationMetadata, Collections.singletonMap(
                            type.getName(),
                            genericsSpec
                    ), 0);
                }
            }
        }
        return rawElement;
    }

    /**
     *
     * @param sourceUnit The source unit
     * @param compilationUnit The compilation unit
     * @param classNode The class node
     * @param annotationMetadata The metadata
     * @return The class element
     * @deprecated Use {@link io.micronaut.inject.ast.ElementFactory} instead
     */
    @Deprecated
    public static ClassElement toClassElement(SourceUnit sourceUnit, CompilationUnit compilationUnit, ClassNode classNode, AnnotationMetadata annotationMetadata) {
        if (classNode.isArray()) {
            ClassNode componentType = classNode.getComponentType();
            ClassElement componentElement = toClassElement(sourceUnit, compilationUnit, componentType, annotationMetadata);
            return componentElement.toArray();
        } else if (ClassHelper.isPrimitiveType(classNode)) {
            return PrimitiveElement.valueOf(classNode.getName());
        } else if (classNode.isEnum()) {
            return new GroovyEnumElement(new GroovyVisitorContext(sourceUnit, compilationUnit), classNode, annotationMetadata);
        } else {
            return new GroovyClassElement(new GroovyVisitorContext(sourceUnit, compilationUnit), classNode, annotationMetadata);
        }
    }

    private ClassElement resolveGenericType(Map<String, ClassNode> typeGenericInfo, ClassNode returnType) {
        if (returnType.isGenericsPlaceHolder()) {
            String unresolvedName = returnType.getUnresolvedName();
            ClassNode classNode = resolveGenericPlaceholder(typeGenericInfo, unresolvedName);
            if (classNode != null) {
                if (classNode.isGenericsPlaceHolder() && classNode != returnType) {
                    return resolveGenericType(typeGenericInfo, classNode);
                } else {
                    AnnotationMetadata annotationMetadata = resolveAnnotationMetadata(classNode);
                    return this.visitorContext.getElementFactory().newClassElement(
                            classNode, annotationMetadata
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
                        AnnotationMetadata annotationMetadata = resolveAnnotationMetadata(cn);
                        return this.visitorContext.getElementFactory().newClassElement(
                                cn, annotationMetadata
                        );
                    }
                }
            }
        }
        return null;
    }

    /**
     * Resolves the annotation metadata for the given type.
     * @param type The type
     * @return The annotation metadata
     */
    protected @NonNull AnnotationMetadata resolveAnnotationMetadata(@NonNull ClassNode type) {
        AnnotationMetadata annotationMetadata;
        if (visitorContext.getConfiguration().includeTypeLevelAnnotationsInGenericArguments()) {
            annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(sourceUnit, compilationUnit, type);
        } else {
            annotationMetadata = AnnotationMetadata.EMPTY_METADATA;
        }
        return annotationMetadata;
    }
}

