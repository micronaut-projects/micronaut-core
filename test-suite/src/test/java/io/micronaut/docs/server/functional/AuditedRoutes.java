package io.micronaut.docs.server.functional;

// tag::imports[]
import io.micronaut.context.BeanContext;
import io.micronaut.context.annotation.Executable;
import io.micronaut.context.annotation.Requires;
import io.micronaut.http.HttpMethod;
import io.micronaut.http.HttpResponse;
import io.micronaut.http.MediaType;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.FilterMatcher;
import io.micronaut.http.annotation.ResponseFilter;
import io.micronaut.http.annotation.ServerFilter;
import io.micronaut.web.router.builder.HttpRouteBuilder;
import io.micronaut.web.router.builder.HttpRoutes;
import io.micronaut.web.router.builder.RouteDeclaration;
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

    @Singleton
    @Audited
    @Requires(property = "spec.name", value = "AuditedRoutesTest")
    public static class Refunds { // <4>
    }
    // end::annotations[]

    // tag::declaration[]
    static final RouteDeclaration BALANCE = RouteDeclaration.of(HttpMethod.GET, "/balance/{account}"); // <1>
    // end::declaration[]

    private final BeanContext beanContext;
    private final Payments payments;

    AuditedRoutes(BeanContext beanContext, Payments payments) {
        this.beanContext = beanContext;
        this.payments = payments;
    }

    @Override
    public void routes(HttpRouteBuilder routes) {
        // tag::annotationRoutes[]
        routes.POST("/payments/{amount}", (request, pathVariables) ->
                HttpResponse.ok(payments.pay(pathVariables.getLong("amount"))).contentType(MediaType.TEXT_PLAIN_TYPE))
            .annotationMetadata(beanContext.getBeanDefinition(Payments.class).getRequiredMethod("pay", long.class)); // <5>
        routes.POST("/refunds/{amount}", (request, pathVariables) ->
                HttpResponse.ok("refunded " + pathVariables.getLong("amount")).contentType(MediaType.TEXT_PLAIN_TYPE))
            .annotationMetadata(beanContext.getBeanDefinition(Refunds.class)); // <6>
        routes.GET("/prices", (request, pathVariables) -> HttpResponse.ok("prices").contentType(MediaType.TEXT_PLAIN_TYPE));
        // end::annotationRoutes[]

        // tag::declared[]
        routes.handle(BALANCE, (request, pathVariables) -> // <2>
            HttpResponse.ok("balance of " + pathVariables.getString("account")).contentType(MediaType.TEXT_PLAIN_TYPE));
        // end::declared[]
    }
}
