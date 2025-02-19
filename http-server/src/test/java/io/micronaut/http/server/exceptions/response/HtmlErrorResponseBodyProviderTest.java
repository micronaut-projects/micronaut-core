package io.micronaut.http.server.exceptions.response;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.core.convert.value.MutableConvertibleValues;
import io.micronaut.http.*;
import io.micronaut.http.simple.SimpleHttpRequest;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import jakarta.inject.Inject;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import spock.lang.Specification;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

@MicronautTest(startApplication = false)
class HtmlErrorResponseBodyProviderTest extends Specification {
    private static final Logger LOG = LoggerFactory.getLogger(HtmlErrorResponseBodyProviderTest.class);

    @Inject
    HtmlErrorResponseBodyProvider htmlProvider;

    @ParameterizedTest
    @EnumSource(HttpStatus.class)
    void htmlPageforStatus(HttpStatus status) {
        if (status.getCode() >= 400) {

            ErrorContext errorContext = new ErrorContext() {
                @Override
                public @NonNull HttpRequest<?> getRequest() {
                    return new SimpleHttpRequest(HttpMethod.GET, "/foobar", null);
                }

                @Override
                public @NonNull Optional<Throwable> getRootCause() {
                    return Optional.empty();
                }

                @Override
                public @NonNull List<Error> getErrors() {
                    return Collections.emptyList();
                }
            };
            HttpResponse<?> response = new HttpResponse<Object>() {
                @Override
                public HttpStatus getStatus() {
                    return status;
                }

                @Override
                public int code() {
                    return status.getCode();
                }

                @Override
                public String reason() {
                    return status.getReason();
                }

                @Override
                public HttpHeaders getHeaders() {
                    return null;
                }

                @Override
                public MutableConvertibleValues<Object> getAttributes() {
                    return null;
                }

                @Override
                public Optional<Object> getBody() {
                    return Optional.empty();
                }
            };
            String html = htmlProvider.body(errorContext, response);

            assertNotNull(html);
            assertExpectedSubstringInHtml(status.getReason(), html);
            assertExpectedSubstringInHtml("<!doctype html>", html);
            if (status.getCode() == 404) {
                assertExpectedSubstringInHtml("The page you were looking for doesn’t exist", html);
                assertExpectedSubstringInHtml("You may have mistyped the address or the page may have moved", html);
            } else if (status.getCode() == 413) {
                assertExpectedSubstringInHtml("The file or data you are trying to upload exceeds the allowed size", html);
                assertExpectedSubstringInHtml("Please try again with a smaller file", html);
            }
        }
    }

    private void assertExpectedSubstringInHtml(String expected, String html) {
        if (!html.contains(expected)) {
            LOG.trace("{}", html);
        }
        assertTrue(html.contains(expected));
    }
}
