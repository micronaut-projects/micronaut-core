package io.micronaut.docs.client.filter

import io.kotlintest.shouldBe
import io.kotlintest.specs.StringSpec
import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.Requires
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Get
import io.micronaut.runtime.server.EmbeddedServer

class BasicAuthFilterSpec: StringSpec() {

    val context = autoClose(
            ApplicationContext.run(EmbeddedServer::class.java,
                    mapOf("spec.name" to BasicAuthFilterSpec::class.simpleName)).applicationContext
    )

    init {
        "test the filter is applied"() {
            val client = context.getBean(BasicAuthClient::class.java)

            client.getMessage() shouldBe "user:pass"
        }
    }

    @Requires(property = "spec.name", value = "BasicAuthFilterSpec")
    @Controller("/message")
    class BasicAuthController {

        @Get
        internal fun message(basicAuth: io.micronaut.http.BasicAuth): String {
            return basicAuth.username + ":" + basicAuth.password
        }
    }

}
