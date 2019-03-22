package io.micronaut.validation.validator.constraints;

import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.core.util.CollectionUtils;
import io.micronaut.core.util.StringUtils;

import javax.annotation.Nonnull;
import javax.inject.Named;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import javax.validation.constraints.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.Collection;
import java.util.Map;

/**
 * A factory bean that contains implementation for many of the default validations.
 * This approach is preferred as it generates less classes and smaller byte code then defining a
 * validator class for each case.
 *
 * @author graemerocher
 * @since 1.2
 */
@Factory
public class DefaultConstraintValidators {

    /**
     * The {@link AssertFalse} validator.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("assertFalseValidator")
    public ConstraintValidator<AssertFalse, Boolean> assertFalseValidator() {
        return (value, annotationMetadata, context) -> value == null || !value;
    }

    /**
     * The {@link AssertTrue} validator.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("assertTrueValidator")
    public ConstraintValidator<AssertTrue, Boolean> assertTrueValidator() {
        return (value, annotationMetadata, context) -> value == null || value;
    }

    /**
     * The {@link DecimalMax} validator for char sequences.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("decimalMaxValidatorCharSequence")
    public DecimalMaxValidator<CharSequence> decimalMaxValidatorCharSequence() {
        return (value, bigDecimal) -> new BigDecimal(value.toString()).compareTo(bigDecimal);
    }

    /**
     * The {@link DecimalMax} validator for number.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("decimalMaxValidatorNumber")
    public DecimalMaxValidator<Number> decimalMaxValidatorNumber() {
        return DefaultConstraintValidators::compareNumber;
    }


    /**
     * The {@link DecimalMin} validator for char sequences.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("decimalMinValidatorCharSequence")
    public DecimalMinValidator<CharSequence> decimalMinValidatorCharSequence() {
        return (value, bigDecimal) -> new BigDecimal(value.toString()).compareTo(bigDecimal);
    }

    /**
     * The {@link DecimalMin} validator for number.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("decimalMinValidatorNumber")
    public DecimalMinValidator<Number> decimalMinValidatorNumber() {
        return DefaultConstraintValidators::compareNumber;
    }

    /**
     * The {@link Digits} validator for number.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("digitsValidatorNumber")
    public DigitsValidator<Number> digitsValidatorNumber() {
        return value -> {
            if (value instanceof BigDecimal) {
                return (BigDecimal) value;
            }
            return new BigDecimal(value.toString());
        };
    }

    /**
     * The {@link Digits} validator for char sequence.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("digitsValidatorCharSequence")
    public DigitsValidator<CharSequence> digitsValidatorCharSequence() {
        return value -> new BigDecimal(value.toString());
    }

    /**
     * The {@link Max} validator for numbers.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("maxNumberValidator")
    public ConstraintValidator<Max, Number> maxNumberValidator() {
        return (value, annotationMetadata, context) -> {
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
            return value.longValue() < max;
        };
    }

    /**
     * The {@link Min} validator for numbers.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("minNumberValidator")
    public ConstraintValidator<Min, Number> minNumberValidator() {
        return (value, annotationMetadata, context) -> {
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
    }

    /**
     * The {@link Negative} validator for numbers.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("negativeNumberValidator")
    public ConstraintValidator<Negative, Number> negativeNumberValidator() {
        return (value, annotationMetadata, context) -> {
            // null is allowed according to spec
            return value == null || value.intValue() < 0;
        };
    }

    /**
     * The {@link NegativeOrZero} validator for numbers.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("negativeOrZeroNumberValidator")
    public ConstraintValidator<NegativeOrZero, Number> negativeOrZeroNumberValidator() {
        return (value, annotationMetadata, context) -> {
            // null is allowed according to spec
            return value == null || value.intValue() <= 0;
        };
    }

    /**
     * The {@link Positive} validator for numbers.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("positiveNumberValidator")
    public ConstraintValidator<Positive, Number> positiveNumberValidator() {
        return (value, annotationMetadata, context) -> {
            // null is allowed according to spec
            return value == null || value.intValue() > 0;
        };
    }

    /**
     * The {@link PositiveOrZero} validator for numbers.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("positiveOrZeroNumberValidator")
    public ConstraintValidator<PositiveOrZero, Number> positiveOrZeroNumberValidator() {
        return (value, annotationMetadata, context) -> {
            // null is allowed according to spec
            return value == null || value.intValue() >= 0;
        };
    }

    /**
     * The {@link NotBlank} validator for char sequences.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("notBlankValidator")
    public ConstraintValidator<NotBlank, CharSequence> notBlankValidator() {
        return (value, annotationMetadata, context) ->
                StringUtils.isNotEmpty(value) && value.toString().trim().length() > 0;
    }

    /**
     * The {@link NotNull} validator.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("notNullValidator")
    public ConstraintValidator<NotNull, Object> notNullValidator() {
        return (value, annotationMetadata, context) -> value != null;
    }

    /**
     * The {@link Null} validator.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("nullValidator")
    public ConstraintValidator<Null, Object> nullValidator() {
        return (value, annotationMetadata, context) -> value == null;
    }

    /**
     * The {@link NotEmpty} validator for byte[].
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("notEmptyByteArrayValidator")
    public ConstraintValidator<NotEmpty, byte[]> notEmptyByteArrayValidator() {
        return (value, annotationMetadata, context) -> value != null && value.length > 0;
    }

    /**
     * The {@link NotEmpty} validator for char[].
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("notEmptyCharArrayValidator")
    public ConstraintValidator<NotEmpty, char[]> notEmptyCharArrayValidator() {
        return (value, annotationMetadata, context) -> value != null && value.length > 0;
    }

    /**
     * The {@link NotEmpty} validator for boolean[].
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("notEmptyBooleanArrayValidator")
    public ConstraintValidator<NotEmpty, boolean[]> notEmptyBooleanArrayValidator() {
        return (value, annotationMetadata, context) -> value != null && value.length > 0;
    }

    /**
     * The {@link NotEmpty} validator for double[].
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("notEmptyDoubleArrayValidator")
    public ConstraintValidator<NotEmpty, double[]> notEmptyDoubleArrayValidator() {
        return (value, annotationMetadata, context) -> value != null && value.length > 0;
    }

    /**
     * The {@link NotEmpty} validator for float[].
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("notEmptyFloatArrayValidator")
    public ConstraintValidator<NotEmpty, float[]> notEmptyFloatArrayValidator() {
        return (value, annotationMetadata, context) -> value != null && value.length > 0;
    }

    /**
     * The {@link NotEmpty} validator for int[].
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("notEmptyIntArrayValidator")
    public ConstraintValidator<NotEmpty, int[]> notEmptyIntArrayValidator() {
        return (value, annotationMetadata, context) -> value != null && value.length > 0;
    }

    /**
     * The {@link NotEmpty} validator for long[].
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("notEmptyLongArrayValidator")
    public ConstraintValidator<NotEmpty, long[]> notEmptyLongArrayValidator() {
        return (value, annotationMetadata, context) -> value != null && value.length > 0;
    }

    /**
     * The {@link NotEmpty} validator for Object[].
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("notEmptyObjectArrayValidator")
    public ConstraintValidator<NotEmpty, Object[]> notEmptyObjectArrayValidator() {
        return (value, annotationMetadata, context) -> value != null && value.length > 0;
    }

    /**
     * The {@link NotEmpty} validator for short[].
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("notEmptyShortArrayValidator")
    public ConstraintValidator<NotEmpty, short[]> notEmptyShortArrayValidator() {
        return (value, annotationMetadata, context) -> value != null && value.length > 0;
    }

    /**
     * The {@link NotEmpty} validator for char sequence.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("notEmptyCharSequenceValidator")
    public ConstraintValidator<NotEmpty, CharSequence> notEmptyCharSequenceValidator() {
        return (value, annotationMetadata, context) -> StringUtils.isNotEmpty(value);
    }

    /**
     * The {@link NotEmpty} validator for collection.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("notEmptyCollectionValidator")
    public ConstraintValidator<NotEmpty, Collection> notEmptyCollectionValidator() {
        return (value, annotationMetadata, context) -> CollectionUtils.isNotEmpty(value);
    }

    /**
     * The {@link NotEmpty} validator for map.
     *
     * @return The validator
     */
    @Singleton
    @Bean
    @Named("notEmptyMapValidator")
    public ConstraintValidator<NotEmpty, Map> notEmptyMapValidator() {
        return (value, annotationMetadata, context) -> CollectionUtils.isNotEmpty(value);
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
}
