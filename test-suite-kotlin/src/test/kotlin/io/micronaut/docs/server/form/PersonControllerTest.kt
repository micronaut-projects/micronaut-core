package io.micronaut.docs.server.form

import io.micronaut.context.annotation.Property
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpResponse
import io.micronaut.http.MediaType
import io.micronaut.http.client.BlockingHttpClient
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.annotation.Client
import io.micronaut.test.extensions.junit5.annotation.MicronautTest
import org.junit.jupiter.api.Assertions
import org.junit.jupiter.api.Test

@Property(name = "spec.name", value = "PersonControllerFormTest")
@MicronautTest
internal class PersonControllerTest {
    @Test
    fun testSave(@Client("/") httpClient: HttpClient, controller: PersonController) {
        val client = httpClient.toBlocking()
        val payload = "firstName=Fred&lastName=Flintstone&age=45"
        val request: HttpRequest<*> =
            HttpRequest.POST("/people", payload).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        invoke(client, request, controller)
    }

    @Test
    fun saveWithArgsOptional(@Client("/") httpClient: HttpClient, controller: PersonController) {
        val client = httpClient.toBlocking()
        val payload = "firstName=Fred&lastName=Flintstone&age=45"
        val request: HttpRequest<*> = HttpRequest.POST("/people/saveWithArgsOptional", payload)
            .contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        invoke(client, request, controller)
    }

    @Test
    fun testSaveWithArgs(@Client("/") httpClient: HttpClient, controller: PersonController) {
        val client = httpClient.toBlocking()
        val payload = "firstName=Fred&lastName=Flintstone&age=45"
        val request: HttpRequest<*> =
            HttpRequest.POST("/people/saveWithArgs", payload).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)
        invoke(client, request, controller)
    }

    fun invoke(client: BlockingHttpClient, request: HttpRequest<*>?, controller: PersonController) {
        Assertions.assertDoesNotThrow<HttpResponse<Any>> {
            client.exchange(
                request
            )
        }
        val person = controller.inMemoryDatastore["Fred"]
        Assertions.assertNotNull(person)
        Assertions.assertEquals("Fred", person!!.firstName)
        Assertions.assertEquals("Flintstone", person.lastName)
        Assertions.assertEquals(45, person.age)
        controller.inMemoryDatastore.clear()
    }
}
