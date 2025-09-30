package example.micronaut.jacksondatabind

import io.micronaut.context.annotation.Requires
import io.micronaut.core.annotation.Introspected
import io.micronaut.core.annotation.ReflectiveAccess

@Requires(property = "spec.name", value = "JacksonDatabindDataClassSerialization")
//tag::clazz[]
@ReflectiveAccess
@Introspected
data class Greeting(val message: String)
//end::clazz[]
