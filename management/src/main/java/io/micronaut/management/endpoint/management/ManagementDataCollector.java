package io.micronaut.management.endpoint.management;

import io.micronaut.web.router.UriRoute;
import org.reactivestreams.Publisher;

import java.util.stream.Stream;

/**
 * <p>Collect management data to respond in {@link ManagementController}.</p>
 *
 * @author Hernán Cervera
 * @since 3.0.0
 * */
public interface ManagementDataCollector<T> {

    /**
     * Collect management data.
     *
     * @param routes management routes.
     * @param routeBase base route of the endpoints.
     * @param managementDiscoveryPath path of the management endpoint exposed by {@link ManagementController}.
     * @param isManagementDiscoveryPathTemplated whether the discovery endpoint path is templated.
     * @return the {@link Publisher}.
     */
    Publisher<T> collectData(Stream<UriRoute> routes, String routeBase,
                             String managementDiscoveryPath, boolean isManagementDiscoveryPathTemplated);
}
