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
package io.micronaut.context.python;

import org.graalvm.polyglot.Context;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * A pooled bean's instances are released when their context closes.
 *
 * <p>The references run both ways, which is why this needs a test rather than an argument: a stored
 * instance reaches its holder through the host-object back-reference, and a holder reaches every
 * context it has served because a {@code Value} holds its own {@code Context}. So a weak key never
 * fires whichever way round the map is put, and two earlier versions of this class leaked in
 * opposite directions -- one pinning every context a long-lived bean had served, the other
 * accumulating an entry per context for prototype beans. Neither was visible without looking.
 */
class PythonPooledInstanceLifetimeTest {

    @Test
    void closingAContextReleasesItsInstance() {
        try (Context first = Context.create(); Context second = Context.create()) {
            PythonPooledInstance holder = PythonPooledInstance.producedBy(
                "Test", context -> context.asValue("instance")
            );

            holder.in(first);
            holder.in(second);
            assertEquals(2, holder.contextCount(), "each context should have its own instance");

            PythonContextRegistry.unregisterContext(first);
            assertEquals(1, holder.contextCount(), "the closed context's instance was not released");

            PythonContextRegistry.unregisterContext(second);
            assertEquals(0, holder.contextCount(), "the second context's instance was not released");
        }
    }

    @Test
    void anInstanceIsResolvedOncePerContext() {
        try (Context context = Context.create()) {
            PythonPooledInstance holder = PythonPooledInstance.producedBy(
                "Test", ctx -> ctx.asValue("instance")
            );

            assertEquals(holder.in(context), holder.in(context), "the instance should be cached");
            assertEquals(1, holder.contextCount());

            PythonContextRegistry.unregisterContext(context);
        }
    }
}
