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
package io.micronaut.web.router;

import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.http.HttpResponse;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A publisher of responses is answered by its first response: the route is single-valued.
 */
class ResponsePublisherRouteInfoTest {

    @Test
    void aPublisherOfResponsesIsSingleValued() {
        DefaultRouteInfo<?> route = new DefaultRouteInfo<>(ReturnType.of(Publisher.class, Argument.of(HttpResponse.class, Argument.STRING)), Object.class, false, false);
        assertTrue(route.isReactive());
        assertTrue(route.isSingleResult());
    }

    @Test
    void aPublisherOfValuesIsAStream() {
        DefaultRouteInfo<?> route = new DefaultRouteInfo<>(ReturnType.of(Publisher.class, Argument.STRING), Object.class, false, false);
        assertTrue(route.isReactive());
        assertFalse(route.isSingleResult());
    }
}
