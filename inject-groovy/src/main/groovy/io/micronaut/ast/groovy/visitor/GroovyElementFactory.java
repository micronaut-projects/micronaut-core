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

import io.micronaut.ast.groovy.config.GroovyConfigurationMetadataBuilder;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.*;
import io.micronaut.inject.ast.beans.BeanElementBuilder;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

import java.util.Map;

/**
 * Implementation of {@link ElementFactory} for Groovy.
 *
 * @author graemerocher
 * @since 2.3.0
 */
public class GroovyElementFactory implements ElementFactory<AnnotatedNode, ClassNode, MethodNode, FieldNode> {
    private final SourceUnit sourceUnit;
    private final CompilationUnit compilationUnit;
    private final GroovyVisitorContext visitorContext;

    public GroovyElementFactory(GroovyVisitorContext groovyVisitorContext) {
        this.visitorContext =  groovyVisitorContext;
        this.sourceUnit = groovyVisitorContext.getSourceUnit();
        this.compilationUnit = groovyVisitorContext.getCompilationUnit();
    }

    @NonNull
    @Override
    public ClassElement newClassElement(@NonNull ClassNode classNode, @NonNull AnnotationMetadata annotationMetadata) {
        if (classNode.isArray()) {
            ClassNode componentType = classNode.getComponentType();
            ClassElement componentElement = newClassElement(componentType, annotationMetadata);
            return componentElement.toArray();
        } else if (ClassHelper.isPrimitiveType(classNode)) {
            return PrimitiveElement.valueOf(classNode.getName());
        } else if (classNode.isEnum()) {
            return new GroovyEnumElement(visitorContext, classNode, annotationMetadata);
        } else if (classNode.isAnnotationDefinition()) {
            return new GroovyAnnotationElement(visitorContext, classNode, annotationMetadata);
        } else {
            return new GroovyClassElement(visitorContext, classNode, annotationMetadata);
        }
    }

    @NonNull
    @Override
    public ClassElement newClassElement(@NonNull ClassNode classNode, @NonNull AnnotationMetadata annotationMetadata, @NonNull Map<String, ClassElement> resolvedGenerics) {
        if (classNode.isArray()) {
            ClassNode componentType = classNode.getComponentType();
            ClassElement componentElement = newClassElement(componentType, annotationMetadata);
            return componentElement.toArray();
        } else if (ClassHelper.isPrimitiveType(classNode)) {
            return PrimitiveElement.valueOf(classNode.getName());
        } else if (classNode.isEnum()) {
            return new GroovyEnumElement(visitorContext, classNode, annotationMetadata) {
                @NonNull
                @Override
                public Map<String, ClassElement> getTypeArguments() {
                    if (resolvedGenerics != null) {
                        return resolvedGenerics;
                    }
                    return super.getTypeArguments();
                }
            };
        } else if (classNode.isAnnotationDefinition()) {
            return new GroovyAnnotationElement(visitorContext, classNode, annotationMetadata);
        } else {
            return new GroovyClassElement(visitorContext, classNode, annotationMetadata) {
                @NonNull
                @Override
                public Map<String, ClassElement> getTypeArguments() {
                    if (resolvedGenerics != null) {
                        return resolvedGenerics;
                    }
                    return super.getTypeArguments();
                }
            };
        }
    }

