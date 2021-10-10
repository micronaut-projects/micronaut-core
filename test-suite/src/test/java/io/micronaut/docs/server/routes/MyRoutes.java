package io.micronaut.docs.server.routes;
// tag::imports[]
import io.micronaut.context.ExecutionHandleLocator;
import io.micronaut.web.router.DefaultRouteBuilder;
import javax.inject.Inject;
import javax.inject.Singleton;
// end::imports[]

// tag::class[]
@Singleton
public class MyRoutes extends DefaultRouteBuilder { // <1>

    public MyRoutes(ExecutionHandleLocator executionHandleLocator, UriNamingStrategy uriNamingStrategy) {
        super(executionHandleLocator, uriNamingStrategy);
    }

    @Inject
    void issuesRoutes(IssuesController issuesController) { // <2>
        GET("/issues/show/{number}", issuesController, "issue", Integer.class); // <3>
    }
}
// end::class[]
