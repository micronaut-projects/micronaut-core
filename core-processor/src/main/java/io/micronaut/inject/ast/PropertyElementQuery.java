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
import io.micronaut.core.naming.NameUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import org.jspecify.annotations.Nullable;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Objects;
import java.util.Set;

/**
 * Represents a query for {@link PropertyElement} definitions.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 * @see PropertyElement
 * @see ClassElement#getBeanProperties(PropertyElementQuery)
 * @see BeanProperties
 */
public final class PropertyElementQuery {

    private static final String[] DEFAULT_READ_PREFIXES = { AccessorsStyle.DEFAULT_READ_PREFIX };
    private static final String[] DEFAULT_WRITE_PREFIXES = { AccessorsStyle.DEFAULT_WRITE_PREFIX };
    private static final EnumSet<BeanProperties.AccessKind> DEFAULT_ACCESS_KINDS = EnumSet.of(BeanProperties.AccessKind.METHOD);
    private static final JsonAutoDetectConfiguration.Visibility DEFAULT_JSON_FIELD_VISIBILITY = JsonAutoDetectConfiguration.Visibility.PUBLIC_ONLY;
    private static final JsonAutoDetectConfiguration.Visibility DEFAULT_JSON_GETTER_VISIBILITY = JsonAutoDetectConfiguration.Visibility.PUBLIC_ONLY;
    private static final JsonAutoDetectConfiguration.Visibility DEFAULT_JSON_IS_GETTER_VISIBILITY = JsonAutoDetectConfiguration.Visibility.PUBLIC_ONLY;
    private static final JsonAutoDetectConfiguration.Visibility DEFAULT_JSON_SETTER_VISIBILITY = JsonAutoDetectConfiguration.Visibility.ANY;
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
    private boolean jsonAutoDetectConfigured;
    private JsonAutoDetectConfiguration.Visibility jsonFieldVisibility = DEFAULT_JSON_FIELD_VISIBILITY;
    private JsonAutoDetectConfiguration.Visibility jsonGetterVisibility = DEFAULT_JSON_GETTER_VISIBILITY;
    private JsonAutoDetectConfiguration.Visibility jsonIsGetterVisibility = DEFAULT_JSON_IS_GETTER_VISIBILITY;
    private JsonAutoDetectConfiguration.Visibility jsonSetterVisibility = DEFAULT_JSON_SETTER_VISIBILITY;

    /**
     * Creates a query for the given metadata.
     * @param annotationMetadata The metadata
     * @return The query
     */
    public static PropertyElementQuery of(AnnotationMetadata annotationMetadata) {
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

        AnnotationValue<JsonAutoDetectConfiguration> jsonAutoDetect = annotationMetadata.getAnnotation(JsonAutoDetectConfiguration.class);
        if (jsonAutoDetect != null) {
            conf.jsonAutoDetect(
                resolveJsonVisibility(
                    jsonAutoDetect,
                    JsonAutoDetectConfiguration.MEMBER_FIELD_VISIBILITY,
                    DEFAULT_JSON_FIELD_VISIBILITY
                ),
                resolveJsonVisibility(
                    jsonAutoDetect,
                    JsonAutoDetectConfiguration.MEMBER_GETTER_VISIBILITY,
                    DEFAULT_JSON_GETTER_VISIBILITY
                ),
                resolveJsonVisibility(
                    jsonAutoDetect,
                    JsonAutoDetectConfiguration.MEMBER_IS_GETTER_VISIBILITY,
                    DEFAULT_JSON_IS_GETTER_VISIBILITY
                ),
                resolveJsonVisibility(
                    jsonAutoDetect,
                    JsonAutoDetectConfiguration.MEMBER_SETTER_VISIBILITY,
                    DEFAULT_JSON_SETTER_VISIBILITY
                )
            );
        }
        return conf;
    }

    private static JsonAutoDetectConfiguration.Visibility resolveJsonVisibility(AnnotationValue<JsonAutoDetectConfiguration> annotation,
                                                                               String member,
                                                                               JsonAutoDetectConfiguration.Visibility defaultVisibility) {
        JsonAutoDetectConfiguration.Visibility visibility = annotation.enumValue(member, JsonAutoDetectConfiguration.Visibility.class)
            .orElse(JsonAutoDetectConfiguration.Visibility.DEFAULT);
        return visibility == JsonAutoDetectConfiguration.Visibility.DEFAULT ? defaultVisibility : visibility;
    }

