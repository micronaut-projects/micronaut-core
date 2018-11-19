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
