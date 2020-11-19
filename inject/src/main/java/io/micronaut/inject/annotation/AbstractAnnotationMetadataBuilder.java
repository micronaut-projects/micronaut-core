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
import io.micronaut.core.annotation.*;
import io.micronaut.core.io.service.ServiceDefinition;
import io.micronaut.core.io.service.SoftServiceLoader;
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.visitor.VisitorContext;

import edu.umd.cs.findbugs.annotations.NonNull;
import edu.umd.cs.findbugs.annotations.Nullable;
import javax.inject.Scope;
import java.lang.annotation.Annotation;
import java.lang.annotation.RetentionPolicy;
import java.util.*;

/**
 * An abstract implementation that builds {@link AnnotationMetadata}.
 *
 * @param <T> The element type
 * @param <A> The annotation type
 * @author Graeme Rocher
 * @since 1.0
 */
public abstract class AbstractAnnotationMetadataBuilder<T, A> {

    private static final Map<String, List<AnnotationMapper<?>>> ANNOTATION_MAPPERS = new HashMap<>(10);
    private static final Map<String, List<AnnotationTransformer<Annotation>>> ANNOTATION_TRANSFORMERS = new HashMap<>(5);
    private static final Map<String, List<AnnotationRemapper>> ANNOTATION_REMAPPERS = new HashMap<>(5);
    private static final Map<MetadataKey, AnnotationMetadata> MUTATED_ANNOTATION_METADATA = new HashMap<>(100);
    private static final List<String> DEFAULT_ANNOTATE_EXCLUDES = Arrays.asList(Internal.class.getName(), Experimental.class.getName());

