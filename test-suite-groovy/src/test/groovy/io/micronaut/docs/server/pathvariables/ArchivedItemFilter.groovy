package io.micronaut.docs.server.pathvariables

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.core.annotation.Nullable
import io.micronaut.http.HttpResponse
import io.micronaut.http.HttpStatus
import io.micronaut.http.PathVariables
import io.micronaut.http.annotation.RequestFilter
import io.micronaut.http.annotation.ServerFilter
// end::imports[]

@Requires(property = "spec.name", value = "ItemControllerSpec")
// tag::class[]
@ServerFilter("/items/**") // <1>
class ArchivedItemFilter {

    private static final Set<Long> ARCHIVED = [7L] as Set

    @RequestFilter
    @Nullable
    HttpResponse<?> archived(PathVariables pathVariables) { // <2>
        OptionalLong id = pathVariables.findLong("id") // <3>
        if (id.present && ARCHIVED.contains(id.asLong)) {
            return HttpResponse.status(HttpStatus.GONE) // <4>
        }
        return null
    }
}
// end::class[]
