package io.micronaut.docs.ioc.validation.custom;

// tag::imports[]
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.validation.validator.constraints.*;
import javax.annotation.*;
// end::imports[]

// tag::class[]
public class DurationPatternValidator implements ConstraintValidator<DurationPattern, CharSequence> {
    @Override
    public boolean isValid(
            @Nullable CharSequence value,
            @Nonnull AnnotationValue<DurationPattern> annotationMetadata,
            @Nonnull ConstraintValidatorContext context) {
        return value == null || value.toString().matches("^PT?[\\d]+[SMHD]{1}$");
    }
}
// end::class[]
