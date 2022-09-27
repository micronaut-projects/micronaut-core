package io.micronaut.inject.factory

import io.micronaut.context.annotation.Bean
import io.micronaut.context.annotation.Factory
import jakarta.inject.Named

@Factory
class PrimitiveFactoryx {
    @Bean
    @Named("totalsx")
    int[] totalz = [ 10 ] as int[]
}
