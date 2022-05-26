package io.micronaut.validation.validator.pojo

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.context.env.Environment
import io.micronaut.core.annotation.Introspected
import io.micronaut.validation.validator.Validator
import io.micronaut.validation.validator.constraints.ConstraintValidator
import jakarta.inject.Singleton
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.validation.constraints.NotEmpty
import javax.validation.constraints.NotNull
import java.util.regex.Pattern

class FavoriteWebsSpec extends Specification {

    @Shared
    @AutoCleanup
    ApplicationContext applicationContext = ApplicationContext.run(
        ['spec.name': 'customValidatorField'],
        Environment.TEST
    )

    @Shared
    Validator validator = applicationContext.getBean(Validator)

    void "test custom constraint validator in a Field"() {
        given:
        FavoriteWebs favoriteWebs = new FavoriteWebs(webs: ['aaaa'])

        when:
        def constraintViolations = validator.validate(favoriteWebs)

        then:
        constraintViolations.size() == 1
        constraintViolations.first().message == "invalid url"
    }
}


@Introspected
class FavoriteWebs {

    @NotNull
    @NotEmpty
    @ValidURLs
    List<String> webs
}

@Factory
@Requires(property = "spec.name", value = "customValidatorField")
class ValidURLsValidatorFactory {

    private static final Pattern URL = Pattern.compile("(http:\\/\\/|https:\\/\\/)?(www.)?([a-zA-Z0-9]+).[a-zA-Z0-9]*.[a-z]{3}.?([a-z]+)?");

    @Singleton
    ConstraintValidator<ValidURLs, List<String>> validURLValidator() {
        return { value, annotationMetadata, context ->
            value != null && value.stream().allMatch { u -> URL.matcher(u).matches() }
        }
    }
}
