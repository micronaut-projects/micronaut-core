package io.micronaut.docs.server.form

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.MediaType
import io.micronaut.http.client.HttpClient
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertDoesNotThrow
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertNotNull
import org.junit.jupiter.api.Test

class PersonControllerTest {

    @Test
    fun testSave() = invoke("/people")

    @Test
    fun testSaveWithArgs() = invoke("/people/saveWithArgs")

    @Test
    fun saveWithArgsOptional() = invoke("/people/saveWithArgsOptional")

    private fun invoke(uri: String) {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf("spec.name" to "PersonControllerFormTest")).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { httpClient ->
                val payload = "firstName=Fred&lastName=Flintstone&age=45"
                val request = HttpRequest.POST<Any>(uri, payload).contentType(MediaType.APPLICATION_FORM_URLENCODED_TYPE)

                assertDoesNotThrow { httpClient.toBlocking().exchange<Any, Any>(request) }

                val controller = server.applicationContext.getBean(PersonController::class.java)
                val person = controller.inMemoryDatastore["Fred"]
                assertNotNull(person)
                assertEquals("Fred", person!!.firstName)
                assertEquals("Flintstone", person.lastName)
                assertEquals(45, person.age)
            }
        }
    }
}
