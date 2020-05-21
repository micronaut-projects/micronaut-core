package io.micronaut.http.client.aop


import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.HttpRequest
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.RequestBean

import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.annotation.Nullable
import javax.validation.Valid
import javax.validation.constraints.Pattern

class RequestBeanSpec extends Specification {

    @Shared
    @AutoCleanup
    EmbeddedServer embeddedServer = ApplicationContext.run(EmbeddedServer)

    @Shared
    RequestBeanClient client = embeddedServer.getApplicationContext().getBean(RequestBeanClient)

    void "test @QueryValue is injected to Bean"() {
        expect:
            client.getQueryValue("Riverside") == "Riverside"
    }

    void "test @QueryValue have empty Optional Value"() {
        expect:
            !client.getOptionalValue()
    }

    void "test @QueryValue has value in Optional"() {
        expect:
            client.getOptionalValue("hello value")
    }

    void "test validated value returns bad request when invalid"() {
        when:
            client.getValidatedValue("third")
        then:
            def ex = thrown(HttpClientResponseException)
            ex.message.contains("Field must have value first or second.")
    }

    void "test validated value returns ok when valid"() {
        expect:
            client.getValidatedValue("second") == "second"
    }

    void "test @PathVariable is injected in to Bean"() {
        expect:
            client.getPathVariable("v1beta") == "v1beta"
    }

    void "test @Header is injected in to Bean"() {
        expect:
            client.getHeader("127.0.0.1") == "127.0.0.1"
    }

    void "test HttpRequest is injected in to Bean"() {
        expect:
            client.getPath() == "/request/bean/httpRequest"
    }

    void "test Immutable injections works"() {
        expect:
            client.getImmutableBean("I am immutable! Muahahah") == "I am immutable! Muahahah"
    }

    void "test Immutable get request path"() {
        expect:
            client.getImmutableBeanGetRequestPath() == "/request/bean/immutable/request/path"
    }

    void "test Immutable validated parameter"() {
        when:
            client.getImmutableBeanValidatedValue("third")
        then:
            def ex = thrown(HttpClientResponseException)
            ex.message.contains("Field must have value first or second.")
    }

    void "test Extending Bean has super values"() {
        expect:
            client.getExtendingBeanValues("I am not super!", "I am super!") == "Extending: 'I am not super!', Super: 'I am super!'"
    }

    @Controller('/request/bean')
    static class RequestBeanController {

        @Get("/queryValue{?queryValue}")
        String getQueryValue(@RequestBean Bean bean) {
            return bean.queryValue
        }

        @Get("/optionalQueryValue{?optionalValue}")
        Boolean getOptionalValue(@RequestBean Bean bean) {
            return bean.optionalValue.isPresent()
        }

        @Get("/{pathVariable}")
        String getPathVariable(@RequestBean Bean bean) {
            return bean.pathVariable
        }

        @Get("/validatedValue{?validatedValue}")
        String getValidatedValue(@Valid @RequestBean Bean bean) {
            return bean.validatedValue
        }

        @Get("/header")
        String getHeader(@RequestBean Bean bean) {
            return bean.forwardedFor
        }

        @Get("/httpRequest")
        String getPath(@RequestBean Bean bean) {
            return bean.request.path
        }

        @Get("/immutable")
        String immutableBeanRequest(@RequestBean ImmutableBean bean) {
            return bean.queryValue
        }

        @Get("/immutable/request/path")
        String immutableBeanGetRequestPath(@RequestBean ImmutableBean bean) {
            return bean.request.path
        }

        @Get("/immutable/validated/field{?validatedValue}")
        String getImmutableBeanValidatedParameter(@Valid @RequestBean ImmutableBean bean) {
            return bean.validatedValue
        }

        @Get("/extended/values")
        String getExtendingBeanValues(@RequestBean ExtendingBean bean) {
            return "Extending: '$bean.extendingValue', Super: '$bean.superValue'"
        }

    }

    @Client('/request/bean')
    static interface RequestBeanClient {

        @Get("/queryValue{?queryValue}")
        String getQueryValue(String queryValue)

        @Get("/optionalQueryValue{?optionalValue}")
        Boolean getOptionalValue(@Nullable String optionalValue)

        @Get("/validatedValue{?validatedValue}")
        String getValidatedValue(@Nullable String validatedValue)

        @Get("/{pathVariable}")
        String getPathVariable(@PathVariable String pathVariable)

        @Get("/header")
        String getHeader(@Header("X-Forwarded-For") String forwardedFor)

        @Get("/httpRequest")
        String getPath()

        @Get("/immutable{?queryValue}")
        String getImmutableBean(String queryValue)

        @Get("/immutable/request/path")
        String getImmutableBeanGetRequestPath()

        @Get("/immutable/validated/field{?validatedValue}")
        String getImmutableBeanValidatedValue(String validatedValue)

        @Get("/extended/values{?extendingValue,superValue}")
        String getExtendingBeanValues(String extendingValue, String superValue)

    }

    @Introspected
    static class Bean {

        HttpRequest<?> request

        @Nullable
        @QueryValue
        String queryValue

        @QueryValue
        Optional<String> optionalValue

        @Nullable
        @QueryValue
        @Pattern(regexp = "first|second", message = "Field must have value 'first' or 'second'.")
        String validatedValue

        @Nullable
        @PathVariable
        String pathVariable

        @Nullable
        @Header("X-Forwarded-For")
        String forwardedFor

    }

    @Introspected
    static class ImmutableBean {

        final HttpRequest<?> request

        @Nullable
        @QueryValue
        @Pattern(regexp = "first|second", message = "Field must have value 'first' or 'second'.")
        final String queryValue

        @Nullable
        @QueryValue
        @Pattern(regexp = "first|second", message = "Field must have value 'first' or 'second'.")
        final String validatedValue

        ImmutableBean(HttpRequest request, String queryValue, String validatedValue) {
            this.request = request
            this.queryValue = queryValue
            this.validatedValue = validatedValue
        }

    }

    @Introspected
    static class ExtendingBean extends SuperBean {

        @Nullable
        @QueryValue
        String extendingValue

    }

    static class SuperBean {

        @Nullable
        @QueryValue
        String superValue

    }

}
