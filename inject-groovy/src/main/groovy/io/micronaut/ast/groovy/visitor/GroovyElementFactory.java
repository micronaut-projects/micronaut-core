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

import edu.umd.cs.findbugs.annotations.NonNull;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.inject.ast.*;
import org.codehaus.groovy.ast.*;
import org.codehaus.groovy.control.CompilationUnit;
import org.codehaus.groovy.control.SourceUnit;

/**
 * Implementation of {@link ElementFactory} for Groovy.
 *
 * @author graemerocher
 * @since 2.3.0
 */
public class GroovyElementFactory implements ElementFactory<AnnotatedNode, ClassNode, MethodNode, FieldNode> {
    private final SourceUnit sourceUnit;
    private final CompilationUnit compilationUnit;

    public GroovyElementFactory(GroovyVisitorContext groovyVisitorContext) {
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
            return new GroovyEnumElement(sourceUnit, compilationUnit, classNode, annotationMetadata);
        } else {
            return new GroovyClassElement(sourceUnit, compilationUnit, classNode, annotationMetadata);
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
                sourceUnit,
                compilationUnit,
                method,
                annotationMetadata
        );
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
                sourceUnit,
                compilationUnit,
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
                sourceUnit,
                compilationUnit,
                field,
                field,
                annotationMetadata
        );
    }

    @NonNull
    @Override
    public FieldElement newFieldElement(@NonNull FieldNode field, @NonNull AnnotationMetadata annotationMetadata) {
        return new GroovyFieldElement(
                sourceUnit,
                compilationUnit,
                field,
                field,
                annotationMetadata
        );
    }

}
