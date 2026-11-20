package io.micronaut.docs.http.client.dns

import io.micronaut.context.annotation.Requires
// tag::imports[]
import io.micronaut.http.annotation.Get
import io.micronaut.http.client.annotation.Client
// end::imports[]

@Requires(property = 'spec.name', value = 'DnsDiscoveryTest')
// tag::class[]
@Client(id = 'orders') // <1>
interface OrdersClient {
    @Get('/orders/{id}')
    String find(String id)
}
// end::class[]