    static {
        SoftServiceLoader<AnnotationMapper> serviceLoader = SoftServiceLoader.load(AnnotationMapper.class, AbstractAnnotationMetadataBuilder.class.getClassLoader());
        for (ServiceDefinition<AnnotationMapper> definition : serviceLoader) {
            if (definition.isPresent()) {
                AnnotationMapper mapper = definition.load();
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
        }

        SoftServiceLoader<AnnotationTransformer> transformerSoftServiceLoader =
                SoftServiceLoader.load(AnnotationTransformer.class, AbstractAnnotationMetadataBuilder.class.getClassLoader());
        for (ServiceDefinition<AnnotationTransformer> definition : transformerSoftServiceLoader) {
            if (definition.isPresent()) {
                AnnotationTransformer transformer = definition.load();
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
        }

        SoftServiceLoader<AnnotationRemapper> remapperLoader = SoftServiceLoader.load(AnnotationRemapper.class, AbstractAnnotationMetadataBuilder.class.getClassLoader());
        for (ServiceDefinition<AnnotationRemapper> definition : remapperLoader) {
            if (definition.isPresent()) {
                AnnotationRemapper mapper = definition.load();
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
    }

    private boolean validating = true;
    private final Set<T> erroneousElements = new HashSet<>();

    /**
     * Default constructor.
     */
    protected AbstractAnnotationMetadataBuilder() {

    }

    /**
     * Build only metadata for declared annotations.
     *
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata buildDeclared(T element) {
        final AnnotationMetadata existing = MUTATED_ANNOTATION_METADATA.get(element);
        if (existing != null) {
            return existing;
        } else {

            DefaultAnnotationMetadata annotationMetadata = new DefaultAnnotationMetadata();

            try {
                AnnotationMetadata metadata = buildInternal(null, element, annotationMetadata, true, true);
                if (metadata.isEmpty()) {
                    return AnnotationMetadata.EMPTY_METADATA;
                }
                return metadata;
            } catch (RuntimeException e) {
                if ("org.eclipse.jdt.internal.compiler.problem.AbortCompilation".equals(e.getClass().getName())) {
                    // workaround for a bug in the Eclipse APT implementation. See bug 541466 on their Bugzilla.
                    return AnnotationMetadata.EMPTY_METADATA;
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Build metadata for the given element, including any metadata that is inherited via method or type overrides.
     *
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata buildOverridden(T element) {
        final AnnotationMetadata existing = MUTATED_ANNOTATION_METADATA.get(new MetadataKey(getDeclaringType(element), element));
        if (existing != null) {
            return existing;
        } else {

            DefaultAnnotationMetadata annotationMetadata = new DefaultAnnotationMetadata();

            try {
                AnnotationMetadata metadata = buildInternal(null, element, annotationMetadata, false, false);
                if (metadata.isEmpty()) {
                    return AnnotationMetadata.EMPTY_METADATA;
                }
                return metadata;
            } catch (RuntimeException e) {
                if ("org.eclipse.jdt.internal.compiler.problem.AbortCompilation".equals(e.getClass().getName())) {
                    // workaround for a bug in the Eclipse APT implementation. See bug 541466 on their Bugzilla.
                    return AnnotationMetadata.EMPTY_METADATA;
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Build the meta data for the given element. If the element is a method the class metadata will be included.
     *
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata build(T element) {
        String declaringType = getDeclaringType(element);
        return build(declaringType, element);
    }

    /**
     * Build the meta data for the given element. If the element is a method the class metadata will be included.
     *
     * @param declaringType The declaring type
     * @param element       The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata build(String declaringType, T element) {
        final AnnotationMetadata existing = lookupExisting(declaringType, element);
        if (existing != null) {
            return existing;
        } else {

            DefaultAnnotationMetadata annotationMetadata = new DefaultAnnotationMetadata();

            try {
                AnnotationMetadata metadata = buildInternal(null, element, annotationMetadata, true, false);
                if (metadata.isEmpty()) {
                    return AnnotationMetadata.EMPTY_METADATA;
                }
                return metadata;
            } catch (RuntimeException e) {
                if ("org.eclipse.jdt.internal.compiler.problem.AbortCompilation".equals(e.getClass().getName())) {
                    // workaround for a bug in the Eclipse APT implementation. See bug 541466 on their Bugzilla.
                    return AnnotationMetadata.EMPTY_METADATA;
                } else {
                    throw e;
                }
            }
        }
    }

    /**
     * Whether the element is a field, method, class or constructor.
     *
     * @param element The element
     * @return True if it is
     */
    protected abstract boolean isMethodOrClassElement(T element);

    /**
     * Obtains the declaring type for an element.
     *
     * @param element The element
     * @return The declaring type
     */
    protected abstract @NonNull String getDeclaringType(@NonNull T element);

    /**
     * Build the meta data for the given method element excluding any class metadata.
     *
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata buildForMethod(T element) {
        String declaringType = getDeclaringType(element);
        final AnnotationMetadata existing = lookupExisting(declaringType, element);
        if (existing != null) {
            return existing;
        } else {
            DefaultAnnotationMetadata annotationMetadata = new DefaultAnnotationMetadata();
            return buildInternal(null, element, annotationMetadata, false, false);
        }
    }

    /**
     * Get the annotation metadata for the given element and the given parent.
     * This method is used for cases when you need to combine annotation metadata for
     * two elements, for example a JavaBean property where the field and the method metadata
     * need to be combined.
     *
     * @param parent  The parent element
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata buildForParent(T parent, T element) {
        String declaringType = getDeclaringType(element);
        return buildForParent(declaringType, parent, element);
    }

    /**
     * Build the meta data for the given parent and method element excluding any class metadata.
     *
     * @param declaringType The declaring type
     * @param parent        The parent element
     * @param element       The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata buildForParent(String declaringType, T parent, T element) {
        final AnnotationMetadata existing = lookupExisting(declaringType, element);
        DefaultAnnotationMetadata annotationMetadata;
        if (existing instanceof DefaultAnnotationMetadata) {
            // ugly, but will have to do
            annotationMetadata = ((DefaultAnnotationMetadata) existing).clone();
        } else if (existing instanceof AnnotationMetadataHierarchy) {
            final AnnotationMetadata declaredMetadata = ((AnnotationMetadataHierarchy) existing).getDeclaredMetadata();
            if (declaredMetadata instanceof DefaultAnnotationMetadata) {
                annotationMetadata = ((DefaultAnnotationMetadata) declaredMetadata).clone();
            } else {
                annotationMetadata = new DefaultAnnotationMetadata();
            }
        } else {
            annotationMetadata = new DefaultAnnotationMetadata();
        }
        return buildInternal(parent, element, annotationMetadata, false, false);
    }

    /**
     * Build the meta data for the given method element excluding any class metadata.
     *
     * @param parent                 The parent element
     * @param element                The element
     * @param inheritTypeAnnotations Whether to inherit annotations from type as stereotypes
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata buildForParent(T parent, T element, boolean inheritTypeAnnotations) {
        String declaringType = getDeclaringType(element);
        final AnnotationMetadata existing = lookupExisting(declaringType, element);
        DefaultAnnotationMetadata annotationMetadata;
        if (existing instanceof DefaultAnnotationMetadata) {
            // ugly, but will have to do
            annotationMetadata = ((DefaultAnnotationMetadata) existing).clone();
        } else {
            annotationMetadata = new DefaultAnnotationMetadata();
        }
        return buildInternal(parent, element, annotationMetadata, inheritTypeAnnotations, false);
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
     * Get the given type of the annotation.
     *
     * @param annotationMirror The annotation
     * @return The type
     */
    protected abstract String getAnnotationTypeName(A annotationMirror);

    /**
     * Get the name for the given element.
     * @param element The element
     * @return The name
     */
    protected abstract String getElementName(T element);

    /**
     * Obtain the annotations for the given type.
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
    protected void validateAnnotationValue(T originatingElement, String annotationName, T member, String memberName, Object resolvedValue) {
        if (!validating) {
            return;
        }

        final AnnotatedElementValidator elementValidator = getElementValidator();
        if (elementValidator != null && !erroneousElements.contains(member)) {
            final boolean shouldValidate = !(annotationName.equals(AliasFor.class.getName())) &&
                    (!(resolvedValue instanceof String) || !resolvedValue.toString().contains("${"));
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
                    readAnnotationRawValues(originatingElement, annotationTypeName, member, aliasedNamed, annotationValue, resolvedValues);
                }
                String memberName = getAnnotationMemberName(member);
                readAnnotationRawValues(originatingElement, annotationTypeName, member, memberName, annotationValue, resolvedValues);
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
     * @param annotationMirror   The annotation
     * @param metadata           the metadata
     * @param isDeclared         Is the annotation a declared annotation
     * @param retentionPolicy    The retention policy
     * @return The annotation values
     */
    protected Map<CharSequence, Object> populateAnnotationData(
            T originatingElement,
            A annotationMirror,
            DefaultAnnotationMetadata metadata,
            boolean isDeclared,
            RetentionPolicy retentionPolicy) {
        String annotationName = getAnnotationTypeName(annotationMirror);

        if (retentionPolicy == RetentionPolicy.RUNTIME) {
            processAnnotationDefaults(originatingElement, annotationMirror, metadata, annotationName);
        }

        List<String> parentAnnotations = new ArrayList<>();
        parentAnnotations.add(annotationName);
        Map<? extends T, ?> elementValues = readAnnotationRawValues(annotationMirror);
        Map<CharSequence, Object> annotationValues;
        if (CollectionUtils.isEmpty(elementValues)) {
            annotationValues = new LinkedHashMap<>(3);
        } else {
            annotationValues = new LinkedHashMap<>(5);
            for (Map.Entry<? extends T, ?> entry : elementValues.entrySet()) {
                T member = entry.getKey();

                if (member == null) {
                    continue;
                }

                boolean isInstantiatedMember = hasAnnotation(member, InstantiatedMember.class);
                Object annotationValue = entry.getValue();
                if (isInstantiatedMember) {
                    final String memberName = getAnnotationMemberName(member);
                    final Object rawValue = readAnnotationValue(originatingElement, member, memberName, annotationValue);
                    if (rawValue instanceof AnnotationClassValue) {
                        AnnotationClassValue acv = (AnnotationClassValue) rawValue;
                        annotationValues.put(memberName, new AnnotationClassValue(acv.getName(), true));
                    }
                } else {
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
                                final Map<? extends T, ?> defaultValues = readAnnotationDefaultValues(mappedAnnotationName, annMirror);
                                if (finalRetentionPolicy == RetentionPolicy.RUNTIME) {
                                    processAnnotationDefaults(originatingElement, metadata, mappedAnnotationName, defaultValues);
                                }
                                final ArrayList<String> parents = new ArrayList<>();
                                processAnnotationStereotype(
                                        parents,
                                        annMirror,
                                        mappedAnnotationName,
                                        metadata,
                                        isDeclared);

                            });
                        }
                    }
                }
            }
        }
        return annotationValues;
    }

