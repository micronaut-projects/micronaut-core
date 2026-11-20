package io.micronaut.http.server;

import io.micronaut.context.ApplicationContext;

/**
 * Selects how the server executes routes in a benchmark: with the execution prepared for each
 * route, or with the compatibility execution, which reads the route on every request.
 */
public final class RouteExecutionModes {

    private RouteExecutionModes() {
    }

    /**
     * @param context       The context of the server
     * @param compatibility Whether every route is executed by the compatibility execution
     */
    public static void select(ApplicationContext context, boolean compatibility) {
        context.getBean(RouteExecutor.class).forceCompatibility = compatibility;
    }
}
