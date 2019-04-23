package io.micronaut.docs.ioc.validation.custom

// tag::imports[]
import io.micronaut.context.annotation.Factory
import io.micronaut.validation.validator.constraints.ConstraintValidator
import javax.inject.Singleton
// end::imports[]

// tag::class[]
@Factory
class MyValidatorFactory {

    @Singleton
    fun durationPatternValidator() : ConstraintValidator<DurationPattern, CharSequence> {
        return ConstraintValidator { value, annotation, context ->
            value == null || value.toString().matches("^PT?[\\d]+[SMHD]{1}$".toRegex())
        }
    }
}
// end::class[]