    /**
     * @return Whether to ignore setters that don't match the getter return type.
     */
    public boolean isIgnoreSettersWithDifferingType() {
        return ignoreSettersWithDifferingType;
    }

    /**
     * Set whether to ignore setters that have a different receiver type to the getter return type.
     * @param shouldIgnore True if they should be ignored.
     * @return This PropertyElementQuery
     */
    public PropertyElementQuery ignoreSettersWithDifferingType(boolean shouldIgnore) {
        this.ignoreSettersWithDifferingType = shouldIgnore;
        return this;
    }

    /**
     * @return The visibility strategy.
     * @see io.micronaut.context.annotation.BeanProperties.Visibility
     */
    public BeanProperties.Visibility getVisibility() {
        return visibility;
    }

    /**
     * Sets the visibility strategy.
     * @param visibility The visibility strategy
     * @return This PropertyElementQuery
     * @see io.micronaut.context.annotation.BeanProperties.Visibility
     */
    public PropertyElementQuery visibility(BeanProperties.Visibility visibility) {
        this.visibility = Objects.requireNonNullElse(visibility, BeanProperties.Visibility.DEFAULT);
        return this;
    }

    /**
     * The access kinds.
     * @return A set of access kinds
     * @see BeanProperties.AccessKind
     */
    public Set<BeanProperties.AccessKind> getAccessKinds() {
        return accessKinds;
    }

    /**
     * Returns whether Jackson auto-detect visibility metadata is configured.
     *
     * @return Whether Jackson auto-detect visibility metadata is configured.
     */
    public boolean isJsonAutoDetectConfigured() {
        return jsonAutoDetectConfigured;
    }

    /**
     * Tests field visibility using mapped Jackson auto-detect metadata.
     *
     * @param fieldElement The field
     * @return True if the field is visible.
     */
    public boolean isJsonAutoDetectFieldVisible(FieldElement fieldElement) {
        return isJsonVisible(fieldElement, jsonFieldVisibility);
    }

    /**
     * Tests getter visibility using mapped Jackson auto-detect metadata.
     *
     * @param methodElement The method
     * @param methodName The method name
     * @return True if the getter is visible.
     */
    public boolean isJsonAutoDetectGetterVisible(MethodElement methodElement, String methodName) {
        if (isJsonIsGetterName(methodName)) {
            return isJsonIsGetter(methodElement, methodName) && isJsonVisible(methodElement, jsonIsGetterVisibility);
        }
        return isJsonVisible(methodElement, jsonGetterVisibility);
    }

    /**
     * Tests setter visibility using mapped Jackson auto-detect metadata.
     *
     * @param methodElement The method
     * @return True if the setter is visible.
     */
    public boolean isJsonAutoDetectSetterVisible(MethodElement methodElement) {
        return isJsonVisible(methodElement, jsonSetterVisibility);
    }

    /**
     * Tests whether the method name is a Jackson auto-detect reader name.
     *
     * @param methodElement The method
     * @param methodName The method name
     * @return True if the method can be a Jackson auto-detect reader.
     */
    public boolean isJsonAutoDetectReaderName(MethodElement methodElement, String methodName) {
        return (jsonGetterVisibility != JsonAutoDetectConfiguration.Visibility.NONE
            && NameUtils.isReaderName(methodName, AccessorsStyle.DEFAULT_READ_PREFIX)
            && !isJsonIsGetterName(methodName))
            || (jsonIsGetterVisibility != JsonAutoDetectConfiguration.Visibility.NONE
                && isJsonIsGetter(methodElement, methodName));
    }

    /**
     * Tests whether the method name is a Jackson auto-detect writer name.
     *
     * @param methodName The method name
     * @return True if the method can be a Jackson auto-detect writer.
     */
    public boolean isJsonAutoDetectWriterName(String methodName) {
        return jsonSetterVisibility != JsonAutoDetectConfiguration.Visibility.NONE &&
            NameUtils.isWriterName(methodName, AccessorsStyle.DEFAULT_WRITE_PREFIX);
    }

    private PropertyElementQuery jsonAutoDetect(JsonAutoDetectConfiguration.Visibility fieldVisibility,
                                                JsonAutoDetectConfiguration.Visibility getterVisibility,
                                                JsonAutoDetectConfiguration.Visibility isGetterVisibility,
                                                JsonAutoDetectConfiguration.Visibility setterVisibility) {
        jsonAutoDetectConfigured = true;
        jsonFieldVisibility = fieldVisibility;
        jsonGetterVisibility = getterVisibility;
        jsonIsGetterVisibility = isGetterVisibility;
        jsonSetterVisibility = setterVisibility;
        return this;
    }

