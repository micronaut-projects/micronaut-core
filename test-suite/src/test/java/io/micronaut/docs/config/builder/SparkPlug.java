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
package io.micronaut.docs.config.builder;

import java.util.Optional;

public class SparkPlug {
    final Optional<String> name;
    final Optional<String> type;
    final Optional<String> companyName;

    public SparkPlug(
            Optional<String> name,
            Optional<String> type,
            Optional<String> companyName
    ) {
        this.name = name;
        this.type = type;
        this.companyName = companyName;
    }
    public Optional<String> getName() { return name; }
    public Optional<String> getType() { return type; }
    public Optional<String> getCompanyName() { return companyName; }

    static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return new StringBuilder()
                .append(type.orElse(""))
                .append('(')
                .append(companyName.orElse(""))
                .append(' ')
                .append(name.orElse(""))
                .append(')').toString();
    }

    static final class Builder {
        private Optional<String> name = Optional.ofNullable("4504 PK20TT");
        private Optional<String> type = Optional.ofNullable("Platinum TT");
        private Optional<String> companyName = Optional.ofNullable("Denso");

        public Builder withName(String name) {
            this.name = Optional.ofNullable(name);
            return this;
        }

        public Builder withType(String type) {
            this.type = Optional.ofNullable(type);
            return this;
        }

        public Builder withCompanyName(String companyName) {
            this.companyName = Optional.ofNullable(companyName);
            return this;
        }

        SparkPlug build() {
            return new SparkPlug(name, type, companyName);
        }
    }
}
