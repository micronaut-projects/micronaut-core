package io.micronaut.docs.server.form;

import io.micronaut.context.annotation.Property;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.MediaType;
import io.micronaut.http.client.BlockingHttpClient;
import io.micronaut.http.client.HttpClient;
import io.micronaut.http.client.annotation.Client;
import io.micronaut.test.extensions.junit5.annotation.MicronautTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@Property(name = "spec.name", value = "PersonControllerFormTest")
@MicronautTest
class PersonControllerTest {

    @Test
    void testSave(@Client("/") HttpClient httpClient, PersonController controller) {
        BlockingHttpClient client = httpClient.toBlocking();
        String payload = "firstName=Fred&lastName=Flintstone&age=45";
        HttpRequest<?> request = HttpRequest.POST("/people", payload).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        invoke(client, request, controller);
    }

    @Test
    void saveWithArgsOptional(@Client("/") HttpClient httpClient, PersonController controller) {
        BlockingHttpClient client = httpClient.toBlocking();
        String payload = "firstName=Fred&lastName=Flintstone&age=45";
        HttpRequest<?> request = HttpRequest.POST("/people/saveWithArgsOptional", payload).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        invoke(client, request, controller);
    }

    @Test
    void testSaveWithArgs(@Client("/") HttpClient httpClient, PersonController controller) {
        BlockingHttpClient client = httpClient.toBlocking();
        String payload = "firstName=Fred&lastName=Flintstone&age=45";
        HttpRequest<?> request = HttpRequest.POST("/people/saveWithArgs", payload).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE);
        invoke(client, request, controller);
    }

    void invoke(BlockingHttpClient client, HttpRequest<?> request, PersonController controller) {
        assertDoesNotThrow(() -> client.exchange(request));
        Person person = controller.inMemoryDatastore.get("Fred");
        assertNotNull(person);
        assertEquals("Fred", person.getFirstName());
        assertEquals("Flintstone", person.getLastName());
        assertEquals(45, person.getAge());
        controller.inMemoryDatastore.clear();
    }
}
