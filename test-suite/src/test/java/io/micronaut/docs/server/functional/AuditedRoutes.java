package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.core.annotation.AnnotationValue;
import io.micronaut.core.version.annotation.Version;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.scheduling.TaskExecutors;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import jakarta.inject.Singleton;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
// end::imports[]

@Requires(property = "spec.name", value = "AuditedRoutesTest")
@Singleton
public class AuditedRoutes implements HttpRoutes {

    // tag::annotations[]
    @FilterMatcher // <1>
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Audited {
    }

    @Audited
    @ServerFilter("/**")
    @Requires(property = "spec.name", value = "AuditedRoutesTest")
    public static class AuditFilter { // <2>
        @ResponseFilter
        public void audit(MutableHttpResponse<?> response) {
            response.header("X-Audited", "true");
        }
    }

    @Singleton
    @Requires(property = "spec.name", value = "AuditedRoutesTest")
    public static class Payments {
        @Executable
        @Audited
        public String pay(long amount) { // <3>
            return "paid " + amount;
        }
    }
    // end::annotations[]

    private final BeanContext beanContext;
    private final Payments payments;

    AuditedRoutes(BeanContext beanContext, Payments payments) {
        this.beanContext = beanContext;
        this.payments = payments;
    }

    @Override
    public void routes(HttpRouteBuilder routes) {
        // tag::annotationRoutes[]
        routes.path("/admin", admin -> {
            admin.executeOn(TaskExecutors.BLOCKING) // <4>
                .annotate(Audited.class) // <5>
                .annotate(Version.class, version -> version.value("2"));
            admin.GET("/users", (request, pathVariables) ->
                HttpResponse.ok("users on " + Thread.currentThread().getName()).contentType(MediaType.TEXT_PLAIN_TYPE));
            admin.DELETE("/users/{id}", (request, pathVariables) ->
                HttpResponse.ok("deleted " + pathVariables.getLong("id")).contentType(MediaType.TEXT_PLAIN_TYPE));
        });
        routes.POST("/payments/{amount}")
            .annotationMetadata(beanContext.getBeanDefinition(Payments.class).getRequiredMethod("pay", long.class)) // <6>
            .handle((request, pathVariables) ->
                HttpResponse.ok(payments.pay(pathVariables.getLong("amount"))).contentType(MediaType.TEXT_PLAIN_TYPE));
        routes.POST("/refunds/{amount}")
            .annotate(Audited.class) // <7>
            .handle((request, pathVariables) ->
                HttpResponse.ok("refunded " + pathVariables.getLong("amount")).contentType(MediaType.TEXT_PLAIN_TYPE));
        routes.GET("/receipts/{id}")
            .annotate(AnnotationValue.builder(Version.class).value("1").build()) // <8>
            .handle((request, pathVariables) ->
                HttpResponse.ok("receipt v1 " + pathVariables.getLong("id")).contentType(MediaType.TEXT_PLAIN_TYPE));
        routes.GET("/receipts/{id}")
            .annotate(Version.class, version -> version.value("2")) // <9>
            .annotate(Audited.class)
            .handle((request, pathVariables) ->
                HttpResponse.ok("receipt v2 " + pathVariables.getLong("id")).contentType(MediaType.TEXT_PLAIN_TYPE));
        routes.GET("/prices", (request, pathVariables) -> HttpResponse.ok("prices").contentType(MediaType.TEXT_PLAIN_TYPE));
        // end::annotationRoutes[]

        routes.route(HttpMethod.GET, "/balance/{account}").handle((request, pathVariables) ->
            HttpResponse.ok("balance of " + pathVariables.getString("account")).contentType(MediaType.TEXT_PLAIN_TYPE));
    }
}
