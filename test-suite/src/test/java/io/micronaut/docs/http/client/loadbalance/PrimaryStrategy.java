package io.micronaut.docs.http.client.loadbalance;

import io.micronaut.context.annotation.Requires;
// tag::imports[]
import io.micronaut.context.annotation.Prototype;
import io.micronaut.discovery.ServiceInstance;
import io.micronaut.http.client.loadbalance.LoadBalancerStrategy;
import jakarta.inject.Named;
import org.jspecify.annotations.Nullable;

import java.util.List;
// end::imports[]

@Requires(property = "spec.name", value = "PrimaryStrategyTest")
// tag::class[]
@Prototype // <1>
@Named("primary") // <2>
public class PrimaryStrategy implements LoadBalancerStrategy {

    @Override
    public ServiceInstance select(List<ServiceInstance> available, @Nullable Object discriminator) {
        return available.get(0); // <3>
    }
}
// end::class[]