    private static boolean isJsonVisible(MemberElement memberElement, JsonAutoDetectConfiguration.Visibility visibility) {
        return switch (visibility) {
            case ANY -> true;
            case NON_PRIVATE -> !memberElement.isPrivate();
            case PROTECTED_AND_PUBLIC -> memberElement.isProtected() || memberElement.isPublic();
            case PUBLIC_ONLY -> memberElement.isPublic();
            case NONE, DEFAULT -> false;
        };
    }

    private static boolean isJsonIsGetterName(String methodName) {
        return NameUtils.isReaderName(methodName, "is") && methodName.startsWith("is");
    }

    private static boolean isJsonIsGetter(MethodElement methodElement, String methodName) {
        return isJsonIsGetterName(methodName) && isBoolean(methodElement.getReturnType());
    }

    private static boolean isBoolean(ClassElement type) {
        return type.getName().equals(PrimitiveElement.BOOLEAN.getName()) || type.getName().equals(Boolean.class.getName());
    }

    /**
     * Sets the access kinds.
     * @param accessKinds The access kinds
     * @return This PropertyElementQuery
     */
    public PropertyElementQuery accessKinds(@Nullable Set<BeanProperties.AccessKind> accessKinds) {
        if (CollectionUtils.isNotEmpty(accessKinds)) {
            this.accessKinds = Collections.unmodifiableSet(accessKinds);
        } else {
            this.accessKinds = DEFAULT_ACCESS_KINDS;
        }
        return this;
    }

    /**
     * The property names to include.
     * @return The includes.
     */
    public Set<String> getIncludes() {
        return includes;
    }

    /**
     * Sets the property names to include.
     * @param includes The includes
     * @return This PropertyElementQuery
     */
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
     * @return The excludes
     */
    public Set<String> getExcludes() {
        return excludes;
    }

    /**
     * Sets the excluded property names.
     * @param excludes The property names to exclude
     * @return This PropertyElementQuery
     */
    public PropertyElementQuery excludes(@Nullable Set<String> excludes) {
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
    public String[] getReadPrefixes() {
        return readPrefixes;
    }

    /**
     * Sets the read method prefixes.
     * @param readPrefixes The read methos prefixes
     * @return This PropertyElementQuery
     */
    public PropertyElementQuery readPrefixes(String... readPrefixes) {
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
    public String [] getWritePrefixes() {
        return writePrefixes;
    }

    /**
     * Sets the write method prefixes.
     * @param writePrefixes The write prefixes
     * @return This PropertyElementQuery
     */
    public PropertyElementQuery writePrefixes(String [] writePrefixes) {
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
     * @param allowSetterWithZeroArgs True to allow zero argument setters
     * @return This PropertyElementQuery
     */
    public PropertyElementQuery allowSetterWithZeroArgs(boolean allowSetterWithZeroArgs) {
        this.allowSetterWithZeroArgs = allowSetterWithZeroArgs;
        return this;
    }

    /**
     * Whether to allow setters with multiple arguments.
     * @return True if setters with multiple arguments are allowed.
     */
    public boolean isAllowSetterWithMultipleArgs() {
        return allowSetterWithMultipleArgs;
    }

    /**
     * Sets whether to allow setters with multiple arguments.
     * @param allowSetterWithMultipleArgs True if setters with multiple arguments are allowed.
     * @return This PropertyElementQuery
     */
    public PropertyElementQuery allowSetterWithMultipleArgs(boolean allowSetterWithMultipleArgs) {
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
     * @param allowStaticProperties True if static properties are allowed.
     * @return This PropertyElementQuery
     */
    public PropertyElementQuery allowStaticProperties(boolean allowStaticProperties) {
        this.allowStaticProperties = allowStaticProperties;
        return this;
    }

    /**
     * @return The excludes annotation names.
     */
    public Set<String> getExcludedAnnotations() {
        return excludedAnnotations;
    }

    /**
     * Sets the annotations names that should be used to indicate a property is excluded.
     * @param excludedAnnotations The excluded annotation names
     * @return This PropertyElementQuery
     */
    public PropertyElementQuery excludedAnnotations(@Nullable Set<String> excludedAnnotations) {
        if (CollectionUtils.isNotEmpty(excludedAnnotations)) {
            this.excludedAnnotations = Collections.unmodifiableSet(excludedAnnotations);
        } else {
            this.excludedAnnotations = Collections.emptySet();
        }
        return this;
    }
}
