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
import io.micronaut.ast.groovy.utils.AstAnnotationUtils;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.DefaultAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.Element;
import io.micronaut.inject.ast.MemberElement;
import org.codehaus.groovy.ast.AnnotatedNode;
import org.codehaus.groovy.ast.ClassNode;
import org.codehaus.groovy.ast.GenericsType;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

import edu.umd.cs.findbugs.annotations.NonNull;
import java.lang.annotation.Annotation;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
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
    private final AnnotatedNode annotatedNode;
    private AnnotationMetadata annotationMetadata;

    /**
     * Default constructor.
     * @param sourceUnit         The source unit
     * @param compilationUnit    The compilation unit
     * @param annotatedNode      The annotated node
     * @param annotationMetadata The annotation metadata
     */
    public AbstractGroovyElement(SourceUnit sourceUnit, CompilationUnit compilationUnit, AnnotatedNode annotatedNode, AnnotationMetadata annotationMetadata) {
        this.compilationUnit = compilationUnit;
        this.annotatedNode = annotatedNode;
        this.annotationMetadata = annotationMetadata;
        this.sourceUnit = sourceUnit;
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        return annotationMetadata;
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
            this.annotationMetadata = DefaultAnnotationMetadata.mutateMember(
                    this.annotationMetadata,
                    av.getAnnotationName(),
                    av.getValues()
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
        if (redirectTypes.length != genericsTypes.length) {
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
                }
                ClassNode cn = genericsSpec.get(name);
                toNewGenericSpec(genericsSpec, newSpec, redirectType.getName(), cn);
            }
            return newSpec;
        }
    }

    private void toNewGenericSpec(Map<String, ClassNode> genericsSpec, Map<String, ClassNode> newSpec, String name, ClassNode cn) {
        if (cn != null) {

            if (cn.isGenericsPlaceHolder()) {
                String n = cn.getUnresolvedName();
                ClassNode resolved = genericsSpec.get(n);
                toNewGenericSpec(genericsSpec, newSpec, name, resolved);
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
            ClassElement classNode = resolveGenericType(sourceUnit, genericsSpec, type);
            if (classNode != null) {
                return classNode;
            } else {
                GenericsType[] genericsTypes = type.getGenericsTypes();
                GenericsType[] redirectTypes = type.redirect().getGenericsTypes();
                if (genericsTypes != null && redirectTypes != null) {
                    genericsSpec = alignNewGenericsInfo(genericsTypes, redirectTypes, genericsSpec);
                    AnnotationMetadata annotationMetadata = AstAnnotationUtils.getAnnotationMetadata(
                            sourceUnit,
                            compilationUnit,
                            type
                    );
                    return new GroovyClassElement(sourceUnit, compilationUnit, type, annotationMetadata, Collections.singletonMap(
                            type.getName(),
                            genericsSpec
                    ));
                }
            }
        }
        return rawElement;
    }

    private ClassElement resolveGenericType(@NonNull SourceUnit sourceUnit, Map<String, ClassNode> typeGenericInfo, ClassNode returnType) {
        if (returnType.isGenericsPlaceHolder()) {
            String unresolvedName = returnType.getUnresolvedName();
            ClassNode classNode = typeGenericInfo.get(unresolvedName);
            if (classNode != null) {
                if (classNode.isGenericsPlaceHolder()) {
                    return resolveGenericType(sourceUnit, typeGenericInfo, classNode);
                } else {
                    return new GroovyClassElement(sourceUnit, compilationUnit, classNode, AstAnnotationUtils.getAnnotationMetadata(
                            sourceUnit,
                            compilationUnit,
                            classNode
                    ));
                }
            }
        }
        return null;
    }
}

