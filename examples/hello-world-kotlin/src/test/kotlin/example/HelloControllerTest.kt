package example

import io.micronaut.context.ApplicationContext
import io.micronaut.runtime.server.EmbeddedServer
import org.jetbrains.spek.api.Spek
import org.jetbrains.spek.api.dsl.describe
import org.jetbrains.spek.api.dsl.it
import org.junit.jupiter.api.Assertions.assertEquals

class HelloControllerTest : Spek({

    describe("Hello world") {
        val server = ApplicationContext.run(EmbeddedServer::class.java)
        val helloClient = server.applicationContext.getBean(HelloClient::class.java)

        it("should return 'Hello Fred!'") {
            assertEquals(helloClient.hello("Fred").blockingGet(), "Hello Fred")
        }

        afterGroup {
            server.stop()
        }
    }
})
