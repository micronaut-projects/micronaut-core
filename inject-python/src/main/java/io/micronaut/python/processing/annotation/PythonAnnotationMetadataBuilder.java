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

import io.micronaut.core.annotation.Experimental;
import io.micronaut.annotation.processing.visitor.JavaVisitorContext;
import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Aliases;
import io.micronaut.context.annotation.Property;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataProvider;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.inject.annotation.AbstractAnnotationMetadataBuilder;
import io.micronaut.inject.annotation.AnnotationMapper;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.ElementQuery;
import io.micronaut.inject.ast.MethodElement;
import io.micronaut.inject.visitor.VisitorContext;
import io.micronaut.python.processing.PythonProcessingEnvironment;
import io.micronaut.python.processing.util.AnnotationNames;
import io.micronaut.python.processing.model.AnnotationMemberDef;
import io.micronaut.python.processing.model.ArgumentDef;
import io.micronaut.python.processing.model.AttributeDef;
import io.micronaut.python.processing.model.ClassDef;
import io.micronaut.python.processing.model.DecoratorDef;
import io.micronaut.python.processing.model.ElementDef;
import io.micronaut.python.processing.model.FunctionDef;
import io.micronaut.python.processing.model.PropertyDef;
import io.micronaut.python.processing.element.PythonClassElement;
import io.micronaut.python.processing.visitor.PythonVisitorContext;
import io.micronaut.python.processing.model.ReturnDef;
import io.micronaut.python.processing.model.ScriptDef;
import io.micronaut.python.processing.model.TypeRef;
import org.jetbrains.annotations.Nullable;

import java.lang.annotation.Annotation;
import java.lang.annotation.Repeatable;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Builder for creating annotation metadata from Python decorators and elements.
 * This class extends Micronaut's annotation metadata builder to handle Python-specific
 * annotation processing, converting Python decorators to Java annotation metadata.
 *
 * @author Micronaut Team
 * @since 5.2.0
 */
@Experimental
public final class PythonAnnotationMetadataBuilder extends AbstractAnnotationMetadataBuilder<ElementDef, DecoratorDef> implements AnnotationLookups {

    private static final String ANNOTATION_NAME_MEMBER = "annotationName";
    private final Map<String, DecoratorDef> decorators;
    private final PythonVisitorContext visitorContext;
    private final Map<String, String> binaryClassNameCache = new HashMap<>();
    private final Map<String, Optional<ElementDef>> annotationMirrorCache = new HashMap<>();
    private final Map<String, AnnotationMemberDef> javaAnnotationMemberCache = new HashMap<>();
    private final PythonAnnotationValues values;
    private final PythonInterceptorBindings interceptorBindings;

    public PythonAnnotationMetadataBuilder(Map<String, DecoratorDef> decorators, PythonVisitorContext visitorContext) {
        this.decorators = decorators;
        this.visitorContext = visitorContext;
        this.values = new PythonAnnotationValues(visitorContext, this);
        this.interceptorBindings = new PythonInterceptorBindings(this, values);
    }

    @Override
    public String binaryClassName(String className) {
        return toBinaryClassName(className);
    }

    @Override
    public @Nullable DecoratorDef decoratorDef(String annotationName) {
        return findDecoratorDef(annotationName);
    }

    @Override
    public Optional<ElementDef> annotationMirror(String annotationName) {
        return getAnnotationMirror(annotationName);
    }

    @Override
    public @Nullable ClassElement javaAnnotationType(DecoratorDef decorator) {
        return getJavaAnnotationType(decorator);
    }

    @Override
    public AnnotationValue<?> annotationValue(DecoratorDef decorator) {
        return toAnnotationValue(decorator);
    }

    @SuppressWarnings({"unchecked", "rawtypes"})
    public List<AnnotationValue<?>> mapAnnotation(DecoratorDef decorator) {
        String annotationName = toBinaryClassName(decorator.annotationName());
        List<AnnotationMapper<Annotation>> mappers = (List) getAnnotationMappers(annotationName);
        if (mappers == null || mappers.isEmpty()) {
            return List.of();
        }
        AnnotationValue<Annotation> annotationValue = (AnnotationValue) toAnnotationValue(decorator);
        List<AnnotationValue<?>> mappedAnnotations = new ArrayList<>();
        for (AnnotationMapper<Annotation> mapper : mappers) {
            List<AnnotationValue<?>> mapped = mapper.map(annotationValue, visitorContext);
            if (mapped != null) {
                mappedAnnotations.addAll(mapped);
            }
        }
        return mappedAnnotations;
    }

