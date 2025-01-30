package io.micronaut.docs.server.form

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.spock.annotation.MicronautTest
import jakarta.inject.Inject
import spock.lang.Specification

@Property(name = "spec.name", value = "PersonControllerFormTest")
@MicronautTest
class PersonControllerSpec extends Specification {
    @Inject
    @Client("/")
    HttpClient httpClient

    @Inject
    PersonController controller

    void testSave() {
        given:
        BlockingHttpClient client = httpClient.toBlocking()
        String payload = "firstName=Fred&lastName=Flintstone&age=45"
        HttpRequest<?> request = HttpRequest.POST("/people", payload).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        when:
        client.exchange(request)

        then:
        noExceptionThrown()

        when:
        Person person = controller.inMemoryDatastore.get("Fred")

        then:
        person
        "Fred" == person.firstName
        "Flintstone" == person.lastName
        45 == person.age

        cleanup:
        controller.inMemoryDatastore.clear()
    }

    void saveWithArgsOptional() {
        BlockingHttpClient client = httpClient.toBlocking()
        String payload = "firstName=Fred&lastName=Flintstone&age=45"
        HttpRequest<?> request = HttpRequest.POST("/people/saveWithArgsOptional", payload).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        when:
        client.exchange(request)

        then:
        noExceptionThrown()

        when:
        Person person = controller.inMemoryDatastore.get("Fred")

        then:
        person
        "Fred" == person.firstName
        "Flintstone" == person.lastName
        45 == person.age

        cleanup:
        controller.inMemoryDatastore.clear()
    }

    void testSaveWithArgs() {
        BlockingHttpClient client = httpClient.toBlocking()
        String payload = "firstName=Fred&lastName=Flintstone&age=45"
        HttpRequest<?> request = HttpRequest.POST("/people/saveWithArgs", payload).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        when:
        client.exchange(request)

        then:
        noExceptionThrown()

        when:
        Person person = controller.inMemoryDatastore.get("Fred")

        then:
        person
        "Fred" == person.firstName
        "Flintstone" == person.lastName
        45 == person.age

        cleanup:
        controller.inMemoryDatastore.clear()
    }

}
