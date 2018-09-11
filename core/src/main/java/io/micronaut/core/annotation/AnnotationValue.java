/*
 * Copyright 2017-2018 original authors
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

package io.micronaut.core.annotation;

import io.micronaut.core.convert.ArgumentConversionContext;
import io.micronaut.core.convert.ConversionContext;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.convert.value.ConvertibleValues;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.value.ValueResolver;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.*;

/**
 * A runtime representation of the an annotation and its values.
 *
 * <p>This class implements the {@link ValueResolver} interface and methods such as {@link ValueResolver#get(CharSequence, Class)} can be used to retrieve the values of annotation members.</p>
 *
 * <p>If a member is not present then the methods of the class will attempt to resolve the default value for a given annotation member. In this sense the behaviour of this class is similar to how
 * a implementation of {@link Annotation} behaves.</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 * @param <A> The annotation type
 */
public class AnnotationValue<A extends Annotation> implements ValueResolver<CharSequence> {

    private final String annotationName;
    private final ConvertibleValues<Object> convertibleValues;
    private final Map<CharSequence, Object> values;
    private final Map<String, Object> defaultValues;

    /**
     * @param annotationName The annotation name
     * @param values         The values
     */
    @SuppressWarnings("unchecked")
    public AnnotationValue(String annotationName, Map<CharSequence, Object> values) {
        this.annotationName = annotationName.intern();
        this.convertibleValues = newConvertibleValues(values);
        this.values = values;
        this.defaultValues = Collections.EMPTY_MAP;
    }

    /**
     * @param annotationName The annotation name
     * @param values         The values
     * @param defaultValues The default values
     */
    @SuppressWarnings("unchecked")
    public AnnotationValue(String annotationName, Map<CharSequence, Object> values, Map<String, Object> defaultValues) {
        this.annotationName = annotationName.intern();
        this.convertibleValues = newConvertibleValues(values);
        this.values = values;
        this.defaultValues = defaultValues != null ? defaultValues : Collections.EMPTY_MAP;
    }

    /**
     * @param annotationName The annotation name
     */
    @SuppressWarnings("unchecked")
    public AnnotationValue(String annotationName) {
        this.annotationName = annotationName.intern();
        this.convertibleValues = ConvertibleValues.EMPTY;
        this.values = Collections.EMPTY_MAP;
        this.defaultValues = Collections.EMPTY_MAP;
    }

    /**
     * @param annotationName    The annotation name
     * @param convertibleValues The convertible values
     */
    public AnnotationValue(String annotationName, ConvertibleValues<Object> convertibleValues) {
        this.annotationName = annotationName.intern();
        this.convertibleValues = convertibleValues;
        Map<String, Object> existing = convertibleValues.asMap();
        this.values = new HashMap<>(existing.size());
        this.values.putAll(existing);
        this.defaultValues = Collections.EMPTY_MAP;
    }

    /**
     * Internal copy constructor.
     * @param target The target
     * @param defaultValues The default values
     * @param convertibleValues The convertible values
     */
    @Internal
    protected AnnotationValue(AnnotationValue<A> target, Map<String, Object> defaultValues, ConvertibleValues<Object> convertibleValues) {
        this.annotationName = target.annotationName;
        this.defaultValues = defaultValues != null ? defaultValues : target.defaultValues;
        this.values = target.values;
        this.convertibleValues = convertibleValues;
    }

    /**
     * The annotation name.
     *
     * @return The annotation name
     */
    public @Nonnull final String getAnnotationName() {
        return annotationName;
    }

    /**
     * Whether a particular member is present.
     * @param member The member
     * @return True if it is
     */
    public final boolean contains(String member) {
        return this.values.containsKey(member);
    }

    /**
     * Resolves the names of all the present annotation members.
     *
     * @return The names of the members
     */
    public @Nonnull final Set<CharSequence> getMemberNames() {
        return values.keySet();
    }

    /**
     * @return The attribute values
     */
    @SuppressWarnings("unchecked")
    public @Nonnull Map<CharSequence, Object> getValues() {
        return Collections.unmodifiableMap(values);
    }

    /**
     * @return The convertible values
     */
    public @Nonnull ConvertibleValues<Object> getConvertibleValues() {
        return convertibleValues;
    }

