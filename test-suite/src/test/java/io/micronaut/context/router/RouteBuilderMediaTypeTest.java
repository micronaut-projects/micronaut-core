package io.micronaut.context.router;

import io.micronaut.context.ApplicationContext;
import io.micronaut.context.BeanContext;
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Property;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.Introspected;
import io.micronaut.core.util.StringUtils;
import io.micronaut.http.*;
import io.micronaut.http.annotation.*;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.http.uri.UriBuilder;
import io.micronaut.inject.BeanDefinition;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.ExecutionHandle;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import io.micronaut.web.router.DefaultRouteBuilder;
import jakarta.inject.Singleton;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.junit.jupiter.api.Test;
import spock.lang.Specification;

import java.net.URI;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.junit.jupiter.api.Assertions.assertNotNull;

@Property(name = "micronaut.http.client.follow-redirects", value = StringUtils.FALSE)
@Property(name = "spec.name", value = "RouteBuilderMediaTypeSpec")
@MicronautTest
class RouteBuilderMediaTypeTest extends Specification {

    @Test
    void createTest(@Client("/") HttpClient httpClient) {
        BlockingHttpClient client = httpClient.toBlocking();
        HttpRequest<?> request = HttpRequest.GET("/contact/create").accept(MediaType.TEXT_HTML);
        HttpResponse<String> responseHtml = assertDoesNotThrow(() -> client.exchange(request, String.class));
        assertTrue(responseHtml.getContentType().isPresent());
        assertEquals(MediaType.TEXT_HTML_TYPE, responseHtml.getContentType().get());
        String html = responseHtml.body();
        assertNotNull(html);
    }

    @Test
    void saveTest(@Client("/") HttpClient httpClient, ContactController contactController) {
        BlockingHttpClient client = httpClient.toBlocking();
        Map<String, String> body = Map.of("firstName", "Sergio", "lastName", "del Amo");
        HttpRequest<?> request = HttpRequest.POST(UriBuilder.of("/contact").path("save").build(), body)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        HttpResponse<?> response = assertDoesNotThrow(() -> client.exchange(request));
        assertEquals(HttpStatus.SEE_OTHER, response.getStatus());
        assertEquals("/foo", response.getHeaders().get(HttpHeaders.LOCATION));
        assertEquals(contactController.contact, new Contact("Sergio", "del Amo"));

        contactController.contact = null;
    }

    @Test
    void saveTestWithoutBodyParameter(@Client("/") HttpClient httpClient, ContactController contactController) {
        BlockingHttpClient client = httpClient.toBlocking();
        Map<String, String> body = Map.of("firstName", "Sergio", "lastName", "del Amo");
        HttpRequest<?> request = HttpRequest.POST(UriBuilder.of("/contact").path("saveWithoutBody").build(), body)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        HttpResponse<?> response = assertDoesNotThrow(() -> client.exchange(request));
        assertEquals(HttpStatus.SEE_OTHER, response.getStatus());
        assertEquals("/foo", response.getHeaders().get(HttpHeaders.LOCATION));
        assertEquals(contactController.contact, new Contact("Sergio", "del Amo"));

        contactController.contact = null;

    }

    @Requires(property = "spec.name", value = "RouteBuilderMediaTypeSpec")
    @Singleton
    static class CreateSaveRouteBuilder extends DefaultRouteBuilder {

        CreateSaveRouteBuilder(ExecutionHandleLocator executionHandleLocator,
                               BeanContext beanContext,
                               List<ContactController> contactControllerList) {
            super(executionHandleLocator);
            for (ContactController controller : contactControllerList) {
                beanContext.getBeanDefinition(ContactController.class);
                BeanDefinition<ContactController> bd = beanContext.getBeanDefinition(ContactController.class);
                bd.findMethod("create", HttpRequest.class).ifPresent(m -> {
                    MethodExecutionHandle<Object, Object> executionHandle = ExecutionHandle.of(controller, (ExecutableMethod) m);
                    buildRoute(HttpMethod.GET, "/contact/create", executionHandle);
                });
                bd.findMethod("save", HttpRequest.class, Contact.class).ifPresent(m -> {
                    MethodExecutionHandle<Object, Object> executionHandle = ExecutionHandle.of(controller, (ExecutableMethod) m);
                    buildRoute(HttpMethod.POST, "/contact/save", Collections.singletonList(MediaType.APPLICATION_FORM_URLENCODED_TYPE), executionHandle);
                });

                bd.findMethod("saveWithoutBody", HttpRequest.class).ifPresent(m -> {
                    MethodExecutionHandle<Object, Object> executionHandle = ExecutionHandle.of(controller, (ExecutableMethod) m);
                    buildRoute(HttpMethod.POST, "/contact/saveWithoutBody", Collections.singletonList(MediaType.APPLICATION_FORM_URLENCODED_TYPE), executionHandle);
                });
            }
        }
    }


    @Requires(property = "spec.name", value = "RouteBuilderMediaTypeSpec")
    @Singleton
    static class ContactController {
        Contact contact;

        @Produces(MediaType.TEXT_HTML)
        @Executable
        String create(HttpRequest<?> request) {
            return "<!DOCTYPE html><html><body></body></html>";
        }

        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Executable
        HttpResponse<?> save(HttpRequest<?> request, @NotNull @Valid @Body Contact form) {
            this.contact = form;
            return HttpResponse.seeOther(URI.create("/foo"));
        }

        @Consumes(MediaType.APPLICATION_FORM_URLENCODED)
        @Executable
        HttpResponse<?> saveWithoutBody(HttpRequest<?> request) {
            this.contact = request.getBody(Contact.class).orElse(null);
            return HttpResponse.seeOther(URI.create("/foo"));
        }
    }

    @Introspected
    record Contact(String firstName, String lastName) {

    }

}
