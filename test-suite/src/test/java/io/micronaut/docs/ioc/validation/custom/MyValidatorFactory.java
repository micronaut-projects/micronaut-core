package io.micronaut.docs.ioc.validation.custom;

// tag::imports[]
import io.micronaut.context.annotation.Factory;
import io.micronaut.validation.validator.constraints.ConstraintValidator;
import javax.inject.Singleton;
// end::imports[]

// tag::class[]
@Factory
public class MyValidatorFactory {

    @Singleton
    ConstraintValidator<DurationPattern, CharSequence> durationPatternValidator() {
        return (value, annotationMetadata, context) ->
                value == null || value.toString().matches("^PT?[\\d]+[SMHD]{1}$");
    }
}
// end::class[]
