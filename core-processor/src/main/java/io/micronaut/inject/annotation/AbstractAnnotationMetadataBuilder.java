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
package io.micronaut.inject.annotation;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.Aliases;
import io.micronaut.context.annotation.DefaultScope;
import io.micronaut.context.annotation.NonBinding;
import io.micronaut.core.annotation.AnnotatedElement;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationMetadataDelegate;
import io.micronaut.core.annotation.AnnotationUtil;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.InstantiatedMember;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.expressions.EvaluatedExpressionReference;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.visitor.VisitorContext;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Stream;

import static io.micronaut.expressions.EvaluatedExpressionConstants.EXPRESSION_PATTERN;

/**
 * An abstract implementation that builds {@link AnnotationMetadata}.
 *
 * @param <T> The element type
 * @param <A> The annotation type
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractAnnotationMetadataBuilder<T, A> {

    /**
     * Names of annotations that should produce deprecation warnings.
     * The key in the map is the deprecated annotation the value the replacement.
     */
    protected static final AnnotatedElementValidator ELEMENT_VALIDATOR;
    private static final Map<String, String> DEPRECATED_ANNOTATION_NAMES = Collections.emptyMap();
    private static final Map<String, List<AnnotationMapper<?>>> ANNOTATION_MAPPERS = new HashMap<>(10);
    private static final Map<String, List<AnnotationTransformer<?>>> ANNOTATION_TRANSFORMERS = new HashMap<>(5);
    private static final Map<String, List<AnnotationRemapper>> ANNOTATION_REMAPPERS = new HashMap<>(5);
    private static final List<AnnotationRemapper> ALL_ANNOTATION_REMAPPERS = new ArrayList<>(5);
    private static final Map<Object, CachedAnnotationMetadata> MUTATED_ANNOTATION_METADATA = new HashMap<>(100);
    private static final Map<String, Map<CharSequence, Object>> ANNOTATION_DEFAULTS = new HashMap<>(20);

    static {
        for (AnnotationMapper<?> mapper : SoftServiceLoader.load(AnnotationMapper.class, AbstractAnnotationMetadataBuilder.class.getClassLoader())
                .disableFork().collectAll()) {
            try {
                String name = null;
                if (mapper instanceof TypedAnnotationMapper<?> typedAnnotationMapper) {
                    name = typedAnnotationMapper.annotationType().getName();
                } else if (mapper instanceof NamedAnnotationMapper namedAnnotationMapper) {
                    name = namedAnnotationMapper.getName();
                }
                if (StringUtils.isNotEmpty(name)) {
                    ANNOTATION_MAPPERS.computeIfAbsent(name, s -> new ArrayList<>(2)).add(mapper);
                }
            } catch (Throwable e) {
                // mapper, missing dependencies, continue
            }
        }

        for (AnnotationTransformer<?> transformer : SoftServiceLoader.load(AnnotationTransformer.class, AbstractAnnotationMetadataBuilder.class.getClassLoader())
                .disableFork().collectAll()) {
            try {
                String name = null;
                if (transformer instanceof TypedAnnotationTransformer<?> typedAnnotationTransformer) {
                    name = typedAnnotationTransformer.annotationType().getName();
                } else if (transformer instanceof NamedAnnotationTransformer namedAnnotationTransformer) {
                    name = namedAnnotationTransformer.getName();
                }
                if (StringUtils.isNotEmpty(name)) {
                    ANNOTATION_TRANSFORMERS.computeIfAbsent(name, s -> new ArrayList<>(2)).add(transformer);
                }
            } catch (Throwable e) {
                // mapper, missing dependencies, continue
            }
        }

        for (AnnotationRemapper mapper : SoftServiceLoader.load(AnnotationRemapper.class, AbstractAnnotationMetadataBuilder.class.getClassLoader())
                .disableFork().collectAll()) {
            try {
                String name = mapper.getPackageName();
                if (name.equals(AnnotationRemapper.ALL_PACKAGES)) {
                    ALL_ANNOTATION_REMAPPERS.add(mapper);
                } else if (StringUtils.isNotEmpty(name)) {
                    ANNOTATION_REMAPPERS.computeIfAbsent(name, s -> new ArrayList<>(2)).add(mapper);
                }
            } catch (Throwable e) {
                // mapper, missing dependencies, continue
            }
        }
        ELEMENT_VALIDATOR = SoftServiceLoader.load(AnnotatedElementValidator.class).firstAvailable().orElse(null);
    }

    private boolean validating = true;
    private final Set<T> erroneousElements = new HashSet<>();

    /**
     * Default constructor.
     */
    protected AbstractAnnotationMetadataBuilder() {

    }

    @SuppressWarnings("java:S1872")
    private AnnotationMetadata metadataForError(RuntimeException e) {
        if ("org.eclipse.jdt.internal.compiler.problem.AbortCompilation".equals(e.getClass().getName())) {
            // workaround for a bug in the Eclipse APT implementation. See bug 541466 on their Bugzilla.
            return AnnotationMetadata.EMPTY_METADATA;
        } else {
            throw e;
        }
    }

    /**
     * Build only metadata for declared annotations.
     *
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata buildDeclared(T element) {
        MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
        try {
            AnnotationMetadata metadata = buildInternalMulti(
                    Collections.emptyList(),
                    element,
                    annotationMetadata, true, true
            );
            if (metadata.isEmpty()) {
                return AnnotationMetadata.EMPTY_METADATA;
            }
            return metadata;
        } catch (RuntimeException e) {
            return metadataForError(e);
        }
    }

    /**
     * Build the metadata for the given element. If the element is a method the class metadata will be included.
     *
     * @param owningType       The owning type
     * @param methodElement    The method element
     * @param parameterElement The parameter element
     * @return The {@link AnnotationMetadata}
     */
    public CachedAnnotationMetadata lookupOrBuildForParameter(T owningType, T methodElement, T parameterElement) {
        return lookupOrBuild(new Key3<>(owningType, methodElement, parameterElement), parameterElement);
    }

    /**
     * Build the metadata for the given element.
     *
     * @param typeElement The element
     * @return The {@link AnnotationMetadata}
     */
    public CachedAnnotationMetadata lookupOrBuildForType(T typeElement) {
        return lookupOrBuild(typeElement, typeElement);
    }

    /**
     * Build the metadata for the given method element excluding any class metadata.
     *
     * @param owningType The owningType
     * @param element    The element
     * @return The {@link CachedAnnotationMetadata}
     */
    public CachedAnnotationMetadata lookupOrBuildForMethod(T owningType, T element) {
        return lookupOrBuild(new Key2<>(owningType, element), element);
    }

    /**
     * Build the metadata for the given field element excluding any class metadata.
     *
     * @param owningType The owningType
     * @param element    The element
     * @return The {@link CachedAnnotationMetadata}
     */
    public CachedAnnotationMetadata lookupOrBuildForField(T owningType, T element) {
        return lookupOrBuild(new Key2<>(owningType, element), element);
    }

    /**
     * Lookup or build new annotation metadata.
     *
     * @param key     The cache key
     * @param element The type element
     * @return The annotation metadata
     * @since 4.0.0
     */
    public CachedAnnotationMetadata lookupOrBuild(Object key, T element) {
        CachedAnnotationMetadata cachedAnnotationMetadata = MUTATED_ANNOTATION_METADATA.get(key);
        if (cachedAnnotationMetadata == null) {
            AnnotationMetadata annotationMetadata = buildInternal(element);
            cachedAnnotationMetadata = new DefaultCachedAnnotationMetadata(annotationMetadata);
            // Don't use `computeIfAbsent` as it can lead to a concurrent exception because the cache is accessed during in `buildInternal`
            MUTATED_ANNOTATION_METADATA.put(key, cachedAnnotationMetadata);
        }
        return cachedAnnotationMetadata;
    }

    private AnnotationMetadata buildInternal(T element) {
        MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
        try {
            return buildInternalMulti(
                    Collections.emptyList(),
                    element,
                    annotationMetadata, false, false
            );
        } catch (RuntimeException e) {
            return metadataForError(e);
        }
    }

    /**
     * Get the type of the given annotation.
     *
     * @param annotationMirror The annotation
     * @return The type
     */
    protected abstract T getTypeForAnnotation(A annotationMirror);

    /**
     * Checks whether an annotation is present.
     *
     * @param element    The element
     * @param annotation The annotation type
     * @return True if the annotation is present
     */
    protected abstract boolean hasAnnotation(T element, Class<? extends Annotation> annotation);

    /**
     * Checks whether an annotation is present.
     *
     * @param element    The element
     * @param annotation The annotation type name
     * @return True if the annotation is present
     */
    protected abstract boolean hasAnnotation(T element, String annotation);

    /**
     * Checks whether any annotations are present on the given element.
     *
     * @param element The element
     * @return True if the annotation is present
     */
    protected abstract boolean hasAnnotations(T element);

    /**
     * Get the given type of the annotation.
     *
     * @param annotationMirror The annotation
     * @return The type
     */
    protected abstract String getAnnotationTypeName(A annotationMirror);

    /**
     * Get the name for the given element.
     *
     * @param element The element
     * @return The name
     */
    protected abstract String getElementName(T element);

    /**
     * Obtain the annotations for the given type. This method
     * is also responsible for unwrapping repeatable annotations.
     * <p>
     * For example, {@code @Parent(value = {@Child, @Child})} should result in the two
     * child annotations being returned from this method <b>instead</b> of the
     * parent annotation.
     *
     * @param element The type element
     * @return The annotations
     */
    protected abstract List<? extends A> getAnnotationsForType(T element);

    /**
     * Build the type hierarchy for the given element.
     *
     * @param element                The element
     * @param inheritTypeAnnotations Whether to inherit type annotations
     * @param declaredOnly           Whether to only include declared annotations
     * @return The type hierarchy
     */
    protected abstract List<T> buildHierarchy(T element, boolean inheritTypeAnnotations, boolean declaredOnly);

    /**
     * Read the given member and value, applying conversions if necessary, and place the data in the given map.
     *
     * @param originatingElement The originating element
     * @param annotationName     The annotation name
     * @param member             The member being read from
     * @param memberName         The member
     * @param annotationValue    The value
     * @param annotationValues   The values to populate
     */
    protected abstract void readAnnotationRawValues(
            T originatingElement,
            String annotationName,
            T member,
            String memberName,
            Object annotationValue,
            Map<CharSequence, Object> annotationValues);

    /**
     * Read the given member and value, applying conversions if necessary, and place the data in the given map.
     *
     * @param originatingElement The originating element
     * @param annotationName     The annotation name
     * @param member             The member being read from
     * @param memberName         The member
     * @param annotationValue    The value
     * @param annotationValues   The values to populate
     * @param resolvedDefaults   The resolved defaults
     * @since 4.3.0
     */
    protected void readAnnotationRawValues(
            T originatingElement,
            String annotationName,
            T member,
            String memberName,
            Object annotationValue,
            Map<CharSequence, Object> annotationValues,
            Map<String, Map<CharSequence, Object>> resolvedDefaults) {
        readAnnotationRawValues(originatingElement, annotationName, member, memberName, annotationValue, annotationValues);
    }

    /**
     * Validates an annotation value.
     *
     * @param originatingElement The originating element
     * @param annotationName     The annotation name
     * @param member             The member
     * @param memberName         The member name
     * @param resolvedValue      The resolved value
     */
    protected void validateAnnotationValue(T originatingElement,
                                           String annotationName,
                                           T member,
                                           String memberName,
                                           Object resolvedValue) {
        if (!validating) {
            return;
        }

        final AnnotatedElementValidator elementValidator = getElementValidator();
        if (elementValidator != null && !erroneousElements.contains(member)) {
            boolean shouldValidate = !(annotationName.equals(AliasFor.class.getName())) &&
                    (!(resolvedValue instanceof String) || !resolvedValue.toString().contains("${"));
            if (shouldValidate) {
                shouldValidate = isValidationRequired(member);
            }
            if (shouldValidate) {
                AnnotationMetadata metadata;
                try {
                    validating = false;
                    metadata = buildDeclared(member);
                } finally {
                    validating = true;
                }

                final Set<String> errors = elementValidator.validatedAnnotatedElement(new AnnotatedElement() {
                    @NonNull
                    @Override
                    public String getName() {
                        return memberName;
                    }

                    @Override
                    public AnnotationMetadata getAnnotationMetadata() {
                        return metadata;
                    }
                }, resolvedValue);

                if (CollectionUtils.isNotEmpty(errors)) {
                    erroneousElements.add(member);
                    for (String error : errors) {
                        error = "@" + NameUtils.getSimpleName(annotationName) + "." + memberName + ": " + error;
                        addError(originatingElement, error);
                    }
                }
            }
        }
    }

    /**
     * Return whether the given member requires validation.
     *
     * @param member The member
     * @return True if it is
     */
    protected abstract boolean isValidationRequired(T member);

    /**
     * Obtains the element validator.
     *
     * @return The validator.
     */
    @Nullable
    protected AnnotatedElementValidator getElementValidator() {
        return ELEMENT_VALIDATOR;
    }

    /**
     * Adds an error.
     *
     * @param originatingElement The originating element
     * @param error              The error
     */
    protected abstract void addError(@NonNull T originatingElement, @NonNull String error);

    /**
     * Adds a warning.
     *
     * @param originatingElement The originating element
     * @param warning            The warning
     */
    protected abstract void addWarning(@NonNull T originatingElement, @NonNull String warning);

    /**
     * Read the given member and value, applying conversions if necessary, and place the data in the given map.
     *
     * @param originatingElement The originating element
     * @param member             The member
     * @param annotationName     The annotation name
     * @param memberName         The member name
     * @param annotationValue    The value
     * @return The object
     */
    protected abstract Object readAnnotationValue(T originatingElement, T member, String annotationName, String memberName, Object annotationValue);

    /**
     * Read the raw default annotation values from the given annotation.
     *
     * @param annotationName annotation name
     * @param annotationType the type
     * @return The values
     */
    protected abstract Map<? extends T, ?> readAnnotationDefaultValues(String annotationName, T annotationType);

    /**
     * Read the raw annotation values from the given annotation.
     *
     * @param annotationMirror The annotation
     * @return The values
     */
    protected abstract Map<? extends T, ?> readAnnotationRawValues(A annotationMirror);

    /**
     * Resolve the annotations values from the given member for the given type.
     *
     * @param originatingElement The originating element
     * @param member             The member
     * @param annotationType     The type
     * @param <K>                The annotation type
     * @return The values
     */
    protected abstract <K extends Annotation> Optional<AnnotationValue<K>> getAnnotationValues(T originatingElement, T member, Class<K> annotationType);

    /**
     * Read the name of an annotation member.
     *
     * @param member The member
     * @return The name
     */
    protected abstract String getAnnotationMemberName(T member);

    /**
     * Obtain the name of the repeatable annotation if the annotation is one.
     *
     * @param annotationMirror The annotation mirror
     * @return Return the name or null
     */
    @Nullable
    protected abstract String getRepeatableName(A annotationMirror);

    /**
     * Obtain the name of the repeatable annotation if the annotation is one.
     *
     * @param annotationType The annotation mirror
     * @return Return the name or null
     */
    @Nullable
    protected abstract String getRepeatableContainerNameForType(T annotationType);

    /**
     * @param annotationElement The annotation element
     * @param annotationType    The annotation type
     * @return The annotation value
     */
    protected AnnotationValue<?> readNestedAnnotationValue(T annotationElement, A annotationType) {
        return readNestedAnnotationValue(annotationElement, annotationType, new HashMap<>());
    }

    /**
     * @param annotationElement The annotation element
     * @param annotationType    The annotation type
     * @param resolvedDefaults resoldved defaults
     * @return The annotation value
     */
    protected AnnotationValue<?> readNestedAnnotationValue(T annotationElement, A annotationType, Map<String, Map<CharSequence, Object>> resolvedDefaults) {
        final String annotationTypeName = getAnnotationTypeName(annotationType);
        Map<? extends T, ?> annotationValues = readAnnotationRawValues(annotationType);
        T theType = getTypeForAnnotation(annotationType);
        if (annotationValues.isEmpty()) {
            Map<CharSequence, Object> annotationDefaults = resolveAnnotationDefaults(theType, annotationTypeName, resolvedDefaults);
            return new AnnotationValue<>(annotationTypeName, Collections.emptyMap(), annotationDefaults);
        }

        Map<CharSequence, Object> resolvedValues = CollectionUtils.newLinkedHashMap(annotationValues.size());
        for (Map.Entry<? extends T, ?> entry : annotationValues.entrySet()) {
            T member = entry.getKey();
            Optional<AnnotationValue<AliasFor>> aliasForValues = getAnnotationValues(annotationElement, member, AliasFor.class);
            Object annotationValue = entry.getValue();
            if (aliasForValues.isPresent()) {
                AnnotationValue<AliasFor> aliasFor = aliasForValues.get();
                Optional<String> aliasMember = aliasFor.stringValue("member");
                Optional<String> aliasAnnotation = aliasFor.stringValue("annotation");
                Optional<String> aliasAnnotationName = aliasFor.stringValue("annotationName");
                if (aliasMember.isPresent() && !(aliasAnnotation.isPresent() || aliasAnnotationName.isPresent())) {
                    String aliasedNamed = aliasMember.get();
                    readAnnotationRawValues(annotationElement,
                            annotationTypeName,
                            member,
                            aliasedNamed,
                            annotationValue,
                            resolvedValues);
                }
            }
            String memberName = getAnnotationMemberName(member);
            readAnnotationRawValues(annotationElement,
                    annotationTypeName,
                    member,
                    memberName,
                    annotationValue,
                    resolvedValues);

        }
        Map<CharSequence, Object> annotationDefaults = resolveAnnotationDefaults(theType, annotationTypeName, resolvedDefaults);
        return new AnnotationValue<>(annotationTypeName, resolvedValues, annotationDefaults);
    }

    private Map<CharSequence, Object> resolveAnnotationDefaults(T annotationElement, String annotationTypeName, Map<String, Map<CharSequence, Object>> resolvedDefaults) {
        Map<CharSequence, Object> defaults = resolvedDefaults.get(annotationTypeName);
        if (defaults != null) {
            return defaults;
        }
        defaults = new LinkedHashMap<>();
        // To prevent recursion we set the map and modify it later
        resolvedDefaults.put(annotationTypeName, defaults);
        Map<? extends T, ?> nativeDefaultValues = readAnnotationDefaultValues(annotationTypeName, annotationElement);
        Map<CharSequence, Object> annotationDefaults = getAnnotationDefaults(annotationElement, annotationTypeName, nativeDefaultValues, resolvedDefaults);
        defaults.putAll(annotationDefaults);
        return annotationDefaults;
    }

    /**
     * Return a mirror for the given annotation.
     *
     * @param annotationName The annotation name
     * @return An optional mirror
     */
    protected abstract Optional<T> getAnnotationMirror(String annotationName);

    /**
     * Detect evaluated expression in annotation value.
     *
     * @param value - Annotation value
     * @return if value contains evaluated expression
     */
    protected boolean isEvaluatedExpression(@Nullable Object value) {
        return (value instanceof String str && str.matches(EXPRESSION_PATTERN))
                   || (value instanceof String[] strArray &&
                           Arrays.stream(strArray).anyMatch(this::isEvaluatedExpression));
    }

    /**
     * Wraps original annotation value to make it processable at later stages.
     *
     * @param originatingElement originating annotated element
     * @param annotationName annotation name
     * @param memberName annotation member name
     * @param initialAnnotationValue original annotation value
     * @return expression reference
     */
    @NonNull
    protected Object buildEvaluatedExpressionReference(@NonNull T originatingElement,
                                                       @NonNull String annotationName,
                                                       @NonNull String memberName,
                                                       @NonNull Object initialAnnotationValue) {
        String originatingClassName = getOriginatingClassName(originatingElement);
        if (originatingClassName != null) {
            String packageName = NameUtils.getPackageName(originatingClassName);
            String simpleClassName = NameUtils.getSimpleName(originatingClassName);
            String exprClassName = "%s.$%s%s".formatted(packageName, simpleClassName, EvaluatedExpressionReferenceCounter.EXPR_SUFFIX);

            Integer expressionIndex = EvaluatedExpressionReferenceCounter.nextIndex(exprClassName);

            return new EvaluatedExpressionReference(initialAnnotationValue, annotationName, memberName, exprClassName + expressionIndex);
        } else {
            return initialAnnotationValue;
        }

    }

    @Nullable
    protected abstract String getOriginatingClassName(@NonNull T orginatingElement);

    /**
     * Get the annotation member.
     *
     * @param annotationElement The annotation element
     * @param member            The member
     * @return The annotation member element
     */
    @Nullable
    protected abstract T getAnnotationMember(T annotationElement, CharSequence member);

    /**
     * Obtain the annotation mappers for the given annotation name.
     *
     * @param annotationName The annotation name
     * @param <K>            The annotation type
     * @return The mappers
     */
    @NonNull
    protected <K extends Annotation> List<AnnotationMapper<K>> getAnnotationMappers(@NonNull String annotationName) {
        return (List) ANNOTATION_MAPPERS.get(annotationName);
    }

    /**
     * Obtain the transformers mappers for the given annotation name.
     *
     * @param annotationName The annotation name
     * @param <K>            The annotation type
     * @return The transformers
     */
    @NonNull
    protected <K extends Annotation> List<AnnotationTransformer<K>> getAnnotationTransformers(@NonNull String annotationName) {
        return (List) ANNOTATION_TRANSFORMERS.get(annotationName);
    }

    /**
     * Returns the visitor context for this implementation.
     *
     * @return The visitor context
     */
    protected abstract VisitorContext getVisitorContext();

    private Map<CharSequence, Object> getAnnotationDefaults(T originatingElement,
                                                            String annotationName,
                                                            Map<? extends T, ?> elementDefaultValues,
                                                            Map<String, Map<CharSequence, Object>> resolvedDefaults) {
        if (elementDefaultValues == null) {
            return null;
        }
        Map<CharSequence, Object> defaultValues = CollectionUtils.newLinkedHashMap(elementDefaultValues.size());
        for (Map.Entry<? extends T, ?> entry : elementDefaultValues.entrySet()) {
            T member = entry.getKey();
            String memberName = getAnnotationMemberName(member);
            if (!defaultValues.containsKey(memberName)) {
                Object annotationValue = entry.getValue();
                readAnnotationRawValues(originatingElement,
                        annotationName,
                        member,
                        memberName,
                        annotationValue,
                        defaultValues,
                        resolvedDefaults);
            }
        }
        return defaultValues;
    }

    @Nullable
    private void processAnnotationAlias(Map<CharSequence, Object> annotationValues,
                                        Object annotationValue,
                                        AnnotationValue<AliasFor> aliasForAnnotation,
                                        List<ProcessedAnnotation> introducedAnnotations) {
        Optional<String> aliasAnnotation = aliasForAnnotation.stringValue("annotation");
        Optional<String> aliasAnnotationName = aliasForAnnotation.stringValue("annotationName");
        Optional<String> aliasMember = aliasForAnnotation.stringValue("member");

        if (aliasAnnotation.isPresent() || aliasAnnotationName.isPresent()) {
            if (aliasMember.isPresent()) {
                String aliasedAnnotation;
                aliasedAnnotation = aliasAnnotation.orElseGet(aliasAnnotationName::get);
                String aliasedMemberName = aliasMember.get();
                if (annotationValue != null) {
                    ProcessedAnnotation newAnnotation = toProcessedAnnotation(
                            AnnotationValue.builder(aliasedAnnotation, getRetentionPolicy(aliasedAnnotation))
                                    .members(Collections.singletonMap(aliasedMemberName, annotationValue))
                                    .build()
                    );
                    introducedAnnotations.add(newAnnotation);
                    ProcessedAnnotation newNewAnnotation = processAliases(newAnnotation, introducedAnnotations);
                    if (newNewAnnotation != newAnnotation) {
                        introducedAnnotations.set(introducedAnnotations.indexOf(newAnnotation), newNewAnnotation);
                    }
                }
            }
        } else if (aliasMember.isPresent()) {
            String aliasedNamed = aliasMember.get();
            if (annotationValue != null) {
                annotationValues.put(aliasedNamed, annotationValue);
            }
        }
    }

    /**
     * Gets the retention policy for the given annotation.
     *
     * @param annotation The annotation
     * @return The retention policy
     */
    @NonNull
    protected abstract RetentionPolicy getRetentionPolicy(@NonNull T annotation);

    /**
     * Gets the retention policy for the given annotation.
     *
     * @param annotation The annotation
     * @return The retention policy
     */
    @NonNull
    public RetentionPolicy getRetentionPolicy(@NonNull String annotation) {
        return getAnnotationMirror(annotation).map(this::getRetentionPolicy).orElse(RetentionPolicy.RUNTIME);
    }

    private AnnotationMetadata buildInternalMulti(
            List<T> parents,
            T element,
            MutableAnnotationMetadata annotationMetadata,
            boolean inheritTypeAnnotations,
            boolean declaredOnly) {
        List<T> hierarchy = buildHierarchy(element, inheritTypeAnnotations, declaredOnly);
        for (T parent : parents) {
            final List<T> parentHierarchy = buildHierarchy(parent, inheritTypeAnnotations, declaredOnly);
            if (hierarchy.isEmpty() && !parentHierarchy.isEmpty()) {
                hierarchy = parentHierarchy;
            } else {
                hierarchy.addAll(0, parentHierarchy);
            }
        }
        Collections.reverse(hierarchy);
        for (T currentElement : hierarchy) {
            if (currentElement == null) {
                continue;
            }
            List<? extends A> annotationHierarchy = getAnnotationsForType(currentElement);

            if (annotationHierarchy.isEmpty()) {
                continue;
            }

            boolean originatingElementIsSameParent = parents.contains(currentElement);
            boolean isDeclared = currentElement == element;
            addAnnotations(
                    annotationMetadata,
                    currentElement,
                    originatingElementIsSameParent,
                    isDeclared,
                    annotationHierarchy
            );

        }
        if (!annotationMetadata.hasDeclaredStereotype(AnnotationUtil.SCOPE) && annotationMetadata.hasDeclaredStereotype(
                DefaultScope.class)) {
            Optional<String> value = annotationMetadata.stringValue(DefaultScope.class);
            value.ifPresent(name -> annotationMetadata.addDeclaredAnnotation(name, Collections.emptyMap()));
        }
        postProcess(annotationMetadata, element);
        return annotationMetadata;
    }

    protected void postProcess(MutableAnnotationMetadata mutableAnnotationMetadata,
                               T element) {
        //no-op
    }

    private void addAnnotations(MutableAnnotationMetadata annotationMetadata,
                                T element,
                                boolean alwaysIncludeAnnotation,
                                boolean isDeclared,
                                List<? extends A> annotationHierarchy) {
        Stream<? extends A> stream = annotationHierarchy.stream();
        Stream<ProcessedAnnotation> annotationValues = annotationMirrorToAnnotationValue(stream, element);
        addAnnotations(annotationMetadata, annotationValues, isDeclared, alwaysIncludeAnnotation);
    }

    @NonNull
    private Stream<ProcessedAnnotation> annotationMirrorToAnnotationValue(Stream<? extends A> stream,
                                                                          T element) {
        return stream
                .filter(annotationMirror -> {
                    String annotationName = getAnnotationTypeName(annotationMirror);
                    if (!annotationName.equals(AnnotationUtil.ANN_INHERITED)
                            && (AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationName) || isExcludedAnnotation(element, annotationName))) {
                        return false;
                    }
                    if (DEPRECATED_ANNOTATION_NAMES.containsKey(annotationName)) {
                        addWarning(element,
                                "Usages of deprecated annotation " + annotationName + " found. You should use " + DEPRECATED_ANNOTATION_NAMES.get(
                                        annotationName) + " instead.");
                    }
                    return true;
                }).map(annotationMirror -> createAnnotationValue(element, annotationMirror));
    }

    // The value of this method can be cached
    @NonNull
    private ProcessedAnnotation createAnnotationValue(@NonNull T originatingElement,
                                                      @NonNull A annotationMirror) {
        String annotationName = getAnnotationTypeName(annotationMirror);
        final T annotationType = getTypeForAnnotation(annotationMirror);
        RetentionPolicy retentionPolicy = getRetentionPolicy(annotationType);

        Map<? extends T, ?> elementValues = readAnnotationRawValues(annotationMirror);
        Map<CharSequence, Object> annotationValues;
        if (CollectionUtils.isEmpty(elementValues)) {
            annotationValues = new LinkedHashMap<>(3);
        } else {
            annotationValues = new LinkedHashMap<>(5);
            Set<String> nonBindingMembers = new LinkedHashSet<>(2);
            for (Map.Entry<? extends T, ?> entry : elementValues.entrySet()) {
                T member = entry.getKey();

                if (member == null) {
                    continue;
                }

                Object annotationValue = entry.getValue();
                if (hasAnnotations(member)) {
                    final MutableAnnotationMetadata memberMetadata = new MutableAnnotationMetadata();
                    final List<? extends A> annotationsForMember = getAnnotationsForType(member)
                            .stream().filter((a) -> !getAnnotationTypeName(a).equals(annotationName))
                            .toList();

                    addAnnotations(memberMetadata, member, false,
                            true, annotationsForMember);

                    boolean isInstantiatedMember = memberMetadata.hasAnnotation(InstantiatedMember.class);

                    if (memberMetadata.hasAnnotation(NonBinding.class)) {
                        final String memberName = getElementName(member);
                        nonBindingMembers.add(memberName);
                    }
                    if (isInstantiatedMember) {
                        final String memberName = getAnnotationMemberName(member);
                        final Object rawValue = readAnnotationValue(originatingElement, member, annotationName, memberName, annotationValue);
                        if (rawValue instanceof AnnotationClassValue<?> annotationClassValue) {
                            annotationValues.put(memberName, new AnnotationClassValue<>(annotationClassValue.getName(), true));
                        }
                    }
                }

                readAnnotationRawValues(originatingElement,
                        annotationName,
                        member,
                        getAnnotationMemberName(member),
                        annotationValue,
                        annotationValues);

            }

            if (!nonBindingMembers.isEmpty()) {
                nonBindingMembers.add(AnnotationUtil.NON_BINDING_ATTRIBUTE);
                annotationValues.put(AnnotationUtil.NON_BINDING_ATTRIBUTE, nonBindingMembers.toArray(String[]::new));
            }
        }

        Map<CharSequence, Object> defaultValues = getCachedAnnotationDefaults(annotationName, annotationType);

        return new ProcessedAnnotation(
                annotationType,
                new AnnotationValue<>(annotationName, annotationValues, defaultValues, retentionPolicy)
        );
    }

    /**
     * Get the cached annotation defaults.
     *
     * @param annotationName The annotation name
     * @param annotationType The annotation type
     * @return The defaults
     */
    @NonNull
    protected Map<CharSequence, Object> getCachedAnnotationDefaults(String annotationName, T annotationType) {
        Map<CharSequence, Object> defaultValues;
        final Map<CharSequence, Object> defaults = ANNOTATION_DEFAULTS.get(annotationName);
        if (defaults != null) {
            defaultValues = new LinkedHashMap<>(defaults);
        } else {
            Map<? extends T, ?> annotationDefaultValues = readAnnotationDefaultValues(annotationName, annotationType);
            defaultValues = getAnnotationDefaults(annotationType, annotationName, annotationDefaultValues, new HashMap<>());
            if (defaultValues != null && !defaultValues.isEmpty()) {
                // Don't cache empty values is it can be invalid annotation type provided by KSP or Groovy
                // Add the default for any retention type annotation
                ANNOTATION_DEFAULTS.put(annotationName, new LinkedHashMap<>(defaultValues));
            } else {
                defaultValues = Collections.emptyMap();
            }
        }
        return defaultValues;
    }

    private void handleAnnotationAlias(T originatingElement,
                                       Map<CharSequence, Object> annotationValues,
                                       T annotationMember,
                                       Object annotationValue,
                                       List<ProcessedAnnotation> introducedAnnotations) {
        Optional<AnnotationValue<Aliases>> aliases = getAnnotationValues(originatingElement, annotationMember, Aliases.class);
        if (aliases.isPresent()) {
            for (AnnotationValue<AliasFor> av : aliases.get().<AliasFor>getAnnotations(AnnotationMetadata.VALUE_MEMBER)) {
                processAnnotationAlias(
                        annotationValues,
                        annotationValue,
                        av,
                        introducedAnnotations
                );
            }
        } else {
            Optional<AnnotationValue<AliasFor>> aliasForValues = getAnnotationValues(originatingElement, annotationMember, AliasFor.class);
            if (aliasForValues.isPresent()) {
                processAnnotationAlias(
                        annotationValues,
                        annotationValue,
                        aliasForValues.get(),
                        introducedAnnotations
                );
            }
        }
    }

    private void addAnnotations(@NonNull MutableAnnotationMetadata annotationMetadata,
                                @NonNull Stream<ProcessedAnnotation> stream,
                                boolean isDeclared,
                                boolean alwaysIncludeAnnotation) {

        ProcessingContext processingContext = new ProcessingContext(getVisitorContext());

        List<AnnotationValue<?>> annotationValues = stream
                .flatMap(processedAnnotation -> processAnnotation(processingContext, processedAnnotation))
                .<AnnotationValue<?>>map(ProcessedAnnotation::getAnnotationValue)
                .toList();

        addAnnotations(annotationMetadata, isDeclared, false, alwaysIncludeAnnotation, List.of(
                Map.entry(
                        List.of(), annotationValues
                )
        ), processingContext.repeatableToContainer);
    }

    private void addAnnotations(@NonNull MutableAnnotationMetadata annotationMetadata,
                                boolean isDeclared,
                                boolean isStereotype,
                                boolean alwaysIncludeAnnotation,
                                @NonNull List<Map.Entry<List<String>, List<AnnotationValue<?>>>> annotations,
                                @NonNull Map<String, String> repeatableToContainer) {

        // We need to add annotations by their levels:
        // 1. The annotation
        // 2. Stereotypes defined on the annotation
        // 3. The stereotypes of the stereotypes added in #2
        // 3. The stereotypes of the stereotypes added in #3 etc

        List<Map.Entry<List<String>, List<AnnotationValue<?>>>> stereotypes = new ArrayList<>(annotations.size());

        for (Map.Entry<List<String>, List<AnnotationValue<?>>> e : annotations) {
            List<String> parentAnnotations = e.getKey();
            for (AnnotationValue<?> annotationValue : e.getValue()) {
                if (annotationValue.getAnnotationName().equals(AnnotationUtil.ANN_INHERITED)) {
                    continue;
                }
                if (isDeclared || isStereotype || alwaysIncludeAnnotation || isInherited(annotationValue.getStereotypes())) {

                    addAnnotation(
                            annotationMetadata,
                            parentAnnotations,
                            isDeclared,
                            isStereotype,
                            annotationValue,
                            repeatableToContainer);

                    List<String> newParentAnnotations = CollectionUtils.concat(parentAnnotations, annotationValue.getAnnotationName());

                    if (annotationValue.getStereotypes() != null) {
                        stereotypes.add(Map.entry(
                                newParentAnnotations,
                                annotationValue.getStereotypes()
                        ));
                    }

                }
            }

        }

        if (!stereotypes.isEmpty()) {
            addAnnotations(annotationMetadata, isDeclared, true, alwaysIncludeAnnotation, stereotypes, repeatableToContainer);
        }
    }

    private boolean isInherited(@Nullable List<AnnotationValue<?>> stereotypes) {
        if (stereotypes == null) {
            return false;
        }
        return stereotypes.stream().anyMatch(av -> av.getAnnotationName().equals(AnnotationUtil.ANN_INHERITED));
    }

    @NonNull
    private Stream<ProcessedAnnotation> processAnnotation(@NonNull ProcessingContext context,
                                                          @NonNull ProcessedAnnotation processedAnnotation) {

        AnnotationValue<?> annotationValue = processedAnnotation.getAnnotationValue();
        if (AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationValue.getAnnotationName()) || context.isProcessed(annotationValue)) {
            return Stream.empty();
        }
        if (processedAnnotation.getAnnotationType() != null) {
            String repeatableContainer = getRepeatableContainerNameForType(processedAnnotation.getAnnotationType());
            if (repeatableContainer != null) {
                // Collect the repeatable container from the annotation mirror for later
                context.repeatableToContainer.put(annotationValue.getAnnotationName(), repeatableContainer);
            }
        }

        // Add annotation default values
        processedAnnotation = addDefaults(processedAnnotation);
        // Check if the annotation has the stereotypes set manually, before adding alias stereotypes
        boolean stereotypesProvided = annotationValue.getStereotypes() != null;
        // First we need to process aliases, those contribute stereotypes with higher priority
        processedAnnotation = processAliases(context, processedAnnotation);

        // The next invocation will invoke current method recursively till the stereotypes are processed.
        // That will build an annotation value tree with annotations and it's stereotypes.
        processedAnnotation = addStereotypes(context, processedAnnotation, stereotypesProvided);
        // Next step is transforming, starting from the stereotypes moving up in the hierarchy.
        return transform(context, processedAnnotation)
                .flatMap(this::flattenRepeatable)
                .map(this::addDefaults);
    }

    @NonNull
    private ProcessedAnnotation processAliases(@NonNull ProcessingContext context,
                                               @NonNull ProcessedAnnotation processedAnnotation) {
        // Aliases produces by the annotations are added to the stereotypes collection
        List<ProcessedAnnotation> introducedAliasForAnnotations = new ArrayList<>();
        ProcessedAnnotation newAnn = processAliases(processedAnnotation, introducedAliasForAnnotations);
        if (!introducedAliasForAnnotations.isEmpty()) {
            newAnn = newAnn.mutateAnnotationValue(builder ->
                    builder.stereotypes(
                                    introducedAliasForAnnotations.stream()
                                            .flatMap(a -> processAnnotation(context, a))
                                            .<AnnotationValue<?>>map(ProcessedAnnotation::getAnnotationValue)
                                            .toList()
                            )
            );
        }
        return newAnn;
    }

    @NonNull
    private ProcessedAnnotation addStereotypes(@NonNull ProcessingContext context,
                                               @NonNull ProcessedAnnotation processedAnnotation,
                                               boolean stereotypesProvided) {
        List<ProcessedAnnotation> stereotypes = Collections.emptyList();
        if (processedAnnotation.getAnnotationValue().getStereotypes() != null) {
            stereotypes = processedAnnotation.getAnnotationValue().getStereotypes().stream()
                .map(this::toProcessedAnnotation)
                .flatMap(this::flattenRepeatable)
                .map(this::addDefaults)
                .toList();
        }
        if (!stereotypesProvided) {
            // The annotation doesn't have the stereotypes set manually
            // In this case `stereotypes` are aliases
            List<ProcessedAnnotation> extractedStereotypes = extractStereotypes(context, processedAnnotation);
            stereotypes = Stream.concat(
                // Some implementation like KSP and Groovy cannot extract proper compiler annotation type from an annotation name (@AliasFor(annotationName))
                // That prevents us to properly extract the annotation defaults
                // To fix it for the aliases we will try to set the annotation type and the defaults from the stereotype of the same annotation if present
                stereotypes.stream().map(annotation -> {
                    for (ProcessedAnnotation extractedStereotype : extractedStereotypes) {
                        if (extractedStereotype.getAnnotationValue().getAnnotationName().equals(annotation.getAnnotationValue().getAnnotationName())) {
                            return annotation
                                .withAnnotationType(extractedStereotype.annotationType)
                                .mutateAnnotationValue(builder -> builder.defaultValues(extractedStereotype.annotationValue.getDefaultValues()));
                        }
                    }
                    return annotation;
                }),
                extractedStereotypes.stream()
            ).toList();
        }
        List<ProcessedAnnotation> addedStereotypes = getAddedStereotypes(context, processedAnnotation.annotationType);
        if (!addedStereotypes.isEmpty()) {
            stereotypes = CollectionUtils.concat(stereotypes, addedStereotypes);
        }
        List<ProcessedAnnotation> finalStereotypes = stereotypes;
        return processedAnnotation.mutateAnnotationValue(builder ->
            builder.replaceStereotypes(finalStereotypes.stream().<AnnotationValue<?>>map(ProcessedAnnotation::getAnnotationValue).toList())
        );
    }

    private ProcessedAnnotation addDefaults(ProcessedAnnotation processedAnnotation) {
        if (processedAnnotation.getAnnotationValue().getDefaultValues() != null) {
            return processedAnnotation;
        }
        if (processedAnnotation.annotationType != null) {
            Map<CharSequence, Object> annotationDefaults = getCachedAnnotationDefaults(
                    processedAnnotation.getAnnotationValue().getAnnotationName(),
                    processedAnnotation.annotationType
            );
            processedAnnotation = processedAnnotation.mutateAnnotationValue(builder -> builder.defaultValues(annotationDefaults));
        } else {
            processedAnnotation = processedAnnotation.mutateAnnotationValue(builder -> builder.defaultValues(Collections.emptyMap()));
        }
        return processedAnnotation;
    }

    @NonNull
    private List<ProcessedAnnotation> extractStereotypes(@NonNull ProcessingContext context,
                                                         @NonNull ProcessedAnnotation processedAnnotation) {
        AnnotationValue<?> annotationValue = processedAnnotation.getAnnotationValue();
        ProcessingContext newContext = context.withParent(processedAnnotation.annotationValue);

        if (processedAnnotation.annotationType == null) {
            // The annotation is not on the classpath
            // We set an empty collection to mark that stereotypes are processed
            return Collections.emptyList();
        }

        List<? extends A> nativeStereotypes = getAnnotationsForType(processedAnnotation.annotationType);
        if (nativeStereotypes.isEmpty()) {
            // We set an empty collection to mark that stereotypes are processed
            return Collections.emptyList();
        }
        String annotationName = annotationValue.getAnnotationName();
        String packageName = NameUtils.getPackageName(annotationName);
        boolean excludesStereotypes = AnnotationUtil.STEREOTYPE_EXCLUDES.contains(packageName) || annotationName.endsWith(".Nullable");
        return annotationMirrorToAnnotationValue(nativeStereotypes.stream(), processedAnnotation.annotationType)
                .filter(stereotypeAnnotation -> {
                    AnnotationValue<?> stereotypeAnnotationValue = stereotypeAnnotation.getAnnotationValue();
                    String stereotypeName = stereotypeAnnotationValue.getAnnotationName();
                    if (stereotypeName.equals(AnnotationUtil.ANN_INHERITED)) {
                        return true;
                    }
                    if (excludesStereotypes) {
                        return false;
                    }
                    // special case: don't add stereotype for @Nonnull when it's marked as UNKNOWN/MAYBE/NEVER.
                    // https://github.com/micronaut-projects/micronaut-core/issues/6795
                    if (stereotypeName.equals("jakarta.annotation.Nonnull")) {
                        String when = Objects.toString(stereotypeAnnotationValue.getValues().get("when"));
                        return !(when.equals("UNKNOWN") || when.equals("MAYBE") || when.equals("NEVER"));
                    }
                    return true;
                }).flatMap(stereotype -> processAnnotation(newContext, stereotype)).toList();
    }

    @NonNull
    private Stream<ProcessedAnnotation> transform(@NonNull ProcessingContext context,
                                                  @NonNull ProcessedAnnotation toTransform) {
        // Transform annotation using:
        // - io.micronaut.inject.annotation.AnnotationMapper
        // - io.micronaut.inject.annotation.AnnotationRemapper
        // - io.micronaut.inject.annotation.AnnotationTransformer
        // Each result of the transformation will be also transformed
        return processAnnotationMappers(context, toTransform)
                .flatMap(annotation -> processAnnotationRemappers(context, annotation))
                .flatMap(annotation -> processAnnotationTransformers(context, annotation));
    }

    @NonNull
    private Stream<ProcessedAnnotation> flattenRepeatable(@NonNull ProcessedAnnotation processedAnnotation) {
        // In a case of a repeatable container process it as a stream of repeatable annotation values
        AnnotationValue<?> annotationValue = processedAnnotation.getAnnotationValue();
        if (isRepeatableAnnotationContainer(annotationValue)) {
            // Repeatable annotations container is being added with values
            // We will add every repeatable annotation separately to properly detect its container and run transformations
            Map<CharSequence, Object> containerValues = new LinkedHashMap<>(annotationValue.getValues());
            containerValues.remove(AnnotationMetadata.VALUE_MEMBER);
            return Stream.concat(
                    Stream.of(
                            // Add repeatable container for possible stereotype annotation retrieval
                            // and additional members defined in the container annotation
                            toProcessedAnnotation(new AnnotationValue<>(
                                    annotationValue.getAnnotationName(),
                                    containerValues,
                                    getRetentionPolicy(annotationValue.getAnnotationName())))
                    ),
                    annotationValue.getAnnotations(AnnotationMetadata.VALUE_MEMBER).stream().map(this::toProcessedAnnotation)
            );
        }
        return Stream.of(processedAnnotation);
    }

    /**
     * @param annotationValue The annotation value
     * @return true if the annotation is a repeatable container
     */
    protected boolean isRepeatableAnnotationContainer(AnnotationValue<?> annotationValue) {
        List<AnnotationValue<Annotation>> repeatableAnnotations = annotationValue.getAnnotations(AnnotationMetadata.VALUE_MEMBER);
        if (repeatableAnnotations.isEmpty()) {
            return false;
        }
        String repeatableAnnotationName = null;
        for (AnnotationValue<Annotation> repeatableAnnotation : repeatableAnnotations) {
            if (repeatableAnnotationName == null) {
                repeatableAnnotationName = repeatableAnnotation.getAnnotationName();
            } else if (!repeatableAnnotationName.equals(repeatableAnnotation.getAnnotationName())) {
                // Unexpected state: different repeatable name
                return false;
            }
        }
        // The container should be this annotation value name
        return findRepeatableContainerNameForType(repeatableAnnotationName) != null;
    }

    @NonNull
    private ProcessedAnnotation processAliases(@NonNull ProcessedAnnotation processedAnnotation,
                                               @NonNull List<ProcessedAnnotation> introducedAnnotations) {
        T annotationType = processedAnnotation.getAnnotationType();
        if (annotationType == null) {
            return processedAnnotation;
        }
        AnnotationValue<?> annotationValue = processedAnnotation.getAnnotationValue();
        Map<CharSequence, Object> newValues = new LinkedHashMap<>(annotationValue.getValues());
        for (Map.Entry<CharSequence, Object> entry : annotationValue.getValues().entrySet()) {
            CharSequence key = entry.getKey();
            Object value = entry.getValue();
            T member = getAnnotationMember(annotationType, key);
            if (member != null) {
                handleAnnotationAlias(
                        annotationType,
                        newValues,
                        member,
                        value,
                        introducedAnnotations
                );
            }
        }

        // @AliasFor can modify the annotation values by aliasing to a member from the same annotation
        if (newValues.equals(annotationValue.getValues())) {
            return processedAnnotation;
        }
        return processedAnnotation.mutateAnnotationValue(builder -> builder.members(newValues));
    }

    private void addAnnotation(@NonNull MutableAnnotationMetadata mutableAnnotationMetadata,
                               @NonNull List<String> parentAnnotations,
                               boolean isDeclared,
                               boolean isStereotype,
                               @NonNull AnnotationValue<?> annotationValue,
                               @NonNull Map<String, String> repeatableToContainer) {

        String annotationName = annotationValue.getAnnotationName();
        Map<CharSequence, Object> annotationDefaults = annotationValue.getDefaultValues();
        if (annotationDefaults != null) {
            mutableAnnotationMetadata.addDefaultAnnotationValues(annotationName, annotationDefaults, annotationValue.getRetentionPolicy());
        } else {
            throw new IllegalStateException("Annotation should contain default values and an empty list " + annotationValue.getAnnotationName());
        }

        String repeatableContainer = repeatableToContainer.get(annotationName);
        if (repeatableContainer == null) {
            repeatableContainer = AnnotationMetadataSupport.getCoreRepeatableAnnotationsContainers().get(annotationName);
        }
        if (repeatableContainer == null) {
            repeatableContainer = findRepeatableContainerNameForType(annotationName);
        }
        if (isStereotype) {
            if (repeatableContainer != null) {
                if (isDeclared) {
                    mutableAnnotationMetadata.addDeclaredRepeatableStereotype(
                            parentAnnotations,
                            repeatableContainer,
                            annotationValue
                    );
                } else {
                    mutableAnnotationMetadata.addRepeatableStereotype(
                            parentAnnotations,
                            repeatableContainer,
                            annotationValue
                    );
                }
            } else {
                if (isDeclared) {
                    mutableAnnotationMetadata.addDeclaredStereotype(
                            parentAnnotations,
                            annotationValue.getAnnotationName(),
                            annotationValue.getValues(),
                            annotationValue.getRetentionPolicy()
                    );
                } else {
                    mutableAnnotationMetadata.addStereotype(
                            parentAnnotations,
                            annotationValue.getAnnotationName(),
                            annotationValue.getValues(),
                            annotationValue.getRetentionPolicy()
                        );
                }
            }
        } else {
            if (repeatableContainer != null) {
                if (isDeclared) {
                    mutableAnnotationMetadata.addDeclaredRepeatable(repeatableContainer, annotationValue);
                } else {
                    mutableAnnotationMetadata.addRepeatable(repeatableContainer, annotationValue);
                }
            } else {
                if (isDeclared) {
                    mutableAnnotationMetadata.addDeclaredAnnotation(
                            annotationValue.getAnnotationName(),
                            annotationValue.getValues(),
                            annotationValue.getRetentionPolicy()
                    );
                } else {
                    mutableAnnotationMetadata.addAnnotation(
                            annotationValue.getAnnotationName(),
                            annotationValue.getValues(),
                            annotationValue.getRetentionPolicy()
                    );
                }
            }
        }
    }

    /**
     * Find the repeatable container for given annotation type.
     * @param annotationName The repeatable annotation
     * @return The repeatable container if exists
     */
    @Nullable
    protected String findRepeatableContainerNameForType(@NonNull String annotationName) {
        T annotationMirror = getAnnotationMirror(annotationName).orElse(null);
        return annotationMirror != null ? getRepeatableContainerNameForType(annotationMirror) : null;
    }

    /**
     * Is the given annotation excluded for the specified element.
     *
     * @param element        The element
     * @param annotationName The annotation name
     * @return True if it is excluded
     */
    protected boolean isExcludedAnnotation(@NonNull T element, @NonNull String annotationName) {
        return AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationName);
    }

    @NonNull
    private List<ProcessedAnnotation> getAddedStereotypes(@NonNull ProcessingContext context,
                                                          @Nullable T element) {
        if (element == null) {
            return List.of();
        }
        CachedAnnotationMetadata modifiedStereotypes = MUTATED_ANNOTATION_METADATA.get(element);
        if (modifiedStereotypes == null || modifiedStereotypes.isEmpty() || !modifiedStereotypes.isMutated()) {
            return List.of();
        }
        return Stream.concat(
                modifiedStereotypes.getStereotypeAnnotationNames().stream().flatMap(stereotypeName -> {
                    final AnnotationValue<Annotation> a = modifiedStereotypes.getAnnotation(stereotypeName);
                    if (a == null) {
                        return Stream.of();
                    }
                    AnnotationValue<?> parent = null;
                    final List<String> stereotypeParents = modifiedStereotypes.getAnnotationNamesByStereotype(stereotypeName);
                    for (String stereotype : stereotypeParents) {
                        AnnotationValue<Annotation> annotationValue = AnnotationValue.builder(stereotype).build();
                        if (parent == null) {
                            parent = annotationValue;
                        } else {
                            parent = parent.mutate().stereotype(annotationValue).build();
                        }
                    }
                    if (parent == null) {
                        return processAnnotation(
                                context.withParents(stereotypeParents),
                                toProcessedAnnotation(a)
                        );
                    } else {
                        return processAnnotation(
                                context.withParents(stereotypeParents),
                                toProcessedAnnotation(parent.mutate().stereotype(a).build())
                        );
                    }

                }),
                modifiedStereotypes.getAnnotationNames().stream().flatMap(annotationName -> {
                    AnnotationValue<Annotation> a = modifiedStereotypes.getAnnotation(annotationName);
                    if (a == null) {
                        return Stream.empty();
                    }
                    return processAnnotation(
                            context,
                            toProcessedAnnotation(a)
                    );
                })
        ).toList();
    }

    @NonNull
    private <K> List<K> eliminateProcessed(@NonNull ProcessingContext context, @NonNull List<K> visitors) {
        if (visitors == null) {
            return Collections.emptyList();
        }
        return visitors.stream().filter(v -> !context.processedVisitors.contains(v.getClass())).toList();
    }

    @NonNull
    private Stream<ProcessedAnnotation> processAnnotationRemappers(@NonNull ProcessingContext context,
                                                                   @NonNull ProcessedAnnotation processedAnnotation) {
        AnnotationValue<?> annotationValue = processedAnnotation.getAnnotationValue();
        String packageName = NameUtils.getPackageName(annotationValue.getAnnotationName());
        List<AnnotationRemapper> annotationRemappers = ANNOTATION_REMAPPERS.get(packageName);
        if (annotationRemappers == null) {
            annotationRemappers = ALL_ANNOTATION_REMAPPERS;
        } else {
            annotationRemappers = CollectionUtils.concat(annotationRemappers, ALL_ANNOTATION_REMAPPERS);
        }
        annotationRemappers = eliminateProcessed(context, annotationRemappers);
        return remapAnnotation(
                context,
                processedAnnotation,
                annotationValue,
                annotationRemappers.iterator()
        );
    }

    @NonNull
    private Stream<ProcessedAnnotation> remapAnnotation(@NonNull ProcessingContext context,
                                                        @NonNull ProcessedAnnotation processedAnnotation,
                                                        @NonNull AnnotationValue<?> annotationValue,
                                                        @NonNull Iterator<AnnotationRemapper> remappers) {
        if (!remappers.hasNext()) {
            return Stream.of(processedAnnotation);
        }
        AnnotationRemapper annotationRemapper = remappers.next();
        ProcessingContext newContext = context.withProcessedVisitor(annotationRemapper.getClass());
        List<AnnotationValue<?>> annotationValues = annotationRemapper.remap(annotationValue, context.visitorContext);
        return annotationValues.stream().flatMap(newAnnotationValue -> {
            if (newAnnotationValue == annotationValue) {
                // Value didn't change, continue with other remappers
                return remapAnnotation(newContext, processedAnnotation, annotationValue, remappers);
            }
            if (annotationValue.getAnnotationName().equals(newAnnotationValue.getAnnotationName())) {
                // Retain the same value native element
                return processAnnotation(newContext, processedAnnotation.withAnnotationValue(newAnnotationValue));
            }
            return processAnnotation(newContext, toProcessedAnnotation(newAnnotationValue));
        });
    }

    private <K extends Annotation> Stream<ProcessedAnnotation> processAnnotationTransformers(@NonNull ProcessingContext context,
                                                                                             @NonNull ProcessedAnnotation processedAnnotation) {
        AnnotationValue<K> annotationValue = (AnnotationValue<K>) processedAnnotation.getAnnotationValue();
        List<AnnotationTransformer<K>> annotationTransformers = getAnnotationTransformers(annotationValue.getAnnotationName());
        annotationTransformers = eliminateProcessed(context, annotationTransformers);
        if (CollectionUtils.isEmpty(annotationTransformers)) {
            return Stream.of(processedAnnotation);
        }
        Iterator<AnnotationTransformer<K>> transformers = annotationTransformers.iterator();
        return transformAnnotation(context, processedAnnotation, annotationValue, transformers);
    }

    @NonNull
    private <K extends Annotation> Stream<ProcessedAnnotation> transformAnnotation(@NonNull ProcessingContext context,
                                                                                   @NonNull ProcessedAnnotation processedAnnotation,
                                                                                   @NonNull AnnotationValue<K> annotationValue,
                                                                                   @NonNull Iterator<AnnotationTransformer<K>> transformers) {
        if (!transformers.hasNext()) {
            return Stream.of(processedAnnotation);
        }
        AnnotationTransformer<K> annotationTransformer = transformers.next();
        ProcessingContext newContext = context.withProcessedVisitor(annotationTransformer.getClass());
        List<AnnotationValue<?>> transform = annotationTransformer.transform(annotationValue, context.visitorContext);
        return transform.stream().flatMap(newAnnotationValue -> {
            if (newAnnotationValue == annotationValue) {
                // Value didn't change, continue with other transformers
                return transformAnnotation(newContext, processedAnnotation, annotationValue, transformers);
            }
            if (annotationValue.getAnnotationName().equals(newAnnotationValue.getAnnotationName())) {
                // Retain the same value native element
                return processAnnotation(newContext, processedAnnotation.withAnnotationValue(newAnnotationValue));
            }
            return processAnnotation(newContext, toProcessedAnnotation(newAnnotationValue));
        });
    }

    @NonNull
    private <K extends Annotation> Stream<ProcessedAnnotation> processAnnotationMappers(@NonNull ProcessingContext context,
                                                                                        @NonNull ProcessedAnnotation processedAnnotation) {
        AnnotationValue<K> annotationValue = (AnnotationValue<K>) processedAnnotation.getAnnotationValue();
        List<AnnotationMapper<K>> mappers = getAnnotationMappers(annotationValue.getAnnotationName());
        mappers = eliminateProcessed(context, mappers);
        if (CollectionUtils.isEmpty(mappers)) {
            return Stream.of(processedAnnotation);
        }
        return mappers.stream().flatMap(mapper -> {
            Stream<ProcessedAnnotation> mappedAnnotationsStream;
            ProcessingContext newContext = context.withProcessedVisitor(mapper.getClass());
            List<AnnotationValue<?>> mappedToAnnotationValues = mapper.map(annotationValue, context.visitorContext);
            if (mappedToAnnotationValues == null) {
                mappedAnnotationsStream = Stream.empty();
            } else {
                mappedAnnotationsStream = mappedToAnnotationValues
                        .stream()
                        .filter(newAnnotationValue -> newAnnotationValue != annotationValue)
                        .flatMap(newAnnotationValue -> processAnnotation(newContext, toProcessedAnnotation(newAnnotationValue)));
            }
            return Stream.concat(
                    Stream.of(processedAnnotation), // Mapper retains the original value
                    mappedAnnotationsStream
            );
        });
    }

    @NonNull
    private ProcessedAnnotation toProcessedAnnotation(@NonNull AnnotationValue<?> av) {
        return new ProcessedAnnotation(
                getAnnotationMirror(av.getAnnotationName()).orElse(null),
                av
        );
    }


    /**
     * Used to clear mutated metadata at the end of a compilation cycle.
     */
    @Internal
    public static void clearMutated() {
        MUTATED_ANNOTATION_METADATA.clear();
    }

    /**
     * Used to clear mutated metadata at the end of a compilation cycle.
     */
    @Internal
    public static void clearMutated(@NonNull Object key) {
        MUTATED_ANNOTATION_METADATA.remove(key);
    }

    /**
     * Used to clear caches at the end of a compilation cycle.
     */
    @Internal
    public static void clearCaches() {
        ANNOTATION_DEFAULTS.clear();
    }

    /**
     * This is used for testing scenarios only where annotation metadata
     * is created without bean creation. It is needed because at compile time
     * there are no defaults added via DefaultAnnotationMetadata.
     */
    @Internal
    public static void copyToRuntime() {
        ANNOTATION_DEFAULTS.forEach(DefaultAnnotationMetadata::registerAnnotationDefaults);
    }

    /**
     * @return Additional mapped annotation names
     */
    @Internal
    public static Set<String> getMappedAnnotationNames() {
        return CollectionUtils.concat(ANNOTATION_MAPPERS.keySet(), ANNOTATION_TRANSFORMERS.keySet());
    }

    /**
     * @return Additional mapped annotation names
     */
    @Internal
    public static Set<String> getMappedAnnotationPackages() {
        return ANNOTATION_REMAPPERS.keySet();
    }

    /**
     * Annotate an existing annotation metadata object.
     *
     * @param annotationMetadata The annotation metadata
     * @param annotationValue    The annotation value
     * @param <A2>               The annotation type
     * @return The mutated metadata
     */
    @NonNull
    public <A2 extends Annotation> AnnotationMetadata annotate(@NonNull AnnotationMetadata annotationMetadata,
                                                               @NonNull AnnotationValue<A2> annotationValue) {
        return modify(annotationMetadata, metadata -> {
            addAnnotations(
                    metadata,
                    Stream.of(toProcessedAnnotation(annotationValue)).map(this::addDefaults),
                    true,
                    false
            );
        });
    }

    /**
     * Removes an annotation from the given annotation metadata.
     *
     * @param annotationMetadata The annotation metadata
     * @param annotationType     The annotation type
     * @return The updated metadata
     * @since 3.0.0
     */
    @NonNull
    public AnnotationMetadata removeAnnotation(@NonNull AnnotationMetadata annotationMetadata,
                                               @NonNull String annotationType) {
        return modify(annotationMetadata, metadata -> {
            T annotationMirror = getAnnotationMirror(annotationType).orElse(null);
            if (annotationMirror != null) {
                String repeatableName = getRepeatableContainerNameForType(annotationMirror);
                if (repeatableName != null) {
                    metadata.removeAnnotation(repeatableName);
                } else {
                    metadata.removeAnnotation(annotationType);
                }
            } else {
                metadata.removeAnnotation(annotationType);
            }
        });
    }

    /**
     * Removes an annotation from the given annotation metadata.
     *
     * @param annotationMetadata The annotation metadata
     * @param annotationType     The annotation type
     * @return The updated metadata
     * @since 3.0.0
     */
    @NonNull
    public AnnotationMetadata removeStereotype(@NonNull AnnotationMetadata annotationMetadata,
                                               @NonNull String annotationType) {
        return modify(annotationMetadata, metadata -> {
            T annotationMirror = getAnnotationMirror(annotationType).orElse(null);
            if (annotationMirror != null) {
                String repeatableName = getRepeatableContainerNameForType(annotationMirror);
                if (repeatableName != null) {
                    metadata.removeStereotype(repeatableName);
                } else {
                    metadata.removeStereotype(annotationType);
                }
            } else {
                metadata.removeStereotype(annotationType);
            }
        });
    }

    /**
     * Removes an annotation from the metadata for the given predicate.
     *
     * @param annotationMetadata The annotation metadata
     * @param predicate          The predicate
     * @param <T1>               The annotation type
     * @return The potentially modified metadata
     */
    @NonNull
    public <T1 extends Annotation> AnnotationMetadata removeAnnotationIf(@NonNull AnnotationMetadata annotationMetadata,
                                                                         @NonNull Predicate<AnnotationValue<T1>> predicate) {
        return modify(annotationMetadata, metadata -> metadata.removeAnnotationIf(predicate));
    }

    private AnnotationMetadata modify(AnnotationMetadata annotationMetadata, Consumer<MutableAnnotationMetadata> consumer) {
        final boolean isHierarchy = annotationMetadata instanceof AnnotationMetadataHierarchy;
        AnnotationMetadata declaredMetadata = annotationMetadata;
        if (isHierarchy) {
            declaredMetadata = annotationMetadata.getDeclaredMetadata();
        }
        MutableAnnotationMetadata mutableAnnotationMetadata;
        if (declaredMetadata == AnnotationMetadata.EMPTY_METADATA) {
            mutableAnnotationMetadata = new MutableAnnotationMetadata();
        } else if (declaredMetadata instanceof MutableAnnotationMetadata mutable) {
            mutableAnnotationMetadata = mutable;
        } else if (declaredMetadata instanceof DefaultAnnotationMetadata) {
            mutableAnnotationMetadata = MutableAnnotationMetadata.of(declaredMetadata);
        } else {
            throw new IllegalStateException("Unrecognized annotation metadata: " + annotationMetadata);
        }
        consumer.accept(mutableAnnotationMetadata);
        if (isHierarchy) {
            return ((AnnotationMetadataHierarchy) annotationMetadata).createSibling(mutableAnnotationMetadata);
        }
        return mutableAnnotationMetadata;
    }

    /**
     * The context of the annotation processing.
     *
     * @param visitorContext    The visitor context
     * @param parentAnnotations The parent annotations
     * @param processedVisitors The processed visitors
     * @param repeatableToContainer The repeatable to container
     * @since 4.0.0
     */
    private record ProcessingContext(@NonNull VisitorContext visitorContext,
                                     @NonNull Set<String> parentAnnotations,
                                     @NonNull Set<Class<?>> processedVisitors,
                                     @NonNull Map<String, String> repeatableToContainer) {

        ProcessingContext(@NonNull VisitorContext visitorContext) {
            this(visitorContext, Collections.emptySet(), Collections.emptySet(), new HashMap<>());
        }

        boolean isProcessed(@NonNull AnnotationValue<?> annotationValue) {
            return parentAnnotations.contains(annotationValue.getAnnotationName());
        }

        @NonNull
        ProcessingContext withParent(@NonNull AnnotationValue<?> parent) {
            Set<String> parents = CollectionUtils.concat(parentAnnotations, parent.getAnnotationName());
            return new ProcessingContext(visitorContext, Collections.unmodifiableSet(parents), processedVisitors, repeatableToContainer);
        }

        @NonNull
        ProcessingContext withParents(@NonNull List<String> newParents) {
            Set<String> parents = CollectionUtils.concat(parentAnnotations, newParents);
            return new ProcessingContext(visitorContext, Collections.unmodifiableSet(parents), processedVisitors, repeatableToContainer);
        }

        @NonNull
        public ProcessingContext withProcessedVisitor(@NonNull Class<?> processedVisitor) {
            Set<Class<?>> visitors = CollectionUtils.concat(processedVisitors, processedVisitor);
            return new ProcessingContext(visitorContext, parentAnnotations, Collections.unmodifiableSet(visitors), repeatableToContainer);
        }
    }

    /**
     * Simple tuple object combining the annotation value plus the native annotation type.
     * NOTE: Some implementation like Groovy don't return correct annotation native type with type hierarchies.
     * We need to carry the provided type.
     *
     * @since 4.0.0
     */
    private final class ProcessedAnnotation {
        @Nullable
        private final T annotationType;
        private final AnnotationValue<?> annotationValue;

        private ProcessedAnnotation(@Nullable T annotationType,
                                    AnnotationValue<?> annotationValue) {
            this.annotationType = annotationType;
            this.annotationValue = annotationValue;
        }

        public ProcessedAnnotation withAnnotationValue(AnnotationValue<?> annotationValue) {
            return new ProcessedAnnotation(annotationType, annotationValue);
        }

        public ProcessedAnnotation withAnnotationType(T annotationType) {
            return new ProcessedAnnotation(annotationType, annotationValue);
        }

        public ProcessedAnnotation mutateAnnotationValue(Function<AnnotationValueBuilder<?>, AnnotationValueBuilder<?>> fn) {
            return new ProcessedAnnotation(annotationType, fn.apply(annotationValue.mutate()).build());
        }

        @Nullable
        public T getAnnotationType() {
            return annotationType;
        }

        public AnnotationValue<?> getAnnotationValue() {
            return annotationValue;
        }

    }

    /**
     * The caching entry.
     *
     * @author Denis Stepanov
     * @since 4.0.0
     */
    public interface CachedAnnotationMetadata extends AnnotationMetadataDelegate {

        /**
         * @return annotation metadata in the cache or empty
         */
        @NonNull
        @Override
        AnnotationMetadata getAnnotationMetadata();

        /**
         * @return Is mutated?
         */
        boolean isMutated();

        /**
         * Modify the annotation metadata in the cache.
         *
         * @param annotationMetadata new value
         */
        void update(@NonNull AnnotationMetadata annotationMetadata);

    }

    /**
     * Key used to reference mutated metadata.
     *
     * @param e1  The element 1
     * @param e2  The element 2
     * @param <T> the element type
     */
    @Internal
    private record Key2<T>(T e1, T e2) {
    }

    /**
     * Key used to reference mutated metadata.
     *
     * @param e1  The element 1
     * @param e2  The element 2
     * @param e3  The element 3
     * @param <T> the element type
     */
    @Internal
    private record Key3<T>(T e1, T e2, T e3) {
    }

    private static final class DefaultCachedAnnotationMetadata implements CachedAnnotationMetadata {
        @Nullable
        private AnnotationMetadata annotationMetadata;
        private boolean isMutated;

        public DefaultCachedAnnotationMetadata(AnnotationMetadata annotationMetadata) {
            if (annotationMetadata instanceof AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata) {
                throw new IllegalStateException();
            }
            this.annotationMetadata = annotationMetadata;
        }

        @Override
        public boolean isMutated() {
            return isMutated;
        }

        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            if (annotationMetadata == null || annotationMetadata.isEmpty()) {
                return AnnotationMetadata.EMPTY_METADATA;
            }
            return annotationMetadata;
        }

        @Override
        public void update(AnnotationMetadata annotationMetadata) {
            if (annotationMetadata instanceof AbstractAnnotationMetadataBuilder.CachedAnnotationMetadata) {
                throw new IllegalStateException();
            }
            this.annotationMetadata = annotationMetadata;
            isMutated = true;
        }
    }

}
