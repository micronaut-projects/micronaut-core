package io.micronaut.docs.http.client.loadbalance

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.context.annotation.Prototype
import io.micronaut.discovery.ServiceInstance
import io.micronaut.http.client.loadbalance.LoadBalancerStrategy
import jakarta.inject.Named
// end::imports[]

@Requires(property = "spec.name", value = "PrimaryStrategyTest")
// tag::class[]
@Prototype // <1>
@Named("primary") // <2>
class PrimaryStrategy : LoadBalancerStrategy {

    override fun select(available: List<ServiceInstance>, discriminator: Any?): ServiceInstance =
        available[0] // <3>
}
// end::class[]
