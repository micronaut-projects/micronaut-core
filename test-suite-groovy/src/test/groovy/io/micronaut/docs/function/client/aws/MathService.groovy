/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.function.client.aws

import io.micronaut.context.annotation.Value

import javax.inject.Singleton

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
class MathService {
    @Value('${math.multiplier:1}')
    Integer multiplier = 1

    int round(float value) {
        Math.round(value) * multiplier
    }

    Integer max() {
        Integer.MAX_VALUE
    }

    long sum(Sum sum) {
        sum.a + sum.b
    }
}

