package io.micronaut.docs.server.pathvariables;

import io.micronaut.context.annotation.Requires;
// tag::imports[]
import io.micronaut.core.annotation.Nullable;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.PathVariables;
import io.micronaut.http.annotation.RequestFilter;
import io.micronaut.http.annotation.ServerFilter;

import java.util.OptionalLong;
import java.util.Set;
// end::imports[]

@Requires(property = "spec.name", value = "ItemControllerTest")
// tag::class[]
@ServerFilter("/items/**") // <1>
public class ArchivedItemFilter {

    private static final Set<Long> ARCHIVED = Set.of(7L);

    @RequestFilter
    public @Nullable HttpResponse<?> archived(PathVariables pathVariables) { // <2>
        OptionalLong id = pathVariables.findLong("id"); // <3>
        if (id.isPresent() && ARCHIVED.contains(id.getAsLong())) {
            return HttpResponse.status(HttpStatus.GONE); // <4>
        }
        return null;
    }
}
// end::class[]