    @Override
    public <T> Optional<T> get(CharSequence member, ArgumentConversionContext<T> conversionContext) {
        Optional<T> result = convertibleValues.get(member, conversionContext);
        if (!result.isPresent()) {
            Object dv = defaultValues.get(member.toString());
            if (dv != null) {
                return ConversionService.SHARED.convert(dv, conversionContext);
            }
        }
        return result;
    }

    /**
     * Get the value of the {@code value} member of the annotation.
     *
     * @param conversionContext The conversion context
     * @param <T> The type
     * @return The result
     */
    public <T> Optional<T> getValue(ArgumentConversionContext<T> conversionContext) {
        return get(AnnotationMetadata.VALUE_MEMBER, conversionContext);
    }

    /**
     * Get the value of the {@code value} member of the annotation.
     *
     * @param argument The argument
     * @param <T> The type
     * @return The result
     */
    public final <T> Optional<T> getValue(Argument<T> argument) {
        return getValue(ConversionContext.of(argument));
    }

    /**
     * Get the value of the {@code value} member of the annotation.
     *
     * @param type The type
     * @param <T> The type
     * @return The result
     */
    public final <T> Optional<T> getValue(Class<T> type) {
        return getValue(ConversionContext.of(type));
    }

    /**
     * Get the value of the {@code value} member of the annotation.
     *
     * @param type The type
     * @param <T> The type
     * @throws IllegalStateException If no member is available that conforms to the given type
     * @return The result
     */
    public @Nonnull final <T> T getRequiredValue(Class<T> type) {
        return getRequiredValue(AnnotationMetadata.VALUE_MEMBER, type);
    }

    /**
     * Get the value of the {@code value} member of the annotation.
     *
     * @param member The member
     * @param type The type
     * @param <T> The type
     * @throws IllegalStateException If no member is available that conforms to the given name and type
     * @return The result
     */
    public @Nonnull final <T> T getRequiredValue(String member, Class<T> type) {
        return get(member, ConversionContext.of(type)).orElseThrow(() -> new IllegalStateException("No value available for annotation member @" + annotationName + "[" + member + "] of type: " + type));
    }

    /**
     * Gets a list of {@link AnnotationValue} for the given member.
     *
     * @param member The member
     * @param type The type
     * @param <T> The type
     * @throws IllegalStateException If no member is available that conforms to the given name and type
     * @return The result
     */
    public @Nonnull final <T extends Annotation> List<AnnotationValue<T>> getAnnotations(String member, Class<T> type) {
        AnnotationValue[] values = get(member, AnnotationValue[].class).orElse(null);
        if (ArrayUtils.isNotEmpty(values)) {
            List<AnnotationValue<T>> list = new ArrayList<>(values.length);
            String typeName = type.getName();
            for (AnnotationValue value : values) {
                if (value.getAnnotationName().equals(typeName)) {
                    //noinspection unchecked
                    list.add(value);
                }
            }
            return list;
        }
        return Collections.emptyList();
    }

    @Override
    public int hashCode() {
        return AnnotationUtil.calculateHashCode(getValues());
    }

    @Override
    public boolean equals(Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (!AnnotationValue.class.isInstance(obj)) {
            return false;
        }

        AnnotationValue other = AnnotationValue.class.cast(obj);

        Map<CharSequence, Object> otherValues = other.getValues();
        Map<CharSequence, Object> values = getValues();
        if (values.size() != otherValues.size()) {
            return false;
        }

        // compare annotation member values
        for (Map.Entry<CharSequence, Object> member : values.entrySet()) {
            Object value = member.getValue();
            Object otherValue = otherValues.get(member.getKey());

            if (!AnnotationUtil.areEqual(value, otherValue)) {
                return false;
            }
        }

        return true;
    }

    /**
     * Start building a new annotation for the given name.
     *
     * @param annotationName The annotation name
     * @return The builder
     */
    public static AnnotationValueBuilder<?> builder(String annotationName) {
        return new AnnotationValueBuilder<>(annotationName);
    }

    /**
     * Start building a new annotation for the given name.
     *
     * @param annotation The annotation name
     * @param <T> The annotation type
     * @return The builder
     */
    public static <T extends Annotation> AnnotationValueBuilder<T> builder(Class<T> annotation) {
        return new AnnotationValueBuilder<>(annotation);
    }

    /**
     * Subclasses can override to provide a custom convertible values instance.
     *
     * @param values The values
     * @return The instance
     */
    private ConvertibleValues<Object> newConvertibleValues(Map<CharSequence, Object> values) {
        return ConvertibleValues.of(values);
    }

}
