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

import groovy.transform.TupleConstructor

@TupleConstructor
class SparkPlug {
    final Optional<String> name
    final Optional<String> type
    final Optional<String> companyName

    static Builder builder() {
        return new Builder()
    }

    @Override
    public String toString() {
        return "${type.orElse("")}(${companyName.orElse("")} ${name.orElse("")})"
    }

    static final class Builder {
        private Optional<String> name = Optional.ofNullable("4504 PK20TT")
        private Optional<String> type = Optional.ofNullable("Platinum TT")
        private Optional<String> companyName = Optional.ofNullable("Denso")

        Builder withName(String name) {
            this.name = Optional.ofNullable(name)
            return this
        }

        Builder withType(String type) {
            this.type = Optional.ofNullable(type)
            return this
        }

        Builder withCompanyName(String companyName) {
            this.companyName = Optional.ofNullable(companyName)
            return this
        }

        SparkPlug build() {
            new SparkPlug(name, type, companyName)
        }
    }
}
