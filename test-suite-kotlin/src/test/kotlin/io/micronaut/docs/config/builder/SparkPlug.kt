/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.docs.config.builder

internal data class SparkPlug(
        val name: String?,
        val type: String?,
        val companyName: String?
) {
    override fun toString(): String {
        return "${type ?: ""}(${companyName ?: ""} ${name ?: ""})"
    }

    companion object {
        fun builder(): Builder {
            return Builder()
        }
    }

    data class Builder(
            var name: String? = "4504 PK20TT",
            var type: String? = "Platinum TT",
            var companyName: String? = "Denso"
    ) {
        fun withName(name: String?): Builder {
            this.name = name
            return this
        }

        fun withType(type: String?): Builder {
            this.type = type
            return this
        }

        fun withCompany(companyName: String?): Builder {
            this.companyName = companyName
            return this
        }

        fun build(): SparkPlug {
            return SparkPlug(name, type, companyName)
        }
    }

}