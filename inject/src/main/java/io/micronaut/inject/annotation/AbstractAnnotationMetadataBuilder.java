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
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.value.OptionalValues;
import io.micronaut.inject.visitor.VisitorContext;

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
    private static final Map<Object, AnnotationMetadata> MUTATED_ANNOTATION_METADATA = new HashMap<>();

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
                        ANNOTATION_MAPPERS.computeIfAbsent(name, s -> new ArrayList<>()).add(mapper);
                    }
                } catch (Throwable e) {
                    // mapper, missing dependencies, continue
                }
            }
        }
    }

    /**
     * Default constructor.
     */
    protected AbstractAnnotationMetadataBuilder() {

    }

    /**
     * Build the meta data for the given element. If the element is a method the class metadata will be included.
     *
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata build(T element) {
        final AnnotationMetadata existing = MUTATED_ANNOTATION_METADATA.get(element);
        if (existing != null) {
            return existing;
        } else {

            DefaultAnnotationMetadata annotationMetadata = new DefaultAnnotationMetadata();

            try {
                AnnotationMetadata metadata = buildInternal(null, element, annotationMetadata, true);
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
     * Build the meta data for the given method element excluding any class metadata.
     *
     * @param element The element
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata buildForMethod(T element) {
        final AnnotationMetadata existing = MUTATED_ANNOTATION_METADATA.get(element);
        if (existing != null) {
            return existing;
        } else {
            DefaultAnnotationMetadata annotationMetadata = new DefaultAnnotationMetadata();
            return buildInternal(null, element, annotationMetadata, false);
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
        final AnnotationMetadata existing = MUTATED_ANNOTATION_METADATA.get(element);
        DefaultAnnotationMetadata annotationMetadata;
        if (existing instanceof DefaultAnnotationMetadata) {
            // ugly, but will have to do
            annotationMetadata = (DefaultAnnotationMetadata) ((DefaultAnnotationMetadata) existing).clone();
        } else {
            annotationMetadata = new DefaultAnnotationMetadata();
        }
        return buildInternal(parent, element, annotationMetadata, false);
    }

    /**
     * Build the meta data for the given method element excluding any class metadata.
     *
     * @param parent  The parent element
     * @param element The element
     * @param inheritTypeAnnotations Whether to inherit annotations from type as stereotypes
     * @return The {@link AnnotationMetadata}
     */
    public AnnotationMetadata buildForParent(T parent, T element, boolean inheritTypeAnnotations) {
        final AnnotationMetadata existing = MUTATED_ANNOTATION_METADATA.get(element);
        DefaultAnnotationMetadata annotationMetadata;
        if (existing instanceof DefaultAnnotationMetadata) {
            // ugly, but will have to do
            annotationMetadata = (DefaultAnnotationMetadata) ((DefaultAnnotationMetadata) existing).clone();
        } else {
            annotationMetadata = new DefaultAnnotationMetadata();
        }
        return buildInternal(parent, element, annotationMetadata, inheritTypeAnnotations);
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
     * @param element The element
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
     * @return The type hierarchy
     */
    protected abstract List<T> buildHierarchy(T element, boolean inheritTypeAnnotations);

    /**
     * Read the given member and value, applying conversions if necessary, and place the data in the given map.
     *
     * @param memberName       The member
     * @param annotationValue  The value
     * @param annotationValues The values to populate
     */
    protected abstract void readAnnotationRawValues(String memberName, Object annotationValue, Map<CharSequence, Object> annotationValues);

    /**
     * Read the given member and value, applying conversions if necessary, and place the data in the given map.
     *
     * @param memberName      The member
     * @param annotationValue The value
     * @return The object
     */
    protected abstract Object readAnnotationValue(String memberName, Object annotationValue);


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
     * @param member         The member
     * @param annotationType The type
     * @return The values
     */
    protected abstract OptionalValues<?> getAnnotationValues(T member, Class<?> annotationType);

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
     * @param annotationMirror The annotation
     * @return The annotation value
     */
    protected io.micronaut.core.annotation.AnnotationValue readNestedAnnotationValue(A annotationMirror) {
        io.micronaut.core.annotation.AnnotationValue av;
        Map<? extends T, ?> annotationValues = readAnnotationRawValues(annotationMirror);
        if (annotationValues.isEmpty()) {
            av = new io.micronaut.core.annotation.AnnotationValue(getAnnotationTypeName(annotationMirror));
        } else {

            Map<CharSequence, Object> resolvedValues = new LinkedHashMap<>();
            for (Map.Entry<? extends T, ?> entry : annotationValues.entrySet()) {
                T member = entry.getKey();
                OptionalValues<?> aliasForValues = getAnnotationValues(member, AliasFor.class);
                Object annotationValue = entry.getValue();
                Optional<?> aliasMember = aliasForValues.get("member");
                Optional<?> aliasAnnotation = aliasForValues.get("annotation");
                Optional<?> aliasAnnotationName = aliasForValues.get("annotationName");
                if (aliasMember.isPresent() && !(aliasAnnotation.isPresent() || aliasAnnotationName.isPresent())) {
                    String aliasedNamed = aliasMember.get().toString();
                    readAnnotationRawValues(aliasedNamed, annotationValue, resolvedValues);
                }
                String memberName = getAnnotationMemberName(member);
                readAnnotationRawValues(memberName, annotationValue, resolvedValues);
            }
            av = new io.micronaut.core.annotation.AnnotationValue(getAnnotationTypeName(annotationMirror), resolvedValues);
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
     * @param annotationMirror The annotation
     * @param metadata         the metadata
     * @param isDeclared       Is the annotation a declared annotation
     * @return The annotation values
     */
    protected Map<CharSequence, Object> populateAnnotationData(
        A annotationMirror,
        DefaultAnnotationMetadata metadata,
        boolean isDeclared) {
        String annotationName = getAnnotationTypeName(annotationMirror);

        processAnnotationDefaults(annotationMirror, metadata, annotationName);

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
                Optional<?> aliases = getAnnotationValues(member, Aliases.class).get("value");
                Object annotationValue = entry.getValue();
                if (isInstantiatedMember) {
                    final String memberName = getAnnotationMemberName(member);
                    final Object rawValue = readAnnotationValue(memberName, annotationValue);
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
                                        metadata,
                                        isDeclared,
                                        parentAnnotations,
                                        annotationValues,
                                        annotationValue,
                                        aliasForValues
                                );
                            }
                        }
                        readAnnotationRawValues(getAnnotationMemberName(member), annotationValue, annotationValues);
                    } else {
                        OptionalValues<?> aliasForValues = getAnnotationValues(member, AliasFor.class);
                        processAnnotationAlias(metadata, isDeclared, parentAnnotations, annotationValues, annotationValue, aliasForValues);
                        readAnnotationRawValues(getAnnotationMemberName(member), annotationValue, annotationValues);
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
                                processAnnotationDefaults(metadata, mappedAnnotationName, defaultValues);
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

    private void processAnnotationDefaults(A annotationMirror, DefaultAnnotationMetadata metadata, String annotationName) {
        Map<? extends T, ?> elementDefaultValues = readAnnotationDefaultValues(annotationMirror);
        processAnnotationDefaults(metadata, annotationName, elementDefaultValues);
    }

    private void processAnnotationDefaults(DefaultAnnotationMetadata metadata, String annotationName, Map<? extends T, ?> elementDefaultValues) {
        if (elementDefaultValues != null) {
            Map<CharSequence, Object> defaultValues = new LinkedHashMap<>();
            for (Map.Entry<? extends T, ?> entry : elementDefaultValues.entrySet()) {
                T member = entry.getKey();
                String memberName = getAnnotationMemberName(member);
                if (!defaultValues.containsKey(memberName)) {
                    Object annotationValue = entry.getValue();
                    readAnnotationRawValues(memberName, annotationValue, defaultValues);
                }
            }
            metadata.addDefaultAnnotationValues(annotationName, defaultValues);
            Map<String, Object> annotationDefaults = new HashMap<>(defaultValues.size());
            for (Map.Entry<CharSequence, Object> entry: defaultValues.entrySet()) {
                annotationDefaults.put(entry.getKey().toString(), entry.getValue());
            }
            DefaultAnnotationMetadata.registerAnnotationDefaults(annotationName, annotationDefaults);
        } else {
            metadata.addDefaultAnnotationValues(annotationName, Collections.emptyMap());
        }
    }

    private void processAnnotationAlias(
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
                Object v = readAnnotationValue(aliasedMemberName, annotationValue);
                if (v != null) {
                    Optional<T> annotationMirror = getAnnotationMirror(aliasedAnnotationName);
                    if (annotationMirror.isPresent()) {
                        final Map<? extends T, ?> defaultValues = readAnnotationDefaultValues(aliasedAnnotationName, annotationMirror.get());
                        processAnnotationDefaults(metadata, aliasedAnnotationName, defaultValues);
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
            Object v = readAnnotationValue(aliasedNamed, annotationValue);
            if (v != null) {
                annotationValues.put(aliasedNamed, v);
            }
            readAnnotationRawValues(aliasedNamed, annotationValue, annotationValues);
        }
    }

    private AnnotationMetadata buildInternal(T parent, T element, DefaultAnnotationMetadata annotationMetadata, boolean inheritTypeAnnotations) {
        List<T> hierarchy = buildHierarchy(element, inheritTypeAnnotations);
        if (parent != null) {
            final List<T> parentHierarchy = buildHierarchy(parent, inheritTypeAnnotations);
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

                Map<CharSequence, Object> annotationValues = populateAnnotationData(annotationMirror, annotationMetadata, isDeclared);

                String repeatableName = getRepeatableName(annotationMirror);

                if (repeatableName != null) {
                    io.micronaut.core.annotation.AnnotationValue av = new io.micronaut.core.annotation.AnnotationValue(annotationName, annotationValues);
                    if (isDeclared) {
                        annotationMetadata.addDeclaredRepeatable(repeatableName, av);
                    } else {
                        annotationMetadata.addRepeatable(repeatableName, av);
                    }
                } else {
                    if (isDeclared) {
                        annotationMetadata.addDeclaredAnnotation(annotationName, annotationValues);
                    } else {
                        annotationMetadata.addAnnotation(annotationName, annotationValues);
                    }
                }
            }
            for (A annotationMirror : annotationHierarchy) {
                processAnnotationStereotype(annotationMirror, annotationMetadata, isDeclared);
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

                    Map<CharSequence, Object> data = populateAnnotationData(annotationMirror, metadata, isDeclared);

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
        String parentAnnotationName = getAnnotationTypeName(annotationMirror);
        T annotationType = getTypeForAnnotation(annotationMirror);
        List<String> parentAnnotations = new ArrayList<>();
        parentAnnotations.add(parentAnnotationName);
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
     * @param element The element
     * @param metadata The metadata
     */
    @Internal
    public static void addMutatedMetadata(Object element, AnnotationMetadata metadata) {
        if (element != null && metadata != null) {
            MUTATED_ANNOTATION_METADATA.put(element, metadata);
        }
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
     * @param annotationName The annotation name
     * @return True if it is
     */
    @Internal
    public static boolean isAnnotationMapped(@Nullable String annotationName) {
        return annotationName != null && ANNOTATION_MAPPERS.containsKey(annotationName);
    }
}
