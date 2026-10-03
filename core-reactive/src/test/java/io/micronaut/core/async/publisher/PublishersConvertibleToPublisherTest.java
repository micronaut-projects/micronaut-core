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
package io.micronaut.core.async.publisher;

import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.concurrent.CompletableFuture;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PublishersConvertibleToPublisherTest {

    @Test
    void repeatedLookupsReturnTheSameResult() {
        for (int i = 0; i < 3; i++) {
            assertTrue(Publishers.isConvertibleToPublisher(Publisher.class));
            assertTrue(Publishers.isConvertibleToPublisher(CustomPublisher.class));
            assertTrue(Publishers.isConvertibleToPublisher(Flux.class));
            assertTrue(Publishers.isConvertibleToPublisher(Mono.class));
            assertTrue(Publishers.isConvertibleToPublisher(Mono.just("a")));
            assertFalse(Publishers.isConvertibleToPublisher(Pojo.class));
            assertFalse(Publishers.isConvertibleToPublisher(new Pojo()));
            assertFalse(Publishers.isConvertibleToPublisher(String.class));
            assertFalse(Publishers.isConvertibleToPublisher(List.class));
            assertFalse(Publishers.isConvertibleToPublisher(CompletableFuture.class));
            assertFalse(Publishers.isConvertibleToPublisher(int.class));
            assertFalse(Publishers.isConvertibleToPublisher(Pojo[].class));
            assertFalse(Publishers.isConvertibleToPublisher((Object) null));
        }
    }

    @Test
    void registeringAReactiveTypeAfterANegativeLookupMakesItConvertible() {
        assertFalse(Publishers.isConvertibleToPublisher(LateReactiveType.class));
        assertFalse(Publishers.isConvertibleToPublisher(LateReactiveTypeImpl.class));
        assertFalse(Publishers.isConvertibleToPublisher(new LateReactiveTypeImpl()));

        Publishers.registerReactiveType(LateReactiveType.class);

        assertTrue(Publishers.isConvertibleToPublisher(LateReactiveType.class));
        assertTrue(Publishers.isConvertibleToPublisher(LateReactiveTypeImpl.class));
        assertTrue(Publishers.isConvertibleToPublisher(new LateReactiveTypeImpl()));
        assertFalse(Publishers.isConvertibleToPublisher(Pojo.class));
    }

    @Test
    void registeringASingleOrCompletableTypeAfterANegativeLookupMakesItConvertible() {
        assertFalse(Publishers.isConvertibleToPublisher(LateSingleType.class));
        assertFalse(Publishers.isConvertibleToPublisher(LateCompletableType.class));

        Publishers.registerReactiveSingle(LateSingleType.class);
        Publishers.registerReactiveCompletable(LateCompletableType.class);

        assertTrue(Publishers.isConvertibleToPublisher(LateSingleType.class));
        assertTrue(Publishers.isSingle(LateSingleType.class));
        assertTrue(Publishers.isConvertibleToPublisher(LateCompletableType.class));
        assertTrue(Publishers.isCompletable(LateCompletableType.class));
    }

    static final class Pojo {
    }

    static final class CustomPublisher implements Publisher<String> {
        @Override
        public void subscribe(Subscriber<? super String> s) {
        }
    }

    interface LateReactiveType {
    }

    static final class LateReactiveTypeImpl implements LateReactiveType {
    }

    static final class LateSingleType {
    }

    static final class LateCompletableType {
    }
}
