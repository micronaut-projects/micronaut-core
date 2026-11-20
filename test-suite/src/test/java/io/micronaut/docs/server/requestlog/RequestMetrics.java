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
package io.micronaut.docs.server.requestlog;

// tag::imports[]
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.event.ApplicationEventListener;
import io.micronaut.http.context.event.HttpRequestTerminatedEvent;
import jakarta.inject.Singleton;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.LongAdder;
// end::imports[]

@Requires(property = "spec.name", value = "RequestMetricsTest")
// tag::clazz[]
@Singleton
public class RequestMetrics implements ApplicationEventListener<HttpRequestTerminatedEvent> {
    private final Map<String, LongAdder> nanosByRoute = new ConcurrentHashMap<>();

    @Override
    public void onApplicationEvent(HttpRequestTerminatedEvent event) {
        event.getRequestLog().ifPresent(log -> { // <1>
            String route = log.routeTemplate().orElse("UNMATCHED"); // <2>
            String key = route + " " + log.status().orElse(0) + (log.isCompleted() ? "" : " incomplete"); // <3>
            log.totalDurationNanos().ifPresent(nanos -> // <4>
                nanosByRoute.computeIfAbsent(key, k -> new LongAdder()).add(nanos));
        });
    }

    public Map<String, LongAdder> nanosByRoute() {
        return nanosByRoute;
    }
}
// end::clazz[]
