/*
 * Copyright 2017-2019 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
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

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Scope;
import java.lang.annotation.Annotation;
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

    private static final Map<String, List<AnnotationMapper>> ANNOTATION_MAPPERS = new HashMap<>();
    private static final Map<String, List<AnnotationRemapper>> ANNOTATION_REMAPPERS = new HashMap<>();
    private static final Map<MetadataKey, AnnotationMetadata> MUTATED_ANNOTATION_METADATA = new HashMap<>();

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
     * @param element The element
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
     * @param element The element
     * @return True if it is
     */
    protected abstract boolean isMethodOrClassElement(T element);

    /**
     * Obtains the declaring type for an element.
     * @param element The element
     * @return The declaring type
     */
    protected abstract @Nonnull String getDeclaringType(@Nonnull T element);

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
     * Build the meta data for the given method element excluding any class metadata.
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
     * @param parent  The parent element
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata buildForParent(String declaringType, T parent, T element) {
        final AnnotationMetadata existing = lookupExisting(declaringType, element);
        DefaultAnnotationMetadata annotationMetadata;
        if (existing instanceof DefaultAnnotationMetadata) {
            // ugly, but will have to do
            annotationMetadata = (DefaultAnnotationMetadata) ((DefaultAnnotationMetadata) existing).clone();
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
            annotationMetadata = (DefaultAnnotationMetadata) ((DefaultAnnotationMetadata) existing).clone();
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
     * @param originatingElement The originating element
     * @param annotationName The annotation name
     * @param member The member
     * @param memberName The member name
     * @param resolvedValue The resolved value
     */
    protected void validateAnnotationValue(T originatingElement, String annotationName, T member, String memberName, Object resolvedValue) {
        final AnnotatedElementValidator elementValidator = getElementValidator();
        if (elementValidator != null && !erroneousElements.contains(member)) {
            final boolean shouldValidate = !(annotationName.equals(AliasFor.class.getName())) &&
                                           (!(resolvedValue instanceof String) || !resolvedValue.toString().contains("${"));
            if (shouldValidate) {
                final Set<String> errors = elementValidator.validatedAnnotatedElement(new AnnotatedElement() {

                    AnnotationMetadata metadata = buildDeclared(member);

                    @Nonnull
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
     * @return The validator.
     */
    protected @Nullable AnnotatedElementValidator getElementValidator() {
        return null;
    }

    /**
     * Adds an error.
     * @param originatingElement The originating element
     * @param error The error
     */
    protected abstract void addError(@Nonnull T originatingElement, @Nonnull String error);

    /**
     * Read the given member and value, applying conversions if necessary, and place the data in the given map.
     *
     * @param originatingElement The originating element
     * @param memberName         The member
     * @param annotationValue    The value
     * @return The object
     */
    protected abstract Object readAnnotationValue(T originatingElement, String memberName, Object annotationValue);


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
    protected abstract @Nullable String getRepeatableName(A annotationMirror);

    /**
     * Obtain the name of the repeatable annotation if the annotation is is one.
     *
     * @param annotationType The annotation mirror
     * @return Return the name or null
     */
    protected abstract @Nullable String getRepeatableNameForType(T annotationType);

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
     * @return The annotation values
     */
    protected Map<CharSequence, Object> populateAnnotationData(
            T originatingElement,
            A annotationMirror,
            DefaultAnnotationMetadata metadata,
            boolean isDeclared) {
        String annotationName = getAnnotationTypeName(annotationMirror);

        processAnnotationDefaults(originatingElement, annotationMirror, metadata, annotationName);

        List<String> parentAnnotations = new ArrayList<>();
        parentAnnotations.add(annotationName);
        Map<? extends T, ?> elementValues = readAnnotationRawValues(annotationMirror);
        Map<CharSequence, Object> annotationValues;
        if (CollectionUtils.isEmpty(elementValues)) {
            annotationValues = Collections.emptyMap();
        } else {
            annotationValues = new LinkedHashMap<>();
            for (Map.Entry<? extends T, ?> entry : elementValues.entrySet()) {
                T member = entry.getKey();

                if (member == null) {
                    continue;
                }

                boolean isInstantiatedMember = hasAnnotation(member, InstantiatedMember.class);
                Optional<?> aliases = getAnnotationValues(originatingElement, member, Aliases.class).get("value");
                Object annotationValue = entry.getValue();
                if (isInstantiatedMember) {
                    final String memberName = getAnnotationMemberName(member);
                    final Object rawValue = readAnnotationValue(originatingElement, memberName, annotationValue);
                    if (rawValue instanceof AnnotationClassValue) {
                        AnnotationClassValue acv = (AnnotationClassValue) rawValue;
                        annotationValues.put(memberName, new AnnotationClassValue(acv.getName(), true));
                    }
                } else {
                    if (aliases.isPresent()) {
                        Object value = aliases.get();
                        if (value instanceof io.micronaut.core.annotation.AnnotationValue[]) {
                            io.micronaut.core.annotation.AnnotationValue[] values = (io.micronaut.core.annotation.AnnotationValue[]) value;
                            for (io.micronaut.core.annotation.AnnotationValue av : values) {
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

            }
        }
        List<AnnotationMapper> mappers = ANNOTATION_MAPPERS.get(annotationName);
        if (mappers != null) {
            AnnotationValue<?> annotationValue = new AnnotationValue(annotationName, annotationValues);
            VisitorContext visitorContext = createVisitorContext();
            for (AnnotationMapper mapper : mappers) {
                List mapped = mapper.map(annotationValue, visitorContext);
                if (mapped != null) {
                    for (Object o : mapped) {
                        if (o instanceof AnnotationValue) {
                            AnnotationValue av = (AnnotationValue) o;
                            String mappedAnnotationName = av.getAnnotationName();

                            Optional<T> mappedMirror = getAnnotationMirror(mappedAnnotationName);
                            String repeatableName = mappedMirror.map(this::getRepeatableNameForType).orElse(null);
                            if (repeatableName != null) {
                                if (isDeclared) {
                                    metadata.addDeclaredRepeatable(
                                            repeatableName,
                                            av
                                    );
                                } else {
                                    metadata.addRepeatable(
                                            repeatableName,
                                            av
                                    );
                                }
                            } else {
                                if (isDeclared) {
                                    metadata.addDeclaredAnnotation(
                                            mappedAnnotationName,
                                            av.getValues()
                                    );
                                } else {
                                    metadata.addAnnotation(
                                            mappedAnnotationName,
                                            av.getValues()
                                    );
                                }
                            }

                            mappedMirror.ifPresent(annMirror -> {
                                final Map<? extends T, ?> defaultValues = readAnnotationDefaultValues(mappedAnnotationName, annMirror);
                                processAnnotationDefaults(originatingElement, metadata, mappedAnnotationName, defaultValues);
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
        return isMethodOrClassElement(element) ? MUTATED_ANNOTATION_METADATA.get(new MetadataKey(declaringType, element)) : null;
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
                Object v = readAnnotationValue(originatingElement, aliasedMemberName, annotationValue);
                if (v != null) {
                    Optional<T> annotationMirror = getAnnotationMirror(aliasedAnnotationName);
                    if (annotationMirror.isPresent()) {
                        final Map<? extends T, ?> defaultValues = readAnnotationDefaultValues(aliasedAnnotationName, annotationMirror.get());
                        processAnnotationDefaults(originatingElement, metadata, aliasedAnnotationName, defaultValues);
                    }

                    if (isDeclared) {
                        metadata.addDeclaredStereotype(
                                parentAnnotations,
                                aliasedAnnotationName,
                                Collections.singletonMap(aliasedMemberName, v)
                        );
                    } else {
                        metadata.addStereotype(
                                parentAnnotations,
                                aliasedAnnotationName,
                                Collections.singletonMap(aliasedMemberName, v)
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
            Object v = readAnnotationValue(originatingElement, aliasedNamed, annotationValue);
            if (v != null) {
                annotationValues.put(aliasedNamed, v);
            }
            readAnnotationRawValues(originatingElement, annotationName, member, aliasedNamed, annotationValue, annotationValues);
        }
    }

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

                Map<CharSequence, Object> annotationValues = populateAnnotationData(currentElement, annotationMirror, annotationMetadata, isDeclared);

                String repeatableName = getRepeatableName(annotationMirror);
                String packageName = NameUtils.getPackageName(annotationName);
                List<AnnotationRemapper> annotationRemappers = ANNOTATION_REMAPPERS.get(packageName);
                boolean notRemapped = CollectionUtils.isEmpty(annotationRemappers);

                if (repeatableName != null) {
                    if (notRemapped) {
                        io.micronaut.core.annotation.AnnotationValue av = new io.micronaut.core.annotation.AnnotationValue(annotationName, annotationValues);
                        if (isDeclared) {
                            annotationMetadata.addDeclaredRepeatable(repeatableName, av);
                        } else {
                            annotationMetadata.addRepeatable(repeatableName, av);
                        }
                    } else {
                        AnnotationValue repeatableAnn = new AnnotationValue(repeatableName);
                        VisitorContext visitorContext = createVisitorContext();
                        io.micronaut.core.annotation.AnnotationValue av = new io.micronaut.core.annotation.AnnotationValue(annotationName, annotationValues);
                        for (AnnotationRemapper annotationRemapper : annotationRemappers) {
                            List<AnnotationValue<?>> remappedRepeatable = annotationRemapper.remap(repeatableAnn, visitorContext);
                            List<AnnotationValue<?>> remappedValue = annotationRemapper.remap(av, visitorContext);
                            if (CollectionUtils.isNotEmpty(remappedRepeatable) && CollectionUtils.isNotEmpty(remappedRepeatable)) {
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
                    }
                } else {
                    if (notRemapped) {
                        if (isDeclared) {
                            annotationMetadata.addDeclaredAnnotation(annotationName, annotationValues);
                        } else {
                            annotationMetadata.addAnnotation(annotationName, annotationValues);
                        }
                    } else {
                        io.micronaut.core.annotation.AnnotationValue av = new io.micronaut.core.annotation.AnnotationValue(annotationName, annotationValues);
                        VisitorContext visitorContext = createVisitorContext();
                        for (AnnotationRemapper annotationRemapper : annotationRemappers) {
                            List<AnnotationValue<?>> remapped = annotationRemapper.remap(av, visitorContext);
                            if (CollectionUtils.isNotEmpty(remapped)) {
                                for (AnnotationValue<?> annotationValue : remapped) {
                                    if (isDeclared) {
                                        annotationMetadata.addDeclaredAnnotation(annotationValue.getAnnotationName(), annotationValue.getValues());
                                    } else {
                                        annotationMetadata.addAnnotation(annotationValue.getAnnotationName(), annotationValue.getValues());
                                    }
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
            Optional<String> value = annotationMetadata.getValue(DefaultScope.class, String.class);
            value.ifPresent(name -> annotationMetadata.addDeclaredAnnotation(name, Collections.emptyMap()));
        }
        return annotationMetadata;
    }

    private void buildStereotypeHierarchy(List<String> parents, T element, DefaultAnnotationMetadata metadata, boolean isDeclared) {
        List<? extends A> annotationMirrors = getAnnotationsForType(element);
        if (!annotationMirrors.isEmpty()) {

            // first add the top level annotations
            List<A> topLevel = new ArrayList<>();
            for (A annotationMirror : annotationMirrors) {

                if (getTypeForAnnotation(annotationMirror) == element) {
                    continue;
                }

                String annotationName = getAnnotationTypeName(annotationMirror);
                if (!AnnotationUtil.INTERNAL_ANNOTATION_NAMES.contains(annotationName)) {
                    topLevel.add(annotationMirror);

                    Map<CharSequence, Object> data = populateAnnotationData(element, annotationMirror, metadata, isDeclared);

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
                            metadata.addDeclaredStereotype(parents, annotationName, data);
                        } else {
                            metadata.addStereotype(parents, annotationName, data);
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
        processAnnotationStereotypes(annotationMetadata, isDeclared, annotationType, parentAnnotationName);
    }

    private void processAnnotationStereotypes(DefaultAnnotationMetadata annotationMetadata, boolean isDeclared, T annotationType, String annotationName) {
        List<String> parentAnnotations = new ArrayList<>();
        parentAnnotations.add(annotationName);
        buildStereotypeHierarchy(
                parentAnnotations,
                annotationType,
                annotationMetadata,
                isDeclared
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
        buildStereotypeHierarchy(stereoTypeParents, annotationType, metadata, isDeclared);
    }

    /**
     * Used to store metadata mutations at compilation time. Not for public consumption.
     *
     * @param declaringType The declaring type
     * @param element The element
     * @param metadata The metadata
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
     * @param element The element
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
        if (annotationMetadata instanceof DefaultAnnotationMetadata) {
            final Optional<T> annotationMirror = getAnnotationMirror(annotationValue.getAnnotationName());
            final DefaultAnnotationMetadata defaultMetadata = (DefaultAnnotationMetadata) annotationMetadata;
            defaultMetadata.addDeclaredAnnotation(
                    annotationValue.getAnnotationName(),
                    annotationValue.getValues()
            );
            annotationMirror.ifPresent(annotationType ->
                    processAnnotationStereotypes(
                            defaultMetadata,
                            true,
                            annotationType,
                            annotationValue.getAnnotationName()
                    )
            );
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
