/*
 * Copyright 2017-2026 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context.env

import io.micronaut.core.order.Ordered
import spock.lang.Specification

import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicInteger

class PropertySourcePropertyResolverConcurrencySpec extends Specification {

    void "a lookup that read the properties before a change does not leave its value for the lookups after the change"() {
        given:
        def resolver = new PropertySourcePropertyResolver(PropertySource.of("v0", ["fuzz.value": "0"]))
        def added = new AtomicInteger()
        def writing = new AtomicBoolean(true)
        def executor = Executors.newFixedThreadPool(5)

        when:
        def readers = (1..4).collect {
            executor.submit {
                while (writing.get()) {
                    int done = added.get()
                    int seen = resolver.getProperty("fuzz.value", Integer).get()
                    assert resolver.containsProperty("fuzz.value")
                    assert seen >= done: "saw $seen after $done was added"
                }
            }
        }
        def writer = executor.submit {
            try {
                for (int i = 1; i <= 2_000; i++) {
                    resolver.addPropertySource(PropertySource.of("v$i", ["fuzz.value": "$i"], Ordered.HIGHEST_PRECEDENCE + i))
                    added.set(i)
                }
            } finally {
                writing.set(false)
            }
        }
        writer.get(60, TimeUnit.SECONDS)
        readers.each { it.get(60, TimeUnit.SECONDS) }

        then:
        resolver.getProperty("fuzz.value", Integer).get() == 2_000
        resolver.getProperty("fuzz.value", String).get() == "2000"

        cleanup:
        executor.shutdownNow()
    }

    void "a refresh that fails before it is published keeps the cached values"() {
        given:
        def resolver = new PropertySourcePropertyResolver(PropertySource.of("random", ["token": '${random.uuid}']))
        def token = resolver.getProperty("token", String).get()

        when:
        def read = resolver.startRead()
        try {
            resolver.addPropertySource(PropertySource.of("replacement", ["token": '${random.uuid}']))
            throw new IllegalStateException("refresh failed")
        } finally {
            resolver.endBuild(read)
        }

        then:
        thrown(IllegalStateException)
        resolver.getProperty("token", String).get() == token
    }
}
