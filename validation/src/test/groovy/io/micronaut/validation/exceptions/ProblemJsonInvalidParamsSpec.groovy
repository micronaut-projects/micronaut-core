package io.micronaut.validation.exceptions

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.NonNull
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.MediaType
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import io.micronaut.http.annotation.Status
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.problem.ProblemInvalidParams
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.See
import spock.lang.Shared
import spock.lang.Specification

import javax.validation.Valid
import javax.validation.constraints.NotBlank
import javax.validation.constraints.NotNull
import javax.validation.constraints.Pattern
import javax.validation.constraints.Positive

@See("https://tools.ietf.org/html/rfc7807#section-3.1")
class ProblemJsonInvalidParamsSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer, [
            'spec.name': 'InvalidParamsSpec',
            'micronaut.server.error-response': 'problem',
    ])

    @Shared
    HttpClient httpClient = embeddedServer.applicationContext.createBean(HttpClient, embeddedServer.URL)

    @Shared
    BlockingHttpClient client = httpClient.toBlocking()

    void "it is easy to return a problem+json response"() {
        when:
        Argument<String> okType = Argument.of(String)
        Argument<ProblemInvalidParams> errorType = Argument.of(ProblemInvalidParams)
        client.exchange(HttpRequest.POST('/colorfavourites', [age: -1, color: 'yellow']), okType, errorType)

        then:
        HttpClientResponseException e = thrown()
        e.status == HttpStatus.BAD_REQUEST
        e.getResponse().contentType.isPresent()
        e.getResponse().contentType.get() == MediaType.PROBLEM_JSON_TYPE

        when:
        Optional<ProblemInvalidParams> bodyOptional = e.getResponse().getBody(errorType)

        then:
        bodyOptional.isPresent()

        when:
        ProblemInvalidParams body = bodyOptional.get()

        then:
        body
        body.title == "Your request parameters didn't validate."
        body.invalidParams
        body.invalidParams.any { it.name == 'statistic.age' && it.reason == 'must be greater than 0' }
        body.invalidParams.any { it.name == 'statistic.color' && it.reason == 'must match "green|red|blue"'}
    }

    @Requires(property = 'spec.name', value = 'InvalidParamsSpec')
    @Controller("/colorfavourites")
    static class InvalidParamsController {

        @Post
        @Status(HttpStatus.CREATED)
        void save(@NonNull @NotNull @Valid Statistic statistic) {

        }
    }

    @Introspected
    static class Statistic {
        @Positive
        @NotNull
        @NonNull
        private Integer age

        @NotBlank
        @Pattern(regexp = 'green|red|blue')
        @NonNull
        protected String color

        @NonNull
        Integer getAge() {
            return age
        }

        void setAge(@NonNull Integer age) {
            this.age = age
        }

        @NonNull
        String getColor() {
            return color
        }

        void setColor(@NonNull String color) {
            this.color = color
        }
    }
}
