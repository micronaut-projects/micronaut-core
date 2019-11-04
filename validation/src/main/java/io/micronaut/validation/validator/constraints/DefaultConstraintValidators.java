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
package io.micronaut.validation.validator.constraints;

import io.micronaut.context.BeanContext;
import io.micronaut.context.Qualifier;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.beans.BeanProperty;
import io.micronaut.core.beans.BeanWrapper;
import io.micronaut.core.reflect.ReflectionUtils;
import io.micronaut.core.type.Argument;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;
import io.micronaut.core.util.clhm.ConcurrentLinkedHashMap;
import io.micronaut.inject.qualifiers.Qualifiers;
import io.micronaut.inject.qualifiers.TypeArgumentQualifier;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import javax.validation.constraints.*;
import java.lang.annotation.Annotation;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.time.*;
import java.time.chrono.HijrahDate;
import java.time.chrono.JapaneseDate;
import java.time.chrono.MinguoDate;
import java.time.chrono.ThaiBuddhistDate;
import java.time.temporal.TemporalAccessor;
import java.util.*;
import java.util.concurrent.atomic.DoubleAccumulator;
import java.util.concurrent.atomic.DoubleAdder;

/**
 * A factory bean that contains implementation for many of the default validations.
 * This approach is preferred as it generates less classes and smaller byte code than defining a
 * validator class for each case.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
@Introspected
public class DefaultConstraintValidators implements ConstraintValidatorRegistry {

    private final Map<ValidatorKey, ConstraintValidator> validatorCache = new ConcurrentLinkedHashMap.Builder<ValidatorKey, ConstraintValidator>().initialCapacity(10).maximumWeightedCapacity(40).build();

    private final ConstraintValidator<AssertFalse, Boolean> assertFalseValidator =
            (value, annotationMetadata, context) -> value == null || !value;

    private final ConstraintValidator<AssertTrue, Boolean> assertTrueValidator =
            (value, annotationMetadata, context) -> value == null || value;

    private final DecimalMaxValidator<CharSequence> decimalMaxValidatorCharSequence =
            (value, bigDecimal) -> new BigDecimal(value.toString()).compareTo(bigDecimal);

    private final DecimalMaxValidator<Number> decimalMaxValidatorNumber = DefaultConstraintValidators::compareNumber;

    private final DecimalMinValidator<CharSequence> decimalMinValidatorCharSequence =
            (value, bigDecimal) -> new BigDecimal(value.toString()).compareTo(bigDecimal);

    private final DecimalMinValidator<Number> decimalMinValidatorNumber = DefaultConstraintValidators::compareNumber;

    private final DigitsValidator<Number> digitsValidatorNumber = (value) -> {
            if (value instanceof BigDecimal) {
                return (BigDecimal) value;
            }
            return new BigDecimal(value.toString());
        };

    private final DigitsValidator<CharSequence> digitsValidatorCharSequence =
            value -> new BigDecimal(value.toString());

    private final ConstraintValidator<Max, Number> maxNumberValidator =
            (value, annotationMetadata, context) -> {
            if (value == null) {
                return true; // nulls are allowed according to spec
            }
            final Long max = annotationMetadata.getValue(Long.class).orElseThrow(() ->
                    new ValidationException("@Max annotation specified without value")
            );

            if (value instanceof BigInteger) {
                return ((BigInteger) value).compareTo(BigInteger.valueOf(max)) < 0;
            } else if (value instanceof BigDecimal) {
                return ((BigDecimal) value).compareTo(BigDecimal.valueOf(max)) < 0;
            }
            return value.longValue() <= max;
        };

    private final ConstraintValidator<Min, Number> minNumberValidator =
            (value, annotationMetadata, context) -> {
            if (value == null) {
                return true; // nulls are allowed according to spec
            }
            final Long max = annotationMetadata.getValue(Long.class).orElseThrow(() ->
                    new ValidationException("@Min annotation specified without value")
            );

            if (value instanceof BigInteger) {
                return ((BigInteger) value).compareTo(BigInteger.valueOf(max)) >= 0;
            } else if (value instanceof BigDecimal) {
                return ((BigDecimal) value).compareTo(BigDecimal.valueOf(max)) >= 0;
            }
            return value.longValue() >= max;
        };

    private final ConstraintValidator<Negative, Number> negativeNumberValidator =
            (value, annotationMetadata, context) -> {
            // null is allowed according to spec
            if (value == null) {
                return true;
            }
            if (value instanceof  BigDecimal) {
                return ((BigDecimal) value).signum() < 0;
            }
            if (value instanceof BigInteger) {
                return ((BigInteger) value).signum() < 0;
            }
            if (value instanceof Double ||
                value instanceof Float  ||
                value instanceof DoubleAdder ||
                value instanceof DoubleAccumulator) {
                return value.doubleValue() < 0;
            }
            return value.longValue() < 0;
        };

    private final ConstraintValidator<NegativeOrZero, Number> negativeOrZeroNumberValidator =
            (value, annotationMetadata, context) -> {
            // null is allowed according to spec
            if (value == null) {
                return true;
            }
            if (value instanceof  BigDecimal) {
                return ((BigDecimal) value).signum() <= 0;
            }
            if (value instanceof BigInteger) {
                return ((BigInteger) value).signum() <= 0;
            }
            if (value instanceof Double ||
                value instanceof Float  ||
                value instanceof DoubleAdder ||
                value instanceof DoubleAccumulator) {
                return value.doubleValue() <= 0;
            }
            return value.longValue() <= 0;
        };

    private final ConstraintValidator<Positive, Number> positiveNumberValidator =
            (value, annotationMetadata, context) -> {
            // null is allowed according to spec
            if (value == null) {
                return true;
            }
            if (value instanceof  BigDecimal) {
                return ((BigDecimal) value).signum() > 0;
            }
            if (value instanceof BigInteger) {
                return ((BigInteger) value).signum() > 0;
            }
            if (value instanceof Double ||
                value instanceof Float  ||
                value instanceof DoubleAdder ||
                value instanceof DoubleAccumulator) {
                return value.doubleValue() > 0;
            }
            return value.longValue() > 0;
        };

    private final ConstraintValidator<PositiveOrZero, Number> positiveOrZeroNumberValidator =
            (value, annotationMetadata, context) -> {
            // null is allowed according to spec
            if (value == null) {
                return true;
            }
            if (value instanceof  BigDecimal) {
                return ((BigDecimal) value).signum() >= 0;
            }
            if (value instanceof BigInteger) {
                return ((BigInteger) value).signum() >= 0;
            }
            if (value instanceof Double ||
                value instanceof Float  ||
                value instanceof DoubleAdder ||
                value instanceof DoubleAccumulator) {
                return value.doubleValue() >= 0;
            }
            return value.longValue() >= 0;
        };

    private final ConstraintValidator<NotBlank, CharSequence> notBlankValidator =
            (value, annotationMetadata, context) ->
                StringUtils.isNotEmpty(value) && value.toString().trim().length() > 0;

    private final ConstraintValidator<NotNull, Object> notNullValidator =
            (value, annotationMetadata, context) -> value != null;

    private final ConstraintValidator<Null, Object> nullValidator =
            (value, annotationMetadata, context) -> value == null;

    private final ConstraintValidator<NotEmpty, byte[]> notEmptyByteArrayValidator =
            (value, annotationMetadata, context) -> value != null && value.length > 0;

    private final ConstraintValidator<NotEmpty, char[]> notEmptyCharArrayValidator =
            (value, annotationMetadata, context) -> value != null && value.length > 0;

    private final ConstraintValidator<NotEmpty, boolean[]> notEmptyBooleanArrayValidator =
            (value, annotationMetadata, context) -> value != null && value.length > 0;

    private final ConstraintValidator<NotEmpty, double[]> notEmptyDoubleArrayValidator =
            (value, annotationMetadata, context) -> value != null && value.length > 0;

    private final ConstraintValidator<NotEmpty, float[]> notEmptyFloatArrayValidator =
            (value, annotationMetadata, context) -> value != null && value.length > 0;

    private final ConstraintValidator<NotEmpty, int[]> notEmptyIntArrayValidator =
            (value, annotationMetadata, context) -> value != null && value.length > 0;

    private final ConstraintValidator<NotEmpty, long[]> notEmptyLongArrayValidator =
            (value, annotationMetadata, context) -> value != null && value.length > 0;

    private final ConstraintValidator<NotEmpty, Object[]> notEmptyObjectArrayValidator = (value, annotationMetadata, context) -> value != null && value.length > 0;

    private final ConstraintValidator<NotEmpty, short[]> notEmptyShortArrayValidator =
            (value, annotationMetadata, context) -> value != null && value.length > 0;

    private final ConstraintValidator<NotEmpty, CharSequence> notEmptyCharSequenceValidator =
            (value, annotationMetadata, context) -> StringUtils.isNotEmpty(value);

    private final ConstraintValidator<NotEmpty, Collection> notEmptyCollectionValidator =
            (value, annotationMetadata, context) -> CollectionUtils.isNotEmpty(value);

    private final ConstraintValidator<NotEmpty, Map> notEmptyMapValidator =
            (value, annotationMetadata, context) -> CollectionUtils.isNotEmpty(value);

    private final SizeValidator<byte[]> sizeByteArrayValidator = value -> value.length;

    private final SizeValidator<char[]> sizeCharArrayValidator = value -> value.length;

    private final SizeValidator<boolean[]> sizeBooleanArrayValidator = value -> value.length;

    private final SizeValidator<double[]> sizeDoubleArrayValidator = value -> value.length;

    private final SizeValidator<float[]> sizeFloatArrayValidator = value -> value.length;

    private final SizeValidator<int[]> sizeIntArrayValidator = value -> value.length;

    private final SizeValidator<long[]> sizeLongArrayValidator = value -> value.length;

    private final SizeValidator<short[]> sizeShortArrayValidator = value -> value.length;

    private final SizeValidator<CharSequence> sizeCharSequenceValidator = CharSequence::length;

    private final SizeValidator<Collection> sizeCollectionValidator = Collection::size;

    private final SizeValidator<Map> sizeMapValidator = Map::size;

    private final ConstraintValidator<Past, TemporalAccessor> pastTemporalAccessorConstraintValidator =
            (value, annotationMetadata, context) -> {
            if (value == null) {
                // null is valid according to spec
                return true;
            }
            Comparable comparable = getNow(value, context.getClockProvider().getClock());
            return comparable.compareTo(value) > 0;
        };

    private final ConstraintValidator<PastOrPresent, TemporalAccessor> pastOrPresentTemporalAccessorConstraintValidator =
            (value, annotationMetadata, context) -> {
            if (value == null) {
                // null is valid according to spec
                return true;
            }
            Comparable comparable = getNow(value, context.getClockProvider().getClock());
            return comparable.compareTo(value) >= 0;
        };

    private final ConstraintValidator<Future, TemporalAccessor> futureTemporalAccessorConstraintValidator = (value, annotationMetadata, context) -> {
            if (value == null) {
                // null is valid according to spec
                return true;
            }
            Comparable comparable = getNow(value, context.getClockProvider().getClock());
            return comparable.compareTo(value) < 0;
        };

    private final ConstraintValidator<FutureOrPresent, TemporalAccessor> futureOrPresentTemporalAccessorConstraintValidator = (value, annotationMetadata, context) -> {
            if (value == null) {
                // null is valid according to spec
                return true;
            }
            Comparable comparable = getNow(value, context.getClockProvider().getClock());
            return comparable.compareTo(value) <= 0;
        };

    private final @Nullable BeanContext beanContext;
    private final Map<ValidatorKey, ConstraintValidator> localValidators;

    /**
     * Default constructor.
     */
    public DefaultConstraintValidators() {
        this(null);
    }

    /**
     * Constructor used for DI.
     *
     * @param beanContext The bean context
     */
    @Inject
    protected DefaultConstraintValidators(@Nullable BeanContext beanContext) {
        this.beanContext = beanContext;
        BeanWrapper<DefaultConstraintValidators> wrapper = BeanWrapper.findWrapper(DefaultConstraintValidators.class, this).orElse(null);
        if (wrapper != null) {

            final Collection<BeanProperty<DefaultConstraintValidators, Object>> beanProperties = wrapper.getBeanProperties();
            Map<ValidatorKey, ConstraintValidator> validatorMap = new HashMap<>(beanProperties.size());
            for (BeanProperty<DefaultConstraintValidators, Object> property : beanProperties) {
                if (ConstraintValidator.class.isAssignableFrom(property.getType())) {
                    final Argument[] typeParameters = property.asArgument().getTypeParameters();
                    if (ArrayUtils.isNotEmpty(typeParameters)) {
                        final int len = typeParameters.length;

                        wrapper.getProperty(property.getName(), ConstraintValidator.class).ifPresent(constraintValidator -> {
                            if (len == 2) {
                                final Class targetType = ReflectionUtils.getWrapperType(typeParameters[1].getType());
                                final ValidatorKey key = new ValidatorKey(typeParameters[0].getType(), targetType);
                                validatorMap.put(key, constraintValidator);
                            } else if (len == 1) {
                                if (constraintValidator instanceof SizeValidator) {
                                    final ValidatorKey key = new ValidatorKey(Size.class, typeParameters[0].getType());
                                    validatorMap.put(key, constraintValidator);
                                } else if (constraintValidator instanceof DigitsValidator) {
                                    final ValidatorKey key = new ValidatorKey(Digits.class, typeParameters[0].getType());
                                    validatorMap.put(key, constraintValidator);
                                } else if (constraintValidator instanceof DecimalMaxValidator) {
                                    final ValidatorKey key = new ValidatorKey(DecimalMax.class, typeParameters[0].getType());
                                    validatorMap.put(key, constraintValidator);
                                } else if (constraintValidator instanceof DecimalMinValidator) {
                                    final ValidatorKey key = new ValidatorKey(DecimalMin.class, typeParameters[0].getType());
                                    validatorMap.put(key, constraintValidator);
                                }
                            }
                        });
                    }
                }
            }
            validatorMap.put(
                    new ValidatorKey(Pattern.class, CharSequence.class),
                    new PatternValidator()
            );
            validatorMap.put(
                    new ValidatorKey(Email.class, CharSequence.class),
                    new EmailValidator()
            );
            this.localValidators = validatorMap;
        } else {
            this.localValidators = Collections.emptyMap();
        }
    }

    @SuppressWarnings("unchecked")
    @Nonnull
    @Override
    public <A extends Annotation, T> Optional<ConstraintValidator<A, T>> findConstraintValidator(@Nonnull Class<A> constraintType, @Nonnull Class<T> targetType) {
        ArgumentUtils.requireNonNull("constraintType", constraintType);
        ArgumentUtils.requireNonNull("targetType", targetType);
        final ValidatorKey key = new ValidatorKey(constraintType, targetType);
        targetType = ReflectionUtils.getWrapperType(targetType);
        ConstraintValidator constraintValidator = localValidators.get(key);
        if (constraintValidator != null) {
            return Optional.of(constraintValidator);
        } else {
            constraintValidator = validatorCache.get(key);
            if (constraintValidator != null) {
                return Optional.of(constraintValidator);
            } else {
                final Qualifier<ConstraintValidator> qualifier = Qualifiers.byTypeArguments(
                        constraintType,
                        ReflectionUtils.getWrapperType(targetType)
                );
                Class<T> finalTargetType = targetType;
                final Optional<ConstraintValidator> local = localValidators.entrySet().stream().filter(entry -> {
                            final ValidatorKey k = entry.getKey();
                            return TypeArgumentQualifier.areTypesCompatible(
                                    new Class[]{constraintType, finalTargetType},
                                    Arrays.asList(k.constraintType, k.targetType)
                            );
                        }
                ).map(Map.Entry::getValue).findFirst();

                if (local.isPresent()) {
                    validatorCache.put(key, local.get());
                    return (Optional) local;
                } else if (beanContext != null) {
                    final ConstraintValidator cv = beanContext
                            .findBean(ConstraintValidator.class, qualifier).orElse(ConstraintValidator.VALID);
                    validatorCache.put(key, cv);
                    if (cv != ConstraintValidator.VALID) {
                        return Optional.of(cv);
                    }
                } else {
                    // last chance lookup
                    final ConstraintValidator cv = findLocalConstraintValidator(constraintType, targetType)
                                                        .orElse(ConstraintValidator.VALID);
                    validatorCache.put(key, cv);
                    if (cv != ConstraintValidator.VALID) {
                        return Optional.of(cv);
                    }
                }
            }
        }
        return Optional.empty();
    }

    /**
     * The {@link AssertFalse} validator.
     *
     * @return The validator
     */
    public ConstraintValidator<AssertFalse, Boolean> getAssertFalseValidator() {
        return assertFalseValidator;
    }

    /**
     * The {@link AssertTrue} validator.
     *
     * @return The validator
     */
    public ConstraintValidator<AssertTrue, Boolean> getAssertTrueValidator() {
        return assertTrueValidator;
    }

    /**
     * The {@link DecimalMax} validator for char sequences.
     *
     * @return The validator
     */
    public DecimalMaxValidator<CharSequence> getDecimalMaxValidatorCharSequence() {
        return decimalMaxValidatorCharSequence;
    }

    /**
     * The {@link DecimalMax} validator for number.
     *
     * @return The validator
     */
    public DecimalMaxValidator<Number> getDecimalMaxValidatorNumber() {
        return decimalMaxValidatorNumber;
    }

    /**
     * The {@link DecimalMin} validator for char sequences.
     *
     * @return The validator
     */
    public DecimalMinValidator<CharSequence> getDecimalMinValidatorCharSequence() {
        return decimalMinValidatorCharSequence;
    }

    /**
     * The {@link DecimalMin} validator for number.
     *
     * @return The validator
     */
    public DecimalMinValidator<Number> getDecimalMinValidatorNumber() {
        return decimalMinValidatorNumber;
    }

    /**
     * The {@link Digits} validator for number.
     *
     * @return The validator
     */
    public DigitsValidator<Number> getDigitsValidatorNumber() {
        return digitsValidatorNumber;
    }

    /**
     * The {@link Digits} validator for char sequence.
     *
     * @return The validator
     */
    public DigitsValidator<CharSequence> getDigitsValidatorCharSequence() {
        return digitsValidatorCharSequence;
    }

    /**
     * The {@link Max} validator for numbers.
     *
     * @return The validator
     */
    public ConstraintValidator<Max, Number> getMaxNumberValidator() {
        return maxNumberValidator;
    }

    /**
     * The {@link Min} validator for numbers.
     *
     * @return The validator
     */
    public ConstraintValidator<Min, Number> getMinNumberValidator() {
        return minNumberValidator;
    }

    /**
     * The {@link Negative} validator for numbers.
     *
     * @return The validator
     */
    public ConstraintValidator<Negative, Number> getNegativeNumberValidator() {
        return negativeNumberValidator;
    }

    /**
     * The {@link NegativeOrZero} validator for numbers.
     *
     * @return The validator
     */
    public ConstraintValidator<NegativeOrZero, Number> getNegativeOrZeroNumberValidator() {
        return negativeOrZeroNumberValidator;
    }

    /**
     * The {@link Positive} validator for numbers.
     *
     * @return The validator
     */
    public ConstraintValidator<Positive, Number> getPositiveNumberValidator() {
        return positiveNumberValidator;
    }

    /**
     * The {@link PositiveOrZero} validator for numbers.
     *
     * @return The validator
     */
    public ConstraintValidator<PositiveOrZero, Number> getPositiveOrZeroNumberValidator() {
        return positiveOrZeroNumberValidator;
    }

    /**
     * The {@link NotBlank} validator for char sequences.
     *
     * @return The validator
     */
    public ConstraintValidator<NotBlank, CharSequence> getNotBlankValidator() {
        return notBlankValidator;
    }

    /**
     * The {@link NotNull} validator.
     *
     * @return The validator
     */
    public ConstraintValidator<NotNull, Object> getNotNullValidator() {
        return notNullValidator;
    }

    /**
     * The {@link Null} validator.
     *
     * @return The validator
     */
    public ConstraintValidator<Null, Object> getNullValidator() {
        return nullValidator;
    }

    /**
     * The {@link NotEmpty} validator for byte[].
     *
     * @return The validator
     */
    public ConstraintValidator<NotEmpty, byte[]> getNotEmptyByteArrayValidator() {
        return notEmptyByteArrayValidator;
    }

    /**
     * The {@link NotEmpty} validator for char[].
     *
     * @return The validator
     */
    public ConstraintValidator<NotEmpty, char[]> getNotEmptyCharArrayValidator() {
        return notEmptyCharArrayValidator;
    }

    /**
     * The {@link NotEmpty} validator for boolean[].
     *
     * @return The validator
     */
    public ConstraintValidator<NotEmpty, boolean[]> getNotEmptyBooleanArrayValidator() {
        return notEmptyBooleanArrayValidator;
    }

    /**
     * The {@link NotEmpty} validator for double[].
     *
     * @return The validator
     */
    public ConstraintValidator<NotEmpty, double[]> getNotEmptyDoubleArrayValidator() {
        return notEmptyDoubleArrayValidator;
    }

    /**
     * The {@link NotEmpty} validator for float[].
     *
     * @return The validator
     */
    public ConstraintValidator<NotEmpty, float[]> getNotEmptyFloatArrayValidator() {
        return notEmptyFloatArrayValidator;
    }

    /**
     * The {@link NotEmpty} validator for int[].
     *
     * @return The validator
     */
    public ConstraintValidator<NotEmpty, int[]> getNotEmptyIntArrayValidator() {
        return notEmptyIntArrayValidator;
    }

    /**
     * The {@link NotEmpty} validator for long[].
     *
     * @return The validator
     */
    public ConstraintValidator<NotEmpty, long[]> getNotEmptyLongArrayValidator() {
        return notEmptyLongArrayValidator;
    }

    /**
     * The {@link NotEmpty} validator for Object[].
     *
     * @return The validator
     */
    public ConstraintValidator<NotEmpty, Object[]> getNotEmptyObjectArrayValidator() {
        return notEmptyObjectArrayValidator;
    }

    /**
     * The {@link NotEmpty} validator for short[].
     *
     * @return The validator
     */
    public ConstraintValidator<NotEmpty, short[]> getNotEmptyShortArrayValidator() {
        return notEmptyShortArrayValidator;
    }

    /**
     * The {@link NotEmpty} validator for char sequence.
     *
     * @return The validator
     */
    public ConstraintValidator<NotEmpty, CharSequence> getNotEmptyCharSequenceValidator() {
        return notEmptyCharSequenceValidator;
    }

    /**
     * The {@link NotEmpty} validator for collection.
     *
     * @return The validator
     */
    public ConstraintValidator<NotEmpty, Collection> getNotEmptyCollectionValidator() {
        return notEmptyCollectionValidator;
    }

    /**
     * The {@link NotEmpty} validator for map.
     *
     * @return The validator
     */
    public ConstraintValidator<NotEmpty, Map> getNotEmptyMapValidator() {
        return notEmptyMapValidator;
    }

    /**
     * The {@link Size} validator for byte[].
     *
     * @return The validator
     */
    public SizeValidator<byte[]> getSizeByteArrayValidator() {
        return sizeByteArrayValidator;
    }

    /**
     * The {@link Size} validator for char[].
     *
     * @return The validator
     */
    public SizeValidator<char[]> getSizeCharArrayValidator() {
        return sizeCharArrayValidator;
    }

    /**
     * The {@link Size} validator for boolean[].
     *
     * @return The validator
     */
    public SizeValidator<boolean[]> getSizeBooleanArrayValidator() {
        return sizeBooleanArrayValidator;
    }

    /**
     * The {@link Size} validator for double[].
     *
     * @return The validator
     */
    public SizeValidator<double[]> getSizeDoubleArrayValidator() {
        return sizeDoubleArrayValidator;
    }

    /**
     * The {@link Size} validator for float[].
     *
     * @return The validator
     */
    public SizeValidator<float[]> getSizeFloatArrayValidator() {
        return sizeFloatArrayValidator;
    }

    /**
     * The {@link Size} validator for int[].
     *
     * @return The validator
     */
    public SizeValidator<int[]> getSizeIntArrayValidator() {
        return sizeIntArrayValidator;
    }

    /**
     * The {@link Size} validator for long[].
     *
     * @return The validator
     */
    public SizeValidator<long[]> getSizeLongArrayValidator() {
        return sizeLongArrayValidator;
    }

    /**
     * The {@link Size} validator for short[].
     *
     * @return The validator
     */
    public SizeValidator<short[]> getSizeShortArrayValidator() {
        return sizeShortArrayValidator;
    }

    /**
     * The {@link Size} validator for CharSequence.
     *
     * @return The validator
     */
    public SizeValidator<CharSequence> getSizeCharSequenceValidator() {
        return sizeCharSequenceValidator;
    }

    /**
     * The {@link Size} validator for Collection.
     *
     * @return The validator
     */
    public SizeValidator<Collection> getSizeCollectionValidator() {
        return sizeCollectionValidator;
    }

    /**
     * The {@link Size} validator for Map.
     *
     * @return The validator
     */
    public SizeValidator<Map> getSizeMapValidator() {
        return sizeMapValidator;
    }

    /**
     * The {@link Past} validator for temporal accessor.
     *
     * @return The validator
     */
    public ConstraintValidator<Past, TemporalAccessor> getPastTemporalAccessorConstraintValidator() {
        return pastTemporalAccessorConstraintValidator;
    }

    /**
     * The {@link PastOrPresent} validator for temporal accessor.
     *
     * @return The validator
     */
    public ConstraintValidator<PastOrPresent, TemporalAccessor> getPastOrPresentTemporalAccessorConstraintValidator() {
        return pastOrPresentTemporalAccessorConstraintValidator;
    }

    /**
     * The {@link Future} validator for temporal accessor.
     *
     * @return The validator
     */
    public ConstraintValidator<Future, TemporalAccessor> getFutureTemporalAccessorConstraintValidator() {
        return futureTemporalAccessorConstraintValidator;
    }

    /**
     * The {@link FutureOrPresent} validator for temporal accessor.
     *
     * @return The validator
     */
    public ConstraintValidator<FutureOrPresent, TemporalAccessor> getFutureOrPresentTemporalAccessorConstraintValidator() {
        return futureOrPresentTemporalAccessorConstraintValidator;
    }

    /**
     * Last chance resolve for constraint validator.
     * @param constraintType The constraint type
     * @param targetType The target type
     * @param <A> The annotation type
     * @param <T> The target type
     * @return The validator if present
     */
    protected <A extends Annotation, T> Optional<ConstraintValidator> findLocalConstraintValidator(
            @Nonnull Class<A> constraintType, @Nonnull Class<T> targetType) {
        return Optional.empty();
    }

    private Comparable<? extends TemporalAccessor> getNow(TemporalAccessor value, Clock clock) {
        if (!(value instanceof Comparable)) {
            throw new IllegalArgumentException("TemporalAccessor value must be comparable");
        }

        if (value instanceof LocalDateTime) {
            return LocalDateTime.now(clock);
        } else if (value instanceof Instant) {
            return Instant.now(clock);
        } else if (value instanceof ZonedDateTime) {
            return ZonedDateTime.now(clock);
        } else if (value instanceof OffsetDateTime) {
            return OffsetDateTime.now(clock);
        } else if (value instanceof LocalDate) {
            return LocalDate.now(clock);
        } else if (value instanceof LocalTime) {
            return LocalTime.now(clock);
        } else if (value instanceof OffsetTime) {
            return OffsetTime.now(clock);
        } else if (value instanceof MonthDay) {
            return MonthDay.now(clock);
        } else if (value instanceof Year) {
            return Year.now(clock);
        } else if (value instanceof YearMonth) {
            return YearMonth.now(clock);
        } else if (value instanceof HijrahDate) {
            return HijrahDate.now(clock);
        } else if (value instanceof JapaneseDate) {
            return JapaneseDate.now(clock);
        } else if (value instanceof ThaiBuddhistDate) {
            return ThaiBuddhistDate.now(clock);
        } else if (value instanceof MinguoDate) {
            return MinguoDate.now(clock);
        }
        throw new IllegalArgumentException("TemporalAccessor value type not supported: " + value.getClass());
    }

    /**
     * Performs the comparision for number.
     * @param value The value
     * @param bigDecimal The big decimal
     * @return The result
     */
    private static int compareNumber(@Nonnull Number value, @Nonnull BigDecimal bigDecimal) {
        int result;
        if (value instanceof BigDecimal) {
            result = ((BigDecimal) value).compareTo(bigDecimal);
        } else if (value instanceof BigInteger) {
            result = new BigDecimal((BigInteger) value).compareTo(bigDecimal);
        } else {
            result = BigDecimal.valueOf(value.doubleValue()).compareTo(bigDecimal);
        }
        return result;
    }

    /**
     * Key for caching validators.
     * @param <A> The annotation type
     * @param <T> The target type.
     */
    protected final class ValidatorKey<A extends Annotation, T> {
        private final Class<A> constraintType;
        private final Class<T> targetType;

        /**
         * The key to lookup the validator.
         * @param constraintType THe constraint type
         * @param targetType The target type
         */
        public ValidatorKey(@Nonnull Class<A> constraintType, @Nonnull Class<T> targetType) {
            this.constraintType = constraintType;
            this.targetType = targetType;
        }

        /**
         * @return The constraint type
         */
        public Class<A> getConstraintType() {
            return constraintType;
        }

        /**
         * @return The target type
         */
        public Class<T> getTargetType() {
            return targetType;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ValidatorKey<?, ?> key = (ValidatorKey<?, ?>) o;
            return constraintType.equals(key.constraintType) &&
                    targetType.equals(key.targetType);
        }

        @Override
        public int hashCode() {
            return Objects.hash(constraintType, targetType);
        }
    }

}
