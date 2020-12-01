package io.micronaut.http.client.aop


import io.micronaut.context.ApplicationContext
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.convert.ArgumentConversionContext
import io.micronaut.core.type.Argument
import io.micronaut.http.HttpRequest
import io.micronaut.http.MutableHttpResponse
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Filter
import io.micronaut.http.annotation.Get
import io.micronaut.http.annotation.Header
import io.micronaut.http.annotation.PathVariable
import io.micronaut.http.annotation.QueryValue
import io.micronaut.http.annotation.RequestBean
import io.micronaut.http.bind.binders.TypedRequestArgumentBinder
import io.micronaut.http.client.annotation.Client
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.http.filter.HttpServerFilter
import io.micronaut.http.filter.OncePerRequestHttpServerFilter
import io.micronaut.http.filter.ServerFilterChain
import io.micronaut.runtime.server.EmbeddedServer
import org.reactivestreams.Publisher
import spock.lang.AutoCleanup
import spock.lang.Shared
import spock.lang.Specification

import javax.annotation.Nullable
import javax.inject.Singleton
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
            client.getInjectedHttpRequest() == "/request/bean/httpRequest"
    }

    void "test Typed Value is injected in to Bean"() {
        expect:
            client.getInjectedTypedValue() == "Type Test Value"
    }

    void "test Immutable injections works"() {
        expect:
            client.getImmutableBean("I am immutable! Muahahah") == "I am immutable! Muahahah"
    }

    void "test Immutable Bean gets injected HttpRequest"() {
        expect:
            client.getImmutableBeanInjectedHttpRequest() == "/request/bean/immutable/request/path"
    }

    void "test Immutable validated parameter"() {
        when:
            client.getImmutableBeanValidatedValue("third")
        then:
            def ex = thrown(HttpClientResponseException)
            ex.message.contains("Field must have value first or second.")
    }

    void "test Immutable Bean gets injected Typed Value"() {
        expect:
            client.getImmutableInjectedTypedValue() == "Type Test Value"
    }

    void "test Extending Bean has super values"() {
        expect:
            client.getExtendingBeanValues("I am not super!", "I am super!") == "Extending: 'I am not super!', Super: 'I am super!'"
    }

    /**
     * Example of such case: Authentication type, where value must be resolved after filters
     */
    void "test Filter values are respected"() {
        expect:
            client.getFilterValue() == "Filter Test Value"
    }

    void "test unsatisfied non-nullable value returns bad request"() {
        when:
            client.getUnsatisfiedValue()
        then:
            def ex = thrown(HttpClientResponseException)
            ex.message.contains("Required argument [String value] not specified")
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
        String getInjectedHttpRequest(@RequestBean Bean bean) {
            return bean.request.path
        }

        @Get("/typed/value")
        String getInjectedTypedValue(@RequestBean Bean bean) {
            return bean.typeValue.value
        }

        @Get("/immutable")
        String immutableBeanRequest(@RequestBean ImmutableBean bean) {
            return bean.queryValue
        }

        @Get("/immutable/request/path")
        String immutableBeanInjectedHttpRequest(@RequestBean ImmutableBean bean) {
            return bean.request.path
        }

        @Get("/immutable/validated/field{?validatedValue}")
        String getImmutableBeanValidatedParameter(@Valid @RequestBean ImmutableBean bean) {
            return bean.validatedValue
        }

        @Get("/immutable/typed/value")
        String getImmutableInjectedTypedValue(@RequestBean ImmutableBean bean) {
            return bean.typeValue.value
        }

        @Get("/extended/values")
        String getExtendingBeanValues(@RequestBean ExtendingBean bean) {
            return "Extending: '$bean.extendingValue', Super: '$bean.superValue'"
        }

        @Get("/filter/value")
        String getFilterValue(@RequestBean ImmutableBean bean) {
            return bean.filterValue.value
        }

        @Get("/unsatisfied/value")
        String getUnsatisfiedValue(@RequestBean UnsatisfiedBean bean) {
            return bean.value
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
        String getInjectedHttpRequest()

        @Get("/typed/value")
        String getInjectedTypedValue()

        @Get("/immutable{?queryValue}")
        String getImmutableBean(String queryValue)

        @Get("/immutable/request/path")
        String getImmutableBeanInjectedHttpRequest()

        @Get("/immutable/validated/field{?validatedValue}")
        String getImmutableBeanValidatedValue(String validatedValue)

        @Get("/immutable/typed/value")
        String getImmutableInjectedTypedValue()

        @Get("/extended/values{?extendingValue,superValue}")
        String getExtendingBeanValues(String extendingValue, String superValue)

        @Get("/filter/value")
        String getFilterValue()

        @Get("/unsatisfied/value")
        String getUnsatisfiedValue()

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

        @Nullable
        TestTypeValue typeValue

    }

    @Introspected
    static class ImmutableBean {

        final HttpRequest<?> request

        @Nullable
        final TestTypeValue typeValue

        @Nullable
        final TestFilterValue filterValue

        @Nullable
        @QueryValue
        @Pattern(regexp = "first|second", message = "Field must have value 'first' or 'second'.")
        final String queryValue

        @Nullable
        @QueryValue
        @Pattern(regexp = "first|second", message = "Field must have value 'first' or 'second'.")
        final String validatedValue

        ImmutableBean(HttpRequest request, TestTypeValue typeValue, TestFilterValue filterValue, String queryValue, String validatedValue) {
            this.request = request
            this.typeValue = typeValue
            this.filterValue = filterValue
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

    @Introspected
    static class UnsatisfiedBean {

        @QueryValue
        String value

    }

    static class TestTypeValue {
        String value
    }

    @Singleton
    static class TestTypeValueBinder implements TypedRequestArgumentBinder<TestTypeValue> {

        @Override
        Argument<TestTypeValue> argumentType() {
            return Argument.of(TestTypeValue)
        }

        @Override
        BindingResult<TestTypeValue> bind(ArgumentConversionContext<TestTypeValue> context, HttpRequest<?> source) {
            return new BindingResult<TestTypeValue>() {
                @Override
                Optional<TestTypeValue> getValue() {
                    Optional.of(new TestTypeValue(value: "Type Test Value"))
                }
            }
        }
    }

    static class TestFilterValue {
        String value
    }

    @Singleton
    static class TestFilterValueBinder implements TypedRequestArgumentBinder<TestFilterValue> {

        @Override
        Argument<TestFilterValue> argumentType() {
            return Argument.of(TestFilterValue)
        }

        @Override
        BindingResult<TestFilterValue> bind(ArgumentConversionContext<TestFilterValue> context, HttpRequest<?> source) {
            return new BindingResult<TestFilterValue>() {
                @Override
                Optional<TestFilterValue> getValue() {
                    Optional.of(new TestFilterValue(value: source.getAttribute("filter.value").orElse(null)))
                }
            }
        }
    }

    @Filter("/request/bean/**")
    static class TestFilter extends OncePerRequestHttpServerFilter {
        @Override
        protected Publisher<MutableHttpResponse<?>> doFilterOnce(HttpRequest<?> request, ServerFilterChain chain) {
            request.getAttributes().put("filter.value", "Filter Test Value")
            return chain.proceed(request)
        }
    }

}
