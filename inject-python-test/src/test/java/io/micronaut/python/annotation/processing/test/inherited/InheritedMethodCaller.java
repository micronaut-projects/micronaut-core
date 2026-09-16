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
package io.micronaut.python.annotation.processing.test.inherited;

import java.util.Optional;

/**
 * Invokes the inherited Java signatures the way a Java caller (for example a Micronaut Data interceptor) would.
 */
public final class InheritedMethodCaller {

    private InheritedMethodCaller() {
    }

    public static String greet(DefaultMethodGreeter greeter, String who) {
        return greeter.greet(who);
    }

    public static int order(DefaultMethodGreeter greeter) {
        return greeter.getOrder();
    }

    public static void onEvent(DefaultMethodGreeter greeter, String event) {
        greeter.onEvent(event);
    }

    public static <E> Optional<E> findById(BoxedIdRepository<E, Integer> repository, Integer id) {
        return repository.findById(id);
    }

    public static void deleteById(BoxedIdRepository<?, Integer> repository, Integer id) {
        repository.deleteById(id);
    }
}
