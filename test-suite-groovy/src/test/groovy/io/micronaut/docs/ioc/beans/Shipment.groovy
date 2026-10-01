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
package io.micronaut.docs.ioc.beans

// tag::class[]
import io.micronaut.core.annotation.Creator
import io.micronaut.core.annotation.Introspected

@Introspected(constructors = true) // <1>
class Shipment {

    final String item
    final int quantity

    Shipment(String item) {
        this(item, 1)
    }

    @Creator // <2>
    Shipment(String item, int quantity) {
        this.item = item
        this.quantity = quantity
    }
}
// end::class[]
