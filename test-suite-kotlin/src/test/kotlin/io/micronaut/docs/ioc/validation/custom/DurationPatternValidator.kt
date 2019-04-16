package io.micronaut.docs.ioc.validation.custom


// tag::imports[]
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.validation.validator.constraints.*
// end::imports[]

// tag::class[]
class DurationPatternValidator : ConstraintValidator<DurationPattern, CharSequence> {
    override fun isValid(
            value: CharSequence?,
            annotationMetadata: AnnotationValue<DurationPattern>,
            context: ConstraintValidatorContext): Boolean {
        return value == null || value.toString().matches("^PT?[\\d]+[SMHD]{1}$".toRegex())
    }
}
// end::class[]
