package org.particleframework.management.endpoint.routes.impl;

import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import org.particleframework.context.annotation.Requires;
import org.particleframework.http.MediaType;
import org.particleframework.management.endpoint.routes.RouteData;
import org.particleframework.management.endpoint.routes.RouteDataCollector;
import org.particleframework.management.endpoint.routes.RoutesEndpoint;
import org.particleframework.scheduling.executor.IOExecutorServiceConfig;
import org.particleframework.web.router.UriRoute;
import org.reactivestreams.Publisher;

import javax.inject.Named;
import javax.inject.Singleton;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Singleton
@Requires(beans = RoutesEndpoint.class)
public class RxJavaRouteDataCollector implements RouteDataCollector<Map<String, Object>> {


    private final RouteData routeData;
    private final ExecutorService executorService;

    public RxJavaRouteDataCollector(RouteData routeData,
                                    @Named(IOExecutorServiceConfig.NAME) ExecutorService executorService) {
        this.routeData = routeData;
        this.executorService = executorService;
    }

    @Override
    public Publisher<Map<String, Object>> getData(Stream<UriRoute> routes) {
        List<UriRoute> routeList = routes.collect(Collectors.toList());
        Map<String, Object> routeMap = new ConcurrentHashMap<>(routeList.size());

        return Flowable.fromIterable(routeList)
                .subscribeOn(Schedulers.from(executorService))
                .collectInto(routeMap, (map, route) -> {
                    map.put(getRouteKey(route), routeData.getData(route));
                }).toFlowable();
    }

    protected String getRouteKey(UriRoute route) {
        String produces = route.getProduces().stream()
                .map(MediaType::toString)
                .collect(Collectors.joining(" || "));

        return new StringBuilder("{[")
                .append(route.getUriMatchTemplate())
                .append("],method=[")
                .append(route.getHttpMethod().name())
                .append("],produces=[")
                .append(produces)
                .append("]}")
                .toString();
    }
}
