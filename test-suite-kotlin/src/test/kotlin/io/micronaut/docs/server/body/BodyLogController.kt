package io.micronaut.docs.server.body

// tag::imports[]
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.http.annotation.Body
import io.micronaut.http.annotation.Controller
import io.micronaut.http.annotation.Post
import org.slf4j.LoggerFactory
// end::imports[]

@Requires(property = "spec.name", value = "BodyLogFilterSpec")
// tag::clazz[]
@Controller("/person")
class BodyLogController {

    @Post
    fun create(@Body person: Person) { // <1>
        LOG.info("Creating person {}", person)
    }

    @Introspected
    data class Person(val firstName: String, val lastName: String)

    companion object {
        private val LOG = LoggerFactory.getLogger(BodyLogController::class.java)
    }
}
// end::clazz[]
