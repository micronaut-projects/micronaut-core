package io.micronaut.http.server.exceptions.response;

import io.micronaut.context.MessageSource;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.i18n.ResourceBundleMessageSource;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.annotation.NonNull;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.client.exceptions.HttpClientResponseException;
import io.micronaut.http.server.exceptions.ErrorResponseProcessorExceptionHandler;
import io.micronaut.http.server.exceptions.ExceptionHandler;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.annotation.security.PermitAll;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.NotBlank;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spock.lang.Specification;

import java.util.Optional;

import static org.junit.Assert.assertFalse;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

@Property(name = "spec.name", value = "DefaultHtmlBodyErrorResponseProviderTest")
@MicronautTest
class DefaultHtmlErrorResponseBodyProviderTest extends Specification {
    private static final Logger LOG = LoggerFactory.getLogger(DefaultHtmlErrorResponseBodyProviderTest.class);

    @Inject
    HtmlErrorResponseBodyProvider htmlProvider;

    @Client("/")
    @Inject
    HttpClient httpClient;

    @Test
    void ifRequestAcceptsBothJsonAnHtmlJsonIsUsed() {
        BlockingHttpClient client = httpClient.toBlocking();
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () ->
            client.exchange(HttpRequest.POST("/book/save", new Book("Building Microservices", "", 5000))
                .accept(MediaType.TEXT_HTML, MediaType.APPLICATION_JSON)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getResponse().getContentType().isPresent());
        assertEquals(MediaType.APPLICATION_JSON, ex.getResponse().getContentType().get().toString());
        Optional<String> jsonOptional = ex.getResponse().getBody(String.class);
        assertTrue(jsonOptional.isPresent());
        String json = jsonOptional.get();
        assertFalse(json.contains("<!doctype html>"));
    }

    @Test
    void validationErrorsShowInHtmlErrorPages() {
        BlockingHttpClient client = httpClient.toBlocking();
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class, () ->
            client.exchange(HttpRequest.POST("/book/save", new Book("Building Microservices", "", 5000))
                .accept(MediaType.TEXT_HTML)));
        assertEquals(HttpStatus.BAD_REQUEST, ex.getStatus());
        assertTrue(ex.getResponse().getContentType().isPresent());
        assertEquals(MediaType.TEXT_HTML, ex.getResponse().getContentType().get().toString());
        Optional<String> htmlOptional = ex.getResponse().getBody(String.class);
        assertTrue(htmlOptional.isPresent());
        String html = htmlOptional.get();
        assertExpectedSubstringInHtml("<!doctype html>", html);
        assertExpectedSubstringInHtml("book.author: must not be blank", html);
        assertExpectedSubstringInHtml("book.pages: must be less than or equal to 4032", html);


        ex = assertThrows(HttpClientResponseException.class, () -> client.exchange(HttpRequest.GET("/paginanoencontrada").accept(MediaType.TEXT_HTML)));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        htmlOptional = ex.getResponse().getBody(String.class);
        assertTrue(htmlOptional.isPresent());
        html = htmlOptional.get();
        assertExpectedSubstringInHtml("<!doctype html>", html);
        assertExpectedSubstringInHtml("Not Found", html);
        assertExpectedSubstringInHtml("The page you were looking for doesn’t exist", html);
        assertExpectedSubstringInHtml("You may have mistyped the address or the page may have moved", html);


        ex = assertThrows(HttpClientResponseException.class, () -> client.exchange(HttpRequest.GET("/paginanoencontrada").header(HttpHeaders.ACCEPT_LANGUAGE, "es").accept(MediaType.TEXT_HTML)));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
        htmlOptional = ex.getResponse().getBody(String.class);
        assertTrue(htmlOptional.isPresent());
        html = htmlOptional.get();
        assertExpectedSubstringInHtml("<!doctype html>", html);
        assertExpectedSubstringInHtml("No encontrado", html);
        assertExpectedSubstringInHtml("La página que buscabas no existe", html);
        assertExpectedSubstringInHtml("Es posible que haya escrito mal la dirección o que la página se haya movido.", html);
    }

    @Test
    void userNotFoundExceptionHandlerReturnsNotFound(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        HttpClientResponseException ex = assertThrows(HttpClientResponseException.class,
            () -> client.exchange(HttpRequest.GET("/throws").accept(MediaType.TEXT_HTML)));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatus());
    }

    private void assertExpectedSubstringInHtml(String expected, String html) {
        if (!html.contains(expected)) {
            LOG.trace("{}", html);
        }
        assertTrue(html.contains(expected));
    }

    @Requires(property = "spec.name", value = "DefaultHtmlBodyErrorResponseProviderTest")
    @Controller("/book")
    static class FooController {

        @Produces(MediaType.TEXT_HTML)
        @Post("/save")
        @Status(HttpStatus.CREATED)
        void save(@Body @Valid Book book) {
            throw new UnsupportedOperationException();
        }
    }

    @Requires(property = "spec.name", value = "DefaultHtmlBodyErrorResponseProviderTest")
    @Factory
    static class MessageSourceFactory {
        @Singleton
        MessageSource createMessageSource() {
            return new ResourceBundleMessageSource("i18n.messages");
        }
    }

    @Introspected
    record Book(@NotBlank String title, @NotBlank String author, @Max(4032) int pages) {
    }

    static class UserNotFoundException extends RuntimeException {
    }

    @Requires(property = "spec.name", value = "DefaultHtmlBodyErrorResponseProviderTest")
    @Produces
    @Singleton
    @Requires(classes = {UserNotFoundException.class, ExceptionHandler.class})
    static class UserNotFoundExceptionHandler extends ErrorResponseProcessorExceptionHandler<UserNotFoundException> {
        protected UserNotFoundExceptionHandler(ErrorResponseProcessor<?> responseProcessor) {
            super(responseProcessor);
        }

        @Override
        protected @NonNull MutableHttpResponse<?> createResponse(UserNotFoundException exception) {
            return HttpResponse.notFound();
        }
    }

    @Requires(property = "spec.name", value = "DefaultHtmlBodyErrorResponseProviderTest")
    @Controller("/throws")
    static class ThrowingController {
        @Produces(MediaType.TEXT_HTML)
        @PermitAll
        @Get
        @Status(HttpStatus.OK)
        void index() {
            throw new UserNotFoundException();
        }
    }
}
