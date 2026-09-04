/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.reflection;

import io.micronaut.context.annotation.ConfigurationProperties;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachProperty;
import io.micronaut.core.annotation.AnnotationClassValue;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.Introspected;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import org.jspecify.annotations.Nullable;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Applies the {@value ReflectionIntrospectionPolicy#PROPERTY_ALLOW_REFLECTION} property of the application
 * configuration to the {@link ReflectionIntrospectionPolicy} when the context starts, and takes it back when
 * the context closes, leaving the patterns of the contexts that are still running.
 *
 * <p>The property allows types; {@link Description} both allows them and says how they are to be described,
 * carrying the {@link Introspected} members a type that cannot be annotated - one of a library, one compiled
 * without the processor - would otherwise have no way to declare:</p>
 *
 * <pre>
 * micronaut:
 *   introspection:
 *     reflective:
 *       - types: com.example.model.*
 *         access-kind: FIELD
 *         visibility: ANY
 *         excludes: password
 *       - types: com.example.api.*
 *         included-annotations: com.fasterxml.jackson.annotation.JsonProperty
 *         indexed:
 *           - annotation: com.fasterxml.jackson.annotation.JsonProperty
 *             member: value
 * </pre>
 *
 * @author Denis Stepanov
 * @since 5.2.0
 */
@Context
@Internal
@Experimental
@ConfigurationProperties(ReflectionIntrospectionConfiguration.PREFIX)
public final class ReflectionIntrospectionConfiguration {

    /**
     * The configuration prefix.
     */
    public static final String PREFIX = "micronaut.introspection";

    private final List<Description> reflective;
    private List<String> allowReflection = List.of();
    // the contributions of this context only, so closing this context leaves the patterns the contexts still
    // running contributed
    private final List<ReflectionIntrospectionPolicy.Registration> registrations = new ArrayList<>();

    /**
     * Creates the configuration; it is instantiated by the context.
     *
     * @param reflective The types to describe reflectively, and how
     */
    public ReflectionIntrospectionConfiguration(@Nullable List<Description> reflective) {
        this.reflective = reflective == null ? List.of() : reflective;
    }

    /**
     * The patterns of the types the shared introspector may describe reflectively.
     *
     * @return The patterns
     */
    public List<String> getAllowReflection() {
        return allowReflection;
    }

    /**
     * The patterns of the types the shared {@link io.micronaut.core.beans.BeanIntrospector} may describe
     * reflectively when it has no generated introspection for them. A pattern is a class name where {@code *}
     * stands for any sequence of characters: {@code com.example.model.*} allows a package and its sub packages.
     * No type is allowed by default.
     *
     * @param allowReflection The patterns
     */
    public void setAllowReflection(List<String> allowReflection) {
        this.allowReflection = allowReflection == null ? List.of() : allowReflection;
    }

    /**
     * The types to describe reflectively, and the annotations to describe them with.
     *
     * @return The descriptions
     */
    public List<Description> getReflective() {
        return reflective;
    }

    @PostConstruct
    void apply() {
        registrations.add(ReflectionIntrospectionPolicy.configure(allowReflection));
        for (Description description : reflective) {
            registrations.add(ReflectionIntrospectionPolicy.configure(description.getTypes(), description.describe()));
        }
    }

    @PreDestroy
    void withdraw() {
        List<ReflectionIntrospectionPolicy.Registration> contributed = List.copyOf(registrations);
        registrations.clear();
        for (ReflectionIntrospectionPolicy.Registration registration : contributed) {
            registration.close();
        }
    }

    /**
     * The types matching a set of patterns, and the {@link Introspected} members the shared introspector
     * describes them with.
     *
     * <p>A member left unset is left to its default, which is what the reflective description already applies,
     * and a type carrying {@link Introspected} of its own keeps what it declares: a pattern names types in
     * bulk, and does not displace what one of them says of itself.</p>
     *
     * <p>{@link Introspected#includedAnnotations()} is not among the members: the processor uses it to choose
     * the classes to scan for the packages {@link Introspected#packages()} names, not to filter the properties
     * its javadoc describes, so configuring it would change nothing.</p>
     */
    @Internal
    @Experimental
    @EachProperty(value = "reflective", list = true)
    public static final class Description {

        private List<String> types = List.of();
        private List<Introspected.AccessKind> accessKind = List.of();
        private List<Introspected.Visibility> visibility = List.of();
        private List<String> includes = List.of();
        private List<String> excludes = List.of();
        private List<String> excludedAnnotations = List.of();
        private @Nullable Boolean annotationMetadata;
        private List<Indexed> indexed = List.of();

        /**
         * Creates the description; it is instantiated by the context.
         */
        public Description() {
        }

        /**
         * The patterns of the types this describes.
         *
         * @return The patterns of the types this describes
         */
        public List<String> getTypes() {
            return types;
        }

        /**
         * The patterns of the types this describes, a pattern being a class name where {@code *} stands for any
         * sequence of characters. The types matching them are allowed to be described reflectively, as
         * {@link #setAllowReflection(List)} allows them.
         *
         * @param types The patterns
         */
        public void setTypes(List<String> types) {
            this.types = types == null ? List.of() : types;
        }

        /**
         * What makes a property.
         *
         * @return The access kinds
         */
        public List<Introspected.AccessKind> getAccessKind() {
            return accessKind;
        }

        /**
         * What makes a property, in the order it is preferred where a type has both.
         *
         * @param accessKind The access kinds, {@link Introspected.AccessKind#METHOD} by default
         * @see Introspected#accessKind()
         */
        public void setAccessKind(List<Introspected.AccessKind> accessKind) {
            this.accessKind = accessKind == null ? List.of() : accessKind;
        }

        /**
         * How visible a member has to be to make a property.
         *
         * @return The visibilities
         */
        public List<Introspected.Visibility> getVisibility() {
            return visibility;
        }

        /**
         * How visible a member has to be to make a property.
         *
         * @param visibility The visibilities, {@link Introspected.Visibility#DEFAULT} by default
         * @see Introspected#visibility()
         */
        public void setVisibility(List<Introspected.Visibility> visibility) {
            this.visibility = visibility == null ? List.of() : visibility;
        }

        /**
         * The names of the only properties to describe.
         *
         * @return The property names to describe
         */
        public List<String> getIncludes() {
            return includes;
        }

        /**
         * The names of the only properties to describe, all of them by default.
         *
         * @param includes The property names
         * @see Introspected#includes()
         */
        public void setIncludes(List<String> includes) {
            this.includes = includes == null ? List.of() : includes;
        }

        /**
         * The names of the properties to leave out.
         *
         * @return The property names to leave out
         */
        public List<String> getExcludes() {
            return excludes;
        }

        /**
         * The names of the properties to leave out, none by default.
         *
         * @param excludes The property names
         * @see Introspected#excludes()
         */
        public void setExcludes(List<String> excludes) {
            this.excludes = excludes == null ? List.of() : excludes;
        }

        /**
         * The annotations that leave a property out.
         *
         * @return The annotations that leave a property out
         */
        public List<String> getExcludedAnnotations() {
            return excludedAnnotations;
        }

        /**
         * The names of the annotations that leave a property out, none by default.
         *
         * @param excludedAnnotations The annotation names
         * @see Introspected#excludedAnnotations()
         */
        public void setExcludedAnnotations(List<String> excludedAnnotations) {
            this.excludedAnnotations = excludedAnnotations == null ? List.of() : excludedAnnotations;
        }

        /**
         * Whether the properties carry their annotations.
         *
         * @return Whether the properties carry their annotations, {@code null} when it is not configured
         */
        public @Nullable Boolean getAnnotationMetadata() {
            return annotationMetadata;
        }

        /**
         * Whether the properties carry the annotations of their members, which they do by default.
         *
         * @param annotationMetadata Whether the properties carry their annotations
         * @see Introspected#annotationMetadata()
         */
        public void setAnnotationMetadata(@Nullable Boolean annotationMetadata) {
            this.annotationMetadata = annotationMetadata;
        }

        /**
         * The annotations to index the properties by.
         *
         * @return The annotations to index the properties by
         */
        public List<Indexed> getIndexed() {
            return indexed;
        }

        /**
         * The annotations to index the properties by, so that
         * {@link io.micronaut.core.beans.BeanIntrospection#getIndexedProperties(Class)} finds them; none by
         * default.
         *
         * @param indexed The annotations
         * @see Introspected#indexed()
         */
        public void setIndexed(List<Indexed> indexed) {
            this.indexed = indexed == null ? List.of() : indexed;
        }

        /**
         * The configured members as the {@link Introspected} annotation the described types carry.
         *
         * @return The annotations, {@link AnnotationMetadata#EMPTY_METADATA} when no member is configured
         */
        AnnotationMetadata describe() {
            Map<CharSequence, Object> values = new LinkedHashMap<>();
            put(values, "accessKind", accessKind.isEmpty() ? null : accessKind.toArray(new Introspected.AccessKind[0]));
            put(values, "visibility", visibility.isEmpty() ? null : visibility.toArray(new Introspected.Visibility[0]));
            put(values, "includes", includes);
            put(values, "excludes", excludes);
            put(values, "excludedAnnotations", excludedAnnotations);
            put(values, "annotationMetadata", annotationMetadata);
            if (!indexed.isEmpty()) {
                values.put("indexed", indexed.stream().map(Indexed::describe).toList());
            }
            return values.isEmpty() ? AnnotationMetadata.EMPTY_METADATA
                : ReflectionAnnotations.declaring(Introspected.class, values);
        }

        private static void put(Map<CharSequence, Object> values, String member, @Nullable Object value) {
            if (value instanceof List<?> list) {
                if (!list.isEmpty()) {
                    values.put(member, list.toArray(new String[0]));
                }
            } else if (value != null) {
                values.put(member, value);
            }
        }

        /**
         * An annotation the properties carrying it are indexed by.
         */
        @Internal
        @Experimental
        @EachProperty(value = "indexed", list = true)
        public static final class Indexed {

            private @Nullable String annotation;
            private @Nullable String member;

            /**
             * Creates the entry; it is instantiated by the context.
             */
            public Indexed() {
            }

            /**
             * The name of the annotation.
             *
             * @return The name of the annotation
             */
            public @Nullable String getAnnotation() {
                return annotation;
            }

            /**
             * The name of the annotation to index the properties by.
             *
             * @param annotation The annotation name
             */
            public void setAnnotation(@Nullable String annotation) {
                this.annotation = annotation;
            }

            /**
             * The name of the member.
             *
             * @return The name of the member
             */
            public @Nullable String getMember() {
                return member;
            }

            /**
             * The name of the member whose value
             * {@link io.micronaut.core.beans.BeanIntrospection#getIndexedProperty(Class, String)} looks a
             * property up by; without it the annotation is indexed by itself alone.
             *
             * @param member The member name
             */
            public void setMember(@Nullable String member) {
                this.member = member;
            }

            AnnotationValue<Introspected.IndexedAnnotation> describe() {
                AnnotationValueBuilder<Introspected.IndexedAnnotation> builder =
                    AnnotationValue.builder(Introspected.IndexedAnnotation.class);
                if (annotation != null) {
                    builder.member("annotation", new AnnotationClassValue<>(annotation));
                }
                if (member != null) {
                    builder.member("member", member);
                }
                return builder.build();
            }
        }
    }
}
