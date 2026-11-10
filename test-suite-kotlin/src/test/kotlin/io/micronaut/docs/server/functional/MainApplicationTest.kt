package io.micronaut.docs.server.functional

import io.micronaut.context.ApplicationContext
import io.micronaut.http.HttpRequest
import io.micronaut.http.client.HttpClient
import io.micronaut.http.server.netty.configuration.NettyHttpServerConfiguration
import io.micronaut.runtime.server.EmbeddedServer
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class MainApplicationTest {

    private val args = arrayOf("-micronaut.server.port=-1", "-spec.name=MainApplicationTest")

    @Test
    fun routesInTheMainFunction() {
        start(*args).use { context ->
            client(context).use { client ->
                assertEquals("Hello World", client.toBlocking().retrieve(HttpRequest.GET<Any>("/hello/World")))
            }
        }
    }

    @Test
    fun routesThatUseBeans() {
        startWithBeans(*args).use { context ->
            client(context).use { client ->
                val saved = client.toBlocking().retrieve(HttpRequest.POST("/items", Item(0, "Pen")), Item::class.java)
                assertEquals("Pen", client.toBlocking().retrieve(HttpRequest.GET<Any>("/items/" + saved.id), Item::class.java).name)
            }
        }
    }

    @Test
    fun chosenServerWithItsConfiguration() {
        startOnNetty(*args).use { context ->
            client(context).use { client ->
                assertEquals("pong", client.toBlocking().retrieve(HttpRequest.GET<Any>("/ping")))
                assertEquals(16384, context.getBean(NettyHttpServerConfiguration::class.java).maxHeaderSize)
            }
        }
    }

    private fun client(context: ApplicationContext): HttpClient =
        context.createBean(HttpClient::class.java, context.getBean(EmbeddedServer::class.java).url)
}
