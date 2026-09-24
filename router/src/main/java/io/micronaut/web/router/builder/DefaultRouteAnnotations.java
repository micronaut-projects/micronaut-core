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
package io.micronaut.web.router.builder;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.AnnotationValueBuilder;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.annotation.AnnotationMetadataSupport;
import io.micronaut.inject.annotation.MutableAnnotationMetadata;
import org.jspecify.annotations.Nullable;

import java.lang.annotation.RetentionPolicy;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The annotations given to a handler route or a group of routes with
 * {@link HttpRouteSpec#annotate(AnnotationValue)} and {@link HttpRouteGroup#annotate(AnnotationValue)}:
 * the members of a later annotation of a type are merged with, and override, the ones of an
 * earlier annotation of the same type, like {@code Element#annotate}, and a repeatable annotation
 * is added to the earlier ones. The stereotypes of an annotation are the ones
 * of its {@link AnnotationValue#getStereotypes() value}: the meta-annotations of an annotation
 * type are not known at runtime.
 *
 * <p>The annotations of a route are layered like the annotations of a controller method over the
 * ones of its class: the annotations of the element given with
 * {@link HttpRouteSpec#annotationMetadata}, then of the groups of the route, outer group first,
 * then of the route, each overriding the members of the same annotation before it.</p>
 *
 * @author Denis Stepanov
 * @since 5.3.0
 */
@Internal
public final class DefaultRouteAnnotations {

    private final Map<String, List<AnnotationValue<?>>> annotations = new LinkedHashMap<>(4);
    private final @Nullable DefaultRouteAnnotations enclosing;
    private @Nullable AnnotationMetadata metadata;

    /**
     * The annotations of a route, or collected to be added to a route or a group.
     */
    public DefaultRouteAnnotations() {
        this(null);
    }

    /**
     * @param enclosing The annotations of the enclosing group, which these override, or {@code null}
     */
    public DefaultRouteAnnotations(@Nullable DefaultRouteAnnotations enclosing) {
        this.enclosing = enclosing;
    }

    /**
     * @return The annotations of the enclosing groups, outer group first, then these, a new mutable list
     */
    public List<DefaultRouteAnnotations> levels() {
        List<DefaultRouteAnnotations> levels = new ArrayList<>(3);
        for (DefaultRouteAnnotations level = this; level != null; level = level.enclosing) {
            levels.addFirst(level);
        }
        return levels;
    }

    /**
     * Add an annotation.
     *
     * @param annotation The annotation
     */
    public void add(AnnotationValue<?> annotation) {
        Objects.requireNonNull(annotation, "annotation");
        String name = annotation.getAnnotationName();
        if (AnnotationMetadataSupport.getRepeatableAnnotation(name) != null) {
            annotations.computeIfAbsent(name, n -> new ArrayList<>(2)).add(annotation);
        } else {
            List<AnnotationValue<?>> existing = annotations.get(name);
            annotations.put(name, List.of(existing == null ? annotation : merge(existing.get(0), annotation)));
        }
        metadata = null;
    }

    /**
     * @param existing   The annotation the route has
     * @param annotation The annotation given to it
     * @return The annotation with the members of both, the given ones overriding the existing ones
     */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static AnnotationValue<?> merge(AnnotationValue<?> existing, AnnotationValue<?> annotation) {
        AnnotationValueBuilder builder = AnnotationValue.builder(existing).members(annotation.getValues());
        List<AnnotationValue<?>> stereotypes = annotation.getStereotypes();
        if (stereotypes != null) {
            for (AnnotationValue<?> stereotype : stereotypes) {
                builder.stereotype(stereotype);
            }
        }
        return builder.build();
    }

    /**
     * @return Whether no annotation was added
     */
    public boolean isEmpty() {
        return annotations.isEmpty();
    }

    /**
     * @return The annotations as metadata, every annotation declared
     */
    public AnnotationMetadata toAnnotationMetadata() {
        AnnotationMetadata built = metadata;
        if (built == null) {
            MutableAnnotationMetadata mutable = new MutableAnnotationMetadata();
            for (List<AnnotationValue<?>> values : annotations.values()) {
                for (AnnotationValue<?> value : values) {
                    add(mutable, value);
                }
            }
            built = mutable;
            metadata = built;
        }
        return built;
    }

    /**
     * The annotations of a route: the metadata of its element, then the annotation levels, each
     * overriding the members of the same annotation of the levels before it. Every level counts as
     * declared on the route.
     *
     * @param base   The annotations of the element of the route, or empty
     * @param levels The annotations of the groups of the route, outer group first, then of the route
     * @return The annotations of the route: the base itself if no level has an annotation
     */
    public static AnnotationMetadata layered(AnnotationMetadata base, List<DefaultRouteAnnotations> levels) {
        List<AnnotationMetadata> hierarchy = new ArrayList<>(levels.size() + 1);
        if (!base.isEmpty()) {
            hierarchy.add(base);
        }
        boolean annotated = false;
        for (DefaultRouteAnnotations level : levels) {
            if (!level.isEmpty()) {
                hierarchy.add(level.toAnnotationMetadata());
                annotated = true;
            }
        }
        if (!annotated) {
            return base;
        }
        if (hierarchy.size() == 1) {
            return hierarchy.get(0);
        }
        return new AnnotationMetadataHierarchy(true, hierarchy.toArray(new AnnotationMetadata[0]));
    }

    private static void add(MutableAnnotationMetadata metadata, AnnotationValue<?> value) {
        String name = value.getAnnotationName();
        String container = AnnotationMetadataSupport.getRepeatableAnnotation(name);
        if (container != null) {
            metadata.addDeclaredRepeatable(container, value);
        } else {
            metadata.addDeclaredAnnotation(name, value.getValues(), RetentionPolicy.RUNTIME);
        }
        addStereotypes(metadata, List.of(name), value);
    }

    private static void addStereotypes(MutableAnnotationMetadata metadata, List<String> parents, AnnotationValue<?> value) {
        List<AnnotationValue<?>> stereotypes = value.getStereotypes();
        if (stereotypes == null) {
            return;
        }
        for (AnnotationValue<?> stereotype : stereotypes) {
            String name = stereotype.getAnnotationName();
            String container = AnnotationMetadataSupport.getRepeatableAnnotation(name);
            if (container != null) {
                metadata.addDeclaredRepeatableStereotype(parents, container, stereotype);
            } else {
                metadata.addDeclaredStereotype(parents, name, stereotype.getValues(), RetentionPolicy.RUNTIME);
            }
            List<String> nested = new ArrayList<>(parents.size() + 1);
            nested.addAll(parents);
            nested.add(name);
            addStereotypes(metadata, nested, stereotype);
        }
    }
}