    private AnnotationValue<?> toAnnotationValue(DecoratorDef decorator) {
        String annotationName = resolveAnnotationName(decorator);
        Map<? extends ElementDef, ?> elementValues = readAnnotationRawValues(decorator);
        Map<CharSequence, Object> annotationValues = new LinkedHashMap<>();
        for (Map.Entry<? extends ElementDef, ?> entry : elementValues.entrySet()) {
            ElementDef member = entry.getKey();
            if (member != null) {
                readAnnotationRawValues(
                    getTypeForAnnotation(decorator),
                    annotationName,
                    member,
                    getAnnotationMemberName(member),
                    entry.getValue(),
                    annotationValues
                );
            }
        }
        return new AnnotationValue<>(annotationName, annotationValues);
    }

    /**
     * Every {@code @AliasFor} of an annotation member: the repeated Java annotations of a Java
     * annotation member, or the {@code AliasFor(...)} decorators of a Python {@code Annotated} member.
     */
    @SuppressWarnings("unchecked")
    private List<AnnotationValue<AliasFor>> memberAliases(AnnotationMemberDef memberDef) {
        List<AnnotationValue<AliasFor>> aliases = memberDef.getAnnotationMetadata().getAnnotationValuesByType(AliasFor.class);
        if (aliases.isEmpty()) {
            aliases = memberDef.getAnnotationMetadata().findAnnotation(AliasFor.class).map(List::of).orElseGet(List::of);
        }
        if (aliases.isEmpty()) {
            String aliasForName = AliasFor.class.getName();
            aliases = memberDef.decorators().stream()
                .filter(decorator -> toBinaryClassName(decorator.annotationName()).equals(aliasForName))
                .map(decorator -> (AnnotationValue<AliasFor>) toAnnotationValue(decorator))
                .toList();
        }
        return aliases.stream()
            .map(alias -> normalizeAliasForAnnotationValue(alias, AliasFor.class))
            .toList();
    }

    /**
     * Visitors such as the configuration reader visitor annotate a class with an annotation it only
     * carries as a stereotype ({@code @ConfigurationReader} behind {@code @ConfigurationProperties}).
     * The declared annotation would then shadow the stereotype's members in lookups, dropping the
     * values that {@code @AliasFor} placed there; carry them over so the declared annotation is a
     * superset of the stereotype.
     */
    @Override
    public <A2 extends Annotation> AnnotationMetadata annotate(AnnotationMetadata annotationMetadata, AnnotationValue<A2> annotationValue) {
        String annotationName = annotationValue.getAnnotationName();
        if (!annotationMetadata.hasAnnotation(annotationName) && annotationMetadata.hasStereotype(annotationName)) {
            Map<CharSequence, Object> stereotypeValues = annotationMetadata.getValues(annotationName);
            if (!stereotypeValues.isEmpty()) {
                Map<CharSequence, Object> merged = new LinkedHashMap<>(stereotypeValues);
                merged.putAll(annotationValue.getValues());
                return super.annotate(annotationMetadata, annotationValue.mutate().members(merged).build());
            }
        }
        return super.annotate(annotationMetadata, annotationValue);
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
        String annotationName = resolveAnnotationName(annotationMirror);
        DecoratorDef resolvedDecorator = findDecoratorDef(annotationName);
        if (resolvedDecorator != null) {
            return getAnnotationMirror(annotationName).orElseGet(() -> new ClassDef(
                annotationName,
                resolvedDecorator.stereotypes()
            ));
        }
        return getAnnotationMirror(annotationName).orElseGet(() -> new ClassDef(
            annotationName,
            annotationMirror.stereotypes()
        ));
    }

    @Override
    protected String getAnnotationTypeName(DecoratorDef annotationMirror) {
        return resolveAnnotationName(annotationMirror);
    }

    @Override
    protected List<ElementDef> buildHierarchy(ElementDef element, boolean inheritTypeAnnotations, boolean declaredOnly) {
        if (element instanceof ClassDef classDef) {
            if (declaredOnly) {
                return List.of(classDef);
            }
            List<ElementDef> hierarchy = new ArrayList<>();
            populateClassHierarchy(classDef, hierarchy, new LinkedHashSet<>());
            return hierarchy;
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
        } else if (element instanceof io.micronaut.python.processing.model.ArgumentDef argumentDef) {
            return List.of(argumentDef);
        } else if (element instanceof ReturnDef returnDef) {
            return List.of(returnDef);
        } else if (element instanceof ScriptDef scriptDef) {
            return List.of(scriptDef);
        }
        return List.of();
    }

