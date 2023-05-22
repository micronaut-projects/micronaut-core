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

import io.micronaut.inject.ast.ClassElement
import io.micronaut.inject.ast.MethodElement
import io.micronaut.inject.ast.ParameterElement

internal class KotlinEnumConstructorElement(private val classElement: ClassElement) :
    MethodElement {

    override fun getName(): String = "valueOf"

    override fun isProtected() = false

    override fun isPublic() = true

    override fun getNativeType(): Any {
        throw UnsupportedOperationException("No native type backing a kotlin enum static constructor")
    }

    override fun isStatic(): Boolean = true

    override fun getDeclaringType(): ClassElement = classElement

    override fun getReturnType(): ClassElement = classElement

    override fun getParameters(): Array<ParameterElement> {
        return arrayOf(ParameterElement.of(String::class.java, "s"))
    }

    override fun withNewParameters(vararg newParameters: ParameterElement?): MethodElement {
        throw UnsupportedOperationException("Cannot replace parameters of a kotlin enum static constructor")
    }

    override fun withParameters(vararg newParameters: ParameterElement?): MethodElement {
        throw UnsupportedOperationException("Cannot replace parameters of a kotlin enum static constructor")
    }
}
