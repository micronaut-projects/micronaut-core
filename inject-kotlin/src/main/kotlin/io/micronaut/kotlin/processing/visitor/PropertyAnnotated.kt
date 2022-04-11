/*
 * Copyright 2017-2022 original authors
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
package io.micronaut.kotlin.processing.visitor

import com.google.devtools.ksp.symbol.*

class PropertyAnnotated(private val propertyDeclaration: KSPropertyDeclaration) : KSAnnotated {
    override val annotations: Sequence<KSAnnotation>
        get() = propertyDeclaration.annotations.filter { it.useSiteTarget == AnnotationUseSiteTarget.FIELD }
    override val location: Location
        get() = propertyDeclaration.location
    override val origin: Origin
        get() = propertyDeclaration.origin
    override val parent: KSNode?
        get() = propertyDeclaration.parent

    override fun <D, R> accept(visitor: KSVisitor<D, R>, data: D): R {
        return visitor.visitAnnotated(this, data)
    }
}