    private void populateClassHierarchy(ClassDef classDef, List<ElementDef> hierarchy, Set<String> visited) {
        String className = toQualifiedPythonName(classDef);
        if (!visited.add(className)) {
            return;
        }
        hierarchy.add(classDef);
        for (TypeRef base : classDef.bases()) {
            resolvePythonBaseClass(classDef, base).ifPresent(baseClass -> populateClassHierarchy(baseClass, hierarchy, visited));
        }
    }

    private Optional<ClassDef> resolvePythonBaseClass(ClassDef declaringClass, TypeRef base) {
        // Use raw parsed Python classes here. Resolving ClassElement instances while class
        // metadata is being built can recursively initialize the same metadata cache.
        Map<String, ClassDef> classes = visitorContext.getProcessingEnvironment().environment().classes();
        ClassDef baseClass = classes.get(base.name());
        if (baseClass == null && base.name().indexOf('.') < 0) {
            String packageName = declaringClass.packageName();
            if (!packageName.isEmpty()) {
                baseClass = classes.get(packageName + '.' + base.name());
            }
            if (baseClass == null) {
                baseClass = classes.get(PythonClassElement.PYTHON_DEFAULT_PACKAGE + '.' + base.name());
            }
        }
        return Optional.ofNullable(baseClass);
    }

    static String toQualifiedPythonName(ClassDef classDef) {
        String packageName = classDef.packageName();
        if (packageName == null || packageName.isEmpty()) {
            packageName = PythonClassElement.PYTHON_DEFAULT_PACKAGE;
        }
        return packageName + '.' + classDef.name();
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
        if ((element instanceof AttributeDef || element instanceof PropertyDef)
            && !annotationMetadata.hasDeclaredStereotype(AnnotationUtil.INJECT)
            && (annotationMetadata.hasDeclaredAnnotation(Property.class)
                || annotationMetadata.hasDeclaredStereotype(Property.class)
                || annotationMetadata.hasDeclaredAnnotation(io.micronaut.context.annotation.Value.class)
                || annotationMetadata.hasDeclaredStereotype(io.micronaut.context.annotation.Value.class))) {
            annotationMetadata.addDeclaredAnnotation(AnnotationUtil.INJECT, Map.of());
        }
        interceptorBindings.apply(annotationMetadata, element);
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
        return typeRef != null && typeRef.isNullableUnion();
    }

