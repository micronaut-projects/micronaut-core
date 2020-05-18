package io.micronaut.validation.validator.pojo

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.annotation.Introspected
import io.micronaut.validation.validator.Validator
import io.micronaut.validation.validator.constraints.ConstraintValidator
import io.micronaut.validation.validator.constraints.ConstraintValidatorContext
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.inject.Singleton
import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull
import java.util.regex.Pattern

class ContextValidationSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run(
        ['spec.name': 'contextValidationSpec'],
        Environment.TEST
    )

    @Shared
    Validator validator = applicationContext.getBean(Validator)

    void "test whether ConstraintValidator has access to validated object"() {
        given:
        FavoriteWebs2 favoriteWebs = new FavoriteWebs2(webs: ['aaaa'])

        expect:
        !favoriteWebs.touched
        def constraintViolations = validator.validate(favoriteWebs)
        constraintViolations.size() == 0
        favoriteWebs.touched
    }
}


@Introspected
class FavoriteWebs2 {

    boolean touched = false

    @ValidURLs
    List<String> webs
    
}

@Factory
@Requires(property = "spec.name", value = "contextValidationSpec")
class ValidURLsValidatorFactory2 {

    @Singleton
    ConstraintValidator<ValidURLs, List<String>> validURLValidator() {
        return { value, annotationMetadata, ConstraintValidatorContext context ->
            if (context.rootBean instanceof FavoriteWebs2) {
                (context.rootBean as FavoriteWebs2).touched = true
                return true
            }
            return false
        }
    }
}
