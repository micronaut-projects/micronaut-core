package io.micronaut.management.endpoint.management.impl;

import io.micronaut.context.annotation.Requires;
import io.micronaut.management.endpoint.annotation.Endpoint;
import io.micronaut.management.endpoint.management.ManagementEndpoint;
import io.micronaut.management.endpoint.routes.RouteData;
import io.micronaut.management.endpoint.routes.impl.RxJavaRouteDataCollector;
import io.micronaut.web.router.MethodBasedRoute;
import io.micronaut.web.router.UriRoute;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@Requires(beans = ManagementEndpoint.class)
public class ManagementRxJavaRouteDataCollector extends RxJavaRouteDataCollector {

    public ManagementRxJavaRouteDataCollector(
            @Named("Navigable") RouteData<Map<String, String>> routeData) {
        super(routeData);
    }

    @Override
    public Publisher<Map<String, Object>> getData(Stream<UriRoute> routes) {
        List<UriRoute> routeList = routes.collect(Collectors.toList());
        Map<String, Object> routeMap = new LinkedHashMap<>(routeList.size());

        return Flowable
                .fromIterable(routeList)
                .collectInto(routeMap, (map, route) ->
                        map.put(getEndpointId(route), routeData.getData(route))
                ).toFlowable();
    }

    public String getEndpointId(UriRoute route) {
        if (!(route instanceof MethodBasedRoute)) {
            throw new IllegalArgumentException("route should be backed by a method");
        }
        Class<?> declaringType = ((MethodBasedRoute) route).getTargetMethod().getDeclaringType();
        Endpoint endpoint = declaringType.getAnnotation(Endpoint.class);
        return endpoint.value().equals("") ? endpoint.id() : endpoint.value();
    }
}
