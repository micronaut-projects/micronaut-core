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
package io.micronaut.kotlin.processing.beans.executable

import io.micronaut.context.annotation.Executable
import jakarta.inject.Singleton

/**
 * The default arguments are declared by the interface, so the synthetic `$default` method applying
 * them belongs to the interface and not to the implementation. This module compiles with
 * `-Xjvm-default=all`, which puts that method on the interface itself rather than on a
 * `DefaultImpls` class. See https://github.com/micronaut-projects/micronaut-core/issues/12638.
 */
interface DefaultArgumentsApi {

    fun send(one: String = "default", two: String? = null): String
}

@Singleton
@Executable
open class DefaultArgumentsController : DefaultArgumentsApi {

    override fun send(one: String, two: String?): String = "$one-$two"
}
