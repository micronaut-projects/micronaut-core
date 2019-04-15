package io.micronaut.docs.ioc.validation.custom

// tag::imports[]
import io.micronaut.context.annotation.Factory
import io.micronaut.core.annotation.AnnotationValue
import io.micronaut.validation.validator.constraints.*
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Factory
class MyValidatorFactory {

    @Singleton
    ConstraintValidator<DurationPattern, CharSequence> durationPatternValidator() {
        return { CharSequence value,
                 AnnotationValue<DurationPattern> annotation,
                 ConstraintValidatorContext context ->
            return value == null || value.toString() ==~ /^PT?[\d]+[SMHD]{1}$/
        } as ConstraintValidator<DurationPattern, CharSequence>
    }
}
// end::class[]

