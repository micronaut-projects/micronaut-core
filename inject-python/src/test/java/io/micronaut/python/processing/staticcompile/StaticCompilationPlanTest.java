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
package io.micronaut.python.processing.staticcompile;

import io.micronaut.python.processing.model.SourceSpan;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StaticCompilationPlanTest {

    private static Ir.CompiledBody body(String className, String methodName) {
        return new Ir.CompiledBody(className, methodName, List.of(), List.of(), Ir.VOID, new Ir.Body(List.of()),
            SourceSpan.at("app/calc.py", 3, 5), new StaticCompilationDecision.Stats(0, 0, 0, 0), false, false, false, false,
            Ir.ExceptionCrossing.UNTRANSLATED);
    }

    @Test
    void theBodiesOfAClassKeepTheOrderThePlannerLoweredThemIn() {
        // the methods of a generated class are generated in this order: an order salted per JVM (Map.copyOf) made
        // them change places from one build to the next, with the same sources
        List<Ir.CompiledBody> lowered = new ArrayList<>();
        List<String> methods = IntStream.range(0, 40).mapToObj(i -> "method_" + (i * 7919 % 40)).toList();
        for (String method : methods) {
            lowered.add(body("app.Pet", method));
            lowered.add(body("app.Owner", method));
        }

        StaticCompilationPlan plan = new StaticCompilationPlan(List.of(), StaticCompilationPlan.byKey(lowered), List.of());

        assertEquals(methods, plan.bodiesOf("app.Pet").stream().map(Ir.CompiledBody::methodName).toList());
        assertEquals(methods, plan.bodiesOf("app.Owner").stream().map(Ir.CompiledBody::methodName).toList());
        assertEquals(lowered.stream().map(Ir.CompiledBody::key).toList(), List.copyOf(plan.bodies().keySet()));
        assertEquals(plan.bodies().keySet().stream().toList(), List.copyOf(plan.withTrace(true).bodies().keySet()));
        assertThrows(UnsupportedOperationException.class, () -> plan.bodies().clear());
    }
}