    @Override
    protected Object readAnnotationValue(
        ElementDef originatingElement,
        ElementDef member,
        String annotationName,
        String memberName,
        Object annotationValue) {
        Object resolvedValue;
        if (member instanceof AnnotationMemberDef memberDef) {
            resolvedValue = resolveEvaluatedExpressionReferences(originatingElement, annotationName, memberName, values.normalize(memberDef, annotationValue));
        } else {
            resolvedValue = annotationValue;
        }
        return resolveEvaluatedExpressionReferences(originatingElement, annotationName, memberName, resolvedValue);
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
        if (annotationValue instanceof Object[] elements) {
            Object[] resolvedValues = new Object[elements.length];
            boolean changed = false;
            for (int i = 0; i < elements.length; i++) {
                Object value = elements[i];
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
        return readAnnotationDefaultValues(annotationName, annotationType, false);
    }

    @Override
    protected Map<? extends ElementDef, ?> readAnnotationDefaultValues(String annotationName,
                                                                       ElementDef annotationType,
                                                                       boolean includeEmptyValues) {
        DecoratorDef decoratorDef = findDecoratorDef(annotationName);
        if (decoratorDef == null) {
            return Map.of();
        }
        ClassElement javaAnnotationType = getJavaAnnotationType(annotationName);
        Map<ElementDef, Object> defaultValues = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : decoratorDef.members().entrySet()) {
            String memberName = AnnotationNames.memberName(entry.getKey());
            if (!includeEmptyValues && isEmptyStringDefault(entry.getValue())) {
                // the same rule as JavaAnnotationMetadataBuilder#isValidDefaultValue, which documents why
                continue;
            }
            defaultValues.put(resolveMemberDef(annotationName, javaAnnotationType, memberName), entry.getValue());
        }
        return defaultValues;
    }

    /**
     * Whether the default is the empty string, which the written annotation metadata omits. Only the empty string is
     * treated as absent; an empty array default is always recorded. See
     * {@code JavaAnnotationMetadataBuilder#isValidDefaultValue} for the rationale.
     *
     * @param value The declared default
     * @return Whether the default should be left out unless empty values are requested
     */
    private static boolean isEmptyStringDefault(@Nullable Object value) {
        return value instanceof String string && string.isEmpty();
    }

    @Override
    protected Map<? extends ElementDef, ?> readAnnotationRawValues(DecoratorDef annotationMirror) {
        Map<?, ?> members = annotationMirror.members();
        ClassElement javaAnnotationType = getJavaAnnotationType(annotationMirror);
        String annotationName = resolveAnnotationName(annotationMirror);

        Map<ElementDef, Object> rawValues = new LinkedHashMap<>();
        for (Map.Entry<?, ?> entry : members.entrySet()) {
            String memberName = AnnotationNames.memberName(entry.getKey());
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
        // @AliasFor names its target annotation as a class (annotation) or as a name (annotationName)
        Object annotation = aliasFor.members().get("annotation");
        if (annotation == null) {
            annotation = aliasFor.members().get(ANNOTATION_NAME_MEMBER);
        }
        return annotation != null && annotationMemberStringValue(annotation) != null;
    }

    private static @Nullable String annotationMemberStringValue(@Nullable Object value) {
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private @Nullable ClassElement getJavaAnnotationType(DecoratorDef annotationMirror) {
        String annotationName = resolveAnnotationName(annotationMirror);
        return getJavaAnnotationType(annotationName);
    }

    private @Nullable ClassElement getJavaAnnotationType(String annotationName) {
        VisitorContext javaVisitorContext = visitorContext.getJavaVisitorContext();
        return Optional.ofNullable(javaVisitorContext)
            .flatMap(vc -> vc.getClassElement(annotationName))
            .orElse(null);
    }

    @Override
    @SuppressWarnings("unchecked")
    protected <K extends Annotation> Optional<AnnotationValue<K>> getAnnotationValues(ElementDef originatingElement, ElementDef member, Class<K> annotationType) {
        if (member instanceof AnnotationMemberDef memberDef) {
            if (annotationType == Aliases.class) {
                // A member aliasing several annotations (ConfigurationProperties.includes aliases
                // ConfigurationReader and BeanProperties) is read by the base builder through the
                // @Aliases container, which neither the Java element metadata nor a Python
                // Annotated[...] member exposes as such: build it from the individual @AliasFor values.
                List<AnnotationValue<AliasFor>> aliases = memberAliases(memberDef);
                if (aliases.size() > 1) {
                    return Optional.of((AnnotationValue<K>) AnnotationValue.builder(Aliases.class)
                        .values(aliases.toArray(AnnotationValue[]::new))
                        .build());
                }
                return Optional.empty();
            }
            Optional<AnnotationValue<K>> annotation = memberDef.getAnnotationMetadata().findAnnotation(annotationType);
            if (annotation.isEmpty()) {
                annotation = findMemberDecorator(memberDef, annotationType);
            }
            return annotation.map(value -> normalizeAliasForAnnotationValue(value, annotationType));
        }
        return Optional.empty();
    }

    @SuppressWarnings("unchecked")
    private <K extends Annotation> Optional<AnnotationValue<K>> findMemberDecorator(AnnotationMemberDef memberDef, Class<K> annotationType) {
        String annotationName = annotationType.getName();
        for (DecoratorDef decorator : memberDef.decorators()) {
            if (toBinaryClassName(decorator.annotationName()).equals(annotationName)) {
                return Optional.of((AnnotationValue<K>) toAnnotationValue(decorator));
            }
        }
        return Optional.empty();
    }

    private <K extends Annotation> AnnotationValue<K> normalizeAliasForAnnotationValue(AnnotationValue<K> annotationValue, Class<K> annotationType) {
        if (annotationType == AliasFor.class && annotationValue.stringValue(ANNOTATION_NAME_MEMBER).isEmpty()) {
            Optional<AnnotationClassValue<?>> annotationClassValue = annotationValue.annotationClassValue("annotation");
            if (annotationClassValue.isPresent()) {
                return (AnnotationValue<K>) annotationValue
                    .mutate()
                    .member(ANNOTATION_NAME_MEMBER, annotationClassValue.get().getName())
                    .build();
            }
        }
        return annotationValue;
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
            String repeatedName = annotationMirror.repeatedName();
            if (repeatedName != null) {
                return toBinaryClassName(repeatedName);
            }
            return getJavaRepeatableContainerName(getJavaAnnotationType(annotationMirror));
        } else {
            return null;
        }
    }

    @Override
    protected String getRepeatableContainerNameForType(ElementDef annotationType) {
        if (visitorContext != null) {
            PythonProcessingEnvironment env = visitorContext.getProcessingEnvironment();
            DecoratorDef decoratorDef = findDecoratorDef(env.environment().decorators(), annotationType.name());
            if (decoratorDef != null && decoratorDef.repeatedName() != null) {
                return toBinaryClassName(decoratorDef.repeatedName());
            }
        }
        return getJavaRepeatableContainerName(getJavaAnnotationType(annotationType.name()));
    }

    private @Nullable String getJavaRepeatableContainerName(@Nullable ClassElement annotationType) {
        if (annotationType == null) {
            return null;
        }
        AnnotationValue<Repeatable> repeatable = annotationType.getAnnotation(Repeatable.class);
        if (repeatable == null) {
            return null;
        }
        return repeatable.annotationClassValue(AnnotationMetadata.VALUE_MEMBER)
            .map(AnnotationClassValue::getName)
            .orElse(null);
    }

    @Override
    protected Optional<ElementDef> getAnnotationMirror(String annotationName) {
        return annotationMirrorCache.computeIfAbsent(annotationName, this::resolveAnnotationMirror);
    }

    private Optional<ElementDef> resolveAnnotationMirror(String annotationName) {
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
        Optional<ElementDef> javaType = javaVisitorContext.getClassElement(annotationName)
            .map(annotationType -> new ClassDef(
                annotationType.getName(),
                toDecoratorDefs(annotationType.getAnnotationMetadata())
            ));
        if (javaType.isPresent()) {
            return javaType;
        }
        // A Python declared annotation has no compiled Java type until its generated stub is compiled, so fall back
        // to the decorator registry. Without this the annotation type cannot be resolved during the round that
        // declares it, and VisitorContext#getAnnotationDefaultValues answers an empty map for it.
        DecoratorDef decoratorDef = findDecoratorDef(annotationName);
        if (decoratorDef == null) {
            return Optional.empty();
        }
        return Optional.of(new ClassDef(
            toBinaryClassName(decoratorDef.annotationName()),
            decoratorDef.stereotypes()
        ));
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
            return resolveJavaAnnotationMember(javaAnnotationType, memberName);
        }
    }

    private AnnotationMemberDef resolveMemberDef(String annotationName, @Nullable ClassElement javaAnnotationType, String memberName) {
        if (javaAnnotationType == null) {
            return resolvePythonAnnotationMember(annotationName, memberName);
        }
        return resolveJavaAnnotationMember(javaAnnotationType, memberName);
    }

    private AnnotationMemberDef resolveJavaAnnotationMember(ClassElement javaAnnotationType, String memberName) {
        String cacheKey = javaAnnotationType.getName() + '#' + memberName;
        return javaAnnotationMemberCache.computeIfAbsent(
            cacheKey,
            ignored -> resolveJavaMemberDef(javaAnnotationType, memberName)
        );
    }

    private AnnotationMemberDef resolvePythonAnnotationMember(String annotationName, String memberName) {
        DecoratorDef decoratorDef = findDecoratorDef(annotationName);
        List<DecoratorDef> memberDecorators = decoratorDef == null
            ? List.of()
            : decoratorDef.memberDecorators().getOrDefault(memberName, List.of());
        // The member type is deliberately left unresolved. It would let PythonAnnotationValues#normalize convert a
        // class reference default to an AnnotationClassValue and a list default to an array, but resolving the type
        // annotation on the decorator parameter here re-enters Python class element construction, which reads the
        // decorator defaults again: a cycle that ends in a StackOverflowError. Resolving member types without that
        // cycle is a separate change; until then such a member keeps the value the Python processor reported.
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
            String candidateName = candidate.name();
            String candidateAnnotationName = toBinaryClassName(candidate.annotationName());
            String defaultPackage = PythonClassElement.PYTHON_DEFAULT_PACKAGE + '.';
            if (candidateAnnotationName.equals(binaryName)
                || (binaryName.startsWith(defaultPackage) && candidateName.equals(binaryName.substring(defaultPackage.length())))) {
                return candidate;
            }
        }
        return null;
    }

    private String resolveAnnotationName(DecoratorDef annotationMirror) {
        DecoratorDef decoratorDef = findDecoratorDef(annotationMirror.annotationName());
        if (decoratorDef != null) {
            return toBinaryClassName(decoratorDef.annotationName());
        }
        return toBinaryClassName(annotationMirror.annotationName());
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
        JavaVisitorContext javaVisitorContext = visitorContext.getJavaVisitorContext();
        if (javaVisitorContext != null) {
            return javaVisitorContext.getAnnotationMetadataBuilder().getRetentionPolicy(annotation.name());
        }
        return RetentionPolicy.RUNTIME;
    }

}
