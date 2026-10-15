/*
 * Copyright 2017-2026 original authors
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
package io.micronaut.docs.aop.retry

import io.micronaut.context.ApplicationContext
import io.micronaut.retry.CircuitBreakerRegistry
import io.micronaut.retry.CircuitBreakerSnapshot
import io.micronaut.retry.CircuitState
import io.micronaut.retry.exception.CircuitOpenException
import spock.lang.AutoCleanup
import spock.lang.Specification

class NamedCircuitBreakerSpec extends Specification {

    // the configuration of the guide
    @AutoCleanup
    ApplicationContext context = ApplicationContext.run(
        'micronaut.retry.circuit-breakers.inventory.reset': '30s',
        'micronaut.retry.circuit-breakers.inventory.attempts': 1,
        'micronaut.retry.circuit-breakers.shipping.reset': '30s',
        'micronaut.retry.circuit-breakers.shipping.request-volume-threshold': 4,
        'micronaut.retry.circuit-breakers.shipping.failure-ratio': 0.5,
        'micronaut.retry.circuit-breakers.shipping.success-threshold': 2,
        'micronaut.retry.circuit-breakers.shipping.skip-on': ['java.util.NoSuchElementException']
    )
    Warehouse warehouse = context.getBean(Warehouse)
    CircuitBreakerRegistry registry = context.getBean(CircuitBreakerRegistry)

    void 'methods of one name share the circuit'() {
        given:
        InventoryService service = context.getBean(InventoryService)
        InventoryClient client = context.getBean(InventoryClient)

        when: 'the attempt and its two retries fail'
        service.stock('apple')

        then: 'the circuit opens'
        thrown(IllegalStateException)
        warehouse.calls == 3
        registry.findState('inventory').get() == CircuitState.OPEN

        when: 'the other method fails fast'
        warehouse.available = true
        service.reserve('apple')

        then:
        IllegalStateException e = thrown()
        e.message == 'Warehouse unavailable'
        warehouse.calls == 3

        when: 'the registry fails fast too'
        client.stock('apple')

        then:
        thrown(IllegalStateException)
        warehouse.calls == 3
    }

    void 'a guard reports outcomes to a rolling window'() {
        given:
        InventoryClient client = context.getBean(InventoryClient)

        when: 'two failures: the window of four calls is not full'
        2.times {
            try {
                client.ship('order-1')
            } catch (IllegalStateException ignored) {
            }
        }

        then:
        registry.findState('shipping').get() == CircuitState.CLOSED

        when: 'a skipOn exception counts as a success'
        warehouse.available = true
        client.ship('unknown')

        then:
        thrown(NoSuchElementException)
        CircuitBreakerSnapshot snapshot = registry.findSnapshot('shipping').get()
        snapshot.calls() == 3
        snapshot.failures() == 2
        snapshot.state() == CircuitState.CLOSED

        when: 'the fourth call fills the window: two failures of four reach the ratio 0.5'
        String tracking = client.ship('order-1')

        then:
        tracking == 'tracking-order-1'
        registry.findState('shipping').get() == CircuitState.OPEN

        when: 'the guard fails fast'
        client.ship('order-1')

        then:
        thrown(CircuitOpenException)
        warehouse.calls == 4
    }

    void 'a rolling window on the annotation'() {
        given:
        PricingService service = context.getBean(PricingService)

        when: 'two calls that fail after their retry: the window of four calls is not full'
        2.times {
            try {
                service.price('apple')
            } catch (IllegalStateException ignored) {
            }
        }
        CircuitBreakerSnapshot window = registry.findSnapshot('pricing').get()

        then:
        warehouse.calls == 4
        window.state() == CircuitState.CLOSED
        window.calls() == 2
        window.failures() == 2

        when: 'a skipOn exception counts as a success'
        warehouse.available = true
        service.price('unknown')

        then:
        thrown(NoSuchElementException)
        registry.findState('pricing').get() == CircuitState.CLOSED

        when: 'the fourth call fills the window: two failures of four reach the ratio 0.5'
        BigDecimal price = service.price('apple')
        CircuitBreakerSnapshot snapshot = registry.findSnapshot('pricing').get()

        then:
        price == BigDecimal.TEN
        snapshot.state() == CircuitState.OPEN
        snapshot.requestVolumeThreshold() == 4
        snapshot.openedCount() == 1

        when: 'the open circuit fails fast'
        int calls = warehouse.calls
        service.price('apple')

        then:
        thrown(IllegalStateException)
        warehouse.calls == calls
    }
}
