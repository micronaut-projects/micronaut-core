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
package io.micronaut.kotlin.processing.beans.configproperties.inheritance

import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.annotation.EachProperty
import io.micronaut.context.annotation.Parameter
import io.micronaut.core.order.Ordered

@EachProperty(value = "teams", list = true)
class ParentArrayEachProps internal constructor(@Parameter private val index: Int) : Ordered {
    var wins: Int? = null
    var manager: ManagerProps? = null

    override fun getOrder(): Int {
        return index
    }

    @ConfigurationProperties("manager")
    class ManagerProps internal constructor(@Parameter private val index: Int) : Ordered {
        var age: Int? = null

        override fun getOrder(): Int {
            return index
        }
    }
}
