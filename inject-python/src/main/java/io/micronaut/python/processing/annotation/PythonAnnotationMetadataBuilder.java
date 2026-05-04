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

import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.GraalPyUtil;
import io.micronaut.python.processing.visitor.*;
import org.graalvm.polyglot.Value;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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

    public PythonAnnotationMetadataBuilder(Map<String, DecoratorDef> decorators, PythonVisitorContext visitorContext) {
        this.decorators = decorators;
        this.visitorContext = visitorContext;
    }

    @Override
    public AnnotationMetadata buildDeclared(ElementDef element) {
        if (element instanceof AnnotationMetadataProvider provider) {
            return provider.getAnnotationMetadata();
        } else {
            return super.buildDeclared(element);
        }
    }

    @Override
    protected ElementDef getTypeForAnnotation(DecoratorDef annotationMirror) {
        return getAnnotationMirror(annotationMirror.annotationName()).orElseGet(() -> new ClassDef(
            annotationMirror.annotationName(),
            annotationMirror.stereotypes()
        ));
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
            List<ElementDef> hierarchy;
            if (inheritTypeAnnotations && functionDef.declaringClass() != null) {
                hierarchy = buildHierarchy(
                    functionDef.declaringClass(),
                    false,
                    declaredOnly
                );
            } else {
                hierarchy = new ArrayList<>();
            }
            hierarchy.add(functionDef);
            return hierarchy;
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
        } else if (element instanceof ReturnDef returnDef) {
            return List.of(returnDef);
        }
        return List.of();
    }

    @Override
    protected List<? extends DecoratorDef> getAnnotationsForType(ElementDef element) {
        List<DecoratorDef> decoratorList = element.decorators();
        if (decoratorList.isEmpty()) {
            DecoratorDef decoratorDef = this.decorators.get(element.name());
            if (decoratorDef != null) {
                return decoratorDef.stereotypes();
            }
        }
        return decoratorList;
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
            if (member instanceof AnnotationMemberDef memberDef && memberDef.memberType() != null) {
                return GraalPyUtil.convertValueToJava(value, memberDef.memberType(), visitorContext);
            } else {
                return GraalPyUtil.convertValueToJava(value, visitorContext);
            }
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
        ClassElement javaAnnotationType = getJavaAnnotationType(annotationName);
        Map<ElementDef, Object> defaultValues = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : decoratorDef.members().entrySet()) {
            String memberName = normalizeAnnotationMemberName(entry.getKey());
            defaultValues.put(resolveMemberDef(javaAnnotationType, memberName), entry.getValue());
        }
        return defaultValues;
    }

    @Override
    protected Map<? extends ElementDef, ?> readAnnotationRawValues(DecoratorDef annotationMirror) {
        Map<?, ?> members = annotationMirror.members();
        ClassElement javaAnnotationType = getJavaAnnotationType(annotationMirror);

        Map<ElementDef, Object> rawValues = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : members.entrySet()) {
            String memberName = normalizeAnnotationMemberName(entry.getKey());
            rawValues.put(resolveMemberDef(javaAnnotationType, memberName), entry.getValue());
        }
        return rawValues;
    }

    private static String normalizeAnnotationMemberName(Object memberName) {
        if (memberName instanceof Number number) {
            int index = number.intValue();
            return index == 0 ? AnnotationMetadata.VALUE_MEMBER : "arg" + index;
        }
        return memberName.toString();
    }

    private @Nullable ClassElement getJavaAnnotationType(DecoratorDef annotationMirror) {
        String annotationName = annotationMirror.annotationName();
        return getJavaAnnotationType(annotationName);
    }

    private @Nullable ClassElement getJavaAnnotationType(String annotationName) {
        VisitorContext javaVisitorContext = visitorContext.getJavaVisitorContext();
        return Optional.ofNullable(javaVisitorContext)
            .flatMap(vc -> vc.getClassElement(annotationName))
            .orElse(null);
    }

    @Override
    protected <K extends Annotation> Optional<AnnotationValue<K>> getAnnotationValues(ElementDef originatingElement, ElementDef member, Class<K> annotationType) {
        if (member instanceof AnnotationMemberDef memberDef) {
            return memberDef.getAnnotationMetadata().findAnnotation(annotationType);
        }
        return Optional.empty();
    }

    @Override
    protected String getElementName(ElementDef element) {
        return element.name();
    }

    @Override
    protected String getAnnotationMemberName(ElementDef member) {
        if (member == null) {
            return null;
        }
        return member.name();
    }

    @Override
    protected String getRepeatableName(DecoratorDef annotationMirror) {
        if (annotationMirror != null) {
            return annotationMirror.repeatedName();
        } else {
            return null;
        }
    }

    @Override
    protected String getRepeatableContainerNameForType(ElementDef annotationType) {
        if (visitorContext != null) {
            PythonProcessingEnvironment env = visitorContext.getProcessingEnvironment();
            DecoratorDef decoratorDef = env.environment().decorators().get(annotationType.name());
            if (decoratorDef != null) {
                return decoratorDef.repeatedName();
            }
        }
        return null;
    }

    @Override
    protected Optional<ElementDef> getAnnotationMirror(String annotationName) {
        JavaVisitorContext javaVisitorContext = visitorContext.getJavaVisitorContext();
        if (javaVisitorContext == null) {
            return Optional.empty();
        }
        Optional<AnnotationValue<?>> annotationValue = javaVisitorContext.getAnnotationMetadataBuilder().buildAnnotation(annotationName);
        if (annotationValue.isPresent()) {
            AnnotationValue<?> av = annotationValue.get();
            return Optional.of(new ClassDef(
                av.getAnnotationName(),
                av.getStereotypes().stream().map(this::toDecoratorDef).toList()
            ));
        }
        return Optional.empty();
    }

    private DecoratorDef toDecoratorDef(AnnotationValue<?> av) {
        return new DecoratorDef(av.getAnnotationName(), av.getAnnotationName(), null, (Map) av.getValues(), av.getStereotypes() == null ? List.of() : av.getStereotypes().stream().map(this::toDecoratorDef).toList());
    }

    @Override
    protected String getOriginatingClassName(ElementDef originating) {
        return originating.name();
    }

    @Override
    protected ElementDef getAnnotationMember(ElementDef annotationElement, CharSequence member) {
        String memberName = member.toString();
        ClassElement javaAnnotationType = getJavaAnnotationType(annotationElement.name());
        if (javaAnnotationType == null) {
            return null;
        } else {
            return resolveMemberDef(javaAnnotationType, memberName);
        }
    }

    private static @Nullable AnnotationMemberDef resolveMemberDef(ClassElement javaAnnotationType, String memberName) {
        MethodElement annotationMember = resolveAnnotationMember(javaAnnotationType, memberName);
        if (annotationMember == null) {
            return new AnnotationMemberDef(memberName, null, null);
        } else {
            return new AnnotationMemberDef(
                memberName,
                annotationMember.getReturnType(),
                annotationMember.getAnnotationMetadata()
            );
        }
    }

    private static @Nullable MethodElement resolveAnnotationMember(ClassElement javaAnnotationType, String memberName) {
        if (javaAnnotationType == null) {
            return null;
        }
        return javaAnnotationType
                .getEnclosedElement(ElementQuery.ALL_METHODS.onlyInstance()
                .named(memberName))
                .orElse(null);
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
