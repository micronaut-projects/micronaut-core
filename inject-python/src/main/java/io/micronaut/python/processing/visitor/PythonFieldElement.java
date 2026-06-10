/*
 * Copyright 2017-2025 original authors
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
package io.micronaut.python.processing.visitor;

import io.micronaut.core.annotation.Experimental;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.micronaut.annotation.processing.visitor.ElementProvider;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.FieldElement;
import io.micronaut.inject.ast.GenericPlaceholderElement;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadata;
import io.micronaut.inject.ast.annotation.ElementAnnotationMetadataFactory;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Nullable;

/**
 * A field element returning data from a Python {@link AttributeDef}.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
@Experimental
public final class PythonFieldElement extends AbstractPythonElement implements FieldElement, ElementProvider {
    private final PythonProcessingEnvironment environment;
    private final ClassElement declaringType;
    private final ClassElement owningType;
    private final ClassElement type;

    public PythonFieldElement(AttributeDef attributeDef,
                              PythonProcessingEnvironment environment,
                              ClassElement declaringType,
                              ClassElement owningType,
                              ElementAnnotationMetadataFactory metadataFactory) {
        super(
            Objects.requireNonNull(attributeDef, "AttributeDef cannot be null").name(),
            attributeDef,
            Objects.requireNonNull(metadataFactory, "ElementAnnotationMetadataFactory cannot be null")
        );
        this.environment = Objects.requireNonNull(environment, "PythonProcessingEnvironment cannot be null");
        this.declaringType = Objects.requireNonNull(declaringType, "Declaring type cannot be null");
        this.owningType = Objects.requireNonNull(owningType, "Owning type cannot be null");

        this.type = resolveType(attributeDef);
    }

    @Override
    public AttributeDef getNativeType() {
        return (AttributeDef) super.getNativeType();
    }

    @Override
    public ClassElement getType() {
        return type;
    }

    @Override
    public ClassElement getGenericType() {
        return getType(); // Python doesn't have generics in the same way
    }

    @Override
    public Object getConstantValue() {
        AttributeDef attr = getNativeType();
        if (attr.value() != null) {
            // Try to convert Python literals to Java types
            return convertPythonValueToJava(attr.value(), environment.visitorContext());
        }
        return null;
    }

    @Override
    public boolean isStatic() {
        return getNativeType().isStatic();
    }

    @Override
    public boolean isFinal() {
        // Check if annotated with typing.Final
        return hasStereotype("typing.Final") || hasStereotype("Final");
    }

    @Override
    public ClassElement getDeclaringType() {
        return declaringType;
    }

    @Override
    public ClassElement getOwningType() {
        return owningType;
    }

    @Override
    public @Nullable javax.lang.model.element.Element element() {
        return environment.originatingElement();
    }

    private ClassElement resolveType(AttributeDef attributeDef) {
        if (attributeDef.typeName() != null) {
            // Use the parsed type name (e.g., "io.micronaut.runtime.server.EmbeddedServer")
            return withDeclaredTypeAnnotationMetadata(
                attributeDef,
                GraalPyUtil.resolvePythonTypeToJava(attributeDef.typeName(), environment.visitorContext(), getBoundGenericTypes(attributeDef))
            );
        }
        if (attributeDef.annotation() != null) {
            // Fallback to resolving the full annotation string
            String annotation = attributeDef.annotation();
            return withDeclaredTypeAnnotationMetadata(
                attributeDef,
                GraalPyUtil.resolvePythonTypeToJava(annotation, environment.visitorContext(), Map.of())
            );
        }
        // Infer from value if no annotation
        if (attributeDef.value() != null) {
            return inferTypeFromValue(attributeDef.value(), environment.visitorContext());
        }
        return environment.visitorContext().getClassElement(Object.class).orElse(null);
    }

    private ClassElement withDeclaredTypeAnnotationMetadata(AttributeDef attributeDef, ClassElement baseType) {
        AnnotationMetadata annotationMetadata = environment.annotationMetadataBuilder().buildDeclared(attributeDef);
        MutableAnnotationMetadata typeAnnotationMetadata = null;
        if (annotationMetadata.hasStereotype(AnnotationUtil.NULLABLE)) {
            typeAnnotationMetadata = new MutableAnnotationMetadata();
            typeAnnotationMetadata.addDeclaredAnnotation(AnnotationUtil.NULLABLE, Map.of());
        }
        if (annotationMetadata.hasStereotype(AnnotationUtil.NON_NULL)) {
            if (typeAnnotationMetadata == null) {
                typeAnnotationMetadata = new MutableAnnotationMetadata();
            }
            typeAnnotationMetadata.addDeclaredAnnotation(AnnotationUtil.NON_NULL, Map.of());
        }
        if (typeAnnotationMetadata == null) {
            return baseType;
        }
        ElementAnnotationMetadata metadata = getElementAnnotationMetadataFactory().buildMutable(typeAnnotationMetadata);
        return new TypeAnnotatedClassElement(baseType, metadata);
    }

    private Map<String, ClassElement> getBoundGenericTypes(AttributeDef attributeDef) {
        ClassDef declaringClass = attributeDef.declaringClass();
        boolean declaredOnOwningType = declaringClass != null
            ? declaringClass.qualifiedName().equals(getOwningType().getName())
            : getDeclaringType().getName().equals(getOwningType().getName());
        if (declaredOnOwningType
            && getOwningType() instanceof PythonClassElement pythonClassElement
            && !pythonClassElement.hasExplicitTypeArguments()) {
            return declaredGenericBindings(true);
        }
        Map<String, Map<String, ClassElement>> allGenerics = getOwningType().getAllTypeArguments();
        Map<String, ClassElement> declaringGenerics = declaringClass != null
            ? allGenerics.getOrDefault(declaringClass.qualifiedName(), Map.of())
            : Map.of();
        if (declaringGenerics.isEmpty()) {
            declaringGenerics = declaredGenericBindings(false);
        }
        return declaringGenerics;
    }

    private Map<String, ClassElement> declaredGenericBindings(boolean preservePlaceholders) {
        List<? extends GenericPlaceholderElement> placeholders = getDeclaringType().getDeclaredGenericPlaceholders();
        if (placeholders.isEmpty()) {
            return Map.of();
        }
        Map<String, ClassElement> bindings = new LinkedHashMap<>(placeholders.size());
        for (GenericPlaceholderElement placeholder : placeholders) {
            bindings.put(
                placeholder.getVariableName(),
                preservePlaceholders ? placeholder : firstBound(placeholder)
            );
        }
        return bindings;
    }

    private static ClassElement firstBound(GenericPlaceholderElement placeholder) {
        List<? extends ClassElement> bounds = placeholder.getBounds();
        if (bounds.isEmpty()) {
            return ClassElement.of(Object.class);
        }
        return bounds.getFirst();
    }

    private ClassElement inferTypeFromValue(Value value, PythonVisitorContext visitorContext) {
        if (value == null) {
            return environment.visitorContext().getClassElement(Object.class).orElse(null);
        }
        Object javaValue = GraalPyUtil.convertValueToJava(value, visitorContext);
        if (javaValue instanceof Integer) {
            return environment.visitorContext().getClassElement(int.class).orElse(null);
        } else if (javaValue instanceof Double || javaValue instanceof Float) {
            return environment.visitorContext().getClassElement(double.class).orElse(null);
        } else if (javaValue instanceof String) {
            return environment.visitorContext().getClassElement(String.class).orElse(null);
        } else if (javaValue instanceof Boolean) {
            return environment.visitorContext().getClassElement(boolean.class).orElse(null);
        }
        return environment.visitorContext().getClassElement(Object.class).orElse(null);
    }

    private Object convertPythonValueToJava(Value pythonValue, VisitorContext visitorContext) {
        if (pythonValue == null) {
            return null;
        }
        return GraalPyUtil.convertValueToJava(pythonValue, visitorContext);
    }

    @Override
    public java.util.Optional<String> getDocumentation(boolean parseContent) {
        String doc = getNativeType().documentation();
        if (doc == null) {
            return java.util.Optional.empty();
        }
        if (parseContent) {
            // Parse Python docstring to extract main description
            return java.util.Optional.of(GraalPyUtil.parsePythonDocstring(doc));
        }
        return java.util.Optional.of(doc);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        PythonFieldElement that = (PythonFieldElement) o;

        return that.getNativeType().name().equals(getNativeType().name()) &&
            owningType.equals(that.owningType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(getNativeType().name(), owningType);
    }

    @Override
    protected AbstractPythonElement copyThis() {
        return new PythonFieldElement(
            getNativeType(),
            environment,
            declaringType,
            owningType,
            getElementAnnotationMetadataFactory()
        );
    }

    @Override
    public FieldElement withAnnotationMetadata(AnnotationMetadata annotationMetadata) {
        return (FieldElement) super.withAnnotationMetadata(annotationMetadata);
    }
}
