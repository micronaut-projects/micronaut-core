package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.HttpStatus
import io.micronaut.http.client.HttpClient
import io.micronaut.http.client.exceptions.HttpClientResponseException
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertThrows
import org.junit.jupiter.api.Test

class StaticResourceRoutesTest {

    @Test
    fun theRoutesServeStaticResources() {
        ApplicationContext.run(EmbeddedServer::class.java, mapOf<String, Any>(
            "spec.name" to "StaticResourceRoutesTest",
            "site.directory" to "src/test/resources/functional-static/site"
        )).use { server ->
            server.applicationContext.createBean(HttpClient::class.java, server.url).use { client ->
                val http = client.toBlocking()

                val css = http.exchange(HttpRequest.GET<Any>("/assets/css/site.css"), String::class.java)
                assertEquals(HttpStatus.OK, css.status)
                assertEquals("body { color: teal; }\n", css.body())
                assertEquals("text/css", css.contentType.orElseThrow().name)
                assertEquals("true", css.headers.get("X-Assets"))
                assertEquals("public, max-age=31536000, immutable", css.headers.get("Cache-Control"))

                val hello = http.exchange(HttpRequest.GET<Any>("/site/hello.txt"), String::class.java)
                assertEquals(HttpStatus.OK, hello.status)
                assertEquals("Hello from the file system\n", hello.body())
                assertEquals("text/plain", hello.contentType.orElseThrow().name)

                // the index file at the prefix of the group, with the content type of the file, not of the group
                val manual = http.exchange(HttpRequest.GET<Any>("/manual"), String::class.java)
                assertEquals(HttpStatus.OK, manual.status)
                assertEquals("<h1>Manual</h1>\n", manual.body())
                assertEquals("text/html", manual.contentType.orElseThrow().name)
                assertEquals("true", manual.headers.get("X-Manual"))

                // a missing file, and paths that try to leave the directory, are not found
                for (path in listOf("/assets/missing.css", "/assets/../secret.txt", "/assets/%2e%2e/secret.txt",
                        "/assets/..%2fsecret.txt", "/site/..%2f..%2fsecret.txt")) {
                    val notFound = assertThrows(HttpClientResponseException::class.java, {
                        http.exchange(HttpRequest.GET<Any>(path), String::class.java)
                    }, path)
                    assertEquals(HttpStatus.NOT_FOUND, notFound.status, path)
                }
            }
        }
    }
}