    @NonNull
    @Override
    public MethodElement newMethodElement(ClassElement declaringClass, @NonNull MethodNode method, @NonNull AnnotationMetadata annotationMetadata) {
        if (!(declaringClass instanceof GroovyClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a GroovyClassElement");
        }
        return new GroovyMethodElement(
                (GroovyClassElement) declaringClass,
                visitorContext,
                method,
                annotationMetadata
        );
    }

    @NonNull
    @Override
    public ClassElement newSourceClassElement(@NonNull ClassNode classNode, @NonNull AnnotationMetadata annotationMetadata) {
        if (classNode.isArray()) {
            ClassNode componentType = classNode.getComponentType();
            ClassElement componentElement = newSourceClassElement(componentType, annotationMetadata);
            return componentElement.toArray();
        } else if (ClassHelper.isPrimitiveType(classNode)) {
            return PrimitiveElement.valueOf(classNode.getName());
        } else if (classNode.isEnum()) {
            return new GroovyEnumElement(visitorContext, classNode, annotationMetadata) {
                @NonNull
                @Override
                public BeanElementBuilder addAssociatedBean(@NonNull ClassElement type) {
                    return new GroovyBeanDefinitionBuilder(
                            this,
                            type,
                            new GroovyConfigurationMetadataBuilder(sourceUnit, compilationUnit),
                            visitorContext
                    );
                }
            };
        } else {
            return new GroovyClassElement(visitorContext, classNode, annotationMetadata) {
                @NonNull
                @Override
                public BeanElementBuilder addAssociatedBean(@NonNull ClassElement type) {
                    return new GroovyBeanDefinitionBuilder(
                            this,
                            type,
                            new GroovyConfigurationMetadataBuilder(sourceUnit, compilationUnit),
                            visitorContext
                    );
                }
            };
        }
    }

    @NonNull
    @Override
    public MethodElement newSourceMethodElement(ClassElement declaringClass, @NonNull MethodNode method, @NonNull AnnotationMetadata annotationMetadata) {
        if (!(declaringClass instanceof GroovyClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a GroovyClassElement");
        }
        return new GroovyMethodElement(
                (GroovyClassElement) declaringClass,
                visitorContext,
                method,
                annotationMetadata
        ) {
            @NonNull
            @Override
            public BeanElementBuilder addAssociatedBean(@NonNull ClassElement type) {
                return new GroovyBeanDefinitionBuilder(
                        this,
                        type,
                        new GroovyConfigurationMetadataBuilder(sourceUnit, compilationUnit),
                        visitorContext
                );
            }
        };
    }

    @NonNull
    @Override
    public ConstructorElement newConstructorElement(ClassElement declaringClass, @NonNull MethodNode constructor, @NonNull AnnotationMetadata annotationMetadata) {
        if (!(declaringClass instanceof GroovyClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a GroovyClassElement");
        }
        if (!(constructor instanceof ConstructorNode)) {
            throw new IllegalArgumentException("Constructor must be a ConstructorNode");
        }
        return new GroovyConstructorElement(
                (GroovyClassElement) declaringClass,
                visitorContext,
                (ConstructorNode) constructor,
                annotationMetadata
        );
    }

    @NonNull
    @Override
    public FieldElement newFieldElement(ClassElement declaringClass, @NonNull FieldNode field, @NonNull AnnotationMetadata annotationMetadata) {
        if (!(declaringClass instanceof GroovyClassElement)) {
            throw new IllegalArgumentException("Declaring class must be a GroovyClassElement");
        }
        return new GroovyFieldElement(
                visitorContext,
                field,
                field,
                annotationMetadata
        );
    }

    @NonNull
    @Override
    public FieldElement newFieldElement(@NonNull FieldNode field, @NonNull AnnotationMetadata annotationMetadata) {
        return new GroovyFieldElement(
                visitorContext,
                field,
                field,
                annotationMetadata
        );
    }

    /**
     * Builds a new field element for the given property.
     *
     * @param property              The property
     * @param annotationMetadata The resolved annotation metadata
     * @return The field element
     */
    public FieldElement newFieldElement(@NonNull PropertyNode property, @NonNull AnnotationMetadata annotationMetadata) {
        return new GroovyFieldElement(
                visitorContext,
                property,
                property,
                annotationMetadata
        );
    }

    /**
     * Constructs a new {@link ParameterElement} for the given field element and metadata.
     * @param field The field
     * @param annotationMetadata The metadata
     * @return The parameter element
     */
    public ParameterElement newParameterElement(@NonNull FieldElement field, @NonNull AnnotationMetadata annotationMetadata) {
        if (!(field instanceof GroovyFieldElement)) {
            throw new IllegalArgumentException("Field must be a GroovyFieldElement");
        }
        FieldNode fieldNode = (FieldNode) field.getNativeType();
        return new GroovyParameterElement(
                null,
                visitorContext,
                new Parameter(fieldNode.getType(), fieldNode.getName()),
                annotationMetadata
        ) {
            @Nullable
            @Override
            public ClassElement getGenericType() {
                return field.getGenericType();
            }
        };
    }

}
