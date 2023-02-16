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
import io.micronaut.context.annotation.Type;
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
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.inject.Qualifier;
import org.jetbrains.annotations.NotNull;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.Stream;

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
    private static final Map<String, String> DEPRECATED_ANNOTATION_NAMES = Collections.emptyMap();
    private static final Map<String, List<AnnotationMapper<?>>> ANNOTATION_MAPPERS = new HashMap<>(10);
    private static final Map<String, List<AnnotationTransformer<?>>> ANNOTATION_TRANSFORMERS = new HashMap<>(5);
    private static final Map<String, List<AnnotationRemapper>> ANNOTATION_REMAPPERS = new HashMap<>(5);
    private static final Map<Object, CachedAnnotationMetadata> MUTATED_ANNOTATION_METADATA = new HashMap<>(100);
    private static final Map<String, Set<String>> NON_BINDING_CACHE = new HashMap<>(50);
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
                if (StringUtils.isNotEmpty(name)) {
                    ANNOTATION_REMAPPERS.computeIfAbsent(name, s -> new ArrayList<>(2)).add(mapper);
                }
            } catch (Throwable e) {
                // mapper, missing dependencies, continue
            }
        }
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
     * Build only metadata for declared annotations.
     *
     * @param element                The element
     * @param annotations            The annotations
     * @param includeTypeAnnotations Whether to include type level annotations in the metadata for the element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata buildDeclared(T element, List<? extends A> annotations, boolean includeTypeAnnotations) {
        if (CollectionUtils.isEmpty(annotations)) {
            return AnnotationMetadata.EMPTY_METADATA;
        }
        MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
        if (includeTypeAnnotations) {
            buildInternalMulti(
                Collections.emptyList(),
                element,
                annotationMetadata, false, true
            );
        }
        try {
            addAnnotations(annotationMetadata, element, false, true,
                annotations, Collections.emptyList());
            if (annotationMetadata.isEmpty()) {
                return AnnotationMetadata.EMPTY_METADATA;
            }
            return annotationMetadata;
        } catch (RuntimeException e) {
            return metadataForError(e);
        }
    }

    /**
     * Build the meta data for the given element. If the element is a method the class metadata will be included.
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
     * Build the meta data for the given element.
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
        return lookupOrBuild(key, element, false);
    }

    /**
     * Lookup or build new annotation metadata.
     *
     * @param key                    The cache key
     * @param element                The type element
     * @param includeTypeAnnotations Whether to include type level annotations in the metadata for the element
     * @return The annotation metadata
     * @since 4.0.0
     */
    public CachedAnnotationMetadata lookupOrBuild(Object key, T element, boolean includeTypeAnnotations) {
        CachedAnnotationMetadata cachedAnnotationMetadata = MUTATED_ANNOTATION_METADATA.get(key);
        if (cachedAnnotationMetadata == null) {
            AnnotationMetadata annotationMetadata = buildInternal(includeTypeAnnotations, false, element);
            cachedAnnotationMetadata = new DefaultCachedAnnotationMetadata(annotationMetadata);
            // Don't use `computeIfAbsent` as it can lead to a concurrent exception because the cache is accessed during in `buildInternal`
            MUTATED_ANNOTATION_METADATA.put(key, cachedAnnotationMetadata);
        }
        return cachedAnnotationMetadata;
    }

    private AnnotationMetadata buildInternal(boolean inheritTypeAnnotations, boolean declaredOnly, T element) {
        MutableAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
        try {
            return buildInternalMulti(
                Collections.emptyList(),
                element,
                annotationMetadata, inheritTypeAnnotations, declaredOnly
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
        return null;
    }

    /**
     * Adds an error.
     *
     * @param originatingElement The originating element
     * @param error              The error
     */
    protected abstract void addError(@NonNull T originatingElement, @NonNull String error);

    /**
     * Adds an warning.
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
     * @param memberName         The member name
     * @param annotationValue    The value
     * @return The object
     */
    protected abstract Object readAnnotationValue(T originatingElement, T member, String memberName, Object annotationValue);

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
     * Obtain the name of the repeatable annotation if the annotation is is one.
     *
     * @param annotationMirror The annotation mirror
     * @return Return the name or null
     */
    @Nullable
    protected abstract String getRepeatableName(A annotationMirror);

    /**
     * Obtain the name of the repeatable annotation if the annotation is is one.
     *
     * @param annotationType The annotation mirror
     * @return Return the name or null
     */
    @Nullable
    protected abstract String getRepeatableNameForType(T annotationType);

    /**
     * @param annotationElement The annotation element
     * @param annotationType    The annotation type
     * @return The annotation value
     */
    protected AnnotationValue<?> readNestedAnnotationValue(T annotationElement, A annotationType) {
        final String annotationTypeName = getAnnotationTypeName(annotationType);
        Map<? extends T, ?> annotationValues = readAnnotationRawValues(annotationType);
        if (annotationValues.isEmpty()) {
            return new AnnotationValue<>(annotationTypeName, Collections.emptyMap());
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
        return new AnnotationValue<>(annotationTypeName, resolvedValues);
    }

    /**
     * Return a mirror for the given annotation.
     *
     * @param annotationName The annotation name
     * @return An optional mirror
     */
    protected abstract Optional<T> getAnnotationMirror(String annotationName);

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
     * Creates the visitor context for this implementation.
     *
     * @return The visitor context
     */
    protected abstract VisitorContext createVisitorContext();

    private Map<CharSequence, Object> getAnnotationDefaults(T originatingElement,
                                                            String annotationName,
                                                            Map<? extends T, ?> elementDefaultValues) {
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
                    defaultValues);
            }
        }
        return defaultValues;
    }

    @Nullable
    private void processAnnotationAlias(Map<CharSequence, Object> annotationValues,
                                        Object annotationValue,
                                        AnnotationValue<AliasFor> aliasForAnnotation,
                                        List<ProcessedAnnotation> introducedAnnotations) {
        // Filter out empty values for Groovy
        Optional<String> aliasAnnotation = aliasForAnnotation.stringValue("annotation").filter(v -> !v.equals(Annotation.class.getName()));
        Optional<String> aliasAnnotationName = aliasForAnnotation.stringValue("annotationName").filter(v -> !v.isEmpty());
        Optional<String> aliasMember = aliasForAnnotation.stringValue("member").filter(v -> !v.isEmpty());

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
                        introducedAnnotations.add(introducedAnnotations.indexOf(newAnnotation), newNewAnnotation);
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
                annotationHierarchy,
                Collections.emptyList()
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
                                boolean originatingElementIsSameParent,
                                boolean isDeclared,
                                List<? extends A> annotationHierarchy,
                                List<String> parentAnnotations) {
        Stream<? extends A> stream = annotationHierarchy.stream();
        Stream<ProcessedAnnotation> annotationValues = annotationMirrorToAnnotationValue(stream,
            element, originatingElementIsSameParent, annotationMetadata, isDeclared, false);
        addAnnotations(annotationMetadata, annotationValues, isDeclared, parentAnnotations);
    }

    @NotNull
    private Stream<ProcessedAnnotation> annotationMirrorToAnnotationValue(Stream<? extends A> stream,
                                                                          T element,
                                                                          boolean originatingElementIsSameParent,
                                                                          MutableAnnotationMetadata annotationMetadata,
                                                                          boolean isDeclared,
                                                                          boolean isStereotype) {
        return stream
            .filter(annotationMirror -> {
                String annotationName = getAnnotationTypeName(annotationMirror);
                if (AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationName)
                    || isExcludedAnnotation(element, annotationName)) {
                    return false;
                }
                if (DEPRECATED_ANNOTATION_NAMES.containsKey(annotationName)) {
                    addWarning(element,
                        "Usages of deprecated annotation " + annotationName + " found. You should use " + DEPRECATED_ANNOTATION_NAMES.get(
                            annotationName) + " instead.");
                }
                return isStereotype || isDeclared || isInheritedAnnotation(annotationMirror) || originatingElementIsSameParent;
            }).map(annotationMirror -> createAnnotationValue(element, annotationMirror, annotationMetadata));
    }

    private ProcessedAnnotation createAnnotationValue(T originatingElement,
                                                      A annotationMirror,
                                                      MutableAnnotationMetadata metadata) {
        String annotationName = getAnnotationTypeName(annotationMirror);
        final T annotationType = getTypeForAnnotation(annotationMirror);
        RetentionPolicy retentionPolicy = getRetentionPolicy(annotationType);

        Map<? extends T, ?> elementValues = readAnnotationRawValues(annotationMirror);
        Map<CharSequence, Object> annotationValues;
        if (CollectionUtils.isEmpty(elementValues)) {
            annotationValues = new LinkedHashMap<>(3);
        } else {
            annotationValues = new LinkedHashMap<>(5);
            Set<String> nonBindingMembers = new HashSet<>(2);
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
                        true, annotationsForMember, Collections.emptyList());

                    boolean isInstantiatedMember = memberMetadata.hasAnnotation(InstantiatedMember.class);

                    if (memberMetadata.hasAnnotation(NonBinding.class)) {
                        final String memberName = getElementName(member);
                        nonBindingMembers.add(memberName);
                    }
                    if (isInstantiatedMember) {
                        final String memberName = getAnnotationMemberName(member);
                        final Object rawValue = readAnnotationValue(originatingElement, member, memberName, annotationValue);
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
                if (hasAnnotation(annotationType, AnnotationUtil.QUALIFIER) || hasAnnotation(annotationType, Qualifier.class)) {
                    metadata.addDeclaredStereotype(
                        Collections.singletonList(getAnnotationTypeName(annotationMirror)),
                        AnnotationUtil.QUALIFIER,
                        Collections.singletonMap("nonBinding", nonBindingMembers)
                    );
                }
            }
        }

        Map<CharSequence, Object> defaultValues = getCachedAnnotationDefaults(annotationName, annotationType);

        return new ProcessedAnnotation(
            annotationType,
            new AnnotationValue<>(annotationName, annotationValues, defaultValues, retentionPolicy)
        );
    }

    @NotNull
    private Map<CharSequence, Object> getCachedAnnotationDefaults(String annotationName, T annotationType) {
        Map<CharSequence, Object> defaultValues;
        final Map<CharSequence, Object> defaults = ANNOTATION_DEFAULTS.get(annotationName);
        if (defaults != null) {
            defaultValues = new LinkedHashMap<>(defaults);
        } else {
            Map<? extends T, ?> annotationDefaultValues = readAnnotationDefaultValues(annotationName, annotationType);
            defaultValues = getAnnotationDefaults(annotationType, annotationName, annotationDefaultValues);
            if (defaultValues != null) {
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

    private void addAnnotations(MutableAnnotationMetadata annotationMetadata,
                                Stream<ProcessedAnnotation> stream,
                                boolean isDeclared,
                                List<String> parentAnnotations) {
        stream = filterAndTransformAnnotations(stream, parentAnnotations);

        List<ProcessedAnnotation> introducedAliasForAnnotations = new ArrayList<>();

        stream = stream.map(processedAnnotation -> processAliases(processedAnnotation, introducedAliasForAnnotations));

        List<ProcessedAnnotation> processedAnnotations = addAnnotations(stream, annotationMetadata, isDeclared, false, parentAnnotations).toList();

        if (CollectionUtils.isNotEmpty(introducedAliasForAnnotations)) {
            // Add annotation created by @AliasFor
            addStereotypeAnnotations(
                introducedAliasForAnnotations.stream(),
                null,
                parentAnnotations,
                annotationMetadata,
                isDeclared
            );
        }

        // After annotations are processes process their stereotypes
        for (ProcessedAnnotation processedAnnotation : processedAnnotations) {
            processStereotypes(annotationMetadata, isDeclared, parentAnnotations, processedAnnotation);
        }
    }

    private Stream<ProcessedAnnotation> processInterceptors(Stream<ProcessedAnnotation> annotationValues,
                                                            MutableAnnotationMetadata annotationMetadata,
                                                            String lastParent,
                                                            LinkedList<AnnotationValueBuilder<?>> interceptorBindings) {

        return annotationValues
            .map(processedAnnotation -> {
                AnnotationValue<?> annotationValue = processedAnnotation.getAnnotationValue();
                String annotationName = annotationValue.getAnnotationName();

                addToInterceptorBindingsIfNecessary(interceptorBindings, lastParent, annotationName);

                final boolean hasInterceptorBinding = !interceptorBindings.isEmpty();
                if (hasInterceptorBinding && AnnotationUtil.ANN_INTERCEPTOR_BINDING.equals(annotationName)) {
                    annotationValue = handleMemberBinding(annotationMetadata, lastParent, annotationValue);
                    interceptorBindings.getLast().members(annotationValue.getValues());
                    return processedAnnotation.withAnnotationValue(annotationValue);
                }
                if (hasInterceptorBinding && Type.class.getName().equals(annotationName)) {
                    final Object o = annotationValue.getValues().get(AnnotationMetadata.VALUE_MEMBER);
                    AnnotationClassValue<?> interceptorType = null;
                    if (o instanceof AnnotationClassValue<?> annotationClassValue) {
                        interceptorType = annotationClassValue;
                    } else if (o instanceof AnnotationClassValue<?>[] annotationClassValues) {
                        if (annotationClassValues.length > 0) {
                            interceptorType = annotationClassValues[0];
                        }
                    }
                    if (interceptorType != null) {
                        for (AnnotationValueBuilder<?> interceptorBinding : interceptorBindings) {
                            interceptorBinding.member("interceptorType", interceptorType);
                        }
                    }
                }
                return processedAnnotation;
            });
    }

    private Stream<ProcessedAnnotation> addAnnotations(Stream<ProcessedAnnotation> annotationValues,
                                                       MutableAnnotationMetadata annotationMetadata,
                                                       boolean isDeclared,
                                                       boolean isStereotype,
                                                       List<String> parentAnnotations) {
        return annotationValues
            .peek(processedAnnotation -> {
                addAnnotationDefaults(annotationMetadata, processedAnnotation);
                addAnnotation(annotationMetadata, parentAnnotations, isDeclared, isStereotype, processedAnnotation);
            });
    }

    private Stream<ProcessedAnnotation> filterAndTransformAnnotations(Stream<ProcessedAnnotation> annotationValues, List<String> parentAnnotations) {
        return annotationValues
            .filter(processedAnnotation -> {
                AnnotationValue<?> annotationValue = processedAnnotation.getAnnotationValue();
                return !AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationValue.getAnnotationName())
                    && !parentAnnotations.contains(annotationValue.getAnnotationName());
            })
            .flatMap(this::transform)
            .flatMap(this::flattenRepeatable);
    }

    private Stream<ProcessedAnnotation> transform(ProcessedAnnotation toTransform) {
        // Transform annotation using:
        // - io.micronaut.inject.annotation.AnnotationMapper
        // - io.micronaut.inject.annotation.AnnotationRemapper
        // - io.micronaut.inject.annotation.AnnotationTransformer
        // Each result of the transformation will be also transformed
        // To eliminate infinity loops "processedVisitors" will track and eliminate processed mappers/transformers
        Set<Class<?>> processedVisitors = new HashSet<>();
        return transform(toTransform, processedVisitors);
    }

    private Stream<ProcessedAnnotation> transform(ProcessedAnnotation toTransform, Set<Class<?>> processedVisitors) {
        return processAnnotationMappers(toTransform, processedVisitors)
            .flatMap(annotation -> processAnnotationRemappers(annotation, processedVisitors))
            .flatMap(annotation -> processAnnotationTransformers(annotation, processedVisitors));
    }

    private Stream<ProcessedAnnotation> flattenRepeatable(ProcessedAnnotation processedAnnotation) {
        // In a case of a repeatable container process it as a stream of repeatable annotation values
        AnnotationValue<?> annotationValue = processedAnnotation.getAnnotationValue();
        List<AnnotationValue<Annotation>> repeatableAnnotations = annotationValue.getAnnotations(AnnotationMetadata.VALUE_MEMBER);
        boolean isRepeatableAnnotationContainer = !repeatableAnnotations.isEmpty() && repeatableAnnotations.stream()
            .allMatch(value -> {
                T annotationMirror = getAnnotationMirror(value.getAnnotationName()).orElse(null);
                return annotationMirror != null && getRepeatableNameForType(annotationMirror) != null;
            });
        if (isRepeatableAnnotationContainer) {
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
                repeatableAnnotations.stream().map(this::toProcessedAnnotation)
            );
        }
        return Stream.of(processedAnnotation);
    }

    private void addAnnotationDefaults(MutableAnnotationMetadata annotationMetadata, ProcessedAnnotation processedAnnotation) {
        AnnotationValue<?> annotationValue = processedAnnotation.getAnnotationValue();
        String annotationName = annotationValue.getAnnotationName();
        T annotationType = processedAnnotation.getAnnotationType();
        Map<CharSequence, Object> annotationDefaults = annotationValue.getDefaultValues();
        if (annotationDefaults == null && annotationType != null) {
            annotationDefaults = getCachedAnnotationDefaults(annotationName, annotationType);
        }
        annotationMetadata.addDefaultAnnotationValues(annotationName, annotationDefaults, annotationValue.getRetentionPolicy());
    }

    private ProcessedAnnotation processAliases(ProcessedAnnotation processedAnnotation, List<ProcessedAnnotation> introducedAnnotations) {
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
        return processedAnnotation.withAnnotationValue(
            AnnotationValue.builder(annotationValue).members(newValues).build()
        );
    }

    private void processStereotypes(MutableAnnotationMetadata annotationMetadata,
                                    boolean isDeclared,
                                    List<String> parentAnnotations,
                                    ProcessedAnnotation processedAnnotation) {
        AnnotationValue<?> annotationValue = processedAnnotation.getAnnotationValue();
        String annotationName = annotationValue.getAnnotationName();
        String packageName = NameUtils.getPackageName(annotationName);
        if (AnnotationUtil.STEREOTYPE_EXCLUDES.contains(packageName) || annotationName.endsWith(".Nullable")) {
            return;
        }
        T annotationType = processedAnnotation.getAnnotationType();
        List<String> newParentAnnotations = new ArrayList<>(parentAnnotations);
        newParentAnnotations.add(annotationName);

        Stream<ProcessedAnnotation> stereotypes;
        if (annotationType == null || CollectionUtils.isNotEmpty(annotationValue.getStereotypes())) {
            // Annotation is not on the classpath or a transformer/mapper provided a value with custom stereotypes
            stereotypes = annotationValue.getStereotypes() == null ? Stream.empty() : annotationValue.getStereotypes().stream().map(this::toProcessedAnnotation);
        } else {
            stereotypes = annotationMirrorToAnnotationValue(getAnnotationsForType(annotationType).stream(),
                annotationType, false, annotationMetadata, isDeclared, true);
        }
        addStereotypeAnnotations(
            stereotypes,
            annotationType,
            newParentAnnotations,
            annotationMetadata,
            isDeclared
        );
    }

    private void addAnnotation(MutableAnnotationMetadata mutableAnnotationMetadata,
                               List<String> parentAnnotations,
                               boolean isDeclared,
                               boolean isStereotype,
                               ProcessedAnnotation processedAnnotation) {
        AnnotationValue<?> annotationValue = processedAnnotation.getAnnotationValue();
        String repeatableContainer = processedAnnotation.getAnnotationType() == null ? null : getRepeatableNameForType(processedAnnotation.getAnnotationType());
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
     * Is the given annotation excluded for the specified element.
     *
     * @param element        The element
     * @param annotationName The annotation name
     * @return True if it is excluded
     */
    protected boolean isExcludedAnnotation(@NonNull T element, @NonNull String annotationName) {
        return AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationName);
    }

    /**
     * Test whether the annotation mirror is inherited.
     *
     * @param annotationMirror The mirror
     * @return True if it is
     */
    protected abstract boolean isInheritedAnnotation(@NonNull A annotationMirror);

    private void addStereotypeAnnotations(Stream<ProcessedAnnotation> stream,
                                          @Nullable
                                          T element,
                                          List<String> parentAnnotations,
                                          MutableAnnotationMetadata metadata,
                                          boolean isDeclared) {

        final String lastParent = CollectionUtils.last(parentAnnotations);
        LinkedList<AnnotationValueBuilder<?>> interceptorBindings = new LinkedList<>();

        stream = filterAndTransformAnnotations(stream, parentAnnotations);

        stream = stream.filter(processedAnnotation -> {
            AnnotationValue<?> annotationValue = processedAnnotation.getAnnotationValue();

            String annotationName = annotationValue.getAnnotationName();
            if (!AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationName) && !parentAnnotations.contains(annotationName)) {
                if (AnnotationUtil.ADVICE_STEREOTYPES.contains(lastParent)) {
                    if (AnnotationUtil.ANN_INTERCEPTOR_BINDING.equals(annotationName)) {
                        // skip @InterceptorBinding stereotype handled in last round
                        return false;
                    }
                }
            }

            // special case: don't add stereotype for @Nonnull when it's marked as UNKNOWN/MAYBE/NEVER.
            // https://github.com/micronaut-projects/micronaut-core/issues/6795
            if (annotationValue.getAnnotationName().equals("javax.annotation.Nonnull")) {
                String when = Objects.toString(annotationValue.getValues().get("when"));
                return !(when.equals("UNKNOWN") || when.equals("MAYBE") || when.equals("NEVER"));
            }
            return true;
        });
        stream = processInterceptors(stream, metadata, lastParent, interceptorBindings);

        List<ProcessedAnnotation> introducedAliasForAnnotations = new ArrayList<>();

        stream = stream.map(processedAnnotation -> processAliases(processedAnnotation, introducedAliasForAnnotations));

        List<ProcessedAnnotation> processedAnnotations = addAnnotations(stream, metadata, isDeclared, true, parentAnnotations).toList();

        if (CollectionUtils.isNotEmpty(introducedAliasForAnnotations)) {
            // Add annotation created by @AliasFor
            addStereotypeAnnotations(
                introducedAliasForAnnotations.stream(),
                null,
                parentAnnotations,
                metadata,
                isDeclared
            );
        }

        // After annotations are processes process their stereotypes
        for (ProcessedAnnotation processedAnnotation : processedAnnotations) {
            processStereotypes(metadata, isDeclared, parentAnnotations, processedAnnotation);
        }

        handleAnnotationsWithMutatedMetadata(element, parentAnnotations, metadata, isDeclared, lastParent);

        if (!interceptorBindings.isEmpty()) {
            for (AnnotationValueBuilder<?> interceptorBinding : interceptorBindings) {
                if (isDeclared) {
                    metadata.addDeclaredRepeatable(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS, interceptorBinding.build());
                } else {
                    metadata.addRepeatable(AnnotationUtil.ANN_INTERCEPTOR_BINDINGS, interceptorBinding.build());
                }
            }
        }
    }

    private void handleAnnotationsWithMutatedMetadata(T element,
                                                      List<String> parentAnnotations,
                                                      MutableAnnotationMetadata metadata,
                                                      boolean isDeclared,
                                                      String lastParent) {
        if (lastParent != null && element != null) {
            CachedAnnotationMetadata modifiedStereotypes = MUTATED_ANNOTATION_METADATA.get(element);
            if (modifiedStereotypes != null && !modifiedStereotypes.isEmpty() && modifiedStereotypes.isMutated()) {
                for (String stereotypeName : modifiedStereotypes.getStereotypeAnnotationNames()) {
                    final AnnotationValue<Annotation> a = modifiedStereotypes.getAnnotation(stereotypeName);
                    if (a == null) {
                        continue;
                    }
                    final List<String> stereotypeParents = modifiedStereotypes.getAnnotationNamesByStereotype(stereotypeName);
                    List<String> newParentAnnotations = new ArrayList<>(parentAnnotations);
                    newParentAnnotations.addAll(stereotypeParents);

                    addStereotypeAnnotations(
                        Stream.of(toProcessedAnnotation(a)),
                        null,
                        newParentAnnotations,
                        metadata,
                        isDeclared
                    );
                }

                for (String annotationName : modifiedStereotypes.getAnnotationNames()) {
                    AnnotationValue<Annotation> a = modifiedStereotypes.getAnnotation(annotationName);
                    if (a == null) {
                        continue;
                    }
                    addStereotypeAnnotations(
                        Stream.of(toProcessedAnnotation(a)),
                        null,
                        parentAnnotations,
                        metadata,
                        isDeclared
                    );
                }

            }
        }
    }

    private AnnotationValue<?> handleMemberBinding(DefaultAnnotationMetadata metadata, String lastParent, AnnotationValue<?> annotationValue) {
        Map<CharSequence, Object> data = annotationValue.getValues();
        if (!data.containsKey(InterceptorBindingQualifier.META_MEMBER_MEMBERS)) {
            return annotationValue;
        }
        data = new LinkedHashMap<>(data);
        final Object o = data.remove(InterceptorBindingQualifier.META_MEMBER_MEMBERS);
        if (o instanceof Boolean && ((Boolean) o)) {
            Map<CharSequence, Object> values = metadata.getValues(lastParent);
            if (!values.isEmpty()) {
                Set<String> nonBinding = NON_BINDING_CACHE.computeIfAbsent(lastParent, (annotationName) -> {
                    final HashSet<String> nonBindingResult = new HashSet<>(5);
                    Map<String, ? extends T> members = getAnnotationMembers(lastParent);
                    if (CollectionUtils.isNotEmpty(members)) {
                        members.forEach((name, ann) -> {
                            if (hasSimpleAnnotation(ann, NonBinding.class.getSimpleName())) {
                                nonBindingResult.add(name);
                            }
                        });
                    }
                    return nonBindingResult.isEmpty() ? Collections.emptySet() : nonBindingResult;
                });

                if (!nonBinding.isEmpty()) {
                    values = new HashMap<>(values);
                    values.keySet().removeAll(nonBinding);
                }
                final AnnotationValueBuilder<Annotation> builder =
                    AnnotationValue
                        .builder(lastParent)
                        .members(values);
                data.put(
                    InterceptorBindingQualifier.META_MEMBER_MEMBERS,
                    builder.build()
                );

            }
        }
        return AnnotationValue.builder(annotationValue).members(data).build();
    }

    /**
     * Gets the annotation members for the given type.
     *
     * @param annotationType The annotation type
     * @return The members
     * @since 3.3.0
     */
    @NonNull
    protected abstract Map<String, ? extends T> getAnnotationMembers(@NonNull String annotationType);

    /**
     * Returns true if a simple meta annotation is present for the given element and annotation type.
     *
     * @param element    The element
     * @param simpleName The simple name, ie {@link Class#getSimpleName()}
     * @return True an annotation with the given simple name exists on the element
     */
    protected abstract boolean hasSimpleAnnotation(T element, String simpleName);

    private void addToInterceptorBindingsIfNecessary(List<AnnotationValueBuilder<?>> interceptorBindings,
                                                     String lastParent,
                                                     String annotationName) {
        if (lastParent != null) {
            AnnotationValueBuilder<?> interceptorBinding = null;
            if (AnnotationUtil.ANN_AROUND.equals(annotationName) || AnnotationUtil.ANN_INTERCEPTOR_BINDING.equals(annotationName)) {
                interceptorBinding = AnnotationValue.builder(AnnotationUtil.ANN_INTERCEPTOR_BINDING)
                    .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(lastParent))
                    .member("kind", "AROUND");
            } else if (AnnotationUtil.ANN_INTRODUCTION.equals(annotationName)) {
                interceptorBinding = AnnotationValue.builder(AnnotationUtil.ANN_INTERCEPTOR_BINDING)
                    .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(lastParent))
                    .member("kind", "INTRODUCTION");
            } else if (AnnotationUtil.ANN_AROUND_CONSTRUCT.equals(annotationName)) {
                interceptorBinding = AnnotationValue.builder(AnnotationUtil.ANN_INTERCEPTOR_BINDING)
                    .member(AnnotationMetadata.VALUE_MEMBER, new AnnotationClassValue<>(lastParent))
                    .member("kind", "AROUND_CONSTRUCT");
            }
            if (interceptorBinding != null) {
                interceptorBindings.add(interceptorBinding);
            }
        }
    }

    private <K> List<K> eliminateProcessed(List<K> visitors, Set<Class<?>> processedVisitors) {
        if (visitors == null) {
            return null;
        }
        return visitors.stream().filter(v -> !processedVisitors.contains(v.getClass())).toList();
    }

    private Stream<ProcessedAnnotation> processAnnotationRemappers(ProcessedAnnotation processedAnnotation,
                                                                   Set<Class<?>> processedVisitors) {
        AnnotationValue<?> annotationValue = processedAnnotation.getAnnotationValue();
        String packageName = NameUtils.getPackageName(annotationValue.getAnnotationName());
        List<AnnotationRemapper> annotationRemappers = ANNOTATION_REMAPPERS.get(packageName);
        annotationRemappers = eliminateProcessed(annotationRemappers, processedVisitors);
        if (CollectionUtils.isEmpty(annotationRemappers)) {
            return Stream.of(processedAnnotation);
        }
        VisitorContext visitorContext = createVisitorContext();
        List<ProcessedAnnotation> result = new ArrayList<>();
        for (AnnotationRemapper annotationRemapper : annotationRemappers) {
            processedVisitors.add(annotationRemapper.getClass());
            for (AnnotationValue<?> newAnnotationValue : annotationRemapper.remap(annotationValue, visitorContext)) {
                if (newAnnotationValue == annotationValue) {
                    result.add(processedAnnotation); // Retain the same value
                } else {
                    result.add(toProcessedAnnotation(newAnnotationValue));
                }
            }
        }
        // Transform new remapped annotations
        return result.stream().flatMap(annotation -> transform(annotation, processedVisitors));
    }

    private <K extends Annotation> Stream<ProcessedAnnotation> processAnnotationTransformers(ProcessedAnnotation processedAnnotation,
                                                                                             Set<Class<?>> processedVisitors) {
        AnnotationValue<K> annotationValue = (AnnotationValue<K>) processedAnnotation.getAnnotationValue();
        List<AnnotationTransformer<K>> annotationTransformers = getAnnotationTransformers(annotationValue.getAnnotationName());
        annotationTransformers = eliminateProcessed(annotationTransformers, processedVisitors);
        if (CollectionUtils.isEmpty(annotationTransformers)) {
            return Stream.of(processedAnnotation);
        }
        VisitorContext visitorContext = createVisitorContext();
        List<ProcessedAnnotation> result = new ArrayList<>();
        for (AnnotationTransformer<K> annotationTransformer : annotationTransformers) {
            processedVisitors.add(annotationTransformer.getClass());
            for (AnnotationValue<?> newAnnotationValue : annotationTransformer.transform(annotationValue, visitorContext)) {
                if (newAnnotationValue == annotationValue) {
                    result.add(processedAnnotation); // Retain the same value
                } else {
                    result.add(toProcessedAnnotation(newAnnotationValue));
                }
            }
        }
        // Transform new transformed annotations
        return result.stream().flatMap(annotation -> transform(annotation, processedVisitors));
    }

    private <K extends Annotation> Stream<ProcessedAnnotation> processAnnotationMappers(ProcessedAnnotation processedAnnotation,
                                                                                        Set<Class<?>> processedVisitors) {
        AnnotationValue<K> annotationValue = (AnnotationValue<K>) processedAnnotation.getAnnotationValue();
        List<AnnotationMapper<K>> mappers = getAnnotationMappers(annotationValue.getAnnotationName());
        mappers = eliminateProcessed(mappers, processedVisitors);
        if (CollectionUtils.isEmpty(mappers)) {
            return Stream.of(processedAnnotation);
        }
        VisitorContext visitorContext = createVisitorContext();
        List<ProcessedAnnotation> result = new ArrayList<>();
        result.add(processedAnnotation); // Mapper retains the original value
        for (AnnotationMapper<K> mapper : mappers) {
            processedVisitors.add(mapper.getClass());
            List<AnnotationValue<?>> mappedToAnnotationValues = mapper.map(annotationValue, visitorContext);
            if (mappedToAnnotationValues != null) {
                for (AnnotationValue<?> mappedToAnnotationValue : mappedToAnnotationValues) {
                    if (mappedToAnnotationValue != annotationValue) {
                        result.add(toProcessedAnnotation(mappedToAnnotationValue));
                    }
                    // else: Mapper returned the same value, but it's already included
                }
            }
        }
        // Transform new mapped annotations
        return result.stream().flatMap(annotation -> transform(annotation, processedVisitors));
    }

    private ProcessedAnnotation toProcessedAnnotation(AnnotationValue<?> av) {
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
        final HashSet<String> all = new HashSet<>(ANNOTATION_MAPPERS.keySet());
        all.addAll(ANNOTATION_TRANSFORMERS.keySet());
        return all;
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
                Stream.of(toProcessedAnnotation(annotationValue)),
                true,
                Collections.emptyList()
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
                String repeatableName = getRepeatableNameForType(annotationMirror);
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
                String repeatableName = getRepeatableNameForType(annotationMirror);
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

        private ProcessedAnnotation(@Nullable T annotationType, AnnotationValue<?> annotationValue) {
            this.annotationType = annotationType;
            this.annotationValue = annotationValue;
        }

        public ProcessedAnnotation withAnnotationValue(AnnotationValue<?> annotationValue) {
            return new ProcessedAnnotation(annotationType, annotationValue);
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
