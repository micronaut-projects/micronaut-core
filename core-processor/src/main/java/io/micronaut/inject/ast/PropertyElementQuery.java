/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.inject.ast;

import io.micronaut.context.annotation.BeanProperties;
import io.micronaut.core.annotation.AccessorsStyle;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.annotation.Nullable;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a query for {@link PropertyElement} definitions.
 *
 * @author Denis Stepanov
 * @see PropertyElement
 * @see ClassElement#getBeanProperties(PropertyElementQuery)
 * @see BeanProperties
 * @since 4.0.0
 */
public final class PropertyElementQuery {

    private static final String[] DEFAULT_READ_PREFIXES = {AccessorsStyle.DEFAULT_READ_PREFIX};
    private static final String[] DEFAULT_WRITE_PREFIXES = {AccessorsStyle.DEFAULT_WRITE_PREFIX};
    private static final EnumSet<BeanProperties.AccessKind> DEFAULT_ACCESS_KINDS = EnumSet.of(BeanProperties.AccessKind.METHOD);
    private BeanProperties.Visibility visibility = BeanProperties.Visibility.DEFAULT;
    private Set<BeanProperties.AccessKind> accessKinds = DEFAULT_ACCESS_KINDS;
    private Set<String> includes = Collections.emptySet();
    private Set<String> excludes = Collections.emptySet();
    private String[] readPrefixes = DEFAULT_READ_PREFIXES;
    private String[] writePrefixes = DEFAULT_WRITE_PREFIXES;
    private boolean allowSetterWithZeroArgs;
    private boolean allowSetterWithMultipleArgs;
    private boolean allowStaticProperties;

    private boolean ignoreSettersWithDifferingType;
    private Set<String> excludedAnnotations = Collections.emptySet();

    /**
     * Creates a query for the given metadata.
     *
     * @param annotationMetadata The metadata
     * @return The query
     */
    public static @NonNull PropertyElementQuery of(@NonNull AnnotationMetadata annotationMetadata) {
        PropertyElementQuery conf = new PropertyElementQuery();

        AnnotationValue<BeanProperties> annotation = annotationMetadata.getAnnotation(BeanProperties.class);
        if (annotation != null) {
            annotation.enumValue(BeanProperties.MEMBER_VISIBILITY, BeanProperties.Visibility.class)
                .ifPresent(conf::visibility);
            if (annotation.isPresent(BeanProperties.MEMBER_ACCESS_KIND)) {
                conf.accessKinds(
                    annotation.enumValuesSet(BeanProperties.MEMBER_ACCESS_KIND, BeanProperties.AccessKind.class)
                );
            }
            annotation.booleanValue(BeanProperties.MEMBER_ALLOW_WRITE_WITH_ZERO_ARGS)
                .ifPresent(conf::allowSetterWithZeroArgs);
            annotation.booleanValue(BeanProperties.MEMBER_ALLOW_WRITE_WITH_MULTIPLE_ARGS)
                .ifPresent(conf::allowSetterWithMultipleArgs);

            conf.includes(CollectionUtils.setOf(annotation.stringValues(BeanProperties.MEMBER_INCLUDES)));
            conf.excludes(CollectionUtils.setOf(annotation.stringValues(BeanProperties.MEMBER_EXCLUDES)));

            conf.excludedAnnotations(CollectionUtils.setOf(annotation.stringValues(BeanProperties.MEMBER_EXCLUDED_ANNOTATIONS)));
        }

        String[] readPrefixes = annotationMetadata.stringValues(AccessorsStyle.class, "readPrefixes");
        if (ArrayUtils.isNotEmpty(readPrefixes)) {
            conf.readPrefixes(readPrefixes);
        }
        String[] writerPrefixes = annotationMetadata.stringValues(AccessorsStyle.class, "writePrefixes");
        if (ArrayUtils.isNotEmpty(writerPrefixes)) {
            conf.writePrefixes(writerPrefixes);
        }
        return conf;
    }

    /**
     * @return Whether to ignore setters that don't match the getter return type.
     */
    public boolean isIgnoreSettersWithDifferingType() {
        return ignoreSettersWithDifferingType;
    }

    /**
     * Set whether to ignore setters that have a different receiver type to the getter return type.
     *
     * @param shouldIgnore True if they should be ignored.
     * @return This PropertyElementQuery
     */
    public @NonNull PropertyElementQuery ignoreSettersWithDifferingType(boolean shouldIgnore) {
        this.ignoreSettersWithDifferingType = shouldIgnore;
        return this;
    }

    /**
     * @return The visibility strategy.
     * @see io.micronaut.context.annotation.BeanProperties.Visibility
     */
    @NonNull
    public BeanProperties.Visibility getVisibility() {
        return visibility;
    }

    /**
     * Sets the visibility strategy.
     *
     * @param visibility The visibility strategy
     * @return This PropertyElementQuery
     * @see io.micronaut.context.annotation.BeanProperties.Visibility
     */
    public @NonNull PropertyElementQuery visibility(BeanProperties.Visibility visibility) {
        this.visibility = Objects.requireNonNullElse(visibility, BeanProperties.Visibility.DEFAULT);
        return this;
    }

    /**
     * The access kinds.
     *
     * @return A set of access kinds
     * @see BeanProperties.AccessKind
     */
    @NonNull
    public Set<BeanProperties.AccessKind> getAccessKinds() {
        return accessKinds;
    }

