package io.micronaut.web.router;

import io.micronaut.core.type.Argument;
import io.micronaut.inject.MethodExecutionHandle;
import io.micronaut.core.type.Argument;
import io.micronaut.inject.MethodExecutionHandle;

import java.util.Arrays;
import java.util.Collection;

public interface MethodBasedRouteMatch<R> extends RouteMatch<R>, MethodExecutionHandle<R> {

    /**
     * <p>Returns the required arguments for this RouteMatch</p>
     *
     * <p>Note that this is not the save as {@link #getArguments()} as it will include a subset of the arguments excluding those that have been subtracted from the URI variables</p>
     *
     * @return The required arguments in order to invoke this route
     */
    default Collection<Argument> getRequiredArguments() {
        return Arrays.asList(getArguments());
    }
}
