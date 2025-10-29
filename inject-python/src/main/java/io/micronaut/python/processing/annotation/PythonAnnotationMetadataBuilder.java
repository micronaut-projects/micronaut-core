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
package io.micronaut.python.processing.annotation;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.graalvm.polyglot.Value;

import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.util.GraalPyUtil;
import io.micronaut.python.processing.visitor.AnnotationMemberDef;
import io.micronaut.python.processing.visitor.AttributeDef;
import io.micronaut.python.processing.visitor.ClassDef;
import io.micronaut.python.processing.visitor.DecoratorDef;
import io.micronaut.python.processing.visitor.ElementDef;
import io.micronaut.python.processing.visitor.FunctionDef;
import io.micronaut.python.processing.visitor.PropertyDef;
import io.micronaut.python.processing.visitor.PythonVisitorContext;

import javax.lang.model.element.Element;

/**
 * Builder for creating annotation metadata from Python decorators and elements.
 * This class extends Micronaut's annotation metadata builder to handle Python-specific
 * annotation processing, converting Python decorators to Java annotation metadata.
 *
 * @author Micronaut Team
 * @since 5.0.0
 */
public class PythonAnnotationMetadataBuilder extends AbstractAnnotationMetadataBuilder<ElementDef, DecoratorDef> {
    private final Map<String, DecoratorDef> decorators;
    private final PythonVisitorContext visitorContext;
    private final Map<String, ElementDef> annotationTypes = new HashMap<>();

    public PythonAnnotationMetadataBuilder(Map<String, DecoratorDef> decorators, PythonVisitorContext visitorContext) {
        this.decorators = decorators;
        this.visitorContext = visitorContext;
    }

    @Override
    protected ElementDef getTypeForAnnotation(DecoratorDef annotationMirror) {
        return new ClassDef(
            annotationMirror.annotationName(),
            annotationMirror.stereotypes()
        );
    }

    @Override
    protected String getAnnotationTypeName(DecoratorDef annotationMirror) {
        return annotationMirror.annotationName();
    }

    @Override
    protected List<ElementDef> buildHierarchy(ElementDef element, boolean inheritTypeAnnotations, boolean declaredOnly) {
        if (element instanceof ClassDef classDef) {
            // TODO: load base classes
            return List.of(classDef);
        } else if (element instanceof FunctionDef functionDef) {
            // TODO: include parent class of FunctionDef
            return List.of(functionDef);
        } else if (element instanceof PropertyDef propertyDef) {
            // For properties, include the property itself and its read/write methods
            List<ElementDef> hierarchy = new java.util.ArrayList<>();
            hierarchy.add(propertyDef);
            if (propertyDef.getter() != null) {
                hierarchy.add(propertyDef.getter());
            }
            if (propertyDef.setter() != null) {
                hierarchy.add(propertyDef.setter());
            }
            return hierarchy;
        } else if (element instanceof AttributeDef attributeDef) {
            return List.of(attributeDef);
        } else if (element instanceof io.micronaut.python.processing.visitor.ArgumentDef argumentDef) {
            return List.of(argumentDef);
        }
        return List.of();
    }

    @Override
    protected List<? extends DecoratorDef> getAnnotationsForType(ElementDef element) {
        return element.decorators();
    }

    @Override
    protected boolean hasAnnotation(ElementDef element, String annotation) {
        List<DecoratorDef> decorators = element.decorators();
        for (DecoratorDef decorator : decorators) {
            if (decorator.annotationName().equals(annotation)) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean hasAnnotation(ElementDef element, Class<? extends Annotation> annotation) {
        List<DecoratorDef> decorators = element.decorators();
        for (DecoratorDef decorator : decorators) {
            if (decorator.annotationName().equals(annotation.getName())) {
                return true;
            }
        }
        return false;
    }

    @Override
    protected boolean hasAnnotations(ElementDef element) {
        return !element.decorators().isEmpty();
    }

    @Override
    protected Object readAnnotationValue(
        ElementDef originatingElement,
        ElementDef member,
        String annotationName,
        String memberName,
        Object annotationValue) {
        if (annotationValue instanceof Value value) {
            return GraalPyUtil.convertValueToJava(value);
        }
        return annotationValue;
    }

    @Override
    protected void readAnnotationRawValues(
        ElementDef originatingElement,
        String annotationName,
        ElementDef member,
        String memberName,
        Object annotationValue,
        Map<CharSequence, Object> annotationValues) {
        if (!annotationValues.containsKey(memberName)) {
            var value = readAnnotationValue(originatingElement, member, annotationName, memberName, annotationValue);
            if (value != null) {
                validateAnnotationValue(originatingElement, annotationName, member, memberName, value);
                annotationValues.put(memberName, value);
            }
        }
    }

    @Override
    protected boolean isValidationRequired(ElementDef member) {
        return false;
    }

    @Override
    protected void addError(ElementDef originatingElement, String error) {
        visitorContext.fail(error, null);
    }

    @Override
    protected void addWarning(ElementDef originatingElement, String warning) {
        visitorContext.warn(warning, null);
    }

    @Override
    protected Map<? extends ElementDef, ?> readAnnotationDefaultValues(String annotationName, ElementDef annotationType) {
        DecoratorDef decoratorDef = this.decorators.get(annotationName);
        if (decoratorDef == null) {
            return Map.of();
        }
        return decoratorDef.members().entrySet().stream().collect(Collectors.toMap(
            entry -> new AnnotationMemberDef(entry.getKey()),
            Map.Entry::getValue
        ));
    }

    @Override
    protected Map<? extends ElementDef, ?> readAnnotationRawValues(DecoratorDef annotationMirror) {
        Map<String, Value> members = annotationMirror.members();
        return members.entrySet().stream().collect(Collectors.toMap(
            entry -> new AnnotationMemberDef(entry.getKey()),
            Map.Entry::getValue
        ));
    }

    @Override
    protected <K extends Annotation> Optional<AnnotationValue<K>> getAnnotationValues(ElementDef originatingElement, ElementDef member, Class<K> annotationType) {
        return Optional.empty();
    }

    @Override
    protected String getElementName(ElementDef element) {
        return element.name();
    }

    @Override
    protected String getAnnotationMemberName(ElementDef member) {
        return member.name();
    }

    @Override
    protected String getRepeatableName(DecoratorDef annotationMirror) {
        // TODO: Support repeatable
        return null;
    }

    @Override
    protected String getRepeatableContainerNameForType(ElementDef annotationType) {
        // TODO: Support repeatable
        return null;
    }

    @Override
    protected Optional<ElementDef> getAnnotationMirror(String annotationName) {
        return Optional.ofNullable(decorators.get(annotationName))
            .map(decoratorDef -> new ClassDef(
                decoratorDef.annotationName(),
                decoratorDef.stereotypes()
            ));
    }

    @Override
    protected String getOriginatingClassName(ElementDef orginatingElement) {
        return orginatingElement.name();
    }

    @Override
    protected ElementDef getAnnotationMember(ElementDef annotationElement, CharSequence member) {
        return null;
    }

    @Override
    protected VisitorContext getVisitorContext() {
        return this.visitorContext;
    }

    @Override
    protected RetentionPolicy getRetentionPolicy(ElementDef annotation) {
        // no concept of retention in Python decorators
        return RetentionPolicy.RUNTIME;
    }

}
