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
package io.micronaut.docs.aop.binding

// tag::imports[]
import io.micronaut.aop.Around
import io.micronaut.aop.InterceptorBinding
import io.micronaut.context.annotation.NonBinding
import kotlin.annotation.AnnotationRetention.RUNTIME
import kotlin.annotation.AnnotationTarget.CLASS
import kotlin.annotation.AnnotationTarget.FUNCTION
// end::imports[]

// tag::annotation[]
@MustBeDocumented
@Retention(RUNTIME)
@Target(CLASS, FUNCTION)
@JvmRepeatable(Audits::class) // <1>
@Around
@InterceptorBinding(bindMembers = true, matchAllOccurrences = true) // <2>
annotation class Audit(
    val value: String, // <3>
    @get:NonBinding // <4>
    val reason: String = ""
)
// end::annotation[]

@MustBeDocumented
@Retention(RUNTIME)
@Target(CLASS, FUNCTION)
annotation class Audits(vararg val value: Audit)