    /**
     * Sets the access kinds.
     *
     * @param accessKinds The access kinds
     * @return This PropertyElementQuery
     */
    public @NonNull PropertyElementQuery accessKinds(@Nullable Set<BeanProperties.AccessKind> accessKinds) {
        if (CollectionUtils.isNotEmpty(accessKinds)) {
            this.accessKinds = Collections.unmodifiableSet(accessKinds);
        } else {
            this.accessKinds = DEFAULT_ACCESS_KINDS;
        }
        return this;
    }

    /**
     * The property names to include.
     *
     * @return The includes.
     */
    @NonNull
    public Set<String> getIncludes() {
        return includes;
    }

    /**
     * Sets the property names to include.
     *
     * @param includes The includes
     * @return This PropertyElementQuery
     */
    @NonNull
    public PropertyElementQuery includes(@Nullable Set<String> includes) {
        if (CollectionUtils.isNotEmpty(includes)) {
            this.includes = Collections.unmodifiableSet(includes);
        } else {
            this.includes = Collections.emptySet();
        }
        return this;
    }

    /**
     * The property names to exclude.
     *
     * @return The excludes
     */
    @NonNull
    public Set<String> getExcludes() {
        return excludes;
    }

    /**
     * Sets the excluded property names.
     *
     * @param excludes The property names to exclude
     * @return This PropertyElementQuery
     */
    public @NonNull PropertyElementQuery excludes(@Nullable Set<String> excludes) {
        if (CollectionUtils.isNotEmpty(excludes)) {
            this.excludes = Collections.unmodifiableSet(excludes);
        } else {
            this.excludes = Collections.emptySet();
        }
        return this;
    }

    /**
     * @return The read method prefixes.
     */
    @NonNull
    public String[] getReadPrefixes() {
        return readPrefixes;
    }

    /**
     * Sets the read method prefixes.
     *
     * @param readPrefixes The read method prefixes
     * @return This PropertyElementQuery
     */
    public @NonNull PropertyElementQuery readPrefixes(String... readPrefixes) {
        if (ArrayUtils.isNotEmpty(readPrefixes)) {
            this.readPrefixes = readPrefixes;
        } else {
            this.readPrefixes = DEFAULT_READ_PREFIXES;
        }
        return this;
    }

    /**
     * @return The write method prefixes.
     */
    public @NonNull String[] getWritePrefixes() {
        return writePrefixes;
    }

    /**
     * Sets the write method prefixes.
     *
     * @param writePrefixes The write prefixes
     * @return This PropertyElementQuery
     */
    public @NonNull PropertyElementQuery writePrefixes(String[] writePrefixes) {
        if (ArrayUtils.isNotEmpty(writePrefixes)) {
            this.writePrefixes = writePrefixes;
        } else {
            this.writePrefixes = DEFAULT_WRITE_PREFIXES;
        }
        return this;
    }

    /**
     * @return Whether to allow zero argument setters for boolean values etc.
     */
    public boolean isAllowSetterWithZeroArgs() {
        return allowSetterWithZeroArgs;
    }

    /**
     * Sets whether to allow zero argument setters for boolean properties etc.
     *
     * @param allowSetterWithZeroArgs True to allow zero argument setters
     * @return This PropertyElementQuery
     */
    public @NonNull PropertyElementQuery allowSetterWithZeroArgs(boolean allowSetterWithZeroArgs) {
        this.allowSetterWithZeroArgs = allowSetterWithZeroArgs;
        return this;
    }

    /**
     * Whether to allow setters with multiple arguments.
     *
     * @return True if setters with multiple arguments are allowed.
     */
    public boolean isAllowSetterWithMultipleArgs() {
        return allowSetterWithMultipleArgs;
    }

    /**
     * Sets whether to allow setters with multiple arguments.
     *
     * @param allowSetterWithMultipleArgs True if setters with multiple arguments are allowed.
     * @return This PropertyElementQuery
     */
    public @NonNull PropertyElementQuery allowSetterWithMultipleArgs(boolean allowSetterWithMultipleArgs) {
        this.allowSetterWithMultipleArgs = allowSetterWithMultipleArgs;
        return this;
    }

    /**
     * @return Whether to allow static properties.
     */
    public boolean isAllowStaticProperties() {
        return allowStaticProperties;
    }

    /**
     * Sets whether to allow static properties.
     *
     * @param allowStaticProperties True if static properties are allowed.
     * @return This PropertyElementQuery
     */
    public @NonNull PropertyElementQuery allowStaticProperties(boolean allowStaticProperties) {
        this.allowStaticProperties = allowStaticProperties;
        return this;
    }

    /**
     * @return The excludes annotation names.
     */
    @NonNull
    public Set<String> getExcludedAnnotations() {
        return excludedAnnotations;
    }

    /**
     * Sets the annotations names that should be used to indicate a property is excluded.
     *
     * @param excludedAnnotations The excluded annotation names
     * @return This PropertyElementQuery
     */
    public @NonNull PropertyElementQuery excludedAnnotations(@Nullable Set<String> excludedAnnotations) {
        if (CollectionUtils.isNotEmpty(excludedAnnotations)) {
            this.excludedAnnotations = Collections.unmodifiableSet(excludedAnnotations);
        } else {
            this.excludedAnnotations = Collections.emptySet();
        }
        return this;
    }
}