    private void handleAnnotationAlias(T originatingElement, DefaultAnnotationMetadata metadata, boolean isDeclared, String annotationName, List<String> parentAnnotations, Map<CharSequence, Object> annotationValues, T member, Object annotationValue) {
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
            readAnnotationRawValues(originatingElement, annotationName, member, getAnnotationMemberName(member), annotationValue, annotationValues);
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
            readAnnotationRawValues(originatingElement, annotationName, member, getAnnotationMemberName(member), annotationValue, annotationValues);
        }
    }

    /**
     * Get the annotation member.
     * @param originatingElement The originatig element
     * @param member The member
     * @return The annotation member
     */
    protected abstract @Nullable T getAnnotationMember(T originatingElement, CharSequence member);

    /**
     * Obtain the annotation mappers for the given annotation name.
     * @param annotationName The annotation name
     * @return The mappers
     */
    protected @NonNull List<AnnotationMapper<? extends Annotation>> getAnnotationMappers(@NonNull String annotationName) {
        return ANNOTATION_MAPPERS.get(annotationName);
    }

    /**
     * Creates the visitor context for this implementation.
     *
     * @return The visitor context
     */
    protected abstract VisitorContext createVisitorContext();

    private void processAnnotationDefaults(T originatingElement, A annotationMirror, DefaultAnnotationMetadata metadata, String annotationName) {
        Map<? extends T, ?> elementDefaultValues = readAnnotationDefaultValues(annotationMirror);
        processAnnotationDefaults(originatingElement, metadata, annotationName, elementDefaultValues);
    }

    private void processAnnotationDefaults(T originatingElement, DefaultAnnotationMetadata metadata, String annotationName, Map<? extends T, ?> elementDefaultValues) {
        if (elementDefaultValues != null) {
            Map<CharSequence, Object> defaultValues = new LinkedHashMap<>();
            for (Map.Entry<? extends T, ?> entry : elementDefaultValues.entrySet()) {
                T member = entry.getKey();
                String memberName = getAnnotationMemberName(member);
                if (!defaultValues.containsKey(memberName)) {
                    Object annotationValue = entry.getValue();
                    readAnnotationRawValues(originatingElement, annotationName, member, memberName, annotationValue, defaultValues);
                }
            }
            metadata.addDefaultAnnotationValues(annotationName, defaultValues);
            Map<String, Object> annotationDefaults = new HashMap<>(defaultValues.size());
            for (Map.Entry<CharSequence, Object> entry : defaultValues.entrySet()) {
                annotationDefaults.put(entry.getKey().toString(), entry.getValue());
            }
            DefaultAnnotationMetadata.registerAnnotationDefaults(annotationName, annotationDefaults);
        } else {
            metadata.addDefaultAnnotationValues(annotationName, Collections.emptyMap());
        }
    }

    private AnnotationMetadata lookupExisting(String declaringType, T element) {
        return MUTATED_ANNOTATION_METADATA.get(new MetadataKey(declaringType, element));
    }

    private void processAnnotationAlias(
            T originatingElement, String annotationName,
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
                String aliasedAnnotationName;
                if (aliasAnnotation.isPresent()) {
                    aliasedAnnotationName = aliasAnnotation.get().toString();
                } else {
                    aliasedAnnotationName = aliasAnnotationName.get().toString();
                }
                String aliasedMemberName = aliasMember.get().toString();
                Object v = readAnnotationValue(originatingElement, member, aliasedMemberName, annotationValue);
                if (v != null) {
                    Optional<T> annotationMirror = getAnnotationMirror(aliasedAnnotationName);
                    RetentionPolicy retentionPolicy = RetentionPolicy.RUNTIME;
                    if (annotationMirror.isPresent()) {
                        final T annotationTypeMirror = annotationMirror.get();
                        final Map<? extends T, ?> defaultValues = readAnnotationDefaultValues(aliasedAnnotationName, annotationTypeMirror);
                        processAnnotationDefaults(originatingElement, metadata, aliasedAnnotationName, defaultValues);
                        retentionPolicy = getRetentionPolicy(annotationTypeMirror);
                    }

                    if (isDeclared) {
                        metadata.addDeclaredStereotype(
                                parentAnnotations,
                                aliasedAnnotationName,
                                Collections.singletonMap(aliasedMemberName, v),
                                retentionPolicy
                        );
                    } else {
                        metadata.addStereotype(
                                parentAnnotations,
                                aliasedAnnotationName,
                                Collections.singletonMap(aliasedMemberName, v),
                                retentionPolicy
                        );
                    }

                    annotationMirror.ifPresent(annMirror -> processAnnotationStereotype(
                            parentAnnotations,
                            annMirror,
                            aliasedAnnotationName,
                            metadata,
                            isDeclared
                    ));
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

    private AnnotationMetadata buildInternal(T parent, T element, DefaultAnnotationMetadata annotationMetadata, boolean inheritTypeAnnotations, boolean declaredOnly) {
        List<T> hierarchy = buildHierarchy(element, inheritTypeAnnotations, declaredOnly);
        if (parent != null) {
            final List<T> parentHierarchy = buildHierarchy(parent, inheritTypeAnnotations, declaredOnly);
            hierarchy.addAll(0, parentHierarchy);
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
            boolean isDeclared = currentElement == element;

            for (A annotationMirror : annotationHierarchy) {
                String annotationName = getAnnotationTypeName(annotationMirror);
                if (AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationName)) {
                    continue;
                }

                final T annotationType = getTypeForAnnotation(annotationMirror);
                RetentionPolicy retentionPolicy = getRetentionPolicy(annotationType);
                Map<CharSequence, Object> annotationValues = populateAnnotationData(currentElement, annotationMirror, annotationMetadata, isDeclared, retentionPolicy);

                String repeatableName = getRepeatableName(annotationMirror);
                String packageName = NameUtils.getPackageName(annotationName);
                List<AnnotationRemapper> annotationRemappers = ANNOTATION_REMAPPERS.get(packageName);
                List<AnnotationTransformer<Annotation>> annotationTransformers = ANNOTATION_TRANSFORMERS.get(annotationName);
                boolean remapped = CollectionUtils.isNotEmpty(annotationRemappers);
                boolean transformed = CollectionUtils.isNotEmpty(annotationTransformers);

                if (repeatableName != null) {
                    if (!remapped && !transformed) {
                        io.micronaut.core.annotation.AnnotationValue av = new io.micronaut.core.annotation.AnnotationValue(annotationName, annotationValues);
                        if (isDeclared) {
                            annotationMetadata.addDeclaredRepeatable(repeatableName, av);
                        } else {
                            annotationMetadata.addRepeatable(repeatableName, av);
                        }
                    } else if (remapped) {
                        AnnotationValue repeatableAnn = new AnnotationValue(repeatableName);
                        VisitorContext visitorContext = createVisitorContext();
                        io.micronaut.core.annotation.AnnotationValue av = new io.micronaut.core.annotation.AnnotationValue(annotationName, annotationValues);
                        for (AnnotationRemapper annotationRemapper : annotationRemappers) {
                            List<AnnotationValue<?>> remappedRepeatable = annotationRemapper.remap(repeatableAnn, visitorContext);
                            List<AnnotationValue<?>> remappedValue = annotationRemapper.remap(av, visitorContext);
                            if (CollectionUtils.isNotEmpty(remappedRepeatable)) {
                                for (AnnotationValue<?> repeatable : remappedRepeatable) {
                                    for (AnnotationValue<?> rmv : remappedValue) {
                                        if (isDeclared) {
                                            annotationMetadata.addDeclaredRepeatable(repeatable.getAnnotationName(), rmv);
                                        } else {
                                            annotationMetadata.addRepeatable(repeatable.getAnnotationName(), rmv);
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        AnnotationValue<Annotation> repeatableAnn = new AnnotationValue<>(repeatableName);
                        VisitorContext visitorContext = createVisitorContext();
                        io.micronaut.core.annotation.AnnotationValue<Annotation> av =
                                new io.micronaut.core.annotation.AnnotationValue<>(annotationName, annotationValues);
                        final List<AnnotationTransformer<Annotation>> repeatableTransformers = ANNOTATION_TRANSFORMERS.get(repeatableName);
                        if (CollectionUtils.isNotEmpty(repeatableTransformers)) {
                            for (AnnotationTransformer<Annotation> repeatableTransformer : repeatableTransformers) {
                                final List<AnnotationValue<?>> transformedRepeatable = repeatableTransformer.transform(repeatableAnn, visitorContext);
                                for (AnnotationValue<?> annotationValue : transformedRepeatable) {
                                    for (AnnotationTransformer<Annotation> transformer : annotationTransformers) {
                                        final List<AnnotationValue<?>> tav = transformer.transform(av, visitorContext);
                                        for (AnnotationValue<?> value : tav) {
                                            if (isDeclared) {
                                                annotationMetadata.addDeclaredRepeatable(annotationValue.getAnnotationName(), value);
                                            } else {
                                                annotationMetadata.addRepeatable(annotationValue.getAnnotationName(), value);
                                            }
                                        }
                                    }

                                }
                            }
                        } else {
                            for (AnnotationTransformer<Annotation> transformer : annotationTransformers) {
                                final List<AnnotationValue<?>> tav = transformer.transform(av, visitorContext);
                                for (AnnotationValue<?> value : tav) {
                                    if (isDeclared) {
                                        annotationMetadata.addDeclaredRepeatable(repeatableName, value);
                                    } else {
                                        annotationMetadata.addRepeatable(repeatableName, value);
                                    }
                                }
                            }
                        }
                    }
                } else {
                    if (!remapped && !transformed) {
                        if (isDeclared) {
                            annotationMetadata.addDeclaredAnnotation(annotationName, annotationValues, retentionPolicy);
                        } else {
                            annotationMetadata.addAnnotation(annotationName, annotationValues, retentionPolicy);
                        }
                    } else if (remapped) {
                        io.micronaut.core.annotation.AnnotationValue av = new io.micronaut.core.annotation.AnnotationValue(annotationName, annotationValues);
                        VisitorContext visitorContext = createVisitorContext();
                        for (AnnotationRemapper annotationRemapper : annotationRemappers) {
                            List<AnnotationValue<?>> remappedValues = annotationRemapper.remap(av, visitorContext);
                            if (CollectionUtils.isNotEmpty(remappedValues)) {
                                for (AnnotationValue<?> annotationValue : remappedValues) {
                                    if (isDeclared) {
                                        annotationMetadata.addDeclaredAnnotation(annotationValue.getAnnotationName(), annotationValue.getValues());
                                    } else {
                                        annotationMetadata.addAnnotation(annotationValue.getAnnotationName(), annotationValue.getValues());
                                    }
                                }
                            }
                        }
                    } else {
                        io.micronaut.core.annotation.AnnotationValue<Annotation> av =
                                new io.micronaut.core.annotation.AnnotationValue<>(annotationName, annotationValues);
                        VisitorContext visitorContext = createVisitorContext();
                        for (AnnotationTransformer<Annotation> annotationTransformer : annotationTransformers) {
                            final List<AnnotationValue<?>> transformedValues = annotationTransformer.transform(av, visitorContext);
                            for (AnnotationValue<?> transformedValue : transformedValues) {
                                if (isDeclared) {
                                    annotationMetadata.addDeclaredAnnotation(
                                            transformedValue.getAnnotationName(),
                                            transformedValue.getValues(),
                                            transformedValue.getRetentionPolicy()
                                    );
                                } else {
                                    annotationMetadata.addAnnotation(
                                            transformedValue.getAnnotationName(),
                                            transformedValue.getValues(),
                                            transformedValue.getRetentionPolicy()
                                    );
                                }
                            }
                        }
                    }
                }
            }
            for (A annotationMirror : annotationHierarchy) {
                String annotationTypeName = getAnnotationTypeName(annotationMirror);
                String packageName = NameUtils.getPackageName(annotationTypeName);
                if (!AnnotationUtil.STEREOTYPE_EXCLUDES.contains(packageName)) {
                    processAnnotationStereotype(annotationMirror, annotationMetadata, isDeclared);
                }
            }

        }
        if (!annotationMetadata.hasDeclaredStereotype(Scope.class) && annotationMetadata.hasDeclaredStereotype(DefaultScope.class)) {
            Optional<String> value = annotationMetadata.stringValue(DefaultScope.class);
            value.ifPresent(name -> annotationMetadata.addDeclaredAnnotation(name, Collections.emptyMap()));
        }
        return annotationMetadata;
    }

    private void buildStereotypeHierarchy(List<String> parents, T element, DefaultAnnotationMetadata metadata, boolean isDeclared, List<String> excludes) {
        List<? extends A> annotationMirrors = getAnnotationsForType(element);
        if (!annotationMirrors.isEmpty()) {

            // first add the top level annotations
            List<A> topLevel = new ArrayList<>();
            for (A annotationMirror : annotationMirrors) {

                String annotationName = getAnnotationTypeName(annotationMirror);
                if (annotationName.equals(getElementName(element))) {
                    continue;
                }

                if (!AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationName) && !excludes.contains(annotationName)) {
                    final T annotationTypeMirror = getTypeForAnnotation(annotationMirror);
                    final RetentionPolicy retentionPolicy = getRetentionPolicy(annotationTypeMirror);

                    topLevel.add(annotationMirror);

                    Map<CharSequence, Object> data = populateAnnotationData(element, annotationMirror, metadata, isDeclared, retentionPolicy);

                    String repeatableName = getRepeatableName(annotationMirror);

                    if (repeatableName != null) {
                        io.micronaut.core.annotation.AnnotationValue av = new io.micronaut.core.annotation.AnnotationValue(annotationName, data);
                        if (isDeclared) {
                            metadata.addDeclaredRepeatableStereotype(parents, repeatableName, av);
                        } else {
                            metadata.addRepeatableStereotype(parents, repeatableName, av);
                        }
                    } else {
                        if (isDeclared) {
                            metadata.addDeclaredStereotype(parents, annotationName, data, retentionPolicy);
                        } else {
                            metadata.addStereotype(parents, annotationName, data, retentionPolicy);
                        }
                    }
                }
            }
            // now add meta annotations
            for (A annotationMirror : topLevel) {
                processAnnotationStereotype(parents, annotationMirror, metadata, isDeclared);
            }
        }
    }

    private void processAnnotationStereotype(A annotationMirror, DefaultAnnotationMetadata annotationMetadata, boolean isDeclared) {
        T annotationType = getTypeForAnnotation(annotationMirror);
        String parentAnnotationName = getAnnotationTypeName(annotationMirror);
        if (!parentAnnotationName.endsWith(".Nullable")) {
            processAnnotationStereotypes(annotationMetadata, isDeclared, annotationType, parentAnnotationName, Collections.emptyList());
        }
    }

    private void processAnnotationStereotypes(DefaultAnnotationMetadata annotationMetadata, boolean isDeclared, T annotationType, String annotationName, List<String> excludes) {
        List<String> parentAnnotations = new ArrayList<>();
        parentAnnotations.add(annotationName);
        buildStereotypeHierarchy(
                parentAnnotations,
                annotationType,
                annotationMetadata,
                isDeclared,
                excludes
        );
    }

    private void processAnnotationStereotype(List<String> parents, A annotationMirror, DefaultAnnotationMetadata metadata, boolean isDeclared) {
        T typeForAnnotation = getTypeForAnnotation(annotationMirror);
        String annotationTypeName = getAnnotationTypeName(annotationMirror);
        processAnnotationStereotype(parents, typeForAnnotation, annotationTypeName, metadata, isDeclared);
    }

    private void processAnnotationStereotype(List<String> parents, T annotationType, String annotationTypeName, DefaultAnnotationMetadata metadata, boolean isDeclared) {
        List<String> stereoTypeParents = new ArrayList<>(parents);
        stereoTypeParents.add(annotationTypeName);
        buildStereotypeHierarchy(stereoTypeParents, annotationType, metadata, isDeclared, Collections.emptyList());
    }

    /**
     * Used to store metadata mutations at compilation time. Not for public consumption.
     *
     * @param declaringType The declaring type
     * @param element       The element
     * @param metadata      The metadata
     */
    @Internal
    public static void addMutatedMetadata(String declaringType, Object element, AnnotationMetadata metadata) {
        if (element != null && metadata != null) {
            MUTATED_ANNOTATION_METADATA.put(new MetadataKey(declaringType, element), metadata);
        }
    }

    /**
     * Used to store metadata mutations at compilation time. Not for public consumption.
     *
     * @param declaringType The declaring type
     * @param element       The element
     * @return True if the annotation metadata was mutated
     */
    @Internal
    public static boolean isMetadataMutated(String declaringType, Object element) {
        if (element != null) {
            return MUTATED_ANNOTATION_METADATA.containsKey(new MetadataKey(declaringType, element));
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
     * Returns whether the given annotation is a mapped annotation.
     *
     * @param annotationName The annotation name
     * @return True if it is
     */
    @Internal
    public static boolean isAnnotationMapped(@Nullable String annotationName) {
        return annotationName != null && ANNOTATION_MAPPERS.containsKey(annotationName);
    }

    /**
     * @return Additional mapped annotation names
     */
    @Internal
    public static Set<String> getMappedAnnotationNames() {
        return ANNOTATION_MAPPERS.keySet();
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
        if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            final Optional<T> annotationMirror = getAnnotationMirror(annotationName);
            final DefaultAnnotationMetadata defaultMetadata = (DefaultAnnotationMetadata) annotationMetadata;
            defaultMetadata.addDeclaredAnnotation(
                    annotationName,
                    annotationValue.getValues()
            );
            annotationMirror.ifPresent(annotationType -> {
                final Map<? extends T, ?> defaultValues = readAnnotationDefaultValues(annotationName, annotationType);
                processAnnotationDefaults(
                        annotationType,
                        defaultMetadata,
                        annotationName,
                        defaultValues
                );
                processAnnotationStereotypes(
                        defaultMetadata,
                        true,
                        annotationType,
                        annotationName,
                        DEFAULT_ANNOTATE_EXCLUDES
                );
            });
        } else if (annotationMetadata instanceof AnnotationMetadataHierarchy) {
            AnnotationMetadataHierarchy hierarchy = (AnnotationMetadataHierarchy) annotationMetadata;
            AnnotationMetadata declaredMetadata = annotate(hierarchy.getDeclaredMetadata(), annotationValue);
            return hierarchy.createSibling(
                    declaredMetadata
            );
        } else if (annotationMetadata == AnnotationMetadata.EMPTY_METADATA) {
            final Optional<T> annotationMirror = getAnnotationMirror(annotationName);
            final Map<CharSequence, Object> values = annotationValue.getValues();
            final Map<String, Map<CharSequence, Object>> declared = new HashMap<>(1);
            declared.put(annotationName, values);
            final DefaultAnnotationMetadata newMetadata = new DefaultAnnotationMetadata(
                    declared,
                    null,
                    null,
                    declared,
                    null
            );
            annotationMirror.ifPresent(annotationType ->
                    processAnnotationStereotypes(
                            newMetadata,
                            true,
                            annotationType,
                            annotationName,
                            DEFAULT_ANNOTATE_EXCLUDES
                    )
            );
            return newMetadata;
        }
        return annotationMetadata;
    }

    /**
     * Key used to reference mutated metadata.
     *
     * @param <T> the element type
     */
    private static class MetadataKey<T> {
        final String declaringName;
        final T element;

        MetadataKey(String declaringName, T element) {
            this.declaringName = declaringName;
            this.element = element;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            MetadataKey that = (MetadataKey) o;
            return declaringName.equals(that.declaringName) &&
                    element.equals(that.element);
        }

        @Override
        public int hashCode() {
            return Objects.hash(declaringName, element);
        }
    }
}
