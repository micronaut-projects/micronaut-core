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
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.InstantiatedMember;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.qualifiers.InterceptorBindingQualifier;
import io.micronaut.inject.visitor.VisitorContext;
import jakarta.inject.Qualifier;

import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.ListIterator;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiConsumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

/**
 * An abstract implementation that builds {@link AnnotationMetadata}.
 *
 * @param <T> The element type
 * @param <A> The annotation type
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractAnnotationMetadataBuilder<T, A> {

    /**
     * Names of annotations that should produce deprecation warnings.
     * The key in the map is the deprecated annotation the value the replacement.
     */
    private static final Map<String, String> DEPRECATED_ANNOTATION_NAMES = Collections.emptyMap();
    private static final Map<String, List<AnnotationMapper<?>>> ANNOTATION_MAPPERS = new HashMap<>(10);
    private static final Map<String, List<AnnotationTransformer<Annotation>>> ANNOTATION_TRANSFORMERS = new HashMap<>(5);
    private static final Map<String, List<AnnotationRemapper>> ANNOTATION_REMAPPERS = new HashMap<>(5);
    private static final Map<MetadataKey<?>, CacheEntry> MUTATED_ANNOTATION_METADATA = new HashMap<>(100);
    private static final Map<String, Set<String>> NON_BINDING_CACHE = new HashMap<>(50);
    private static final List<String> DEFAULT_ANNOTATE_EXCLUDES = Arrays.asList(Internal.class.getName(),
        Experimental.class.getName());
    private static final Map<String, Map<String, Object>> ANNOTATION_DEFAULTS = new HashMap<>(20);

    static {
        for (AnnotationMapper mapper : SoftServiceLoader.load(AnnotationMapper.class, AbstractAnnotationMetadataBuilder.class.getClassLoader())
            .disableFork().collectAll()) {
            try {
                String name = null;
                if (mapper instanceof TypedAnnotationMapper) {
                    name = ((TypedAnnotationMapper) mapper).annotationType().getName();
                } else if (mapper instanceof NamedAnnotationMapper) {
                    name = ((NamedAnnotationMapper) mapper).getName();
                }
                if (StringUtils.isNotEmpty(name)) {
                    ANNOTATION_MAPPERS.computeIfAbsent(name, s -> new ArrayList<>(2)).add(mapper);
                }
            } catch (Throwable e) {
                // mapper, missing dependencies, continue
            }
        }

        for (AnnotationTransformer transformer : SoftServiceLoader.load(AnnotationTransformer.class, AbstractAnnotationMetadataBuilder.class.getClassLoader())
            .disableFork().collectAll()) {
            try {
                String name = null;
                if (transformer instanceof TypedAnnotationTransformer) {
                    name = ((TypedAnnotationTransformer) transformer).annotationType().getName();
                } else if (transformer instanceof NamedAnnotationTransformer) {
                    name = ((NamedAnnotationTransformer) transformer).getName();
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
        DefaultAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
        try {
            AnnotationMetadata metadata = buildInternalMulti(
                Collections.emptyList(),
                element,
                annotationMetadata, true, true, true
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
        DefaultAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
        if (includeTypeAnnotations) {
            buildInternalMulti(
                Collections.emptyList(),
                element,
                annotationMetadata, false, true, true
            );
        }
        try {
            includeAnnotations(annotationMetadata, element, false, true, annotations, true);
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
    public CacheEntry lookupOrBuildForParameter(T owningType, T methodElement, T parameterElement) {
        return lookupOrBuild(true, owningType, methodElement, parameterElement);
    }

    /**
     * Build the meta data for the given element.
     *
     * @param typeElement The element
     * @return The {@link AnnotationMetadata}
     */
    public CacheEntry lookupOrBuildForType(T typeElement) {
        return lookupOrBuild(true, typeElement);
    }

    /**
     * Build the metadata for the given method element excluding any class metadata.
     *
     * @param owningType The owningType
     * @param element    The element
     * @return The {@link CacheEntry}
     */
    public CacheEntry lookupOrBuildForMethod(T owningType, T element) {
        return lookupOrBuild(false, owningType, element);
    }

    /**
     * Build the metadata for the given field element excluding any class metadata.
     *
     * @param owningType The owningType
     * @param element    The element
     * @return The {@link CacheEntry}
     */
    public CacheEntry lookupOrBuildForField(T owningType, T element) {
        return lookupOrBuild(false, owningType, element);
    }

    private CacheEntry lookupOrBuild(boolean inheritTypeAnnotations, T... elements) {
        return lookupExisting(elements, () -> {
            T element = elements[elements.length - 1];
            return buildInternal(inheritTypeAnnotations, false, element);
        });
    }

    private AnnotationMetadata buildInternal(boolean inheritTypeAnnotations, boolean declaredOnly, T element) {
        DefaultAnnotationMetadata annotationMetadata = new MutableAnnotationMetadata();
        try {
            return buildInternalMulti(
                Collections.emptyList(),
                element,
                annotationMetadata, inheritTypeAnnotations, declaredOnly, true
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
    protected @Nullable
    AnnotatedElementValidator getElementValidator() {
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
     * @param annotationMirror The annotation
     * @return The values
     */
    protected abstract Map<? extends T, ?> readAnnotationDefaultValues(A annotationMirror);

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
     * @return The values
     */
    protected abstract OptionalValues<?> getAnnotationValues(T originatingElement, T member, Class<?> annotationType);

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
    protected abstract @Nullable
    String getRepeatableName(A annotationMirror);

    /**
     * Obtain the name of the repeatable annotation if the annotation is is one.
     *
     * @param annotationType The annotation mirror
     * @return Return the name or null
     */
    protected abstract @Nullable
    String getRepeatableNameForType(T annotationType);

    /**
     * @param originatingElement The originating element
     * @param annotationMirror   The annotation
     * @return The annotation value
     */
    protected io.micronaut.core.annotation.AnnotationValue readNestedAnnotationValue(T originatingElement, A annotationMirror) {
        io.micronaut.core.annotation.AnnotationValue av;
        Map<? extends T, ?> annotationValues = readAnnotationRawValues(annotationMirror);
        final String annotationTypeName = getAnnotationTypeName(annotationMirror);
        if (annotationValues.isEmpty()) {
            av = new io.micronaut.core.annotation.AnnotationValue(annotationTypeName);
        } else {

            Map<CharSequence, Object> resolvedValues = new LinkedHashMap<>();
            for (Map.Entry<? extends T, ?> entry : annotationValues.entrySet()) {
                T member = entry.getKey();
                OptionalValues<?> aliasForValues = getAnnotationValues(originatingElement, member, AliasFor.class);
                Object annotationValue = entry.getValue();
                Optional<?> aliasMember = aliasForValues.get("member");
                Optional<?> aliasAnnotation = aliasForValues.get("annotation");
                Optional<?> aliasAnnotationName = aliasForValues.get("annotationName");
                if (aliasMember.isPresent() && !(aliasAnnotation.isPresent() || aliasAnnotationName.isPresent())) {
                    String aliasedNamed = aliasMember.get().toString();
                    readAnnotationRawValues(originatingElement,
                        annotationTypeName,
                        member,
                        aliasedNamed,
                        annotationValue,
                        resolvedValues);
                }
                String memberName = getAnnotationMemberName(member);
                readAnnotationRawValues(originatingElement,
                    annotationTypeName,
                    member,
                    memberName,
                    annotationValue,
                    resolvedValues);
            }
            av = new io.micronaut.core.annotation.AnnotationValue(annotationTypeName, resolvedValues);
        }

        return av;
    }

    /**
     * Return a mirror for the given annotation.
     *
     * @param annotationName The annotation name
     * @return An optional mirror
     */
    protected abstract Optional<T> getAnnotationMirror(String annotationName);

    /**
     * Populate the annotation data for the given annotation.
     *
     * @param originatingElement The element the annotation data originates from
     * @param parent             The parent element
     * @param annotationMirror   The annotation
     * @param metadata           the metadata
     * @param isDeclared         Is the annotation a declared annotation
     * @param retentionPolicy    The retention policy
     * @param allowAliases       Whether aliases are allowed
     * @return The annotation values
     */
    protected Map<CharSequence, Object> populateAnnotationData(
        T originatingElement,
        @Nullable T parent,
        A annotationMirror,
        DefaultAnnotationMetadata metadata,
        boolean isDeclared,
        RetentionPolicy retentionPolicy,
        boolean allowAliases) {
        return populateAnnotationData(
            originatingElement,
            parent == originatingElement,
            annotationMirror,
            metadata,
            isDeclared,
            retentionPolicy,
            allowAliases
        );
    }

    /**
     * Populate the annotation data for the given annotation.
     *
     * @param originatingElement             The element the annotation data originates from
     * @param originatingElementIsSameParent Whether the originating element is considered a parent element
     * @param annotationMirror               The annotation
     * @param metadata                       the metadata
     * @param isDeclared                     Is the annotation a declared annotation
     * @param retentionPolicy                The retention policy
     * @param allowAliases                   Whether aliases are allowed
     * @return The annotation values
     */
    protected Map<CharSequence, Object> populateAnnotationData(
        T originatingElement,
        boolean originatingElementIsSameParent,
        A annotationMirror,
        DefaultAnnotationMetadata metadata,
        boolean isDeclared,
        RetentionPolicy retentionPolicy,
        boolean allowAliases) {
        String annotationName = getAnnotationTypeName(annotationMirror);

        if (retentionPolicy == RetentionPolicy.RUNTIME) {
            processAnnotationDefaults(originatingElement,
                metadata,
                annotationName,
                () -> readAnnotationDefaultValues(annotationMirror));
        }

        List<String> parentAnnotations = new ArrayList<>();
        parentAnnotations.add(annotationName);
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
                    final DefaultAnnotationMetadata memberMetadata = new DefaultAnnotationMetadata();
                    final List<? extends A> annotationsForMember = getAnnotationsForType(member)
                        .stream().filter((a) -> !getAnnotationTypeName(a).equals(annotationName))
                        .collect(Collectors.toList());
                    includeAnnotations(memberMetadata, member, false, true, annotationsForMember, false);

                    boolean isInstantiatedMember = memberMetadata.hasAnnotation(InstantiatedMember.class);

                    if (memberMetadata.hasAnnotation(NonBinding.class)) {
                        final String memberName = getElementName(member);
                        nonBindingMembers.add(memberName);
                    }
                    if (isInstantiatedMember) {
                        final String memberName = getAnnotationMemberName(member);
                        final Object rawValue = readAnnotationValue(originatingElement, member, memberName, annotationValue);
                        if (rawValue instanceof AnnotationClassValue) {
                            AnnotationClassValue acv = (AnnotationClassValue) rawValue;
                            annotationValues.put(memberName, new AnnotationClassValue(acv.getName(), true));
                        }
                    }
                }

                if (allowAliases) {
                    handleAnnotationAlias(
                        originatingElement,
                        metadata,
                        isDeclared,
                        annotationName,
                        parentAnnotations,
                        annotationValues,
                        member,
                        annotationValue
                    );
                }
            }

            if (!nonBindingMembers.isEmpty()) {
                T annotationType = getTypeForAnnotation(annotationMirror);
                if (hasAnnotation(annotationType, AnnotationUtil.QUALIFIER) ||
                    hasAnnotation(annotationType, Qualifier.class)) {
                    metadata.addDeclaredStereotype(
                        Collections.singletonList(getAnnotationTypeName(annotationMirror)),
                        AnnotationUtil.QUALIFIER,
                        Collections.singletonMap("nonBinding", nonBindingMembers)
                    );
                }
            }
        }
        List<AnnotationMapper<?>> mappers = getAnnotationMappers(annotationName);
        if (mappers != null) {
            AnnotationValue<?> annotationValue = new AnnotationValue(annotationName, annotationValues);
            VisitorContext visitorContext = createVisitorContext();
            for (AnnotationMapper mapper : mappers) {
                List mapped = mapper.map(annotationValue, visitorContext);
                if (mapped != null) {
                    for (Object o : mapped) {
                        if (o instanceof AnnotationValue) {
                            AnnotationValue av = (AnnotationValue) o;
                            retentionPolicy = av.getRetentionPolicy();
                            String mappedAnnotationName = av.getAnnotationName();

                            Optional<T> mappedMirror = getAnnotationMirror(mappedAnnotationName);
                            String repeatableName = mappedMirror.map(this::getRepeatableNameForType).orElse(null);
                            if (repeatableName != null) {
                                if (isDeclared) {
                                    metadata.addDeclaredRepeatable(
                                        repeatableName,
                                        av,
                                        retentionPolicy
                                    );
                                } else {
                                    metadata.addRepeatable(
                                        repeatableName,
                                        av,
                                        retentionPolicy
                                    );
                                }
                            } else {
                                Map<CharSequence, Object> values = av.getValues();

                                if (isDeclared) {
                                    metadata.addDeclaredAnnotation(
                                        mappedAnnotationName,
                                        values,
                                        retentionPolicy
                                    );
                                } else {
                                    metadata.addAnnotation(
                                        mappedAnnotationName,
                                        values,
                                        retentionPolicy
                                    );
                                }

                            }

                            RetentionPolicy finalRetentionPolicy = retentionPolicy;
                            mappedMirror.ifPresent(annMirror -> {
                                Map<CharSequence, Object> values = av.getValues();
                                values.forEach((key, value) -> {
                                    T member = getAnnotationMember(annMirror, key);
                                    if (member != null) {
                                        handleAnnotationAlias(
                                            originatingElement,
                                            metadata,
                                            isDeclared,
                                            mappedAnnotationName,
                                            Collections.emptyList(),
                                            annotationValues,
                                            member,
                                            value
                                        );
                                    }
                                });
                                if (finalRetentionPolicy == RetentionPolicy.RUNTIME) {
                                    processAnnotationDefaults(originatingElement,
                                        metadata,
                                        mappedAnnotationName,
                                        () -> readAnnotationDefaultValues(mappedAnnotationName, annMirror));
                                }
                                final ArrayList<String> parents = new ArrayList<>();
                                processAnnotationStereotype(
                                    parents,
                                    annMirror,
                                    mappedAnnotationName,
                                    metadata,
                                    isDeclared,
                                    isInheritedAnnotationType(annMirror) || originatingElementIsSameParent);

                            });
                        }
                    }
                }
            }
        }
        return annotationValues;
    }

    private void handleAnnotationAlias(T originatingElement,
                                       DefaultAnnotationMetadata metadata,
                                       boolean isDeclared,
                                       String annotationName,
                                       List<String> parentAnnotations,
                                       Map<CharSequence, Object> annotationValues,
                                       T member,
                                       Object annotationValue) {
        Optional<?> aliases = getAnnotationValues(originatingElement, member, Aliases.class).get("value");
        if (aliases.isPresent()) {
            Object value = aliases.get();
            if (value instanceof AnnotationValue[]) {
                AnnotationValue[] values = (AnnotationValue[]) value;
                for (AnnotationValue av : values) {
                    OptionalValues<Object> aliasForValues = OptionalValues.of(Object.class, av.getValues());
                    processAnnotationAlias(
                        originatingElement,
                        annotationName,
                        member, metadata,
                        isDeclared,
                        parentAnnotations,
                        annotationValues,
                        annotationValue,
                        aliasForValues
                    );
                }
            }
            readAnnotationRawValues(originatingElement,
                annotationName,
                member,
                getAnnotationMemberName(member),
                annotationValue,
                annotationValues);
        } else {
            OptionalValues<?> aliasForValues = getAnnotationValues(
                originatingElement,
                member,
                AliasFor.class
            );
            processAnnotationAlias(
                originatingElement,
                annotationName,
                member,
                metadata,
                isDeclared,
                parentAnnotations,
                annotationValues,
                annotationValue,
                aliasForValues
            );
            readAnnotationRawValues(originatingElement,
                annotationName,
                member,
                getAnnotationMemberName(member),
                annotationValue,
                annotationValues);
        }
    }

    /**
     * Get the annotation member.
     *
     * @param originatingElement The originatig element
     * @param member             The member
     * @return The annotation member
     */
    protected abstract @Nullable
    T getAnnotationMember(T originatingElement, CharSequence member);

    /**
     * Obtain the annotation mappers for the given annotation name.
     *
     * @param annotationName The annotation name
     * @return The mappers
     */
    protected @NonNull
    List<AnnotationMapper<? extends Annotation>> getAnnotationMappers(@NonNull String annotationName) {
        return ANNOTATION_MAPPERS.get(annotationName);
    }

    /**
     * Obtain the transformers mappers for the given annotation name.
     *
     * @param annotationName The annotation name
     * @return The transformers
     */
    protected @NonNull
    List<AnnotationTransformer<Annotation>> getAnnotationTransformers(@NonNull String annotationName) {
        return ANNOTATION_TRANSFORMERS.get(annotationName);
    }

    /**
     * Creates the visitor context for this implementation.
     *
     * @return The visitor context
     */
    protected abstract VisitorContext createVisitorContext();

    private void processAnnotationDefaults(T originatingElement,
                                           DefaultAnnotationMetadata metadata,
                                           String annotationName,
                                           Supplier<Map<? extends T, ?>> elementDefaultValues) {
        Map<CharSequence, Object> defaultValues;
        final Map<String, Object> defaults = ANNOTATION_DEFAULTS.get(annotationName);
        if (defaults != null) {
            defaultValues = new LinkedHashMap<>(defaults);
        } else {
            defaultValues = getAnnotationDefaults(originatingElement, annotationName, elementDefaultValues.get());
            if (defaultValues != null) {
                ANNOTATION_DEFAULTS.put(annotationName, defaultValues.entrySet().stream()
                    .collect(Collectors.toMap(
                        (entry) -> entry.getKey().toString(),
                        Map.Entry::getValue)));
            } else {
                defaultValues = Collections.emptyMap();
            }
        }
        metadata.addDefaultAnnotationValues(annotationName, defaultValues);
    }

    private Map<CharSequence, Object> getAnnotationDefaults(T originatingElement,
                                                            String annotationName,
                                                            Map<? extends T, ?> elementDefaultValues) {
        if (elementDefaultValues != null) {
            Map<CharSequence, Object> defaultValues = new LinkedHashMap<>();
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
        } else {
            return null;
        }
    }

    @NonNull
    private CacheEntry lookupExisting(T[] elements, Supplier<AnnotationMetadata> annotationMetadataSupplier) {
        return MUTATED_ANNOTATION_METADATA.computeIfAbsent(new MetadataKey<>(elements), metadataKey -> new DefaultCacheEntry(annotationMetadataSupplier.get()));
    }

    private void processAnnotationAlias(
        T originatingElement,
        String annotationName,
        T member,
        DefaultAnnotationMetadata metadata,
        boolean isDeclared,
        List<String> parentAnnotations,
        Map<CharSequence, Object> annotationValues,
        Object annotationValue,
        OptionalValues<?> aliasForValues) {
        Optional<?> aliasAnnotation = aliasForValues.get("annotation");
        Optional<?> aliasAnnotationName = aliasForValues.get("annotationName");
        Optional<?> aliasMember = aliasForValues.get("member");

        if (aliasAnnotation.isPresent() || aliasAnnotationName.isPresent()) {
            if (aliasMember.isPresent()) {
                String aliasedAnnotation;
                if (aliasAnnotation.isPresent()) {
                    aliasedAnnotation = aliasAnnotation.get().toString();
                } else {
                    aliasedAnnotation = aliasAnnotationName.get().toString();
                }
                String aliasedMemberName = aliasMember.get().toString();
                Object v = readAnnotationValue(originatingElement, member, aliasedMemberName, annotationValue);

                if (v != null) {
                    final List<AnnotationValue<?>> remappedValues = remapAnnotation(aliasedAnnotation);
                    for (AnnotationValue<?> remappedAnnotation : remappedValues) {
                        String aliasedAnnotationName = remappedAnnotation.getAnnotationName();
                        Optional<T> annotationMirror = getAnnotationMirror(aliasedAnnotationName);
                        RetentionPolicy retentionPolicy = RetentionPolicy.RUNTIME;
                        String repeatableName = null;
                        if (annotationMirror.isPresent()) {
                            final T annotationTypeMirror = annotationMirror.get();
                            processAnnotationDefaults(originatingElement,
                                metadata,
                                aliasedAnnotationName,
                                () -> readAnnotationDefaultValues(aliasedAnnotationName,
                                    annotationTypeMirror));
                            retentionPolicy = getRetentionPolicy(annotationTypeMirror);
                            repeatableName = getRepeatableNameForType(annotationTypeMirror);
                        }

                        if (isDeclared) {
                            if (StringUtils.isNotEmpty(repeatableName)) {
                                metadata.addDeclaredRepeatableStereotype(
                                    parentAnnotations,
                                    repeatableName,
                                    AnnotationValue.builder(aliasedAnnotationName, retentionPolicy)
                                        .members(Collections.singletonMap(aliasedMemberName, v))
                                        .build()
                                );
                            } else {
                                metadata.addDeclaredStereotype(
                                    Collections.emptyList(),
                                    aliasedAnnotationName,
                                    Collections.singletonMap(aliasedMemberName, v),
                                    retentionPolicy
                                );
                            }
                        } else {
                            if (StringUtils.isNotEmpty(repeatableName)) {
                                metadata.addRepeatableStereotype(
                                    parentAnnotations,
                                    repeatableName,
                                    AnnotationValue.builder(aliasedAnnotationName, retentionPolicy)
                                        .members(Collections.singletonMap(aliasedMemberName, v))
                                        .build()
                                );
                            } else {

                                metadata.addStereotype(
                                    Collections.emptyList(),
                                    aliasedAnnotationName,
                                    Collections.singletonMap(aliasedMemberName, v),
                                    retentionPolicy
                                );
                            }
                        }

                        if (annotationMirror.isPresent()) {
                            final T am = annotationMirror.get();
                            processAnnotationStereotype(
                                Collections.singletonList(aliasedAnnotationName),
                                am,
                                aliasedAnnotationName,
                                metadata,
                                isDeclared,
                                isInheritedAnnotationType(am)
                            );
                        } else {
                            processAnnotationStereotype(
                                Collections.singletonList(aliasedAnnotationName),
                                remappedAnnotation,
                                metadata,
                                isDeclared);
                        }
                    }
                }
            }
        } else if (aliasMember.isPresent()) {
            String aliasedNamed = aliasMember.get().toString();
            Object v = readAnnotationValue(originatingElement, member, aliasedNamed, annotationValue);
            if (v != null) {
                annotationValues.put(aliasedNamed, v);
            }
            readAnnotationRawValues(originatingElement, annotationName, member, aliasedNamed, annotationValue, annotationValues);
        }
    }

    /**
     * Gets the retention policy for the given annotation.
     *
     * @param annotation The annotation
     * @return The retention policy
     */
    protected abstract @NonNull
    RetentionPolicy getRetentionPolicy(@NonNull T annotation);

    private AnnotationMetadata buildInternalMulti(
        List<T> parents,
        T element,
        DefaultAnnotationMetadata annotationMetadata,
        boolean inheritTypeAnnotations,
        boolean declaredOnly,
        boolean allowAliases) {
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

            includeAnnotations(
                annotationMetadata,
                currentElement,
                parents.contains(currentElement),
                currentElement == element,
                annotationHierarchy,
                allowAliases
            );

        }
        if (!annotationMetadata.hasDeclaredStereotype(AnnotationUtil.SCOPE) && annotationMetadata.hasDeclaredStereotype(
            DefaultScope.class)) {
            Optional<String> value = annotationMetadata.stringValue(DefaultScope.class);
            value.ifPresent(name -> annotationMetadata.addDeclaredAnnotation(name, Collections.emptyMap()));
        }
        return annotationMetadata;
    }

    private void includeAnnotations(DefaultAnnotationMetadata annotationMetadata,
                                    T element,
                                    boolean originatingElementIsSameParent,
                                    boolean isDeclared,
                                    List<? extends A> annotationHierarchy,
                                    boolean allowAliases) {
        final ArrayList<? extends A> hierarchyCopy = new ArrayList<>(annotationHierarchy);
        final ListIterator<? extends A> listIterator = hierarchyCopy.listIterator();
        while (listIterator.hasNext()) {
            A annotationMirror = listIterator.next();
            String annotationName = getAnnotationTypeName(annotationMirror);
            if (isExcludedAnnotation(element, annotationName)) {
                continue;
            }
            if (DEPRECATED_ANNOTATION_NAMES.containsKey(annotationName)) {
                addWarning(element,
                    "Usages of deprecated annotation " + annotationName + " found. You should use " + DEPRECATED_ANNOTATION_NAMES.get(
                        annotationName) + " instead.");
            }

            final T annotationType = getTypeForAnnotation(annotationMirror);
            RetentionPolicy retentionPolicy = getRetentionPolicy(annotationType);
            Map<CharSequence, Object> annotationValues = populateAnnotationData(
                element,
                originatingElementIsSameParent,
                annotationMirror,
                annotationMetadata,
                isDeclared,
                retentionPolicy,
                allowAliases
            );

            if (isDeclared) {
                applyTransformations(
                    listIterator,
                    annotationMetadata,
                    true,
                    annotationType,
                    annotationValues,
                    Collections.emptyList(),
                    null,
                    annotationMetadata::addDeclaredRepeatable,
                    annotationMetadata::addDeclaredAnnotation);
            } else {
                if (isInheritedAnnotation(annotationMirror) || originatingElementIsSameParent) {
                    applyTransformations(
                        listIterator,
                        annotationMetadata,
                        false,
                        annotationType,
                        annotationValues,
                        Collections.emptyList(),
                        null,
                        annotationMetadata::addRepeatable,
                        annotationMetadata::addAnnotation);
                } else {
                    listIterator.remove();
                }
            }
        }
        for (A annotationMirror : hierarchyCopy) {
            String annotationTypeName = getAnnotationTypeName(annotationMirror);
            String packageName = NameUtils.getPackageName(annotationTypeName);
            if (!AnnotationUtil.STEREOTYPE_EXCLUDES.contains(packageName)) {
                processAnnotationStereotype(element,
                    originatingElementIsSameParent,
                    annotationMirror,
                    annotationMetadata,
                    isDeclared);
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

    /**
     * Test whether the annotation mirror is inherited.
     *
     * @param annotationType The mirror
     * @return True if it is
     */
    protected abstract boolean isInheritedAnnotationType(@NonNull T annotationType);

    private void buildStereotypeHierarchy(
        List<String> parents,
        T element,
        DefaultAnnotationMetadata metadata,
        boolean isDeclared,
        boolean isInherited,
        boolean allowAliases,
        List<String> excludes) {
        List<? extends A> annotationMirrors = getAnnotationsForType(element);

        LinkedList<AnnotationValueBuilder<?>> interceptorBindings = new LinkedList<>();
        final String lastParent = CollectionUtils.last(parents);
        if (!annotationMirrors.isEmpty()) {

            // first add the top level annotations
            List<A> topLevel = new ArrayList<>();
            final ListIterator<? extends A> listIterator = annotationMirrors.listIterator();
            while (listIterator.hasNext()) {
                A annotationMirror = listIterator.next();
                String annotationName = getAnnotationTypeName(annotationMirror);
                if (annotationName.equals(getElementName(element))) {
                    continue;
                }

                if (!AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationName) && !excludes.contains(annotationName)) {
                    if (AnnotationUtil.ADVICE_STEREOTYPES.contains(lastParent)) {
                        if (AnnotationUtil.ANN_INTERCEPTOR_BINDING.equals(annotationName)) {
                            // skip @InterceptorBinding stereotype handled in last round
                            continue;
                        }
                    }
                    topLevel.add(annotationMirror);
                    final T annotationTypeMirror = getTypeForAnnotation(annotationMirror);
                    final RetentionPolicy retentionPolicy = getRetentionPolicy(annotationTypeMirror);
                    Map<CharSequence, Object> data = populateAnnotationData(
                        element,
                        null,
                        annotationMirror,
                        metadata,
                        isDeclared,
                        retentionPolicy,
                        allowAliases
                    );

                    handleAnnotationStereotype(
                        parents,
                        metadata,
                        isDeclared,
                        isInherited,
                        interceptorBindings,
                        lastParent,
                        listIterator,
                        annotationTypeMirror,
                        annotationName,
                        data
                    );
                }
            }
            // remove any annotations stripped out by transformations
            topLevel.removeIf((a) -> !annotationMirrors.contains(a));
            // now add meta annotations
            for (A annotationMirror : topLevel) {
                processAnnotationStereotype(
                    parents,
                    annotationMirror,
                    metadata,
                    isDeclared,
                    isInherited
                );
            }
        }

        if (lastParent != null) {
            AnnotationMetadata modifiedStereotypes = MUTATED_ANNOTATION_METADATA.get(new MetadataKey<>(element));
            if (modifiedStereotypes != null && !modifiedStereotypes.isEmpty()) {
                Set<String> annotationNames = modifiedStereotypes.getAnnotationNames();
                handleModifiedStereotypes(parents,
                    metadata,
                    isDeclared,
                    isInherited,
                    excludes,
                    interceptorBindings,
                    lastParent,
                    modifiedStereotypes);

                for (String annotationName : annotationNames) {
                    AnnotationValue<Annotation> a = modifiedStereotypes.getAnnotation(annotationName);
                    if (a != null) {
                        String stereotypeName = a.getAnnotationName();
                        if (!AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(stereotypeName) && !excludes.contains(
                            stereotypeName)) {
                            final T annotationType = getAnnotationMirror(stereotypeName).orElse(null);
                            if (annotationType != null) {
                                Map<CharSequence, Object> values = a.getValues();
                                handleAnnotationStereotype(
                                    parents,
                                    metadata,
                                    isDeclared,
                                    isInherited,
                                    interceptorBindings,
                                    lastParent,
                                    null,
                                    annotationType,
                                    annotationName,
                                    values
                                );
                            } else {
                                // a meta annotation not actually on the classpath
                                if (isDeclared) {
                                    metadata.addDeclaredStereotype(
                                        parents,
                                        stereotypeName,
                                        a.getValues(),
                                        a.getRetentionPolicy()
                                    );
                                } else {
                                    metadata.addStereotype(
                                        parents,
                                        stereotypeName,
                                        a.getValues(),
                                        a.getRetentionPolicy()
                                    );
                                }
                            }

                        }
                    }
                }
            }
        }

        if (!interceptorBindings.isEmpty()) {
            for (AnnotationValueBuilder<?> interceptorBinding : interceptorBindings) {

                if (isDeclared) {
                    metadata.addDeclaredRepeatable(
                        AnnotationUtil.ANN_INTERCEPTOR_BINDINGS,
                        interceptorBinding.build()
                    );
                } else {
                    metadata.addRepeatable(
                        AnnotationUtil.ANN_INTERCEPTOR_BINDINGS,
                        interceptorBinding.build()
                    );
                }
            }
        }
    }

    private void handleModifiedStereotypes(List<String> parents,
                                           DefaultAnnotationMetadata metadata,
                                           boolean isDeclared,
                                           boolean isInherited,
                                           List<String> excludes,
                                           LinkedList<AnnotationValueBuilder<?>> interceptorBindings,
                                           String lastParent,
                                           AnnotationMetadata modifiedStereotypes) {
        final Set<String> stereotypeAnnotationNames = modifiedStereotypes.getStereotypeAnnotationNames();
        for (String stereotypeName : stereotypeAnnotationNames) {
            final AnnotationValue<Annotation> a = modifiedStereotypes.getAnnotation(stereotypeName);
            if (a != null && !AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(stereotypeName) && !excludes.contains(
                stereotypeName)) {
                final T annotationType = getAnnotationMirror(stereotypeName).orElse(null);
                final List<String> stereotypeParents = modifiedStereotypes.getAnnotationNamesByStereotype(
                    stereotypeName);
                List<String> resolvedParents = new ArrayList<>(parents);
                resolvedParents.addAll(stereotypeParents);
                Map<CharSequence, Object> values = a.getValues();
                if (annotationType != null) {

                    handleAnnotationStereotype(
                        resolvedParents,
                        metadata,
                        isDeclared,
                        isInherited,
                        interceptorBindings,
                        lastParent,
                        null,
                        annotationType,
                        stereotypeName,
                        values
                    );
                } else {
                    metadata.addStereotype(
                        resolvedParents,
                        stereotypeName,
                        values,
                        RetentionPolicy.RUNTIME
                    );
                }
            }
        }
    }

    private void handleAnnotationStereotype(
        List<String> parents,
        DefaultAnnotationMetadata metadata,
        boolean isDeclared,
        boolean isInherited,
        LinkedList<AnnotationValueBuilder<?>> interceptorBindings,
        String lastParent,
        @Nullable ListIterator<? extends A> listIterator,
        T annotationType,
        String annotationName,
        Map<CharSequence, Object> data) {
        addToInterceptorBindingsIfNecessary(interceptorBindings, lastParent, annotationName);

        final boolean hasInterceptorBinding = !interceptorBindings.isEmpty();
        if (hasInterceptorBinding && AnnotationUtil.ANN_INTERCEPTOR_BINDING.equals(annotationName)) {
            handleMemberBinding(metadata, lastParent, data);
            interceptorBindings.getLast().members(data);
            return;
        }
        // special case: don't add stereotype for @Nonnull when it's marked as UNKNOWN/MAYBE/NEVER.
        // https://github.com/micronaut-projects/micronaut-core/issues/6795
        if (annotationName.equals("javax.annotation.Nonnull")) {
            String when = Objects.toString(data.get("when"));
            if (when.equals("UNKNOWN") || when.equals("MAYBE") || when.equals("NEVER")) {
                return;
            }
        }
        if (hasInterceptorBinding && Type.class.getName().equals(annotationName)) {
            final Object o = data.get(AnnotationMetadata.VALUE_MEMBER);
            AnnotationClassValue<?> interceptorType = null;
            if (o instanceof AnnotationClassValue) {
                interceptorType = (AnnotationClassValue<?>) o;
            } else if (o instanceof AnnotationClassValue[]) {
                final AnnotationClassValue[] values = (AnnotationClassValue[]) o;
                if (values.length > 0) {
                    interceptorType = values[0];
                }
            }
            if (interceptorType != null) {
                for (AnnotationValueBuilder<?> interceptorBinding : interceptorBindings) {
                    interceptorBinding.member("interceptorType", interceptorType);
                }
            }
        }

        if (isDeclared) {
            applyTransformations(listIterator, metadata, true, annotationType, data, parents, interceptorBindings,
                (string, av) -> metadata.addDeclaredRepeatableStereotype(parents, string, av),
                (string, values, rp) -> metadata.addDeclaredStereotype(parents, string, values, rp));
        } else if (isInherited) {
            applyTransformations(listIterator, metadata, false, annotationType, data, parents, interceptorBindings,
                (string, av) -> metadata.addRepeatableStereotype(parents, string, av),
                (string, values, rp) -> metadata.addStereotype(parents, string, values, rp));
        } else {
            if (listIterator != null) {
                listIterator.remove();
            }
        }
    }

    private void handleMemberBinding(DefaultAnnotationMetadata metadata, String lastParent, Map<CharSequence, Object> data) {
        if (data.containsKey(InterceptorBindingQualifier.META_MEMBER_MEMBERS)) {
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
        }
    }

    /**
     * Gets the annotation members for the given type.
     *
     * @param annotationType The annotation type
     * @return The members
     * @since 3.3.0
     */
    protected abstract @NonNull
    Map<String, ? extends T> getAnnotationMembers(@NonNull String annotationType);

    /**
     * Returns true if a simple meta annotation is present for the given element and annotation type.
     *
     * @param element    The element
     * @param simpleName The simple name, ie {@link Class#getSimpleName()}
     * @return True an annotation with the given simple name exists on the element
     */
    protected abstract boolean hasSimpleAnnotation(T element, String simpleName);

    private void addToInterceptorBindingsIfNecessary(LinkedList<AnnotationValueBuilder<?>> interceptorBindings,
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

    private void buildStereotypeHierarchy(
        List<String> parents,
        AnnotationValue<?> annotationValue,
        DefaultAnnotationMetadata metadata,
        boolean isDeclared,
        List<String> excludes) {
        List<AnnotationValue<?>> annotationMirrors = annotationValue.getStereotypes();

        LinkedList<AnnotationValueBuilder<?>> interceptorBindings = new LinkedList<>();
        final String lastParent = CollectionUtils.last(parents);
        if (CollectionUtils.isNotEmpty(annotationMirrors)) {

            // first add the top level annotations
            List<AnnotationValue<?>> topLevel = new ArrayList<>();
            for (AnnotationValue<?> annotationMirror : annotationMirrors) {
                String annotationName = annotationMirror.getAnnotationName();
                if (annotationName.equals(annotationValue.getAnnotationName())) {
                    continue;
                }

                if (!AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationName) && !excludes.contains(annotationName)) {
                    if (AnnotationUtil.ADVICE_STEREOTYPES.contains(lastParent)) {
                        if (AnnotationUtil.ANN_INTERCEPTOR_BINDING.equals(annotationName)) {
                            // skip @InterceptorBinding stereotype handled in last round
                            continue;
                        }
                    }
                    addToInterceptorBindingsIfNecessary(interceptorBindings, lastParent, annotationName);

                    final RetentionPolicy retentionPolicy = annotationMirror.getRetentionPolicy();

                    topLevel.add(annotationMirror);

                    Map<CharSequence, Object> data = annotationMirror.getValues();

                    final boolean hasInterceptorBinding = !interceptorBindings.isEmpty();
                    if (hasInterceptorBinding && AnnotationUtil.ANN_INTERCEPTOR_BINDING.equals(annotationName)) {
                        handleMemberBinding(metadata, lastParent, data);
                        interceptorBindings.getLast().members(data);
                        continue;
                    }
                    if (hasInterceptorBinding && Type.class.getName().equals(annotationName)) {
                        final Object o = data.get(AnnotationMetadata.VALUE_MEMBER);
                        AnnotationClassValue<?> interceptorType = null;
                        if (o instanceof AnnotationClassValue) {
                            interceptorType = (AnnotationClassValue<?>) o;
                        } else if (o instanceof AnnotationClassValue[]) {
                            final AnnotationClassValue[] values = (AnnotationClassValue[]) o;
                            if (values.length > 0) {
                                interceptorType = values[0];
                            }
                        }
                        if (interceptorType != null) {
                            for (AnnotationValueBuilder<?> interceptorBinding : interceptorBindings) {
                                interceptorBinding.member("interceptorType", interceptorType);
                            }
                        }
                    }

                    if (isDeclared) {
                        metadata.addDeclaredStereotype(parents, annotationName, data, retentionPolicy);
                    } else {
                        metadata.addStereotype(parents, annotationName, data, retentionPolicy);
                    }
                }
            }
            // now add meta annotations
            for (AnnotationValue<?> annotationMirror : topLevel) {
                processAnnotationStereotype(parents, annotationMirror, metadata, isDeclared);
            }
        }

        if (!interceptorBindings.isEmpty()) {
            for (AnnotationValueBuilder<?> interceptorBinding : interceptorBindings) {

                if (isDeclared) {
                    metadata.addDeclaredRepeatable(
                        AnnotationUtil.ANN_INTERCEPTOR_BINDINGS,
                        interceptorBinding.build()
                    );
                } else {
                    metadata.addRepeatable(
                        AnnotationUtil.ANN_INTERCEPTOR_BINDINGS,
                        interceptorBinding.build()
                    );
                }
            }
        }
    }

    private void processAnnotationStereotype(
        T element,
        boolean originatingElementIsSameParent,
        A annotationMirror,
        DefaultAnnotationMetadata annotationMetadata,
        boolean isDeclared) {
        T annotationType = getTypeForAnnotation(annotationMirror);
        String parentAnnotationName = getAnnotationTypeName(annotationMirror);
        if (!parentAnnotationName.endsWith(".Nullable")) {
            processAnnotationStereotypes(
                annotationMetadata,
                isDeclared,
                isInheritedAnnotation(annotationMirror) || originatingElementIsSameParent,
                annotationType,
                parentAnnotationName,
                Collections.emptyList()
            );
        }
    }

    private void processAnnotationStereotypes(
        DefaultAnnotationMetadata annotationMetadata,
        boolean isDeclared,
        boolean isInherited,
        T annotationType,
        String annotationName,
        List<String> excludes) {
        List<String> parentAnnotations = new ArrayList<>();
        parentAnnotations.add(annotationName);
        buildStereotypeHierarchy(
            parentAnnotations,
            annotationType,
            annotationMetadata,
            isDeclared,
            isInherited,
            true,
            excludes
        );
    }

    private void processAnnotationStereotypes(DefaultAnnotationMetadata annotationMetadata,
                                              boolean isDeclared,
                                              AnnotationValue<?> annotation,
                                              List<String> parents) {
        List<String> parentAnnotations = new ArrayList<>(parents);
        parentAnnotations.add(annotation.getAnnotationName());
        buildStereotypeHierarchy(
            parentAnnotations,
            annotation,
            annotationMetadata,
            isDeclared,
            Collections.emptyList()
        );
    }

    private void processAnnotationStereotype(
        List<String> parents,
        A annotationMirror,
        DefaultAnnotationMetadata metadata,
        boolean isDeclared,
        boolean isInherited) {
        T typeForAnnotation = getTypeForAnnotation(annotationMirror);
        String annotationTypeName = getAnnotationTypeName(annotationMirror);
        processAnnotationStereotype(parents, typeForAnnotation, annotationTypeName, metadata, isDeclared, isInherited);
    }

    private void processAnnotationStereotype(
        List<String> parents,
        T annotationType,
        String annotationTypeName,
        DefaultAnnotationMetadata metadata,
        boolean isDeclared,
        boolean isInherited) {
        List<String> stereoTypeParents = new ArrayList<>(parents);
        stereoTypeParents.add(annotationTypeName);
        buildStereotypeHierarchy(stereoTypeParents,
            annotationType,
            metadata,
            isDeclared,
            isInherited,
            true,
            Collections.emptyList());
    }

    private void processAnnotationStereotype(
        List<String> parents,
        AnnotationValue<?> annotationType,
        DefaultAnnotationMetadata metadata,
        boolean isDeclared) {
        List<String> stereoTypeParents = new ArrayList<>(parents);
        stereoTypeParents.add(annotationType.getAnnotationName());
        buildStereotypeHierarchy(stereoTypeParents, annotationType, metadata, isDeclared, Collections.emptyList());
    }

    private void applyTransformations(@Nullable ListIterator<? extends A> hierarchyIterator,
                                      DefaultAnnotationMetadata annotationMetadata,
                                      boolean isDeclared,
                                      @NonNull T annotationType,
                                      Map<CharSequence, Object> data,
                                      List<String> parents,
                                      @Nullable LinkedList<AnnotationValueBuilder<?>> interceptorBindings,
                                      BiConsumer<String, AnnotationValue> addRepeatableAnnotation,
                                      TriConsumer<String, Map<CharSequence, Object>, RetentionPolicy> addAnnotation) {
        applyTransformationsForAnnotationType(
            hierarchyIterator,
            annotationMetadata,
            isDeclared,
            annotationType,
            data,
            parents,
            interceptorBindings,
            addRepeatableAnnotation,
            addAnnotation
        );
    }

    private void applyTransformationsForAnnotationType(
        @Nullable ListIterator<? extends A> hierarchyIterator,
        DefaultAnnotationMetadata annotationMetadata,
        boolean isDeclared,
        @NonNull T annotationType,
        Map<CharSequence, Object> data,
        List<String> parents,
        @Nullable LinkedList<AnnotationValueBuilder<?>> interceptorBindings,
        BiConsumer<String, AnnotationValue> addRepeatableAnnotation,
        TriConsumer<String, Map<CharSequence, Object>, RetentionPolicy> addAnnotation) {
        String annotationName = getElementName(annotationType);
        String packageName = NameUtils.getPackageName(annotationName);
        String repeatableName = getRepeatableNameForType(annotationType);

        RetentionPolicy retentionPolicy = getRetentionPolicy(annotationType);
        List<AnnotationRemapper> annotationRemappers = ANNOTATION_REMAPPERS.get(packageName);
        List<AnnotationTransformer<Annotation>> annotationTransformers = getAnnotationTransformers(annotationName);
        boolean remapped = CollectionUtils.isNotEmpty(annotationRemappers);
        boolean transformed = CollectionUtils.isNotEmpty(annotationTransformers);

        if (repeatableName != null) {
            if (!remapped && !transformed) {
                io.micronaut.core.annotation.AnnotationValue av = new io.micronaut.core.annotation.AnnotationValue(annotationName,
                    data);
                addRepeatableAnnotation.accept(repeatableName, av);
            } else if (remapped) {

                VisitorContext visitorContext = createVisitorContext();
                io.micronaut.core.annotation.AnnotationValue<?> av =
                    new io.micronaut.core.annotation.AnnotationValue<>(annotationName, data);
                AnnotationValue<?> repeatableAnn = AnnotationValue.builder(repeatableName)
                    .values(av)
                    .build();
                boolean wasRemapped = false;
                for (AnnotationRemapper annotationRemapper : annotationRemappers) {
                    List<AnnotationValue<?>> remappedRepeatable = annotationRemapper.remap(repeatableAnn, visitorContext);
                    List<AnnotationValue<?>> remappedValue = annotationRemapper.remap(av, visitorContext);
                    if (CollectionUtils.isNotEmpty(remappedRepeatable)) {
                        for (AnnotationValue<?> repeatable : remappedRepeatable) {
                            for (AnnotationValue<?> rmv : remappedValue) {
                                if (rmv == av && remappedValue.size() == 1) {
                                    // bail, the re-mapper just returned the same annotation
                                    addRepeatableAnnotation.accept(repeatableName, av);
                                    break;
                                } else {
                                    wasRemapped = true;
                                    addRepeatableAnnotation.accept(repeatable.getAnnotationName(), rmv);
                                }
                            }
                        }
                    }
                }
                if (wasRemapped && hierarchyIterator != null) {
                    hierarchyIterator.remove();
                }
            } else {
                VisitorContext visitorContext = createVisitorContext();
                io.micronaut.core.annotation.AnnotationValue<Annotation> av =
                    new io.micronaut.core.annotation.AnnotationValue<>(annotationName, data);
                AnnotationValue<Annotation> repeatableAnn = AnnotationValue.builder(repeatableName).values(av).build();
                final List<AnnotationTransformer<Annotation>> repeatableTransformers = getAnnotationTransformers(repeatableName);
                if (hierarchyIterator != null) {
                    hierarchyIterator.remove();
                }
                if (CollectionUtils.isNotEmpty(repeatableTransformers)) {
                    for (AnnotationTransformer<Annotation> repeatableTransformer : repeatableTransformers) {
                        final List<AnnotationValue<?>> transformedRepeatable = repeatableTransformer.transform(repeatableAnn,
                            visitorContext);
                        for (AnnotationValue<?> annotationValue : transformedRepeatable) {
                            for (AnnotationTransformer<Annotation> transformer : annotationTransformers) {
                                final List<AnnotationValue<?>> tav = transformer.transform(av, visitorContext);
                                for (AnnotationValue<?> value : tav) {
                                    addRepeatableAnnotation.accept(annotationValue.getAnnotationName(), value);
                                    if (CollectionUtils.isNotEmpty(value.getStereotypes())) {
                                        addTransformedStereotypes(annotationMetadata, isDeclared, value, parents);
                                    } else {
                                        addTransformedStereotypes(annotationMetadata,
                                            isDeclared,
                                            value.getAnnotationName(),
                                            parents);
                                    }
                                }
                            }

                        }
                    }
                } else {
                    for (AnnotationTransformer<Annotation> transformer : annotationTransformers) {
                        final List<AnnotationValue<?>> tav = transformer.transform(av, visitorContext);
                        for (AnnotationValue<?> value : tav) {
                            addRepeatableAnnotation.accept(repeatableName, value);
                            if (CollectionUtils.isNotEmpty(value.getStereotypes())) {
                                addTransformedStereotypes(annotationMetadata, isDeclared, value, parents);
                            } else {
                                addTransformedStereotypes(annotationMetadata, isDeclared, value.getAnnotationName(), parents);
                            }
                        }
                    }
                }
            }
        } else {
            if (!remapped && !transformed) {
                addAnnotation.accept(annotationName, data, retentionPolicy);
            } else if (remapped) {
                io.micronaut.core.annotation.AnnotationValue<?> av = new io.micronaut.core.annotation.AnnotationValue(
                    annotationName,
                    data);
                VisitorContext visitorContext = createVisitorContext();

                boolean wasRemapped = false;
                for (AnnotationRemapper annotationRemapper : annotationRemappers) {
                    List<AnnotationValue<?>> remappedValues = annotationRemapper.remap(av, visitorContext);
                    if (CollectionUtils.isNotEmpty(remappedValues)) {
                        for (AnnotationValue<?> annotationValue : remappedValues) {
                            if (annotationValue == av && remappedValues.size() == 1) {
                                // bail, the re-mapper just returned the same annotation
                                addAnnotation.accept(annotationName, data, retentionPolicy);
                                break;
                            } else {
                                wasRemapped = true;
                                final String transformedAnnotationName = handleTransformedAnnotationValue(parents,
                                    interceptorBindings,
                                    addRepeatableAnnotation,
                                    addAnnotation,
                                    annotationValue,
                                    annotationMetadata);
                                if (CollectionUtils.isNotEmpty(annotationValue.getStereotypes())) {
                                    addTransformedStereotypes(annotationMetadata, isDeclared, annotationValue, parents);
                                } else {
                                    addTransformedStereotypes(annotationMetadata, isDeclared, transformedAnnotationName, parents);
                                }
                            }
                        }
                    }
                }
                if (wasRemapped && hierarchyIterator != null) {
                    hierarchyIterator.remove();
                }
            } else {
                io.micronaut.core.annotation.AnnotationValue<Annotation> av =
                    new io.micronaut.core.annotation.AnnotationValue<>(annotationName, data);
                VisitorContext visitorContext = createVisitorContext();
                if (hierarchyIterator != null) {
                    hierarchyIterator.remove();
                }
                for (AnnotationTransformer<Annotation> annotationTransformer : annotationTransformers) {
                    final List<AnnotationValue<?>> transformedValues = annotationTransformer.transform(av, visitorContext);
                    for (AnnotationValue<?> transformedValue : transformedValues) {
                        final String transformedAnnotationName = handleTransformedAnnotationValue(parents,
                            interceptorBindings,
                            addRepeatableAnnotation,
                            addAnnotation,
                            transformedValue,
                            annotationMetadata

                        );
                        if (CollectionUtils.isNotEmpty(transformedValue.getStereotypes())) {
                            addTransformedStereotypes(annotationMetadata, isDeclared, transformedValue, parents);
                        } else {
                            addTransformedStereotypes(annotationMetadata, isDeclared, transformedAnnotationName, parents);
                        }
                    }
                }
            }
        }
    }

    private String handleTransformedAnnotationValue(List<String> parents,
                                                    LinkedList<AnnotationValueBuilder<?>> interceptorBindings,
                                                    BiConsumer<String, AnnotationValue> addRepeatableAnnotation,
                                                    TriConsumer<String, Map<CharSequence, Object>, RetentionPolicy> addAnnotation,
                                                    AnnotationValue<?> transformedValue,
                                                    DefaultAnnotationMetadata annotationMetadata) {
        final String transformedAnnotationName = transformedValue.getAnnotationName();
        addTransformedInterceptorBindingsIfNecessary(
            parents,
            interceptorBindings,
            transformedValue,
            transformedAnnotationName,
            annotationMetadata
        );
        final String transformedRepeatableName;

        if (isRepeatableCandidate(transformedAnnotationName)) {
            String resolvedName = null;
            // wrap with exception handling just in case there is any problems loading the type
            try {
                resolvedName = getAnnotationMirror(transformedAnnotationName)
                    .map(this::getRepeatableNameForType)
                    .orElse(null);
            } catch (Exception e) {
                // ignore
            }
            transformedRepeatableName = resolvedName;
        } else {
            transformedRepeatableName = null;
        }

        if (transformedRepeatableName != null) {
            addRepeatableAnnotation.accept(transformedRepeatableName, transformedValue);
        } else {
            addAnnotation.accept(transformedAnnotationName,
                transformedValue.getValues(),
                transformedValue.getRetentionPolicy());
        }
        return transformedAnnotationName;
    }

    private void addTransformedInterceptorBindingsIfNecessary(List<String> parents,
                                                              LinkedList<AnnotationValueBuilder<?>> interceptorBindings,
                                                              AnnotationValue<?> transformedValue,
                                                              String transformedAnnotationName,
                                                              DefaultAnnotationMetadata annotationMetadata) {
        if (interceptorBindings != null && !parents.isEmpty() && AnnotationUtil.ANN_INTERCEPTOR_BINDING.equals(
            transformedAnnotationName)) {
            final AnnotationValueBuilder<Annotation> newBuilder = AnnotationValue
                .builder(transformedAnnotationName, transformedValue.getRetentionPolicy())
                .members(transformedValue.getValues());
            if (!transformedValue.contains(AnnotationMetadata.VALUE_MEMBER)) {
                newBuilder.value(parents.get(parents.size() - 1));
            }
            if (transformedValue.booleanValue("bindMembers").orElse(false)) {

                final String parent = CollectionUtils.last(parents);
                final HashMap<CharSequence, Object> data = new HashMap<>(transformedValue.getValues());
                handleMemberBinding(
                    annotationMetadata,
                    parent,
                    data
                );
                newBuilder.members(data);
            }
            interceptorBindings.add(newBuilder);
        }
    }

    private List<AnnotationValue<?>> remapAnnotation(String annotationName) {
        String packageName = NameUtils.getPackageName(annotationName);
        List<AnnotationRemapper> annotationRemappers = ANNOTATION_REMAPPERS.get(packageName);
        List<AnnotationValue<?>> mappedAnnotations = new ArrayList<>();
        if (annotationRemappers == null || annotationRemappers.isEmpty()) {
            mappedAnnotations.add(AnnotationValue.builder(annotationName).build());
            return mappedAnnotations;
        }

        VisitorContext visitorContext = createVisitorContext();
        io.micronaut.core.annotation.AnnotationValue<?> av = new AnnotationValue<>(annotationName);

        for (AnnotationRemapper annotationRemapper : annotationRemappers) {
            List<AnnotationValue<?>> remappedValues = annotationRemapper.remap(av, visitorContext);
            if (CollectionUtils.isNotEmpty(remappedValues)) {
                for (AnnotationValue<?> annotationValue : remappedValues) {
                    if (annotationValue == av && remappedValues.size() == 1) {
                        // bail, the re-mapper just returned the same annotation
                        break;
                    } else {
                        mappedAnnotations.add(annotationValue);
                    }
                }
            }
        }
        return mappedAnnotations;
    }

    private boolean isRepeatableCandidate(String transformedAnnotationName) {
        return !AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(transformedAnnotationName) &&
            !AnnotationUtil.NULLABLE.equals(transformedAnnotationName) &&
            !AnnotationUtil.NON_NULL.equals(transformedAnnotationName);
    }

    private void addTransformedStereotypes(DefaultAnnotationMetadata annotationMetadata,
                                           boolean isDeclared,
                                           String transformedAnnotationName,
                                           List<String> parents) {
        if (!AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(transformedAnnotationName)) {
            String packageName = NameUtils.getPackageName(transformedAnnotationName);
            if (!AnnotationUtil.STEREOTYPE_EXCLUDES.contains(packageName)) {
                getAnnotationMirror(transformedAnnotationName).ifPresent(a -> processAnnotationStereotypes(
                    annotationMetadata,
                    isDeclared,
                    false,
                    a,
                    transformedAnnotationName,
                    parents
                ));
            }
        }
    }

    private void addTransformedStereotypes(DefaultAnnotationMetadata annotationMetadata,
                                           boolean isDeclared,
                                           AnnotationValue<?> transformedAnnotation,
                                           List<String> parents) {
        String transformedAnnotationName = transformedAnnotation.getAnnotationName();
        if (!AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(transformedAnnotationName)) {
            String packageName = NameUtils.getPackageName(transformedAnnotationName);
            if (!AnnotationUtil.STEREOTYPE_EXCLUDES.contains(packageName)) {
                processAnnotationStereotypes(
                    annotationMetadata,
                    isDeclared,
                    transformedAnnotation,
                    parents);
            }
        }
    }

    /**
     * Used to store metadata mutations at compilation time. Not for public consumption.
     *
     * @param owningType The owning type
     * @param element    The element
     * @return True if the annotation metadata was mutated
     */
    @Internal
    public boolean isMetadataMutated(T owningType, T element) {
        if (element != null) {
            CacheEntry entry = MUTATED_ANNOTATION_METADATA.get(new MetadataKey(owningType, element));
            return entry != null && entry.isMutated();
        }
        return false;
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
     * Returns whether the given annotation is a mapped annotation.
     *
     * @param annotationName The annotation name
     * @return True if it is
     */
    @Internal
    public static boolean isAnnotationMapped(@Nullable String annotationName) {
        return annotationName != null &&
            (
                ANNOTATION_MAPPERS.containsKey(annotationName) ||
                    ANNOTATION_TRANSFORMERS.containsKey(annotationName) ||
                    ANNOTATION_TRANSFORMERS.keySet().stream().anyMatch(annotationName::startsWith));
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
    public <A2 extends Annotation> AnnotationMetadata annotate(
        AnnotationMetadata annotationMetadata,
        AnnotationValue<A2> annotationValue) {
        String annotationName = annotationValue.getAnnotationName();
        final boolean isReference = annotationMetadata instanceof AnnotationMetadataReference;
        boolean isReferenceOrEmpty = annotationMetadata == AnnotationMetadata.EMPTY_METADATA || isReference;
        if (annotationMetadata instanceof DefaultAnnotationMetadata || isReferenceOrEmpty) {
            final DefaultAnnotationMetadata defaultMetadata = isReferenceOrEmpty
                ? new MutableAnnotationMetadata()
                : (DefaultAnnotationMetadata) annotationMetadata;
            T annotationMirror = getAnnotationMirror(annotationName).orElse(null);
            if (annotationMirror != null) {
                applyTransformationsForAnnotationType(
                    null,
                    defaultMetadata,
                    true,
                    annotationMirror,
                    annotationValue.getValues(),
                    Collections.emptyList(),
                    new LinkedList<>(),
                    defaultMetadata::addDeclaredRepeatable,
                    defaultMetadata::addDeclaredAnnotation
                );
                processAnnotationDefaults(
                    annotationMirror,
                    defaultMetadata,
                    annotationName,
                    () -> readAnnotationDefaultValues(annotationName, annotationMirror)
                );
                processAnnotationStereotypes(
                    defaultMetadata,
                    true,
                    isInheritedAnnotationType(annotationMirror),
                    annotationMirror,
                    annotationName,
                    DEFAULT_ANNOTATE_EXCLUDES
                );
            } else {
                defaultMetadata.addDeclaredAnnotation(
                    annotationName,
                    annotationValue.getValues()
                );
            }

            if (isReference) {
                AnnotationMetadataReference ref = (AnnotationMetadataReference) annotationMetadata;
                return new AnnotationMetadataHierarchy(ref, defaultMetadata);
            } else {
                return defaultMetadata;
            }
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            AnnotationMetadataHierarchy hierarchy = (AnnotationMetadataHierarchy) annotationMetadata;
            AnnotationMetadata declaredMetadata = annotate(hierarchy.getDeclaredMetadata(), annotationValue);
            return hierarchy.createSibling(
                declaredMetadata
            );
        }
        return annotationMetadata;
    }

    /**
     * Removes an annotation from the given annotation metadata.
     *
     * @param annotationMetadata The annotation metadata
     * @param annotationType     The annotation type
     * @return The updated metadata
     * @since 3.0.0
     */
    public AnnotationMetadata removeAnnotation(AnnotationMetadata annotationMetadata, String annotationType) {
        // we only care if the metadata is an hierarchy or default mutable
        final boolean isHierarchy = annotationMetadata instanceof AnnotationMetadataHierarchy;
        AnnotationMetadata declaredMetadata = annotationMetadata;
        if (isHierarchy) {
            declaredMetadata = annotationMetadata.getDeclaredMetadata();
        }
        // if it is anything else other than DefaultAnnotationMetadata here it is probably empty
        // in which case nothing needs to be done
        if (declaredMetadata instanceof DefaultAnnotationMetadata) {
            final DefaultAnnotationMetadata defaultMetadata = (DefaultAnnotationMetadata) declaredMetadata;
            T annotationMirror = getAnnotationMirror(annotationType).orElse(null);
            if (annotationMirror != null) {
                String repeatableName = getRepeatableNameForType(annotationMirror);
                if (repeatableName != null) {
                    defaultMetadata.removeAnnotation(repeatableName);
                } else {
                    defaultMetadata.removeAnnotation(annotationType);
                }
            } else {
                defaultMetadata.removeAnnotation(annotationType);
            }

            if (isHierarchy) {
                return ((AnnotationMetadataHierarchy) annotationMetadata).createSibling(
                    declaredMetadata
                );
            } else {
                return declaredMetadata;
            }
        }
        return annotationMetadata;
    }

    /**
     * Removes an annotation from the given annotation metadata.
     *
     * @param annotationMetadata The annotation metadata
     * @param annotationType     The annotation type
     * @return The updated metadata
     * @since 3.0.0
     */
    public AnnotationMetadata removeStereotype(AnnotationMetadata annotationMetadata, String annotationType) {
        // we only care if the metadata is an hierarchy or default mutable
        final boolean isHierarchy = annotationMetadata instanceof AnnotationMetadataHierarchy;
        AnnotationMetadata declaredMetadata = annotationMetadata;
        if (isHierarchy) {
            declaredMetadata = annotationMetadata.getDeclaredMetadata();
        }
        // if it is anything else other than DefaultAnnotationMetadata here it is probably empty
        // in which case nothing needs to be done
        if (declaredMetadata instanceof DefaultAnnotationMetadata) {
            final DefaultAnnotationMetadata defaultMetadata = (DefaultAnnotationMetadata) declaredMetadata;
            T annotationMirror = getAnnotationMirror(annotationType).orElse(null);
            if (annotationMirror != null) {
                String repeatableName = getRepeatableNameForType(annotationMirror);
                if (repeatableName != null) {
                    defaultMetadata.removeStereotype(repeatableName);
                } else {
                    defaultMetadata.removeStereotype(annotationType);
                }
            } else {
                defaultMetadata.removeStereotype(annotationType);
            }

            if (isHierarchy) {
                return ((AnnotationMetadataHierarchy) annotationMetadata).createSibling(
                    declaredMetadata
                );
            } else {
                return declaredMetadata;
            }
        }
        return annotationMetadata;
    }

    /**
     * Removes an annotation from the metadata for the given predicate.
     *
     * @param annotationMetadata The annotation metadata
     * @param predicate          The predicate
     * @param <T1>               The annotation type
     * @return The potentially modified metadata
     */
    public @NonNull <T1 extends Annotation> AnnotationMetadata removeAnnotationIf(
        @NonNull AnnotationMetadata annotationMetadata,
        @NonNull Predicate<AnnotationValue<T1>> predicate) {
        // we only care if the metadata is an hierarchy or default mutable
        final boolean isHierarchy = annotationMetadata instanceof AnnotationMetadataHierarchy;
        AnnotationMetadata declaredMetadata = annotationMetadata;
        if (isHierarchy) {
            declaredMetadata = annotationMetadata.getDeclaredMetadata();
        }
        // if it is anything else other than DefaultAnnotationMetadata here it is probably empty
        // in which case nothing needs to be done
        if (declaredMetadata instanceof DefaultAnnotationMetadata) {
            final DefaultAnnotationMetadata defaultMetadata = (DefaultAnnotationMetadata) declaredMetadata;

            defaultMetadata.removeAnnotationIf(predicate);

            if (isHierarchy) {
                return ((AnnotationMetadataHierarchy) annotationMetadata).createSibling(
                    declaredMetadata
                );
            } else {
                return declaredMetadata;
            }
        }
        return annotationMetadata;
    }

    /**
     * The caching entry.
     *
     * @author Denis Stepanov
     * @since 4.0.0
     */
    public interface CacheEntry extends AnnotationMetadataDelegate {

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
     * @param <T> the element type
     */
    private static class MetadataKey<T> {
        final T[] elements;
        final int hashCode;

        MetadataKey(T... elements) {
            this.elements = elements;
            this.hashCode = Objects.hash(elements);
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MetadataKey<?> that = (MetadataKey<?>) o;
            return Arrays.equals(elements, that.elements);
        }

        @Override
        public int hashCode() {
            return hashCode;
        }
    }

    private interface TriConsumer<T, U, V> {
        void accept(T t, U u, V v);
    }

    private static final class DefaultCacheEntry implements CacheEntry {
        @Nullable
        private AnnotationMetadata annotationMetadata;
        private boolean isMutated;

        public DefaultCacheEntry(AnnotationMetadata annotationMetadata) {
            if (annotationMetadata instanceof CacheEntry) {
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
            if (annotationMetadata instanceof CacheEntry) {
                throw new IllegalStateException();
            }
            this.annotationMetadata = annotationMetadata;
            isMutated = true;
        }
    }

}
