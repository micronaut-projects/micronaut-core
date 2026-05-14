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
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
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
import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.HashMap;
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
    private final Map<String, String> binaryClassNameCache = new HashMap<>();

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
        String annotationName = toBinaryClassName(annotationMirror.annotationName());
        return getAnnotationMirror(annotationName).orElseGet(() -> new ClassDef(
            annotationName,
            annotationMirror.stereotypes()
        ));
    }

    @Override
    protected String getAnnotationTypeName(DecoratorDef annotationMirror) {
        return toBinaryClassName(annotationMirror.annotationName());
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
        if (element instanceof AnnotationMemberDef memberDef) {
            List<DecoratorDef> memberAnnotations = toDecoratorDefs(memberDef.getAnnotationMetadata());
            if (!memberAnnotations.isEmpty()) {
                return memberAnnotations;
            }
        }
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
        if (element instanceof AnnotationMemberDef memberDef && memberDef.getAnnotationMetadata().hasAnnotation(annotation)) {
            return true;
        }
        String annotationName = toBinaryClassName(annotation);
        List<DecoratorDef> decorators = element.decorators();
        for (DecoratorDef decorator : decorators) {
            if (toBinaryClassName(decorator.annotationName()).equals(annotationName)) {
                return true;
            }
        }
        if (AnnotationUtil.NULLABLE.equals(annotation) && hasSyntheticNullable(element)) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean hasAnnotation(ElementDef element, Class<? extends Annotation> annotation) {
        if (element instanceof AnnotationMemberDef memberDef && memberDef.getAnnotationMetadata().hasAnnotation(annotation)) {
            return true;
        }
        String annotationName = annotation.getName();
        List<DecoratorDef> decorators = element.decorators();
        for (DecoratorDef decorator : decorators) {
            if (toBinaryClassName(decorator.annotationName()).equals(annotationName)) {
                return true;
            }
        }
        if (AnnotationUtil.NULLABLE.equals(annotation.getName()) && hasSyntheticNullable(element)) {
            return true;
        }
        return false;
    }

    @Override
    protected boolean hasAnnotations(ElementDef element) {
        if (element instanceof AnnotationMemberDef memberDef && !memberDef.getAnnotationMetadata().isEmpty()) {
            return true;
        }
        return !element.decorators().isEmpty() || hasSyntheticNullable(element);
    }

    @Override
    protected void postProcess(MutableAnnotationMetadata annotationMetadata, ElementDef element) {
        if (hasSyntheticNullable(element) && !annotationMetadata.hasDeclaredStereotype(AnnotationUtil.NON_NULL)) {
            annotationMetadata.addDeclaredAnnotation(AnnotationUtil.NULLABLE, Map.of());
        }
    }

    private static boolean hasSyntheticNullable(ElementDef element) {
        TypeRef typeRef = switch (element) {
            case ArgumentDef argumentDef -> argumentDef.typeAnnotation();
            case AttributeDef attributeDef -> attributeDef.typeName();
            case ReturnDef returnDef -> returnDef.typeAnnotation();
            default -> null;
        };
        return isNullableUnion(typeRef);
    }

    private static boolean isNullableUnion(@Nullable TypeRef typeRef) {
        return typeRef != null && isNullableUnion(typeRef.name());
    }

    private static boolean isNullableUnion(@Nullable String typeName) {
        if (typeName == null || typeName.indexOf('|') == -1) {
            return false;
        }
        List<String> unionTypes = parseUnionTypes(typeName);
        return unionTypes.size() > 1 && unionTypes.stream().anyMatch("None"::equals);
    }

    private static List<String> parseUnionTypes(String typeName) {
        List<String> types = new ArrayList<>();
        int start = 0;
        int bracketCount = 0;
        for (int i = 0; i < typeName.length(); i++) {
            char c = typeName.charAt(i);
            if (c == '[') {
                bracketCount++;
            } else if (c == ']') {
                bracketCount--;
            } else if (c == '|' && bracketCount == 0) {
                String type = typeName.substring(start, i).trim();
                if (!type.isEmpty()) {
                    types.add(type);
                }
                start = i + 1;
            }
        }
        String lastType = typeName.substring(start).trim();
        if (!lastType.isEmpty()) {
            types.add(lastType);
        }
        return types;
    }

    @Override
    protected Object readAnnotationValue(
        ElementDef originatingElement,
        ElementDef member,
        String annotationName,
        String memberName,
        Object annotationValue) {
        Object resolvedValue;
        if (annotationValue instanceof Value value) {
            if (member instanceof AnnotationMemberDef memberDef && memberDef.memberType() != null) {
                return normalizeAnnotationValue(
                    originatingElement,
                    annotationName,
                    memberName,
                    memberDef,
                    GraalPyUtil.convertValueToJava(value, memberDef.memberType(), visitorContext)
                );
            } else {
                return resolveEvaluatedExpressionReferences(
                    originatingElement,
                    annotationName,
                    memberName,
                    GraalPyUtil.convertValueToJava(value, visitorContext)
                );
            }
        }
        if (member instanceof AnnotationMemberDef memberDef) {
            resolvedValue = normalizeAnnotationValue(originatingElement, annotationName, memberName, memberDef, annotationValue);
        } else {
            resolvedValue = annotationValue;
        }
        return resolveEvaluatedExpressionReferences(originatingElement, annotationName, memberName, resolvedValue);
    }

    private Object normalizeAnnotationValue(
        ElementDef originatingElement,
        String annotationName,
        String memberName,
        AnnotationMemberDef memberDef,
        Object annotationValue
    ) {
        ClassElement memberType = memberDef.memberType();
        if (annotationValue instanceof String stringValue && isEnumMember(memberType)) {
            int lastDot = stringValue.lastIndexOf('.');
            if (lastDot > -1) {
                annotationValue = stringValue.substring(lastDot + 1);
            }
        } else if (isClassArrayMember(memberType)) {
            annotationValue = annotationClassValues(annotationValue);
        } else if (isClassMember(memberType)) {
            annotationValue = annotationClassValue(annotationValue);
        }
        return resolveEvaluatedExpressionReferences(originatingElement, annotationName, memberName, annotationValue);
    }

    private boolean isClassMember(@Nullable ClassElement memberType) {
        return memberType != null && !memberType.isArray() && Class.class.getName().equals(memberType.getName());
    }

    private boolean isClassArrayMember(@Nullable ClassElement memberType) {
        return memberType != null && memberType.isArray() && isClassMember(memberType.fromArray());
    }

    private @Nullable AnnotationClassValue<?> annotationClassValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof AnnotationClassValue<?> annotationClassValue) {
            return annotationClassValue;
        }
        if (value instanceof Class<?> classValue) {
            return new AnnotationClassValue<>(classValue);
        }
        if (value instanceof ClassElement classElement) {
            return new AnnotationClassValue<>(classElement.getRawClassElement().getName());
        }
        if (value instanceof Value polyglotValue) {
            if (polyglotValue.isNull()) {
                return null;
            }
            if (polyglotValue.isHostObject()) {
                return annotationClassValue(polyglotValue.asHostObject());
            }
            if (polyglotValue.isString()) {
                return annotationClassValue(polyglotValue.asString());
            }
            return annotationClassValue(GraalPyUtil.convertValueToJava(polyglotValue, visitorContext));
        }
        String typeName = rawTypeName(value.toString());
        ClassElement classElement = GraalPyUtil.resolvePythonTypeToJava(typeName, visitorContext, Map.of());
        if (!Object.class.getName().equals(classElement.getName()) || Object.class.getName().equals(typeName)) {
            return new AnnotationClassValue<>(classElement.getName());
        }
        return new AnnotationClassValue<>(typeName);
    }

    private static String rawTypeName(String typeName) {
        int genericStart = typeName.indexOf('<');
        return genericStart > -1 ? typeName.substring(0, genericStart) : typeName;
    }

    private AnnotationClassValue<?>[] annotationClassValues(@Nullable Object value) {
        List<AnnotationClassValue<?>> values = new ArrayList<>();
        collectAnnotationClassValues(value, values);
        return values.toArray(AnnotationClassValue[]::new);
    }

    private void collectAnnotationClassValues(@Nullable Object value, List<AnnotationClassValue<?>> values) {
        if (value == null) {
            return;
        }
        if (value instanceof Value polyglotValue) {
            if (polyglotValue.isNull()) {
                return;
            }
            if (polyglotValue.hasArrayElements()) {
                int size = Math.toIntExact(polyglotValue.getArraySize());
                for (int i = 0; i < size; i++) {
                    collectAnnotationClassValues(polyglotValue.getArrayElement(i), values);
                }
                return;
            }
            addAnnotationClassValue(polyglotValue, values);
            return;
        }
        if (value.getClass().isArray()) {
            int size = Array.getLength(value);
            for (int i = 0; i < size; i++) {
                collectAnnotationClassValues(Array.get(value, i), values);
            }
            return;
        }
        if (value instanceof Iterable<?> iterable) {
            for (Object element : iterable) {
                collectAnnotationClassValues(element, values);
            }
            return;
        }
        addAnnotationClassValue(value, values);
    }

    private void addAnnotationClassValue(@Nullable Object value, List<AnnotationClassValue<?>> values) {
        AnnotationClassValue<?> classValue = annotationClassValue(value);
        if (classValue != null) {
            values.add(classValue);
        }
    }

    private Object resolveEvaluatedExpressionReferences(
        ElementDef originatingElement,
        String annotationName,
        String memberName,
        Object annotationValue
    ) {
        if (memberName != null && isEvaluatedExpression(annotationValue)) {
            return buildEvaluatedExpressionReference(originatingElement, annotationName, memberName, annotationValue);
        }
        if (annotationValue instanceof AnnotationValue<?> nestedAnnotation) {
            return resolveNestedEvaluatedExpressionReferences(originatingElement, nestedAnnotation);
        }
        if (annotationValue instanceof AnnotationValue<?>[] nestedAnnotations) {
            AnnotationValue<?>[] resolvedAnnotations = new AnnotationValue<?>[nestedAnnotations.length];
            for (int i = 0; i < nestedAnnotations.length; i++) {
                resolvedAnnotations[i] = resolveNestedEvaluatedExpressionReferences(originatingElement, nestedAnnotations[i]);
            }
            return resolvedAnnotations;
        }
        if (annotationValue instanceof Object[] values) {
            Object[] resolvedValues = new Object[values.length];
            boolean changed = false;
            for (int i = 0; i < values.length; i++) {
                Object value = values[i];
                Object resolvedValue = value instanceof AnnotationValue<?> nestedAnnotation
                    ? resolveNestedEvaluatedExpressionReferences(originatingElement, nestedAnnotation)
                    : value;
                resolvedValues[i] = resolvedValue;
                changed |= resolvedValue != value;
            }
            if (changed) {
                return resolvedValues;
            }
        }
        return annotationValue;
    }

    private AnnotationValue<?> resolveNestedEvaluatedExpressionReferences(
        ElementDef originatingElement,
        AnnotationValue<?> annotationValue
    ) {
        Map<CharSequence, Object> resolvedValues = new LinkedHashMap<>();
        boolean changed = false;
        for (Map.Entry<CharSequence, Object> entry : annotationValue.getValues().entrySet()) {
            String memberName = entry.getKey().toString();
            Object value = entry.getValue();
            Object resolvedValue = resolveEvaluatedExpressionReferences(
                originatingElement,
                annotationValue.getAnnotationName(),
                memberName,
                value
            );
            resolvedValues.put(memberName, resolvedValue);
            changed |= resolvedValue != value;
        }
        if (!changed) {
            return annotationValue;
        }
        return new AnnotationValue<>(annotationValue.getAnnotationName(), resolvedValues);
    }

    private static boolean isEnumMember(@Nullable ClassElement memberType) {
        return memberType != null && (memberType.isEnum() || memberType.isAssignable(Enum.class));
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
        DecoratorDef decoratorDef = findDecoratorDef(annotationName);
        if (decoratorDef == null) {
            return Map.of();
        }
        ClassElement javaAnnotationType = getJavaAnnotationType(annotationName);
        Map<ElementDef, Object> defaultValues = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : decoratorDef.members().entrySet()) {
            String memberName = normalizeAnnotationMemberName(entry.getKey());
            defaultValues.put(resolveMemberDef(annotationName, javaAnnotationType, memberName), entry.getValue());
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
            String annotationName = toBinaryClassName(annotationMirror.annotationName());
            putRawValue(annotationName, javaAnnotationType, rawValues, memberName, entry.getValue());
            for (String aliasMemberName : resolveSameAnnotationAliasMembers(annotationName, memberName)) {
                putRawValue(annotationName, javaAnnotationType, rawValues, aliasMemberName, entry.getValue());
            }
        }
        return rawValues;
    }

    private void putRawValue(
        String annotationName,
        @Nullable ClassElement javaAnnotationType,
        Map<ElementDef, Object> rawValues,
        String memberName,
        Object value
    ) {
        if (rawValues.keySet().stream().noneMatch(member -> memberName.equals(getAnnotationMemberName(member)))) {
            rawValues.put(resolveMemberDef(annotationName, javaAnnotationType, memberName), value);
        }
    }

    private List<String> resolveSameAnnotationAliasMembers(String annotationName, String memberName) {
        DecoratorDef decoratorDef = findDecoratorDef(annotationName);
        if (decoratorDef == null) {
            return List.of();
        }
        List<DecoratorDef> memberDecorators = decoratorDef.memberDecorators().getOrDefault(memberName, List.of());
        if (memberDecorators.isEmpty()) {
            return List.of();
        }
        List<String> aliases = new ArrayList<>();
        for (DecoratorDef memberDecorator : memberDecorators) {
            if (!"io.micronaut.context.annotation.AliasFor".equals(memberDecorator.annotationName())) {
                continue;
            }
            if (hasAnnotationAliasTarget(memberDecorator)) {
                continue;
            }
            Object aliasMember = memberDecorator.members().get("member");
            if (aliasMember == null) {
                aliasMember = memberDecorator.members().get(AnnotationMetadata.VALUE_MEMBER);
            }
            String aliasMemberName = annotationMemberStringValue(aliasMember);
            if (aliasMemberName != null && !aliasMemberName.isBlank() && !aliasMemberName.equals(memberName)) {
                aliases.add(aliasMemberName);
            }
        }
        return aliases;
    }

    private static boolean hasAnnotationAliasTarget(DecoratorDef aliasFor) {
        Object annotation = aliasFor.members().get("annotation");
        return annotation != null && annotationMemberStringValue(annotation) != null;
    }

    private static @Nullable String annotationMemberStringValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        if (value instanceof Value polyglotValue) {
            if (polyglotValue.isNull()) {
                return null;
            }
            return polyglotValue.isString() ? polyglotValue.asString() : polyglotValue.toString();
        }
        return value.toString();
    }

    private static String normalizeAnnotationMemberName(Object memberName) {
        if (memberName instanceof Number number) {
            int index = number.intValue();
            return index == 0 ? AnnotationMetadata.VALUE_MEMBER : "arg" + index;
        }
        return memberName.toString();
    }

    private @Nullable ClassElement getJavaAnnotationType(DecoratorDef annotationMirror) {
        String annotationName = toBinaryClassName(annotationMirror.annotationName());
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
            return toBinaryClassName(annotationMirror.repeatedName());
        } else {
            return null;
        }
    }

    @Override
    protected String getRepeatableContainerNameForType(ElementDef annotationType) {
        if (visitorContext != null) {
            PythonProcessingEnvironment env = visitorContext.getProcessingEnvironment();
            DecoratorDef decoratorDef = findDecoratorDef(env.environment().decorators(), annotationType.name());
            if (decoratorDef != null) {
                return toBinaryClassName(decoratorDef.repeatedName());
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
        String annotationName = toBinaryClassName(av.getAnnotationName());
        return new DecoratorDef(annotationName, annotationName, null, (Map) av.getValues(), av.getStereotypes() == null ? List.of() : av.getStereotypes().stream().map(this::toDecoratorDef).toList());
    }

    private List<DecoratorDef> toDecoratorDefs(AnnotationMetadata annotationMetadata) {
        if (annotationMetadata.isEmpty()) {
            return List.of();
        }
        List<DecoratorDef> decoratorDefs = new ArrayList<>();
        for (String annotationName : annotationMetadata.getDeclaredAnnotationNames()) {
            AnnotationValue<?> annotationValue = annotationMetadata.getDeclaredAnnotation(annotationName);
            if (annotationValue != null) {
                decoratorDefs.add(toDecoratorDef(annotationValue));
            }
        }
        return decoratorDefs;
    }

    @Override
    protected String getOriginatingClassName(ElementDef originating) {
        if (originating instanceof ClassDef classDef) {
            return classDef.qualifiedName();
        }
        if (originating instanceof FunctionDef functionDef && functionDef.declaringClass() != null) {
            return functionDef.declaringClass().qualifiedName();
        }
        if (originating instanceof ArgumentDef argumentDef
            && argumentDef.declaringFunction() != null
            && argumentDef.declaringFunction().declaringClass() != null) {
            return argumentDef.declaringFunction().declaringClass().qualifiedName();
        }
        if (originating instanceof AttributeDef attributeDef && attributeDef.declaringClass() != null) {
            return attributeDef.declaringClass().qualifiedName();
        }
        if (originating instanceof PropertyDef propertyDef && propertyDef.declaringClass() != null) {
            return propertyDef.declaringClass().qualifiedName();
        }
        if (originating instanceof ScriptDef scriptDef) {
            return scriptDef.qualifiedName();
        }
        return originating.name();
    }

    @Override
    protected ElementDef getAnnotationMember(ElementDef annotationElement, CharSequence member) {
        String memberName = member.toString();
        ClassElement javaAnnotationType = getJavaAnnotationType(annotationElement.name());
        if (javaAnnotationType == null) {
            return resolvePythonAnnotationMember(annotationElement.name(), memberName);
        } else {
            return resolveJavaMemberDef(javaAnnotationType, memberName);
        }
    }

    private AnnotationMemberDef resolveMemberDef(String annotationName, @Nullable ClassElement javaAnnotationType, String memberName) {
        if (javaAnnotationType == null) {
            return resolvePythonAnnotationMember(annotationName, memberName);
        }
        return resolveJavaMemberDef(javaAnnotationType, memberName);
    }

    private AnnotationMemberDef resolvePythonAnnotationMember(String annotationName, String memberName) {
        DecoratorDef decoratorDef = findDecoratorDef(annotationName);
        List<DecoratorDef> memberDecorators = decoratorDef == null
            ? List.of()
            : decoratorDef.memberDecorators().getOrDefault(memberName, List.of());
        return new AnnotationMemberDef(memberName, null, null, memberDecorators);
    }

    private @Nullable DecoratorDef findDecoratorDef(String annotationName) {
        return findDecoratorDef(decorators, annotationName);
    }

    private @Nullable DecoratorDef findDecoratorDef(Map<String, DecoratorDef> decorators, String annotationName) {
        DecoratorDef decoratorDef = decorators.get(annotationName);
        if (decoratorDef != null) {
            return decoratorDef;
        }
        String binaryName = toBinaryClassName(annotationName);
        decoratorDef = decorators.get(binaryName);
        if (decoratorDef != null) {
            return decoratorDef;
        }
        for (DecoratorDef candidate : decorators.values()) {
            if (toBinaryClassName(candidate.annotationName()).equals(binaryName)) {
                return candidate;
            }
        }
        return null;
    }

    private @Nullable String toBinaryClassName(@Nullable String className) {
        if (className == null) {
            return null;
        }
        return binaryClassNameCache.computeIfAbsent(className, this::resolveBinaryClassName);
    }

    private String resolveBinaryClassName(String className) {
        JavaVisitorContext javaVisitorContext = visitorContext.getJavaVisitorContext();
        if (javaVisitorContext == null) {
            return className;
        }
        return javaVisitorContext.getClassElement(className)
            .map(ClassElement::getName)
            .orElse(className);
    }

    private static @Nullable AnnotationMemberDef resolveJavaMemberDef(ClassElement javaAnnotationType, String memberName) {
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
