package io.micronaut.docs.function.client.aws

import groovy.transform.Field

math.multiplier = 2
@Field MathService mathService

int round(float value) {
    mathService.round(value)
}
