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
package io.micronaut.annotation.processing.test.support

import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty

@Suppress("MemberVisibilityCanBePrivate")
internal class DefaultPropertyDelegate<R,T>(private val createDefault: () -> T) : ReadWriteProperty<R, T> {
    val hasDefaultValue
        @Synchronized get() = (value == DEFAULT)

    private var value: Any? = DEFAULT
    val defaultValue by lazy { createDefault() }

    @Synchronized
    override operator fun getValue(thisRef: R, property: KProperty<*>): T {
        @Suppress("UNCHECKED_CAST")
        return if(hasDefaultValue)
            defaultValue
        else
            value as T
    }

    @Synchronized
    override operator fun setValue(thisRef: R, property: KProperty<*>, value: T) {
        this.value = value
    }

    companion object {
        private object DEFAULT
    }
}

internal fun <R,T> default(createDefault: () -> T) = DefaultPropertyDelegate<R,T>(createDefault)
