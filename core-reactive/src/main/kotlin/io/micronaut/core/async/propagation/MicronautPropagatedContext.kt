/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.core.async.propagation

import io.micronaut.core.annotation.Internal
import io.micronaut.core.propagation.PropagatedContext
import kotlinx.coroutines.ThreadContextElement
import kotlin.coroutines.AbstractCoroutineContextElement
import kotlin.coroutines.CoroutineContext

/**
 * Kotlin's coroutine context that propagates Micronaut Propagated Context.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
internal class MicronautPropagatedContext internal constructor(var propagatedContext: PropagatedContext) : ThreadContextElement<PropagatedContext.Scope>, AbstractCoroutineContextElement(Key) {

    companion object Key : CoroutineContext.Key<MicronautPropagatedContext>

    override fun updateThreadContext(context: CoroutineContext): PropagatedContext.Scope {
        return propagatedContext.propagate()
    }

    override fun restoreThreadContext(context: CoroutineContext, oldState: PropagatedContext.Scope) {
        oldState.close()
    }

}
