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
package io.micronaut.kotlin.processing.annotation

import com.google.devtools.ksp.symbol.KSAnnotated
import com.google.devtools.ksp.symbol.KSAnnotation
import com.google.devtools.ksp.symbol.KSNode
import com.google.devtools.ksp.symbol.KSVisitor
import com.google.devtools.ksp.symbol.Location
import com.google.devtools.ksp.symbol.Origin

/**
 * A simple annotations container.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
internal class KotlinAnnotations(override val annotations: Sequence<KSAnnotation>) : KSAnnotated {

    override val location: Location
        get() {
            throw notSupportedMethod()
        }
    override val origin: Origin
        get() {
            throw notSupportedMethod()
        }
    override val parent: KSNode?
        get() {
            throw notSupportedMethod()
        }

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        throw notSupportedMethod()
    }

    companion object {
        private fun notSupportedMethod(): IllegalStateException {
            return IllegalStateException("Not supported method")
        }
    }
}
