package example.micronaut.jacksondatabind

import com.fasterxml.jackson.annotation.JsonProperty
import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.ReflectiveAccess

@Requires(property = "spec.name", value = "JacksonDatabindDataClassSerialization")
//tag::clazz[]
@ReflectiveAccess
@Introspected
data class Greeting(@JsonProperty("message") val message: String)
//end::clazz[]
