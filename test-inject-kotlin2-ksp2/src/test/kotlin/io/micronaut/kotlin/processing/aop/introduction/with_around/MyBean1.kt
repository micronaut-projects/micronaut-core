/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.kotlin.processing.aop.introduction.with_around

@ProxyIntroduction
@ProxyAround
open class MyBean1 {

    private var id: Long? = null
    private var name: String? = null

    open fun getId(): Long? = id

    open fun setId(id: Long?) {
        this.id = id
    }

    open fun getName(): String? = name

    open fun setId(name: String?) {
        this.name = name
    }
}
