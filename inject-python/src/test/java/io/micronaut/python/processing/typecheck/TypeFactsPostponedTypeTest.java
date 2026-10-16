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
package io.micronaut.python.processing.typecheck;

import io.micronaut.annotation.processing.PostponeToNextRoundException;
import io.micronaut.inject.visitor.VisitorContext;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Proxy;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A type javac completes in a later round is unknown, and the checker never reports on what it does not know.
 */
final class TypeFactsPostponedTypeTest {

    @Test
    void aTypeCompletedInALaterRoundFitsAnyParameter() {
        VisitorContext postponing = (VisitorContext) Proxy.newProxyInstance(
            getClass().getClassLoader(),
            new Class<?>[] {VisitorContext.class},
            (proxy, method, args) -> {
                if (method.getName().equals("getClassElement")) {
                    throw new PostponeToNextRoundException(new Object(), "generated.Later");
                }
                if (method.getReturnType() == Optional.class) {
                    return Optional.empty();
                }
                return null;
            });
        TypeFacts facts = new TypeFacts(postponing);
        assertTrue(facts.isAssignable("generated.Later", "java.lang.Runnable"));
        assertTrue(facts.isAssignable("java.lang.Runnable", "generated.Later"));
    }
}
