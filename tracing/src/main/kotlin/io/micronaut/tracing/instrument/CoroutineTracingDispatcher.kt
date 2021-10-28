/*
 * Copyright 2017-2021 original authors
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
package io.micronaut.tracing.instrument

import io.micronaut.context.annotation.Context
import io.micronaut.context.annotation.Factory
import io.micronaut.context.annotation.Requires
import io.micronaut.http.bind.binders.ContinuationArgumentBinder
import io.micronaut.scheduling.instrument.Instrumentation
import io.micronaut.scheduling.instrument.InvocationInstrumenter
import io.micronaut.tracing.instrument.util.TracingInvocationInstrumenterFactory
import kotlinx.coroutines.ThreadContextElement
import java.util.function.Supplier
import kotlin.coroutines.CoroutineContext

@Factory
@Context
@Requires(classes = [ThreadContextElement::class])
class CoroutineTracingDispatcherSupplierFactory {
    @Context
    fun coroutineTracingDispatcherSupplier(tiifs: List<TracingInvocationInstrumenterFactory>): Supplier<CoroutineTracingDispatcher> =
        Supplier<CoroutineTracingDispatcher> {
            CoroutineTracingDispatcher(tiifs.mapNotNull(TracingInvocationInstrumenterFactory::newTracingInvocationInstrumenter))
        }
        .also { ContinuationArgumentBinder.tracingCoroutineContextFactory = it }
}

internal class CoroutineTracingDispatcherContextKey : CoroutineContext.Key<CoroutineTracingDispatcher>

class CoroutineTracingDispatcher(
    private val invocationInstrumenters: List<InvocationInstrumenter>
): ThreadContextElement<List<Instrumentation>> {
    override val key: CoroutineContext.Key<*>
        get() = CoroutineTracingDispatcherContextKey()

    override fun restoreThreadContext(context: CoroutineContext, oldState: List<Instrumentation>) =
        oldState.forEach(Instrumentation::close)

    override fun updateThreadContext(context: CoroutineContext): List<Instrumentation> =
        invocationInstrumenters.map(InvocationInstrumenter::newInstrumentation)
}
