package io.micronaut.docs.server.pathvariables

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.PathVariables
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
// end::imports[]

@Requires(property = "spec.name", value = "ItemControllerTest")
// tag::class[]
@ServerFilter("/items/**") // <1>
class ArchivedItemFilter {

    private val archived = setOf(7L)

    @RequestFilter
    fun archived(pathVariables: PathVariables): HttpResponse<*>? { // <2>
        val id = pathVariables.findLong("id") // <3>
        if (id.isPresent && archived.contains(id.asLong)) {
            return HttpResponse.status<Any>(HttpStatus.GONE) // <4>
        }
        return null
    }
}
// end::class[]
