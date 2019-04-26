package io.micronaut.docs.config.builder;

import java.util.Optional;

class SparkPlug {
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

        Builder withName(String name) {
            this.name = Optional.ofNullable(name);
            return this;
        }

        Builder withType(String type) {
            this.type = Optional.ofNullable(type);
            return this;
        }

        Builder withCompanyName(String companyName) {
            this.companyName = Optional.ofNullable(companyName);
            return this;
        }

        SparkPlug build() {
            return new SparkPlug(name, type, companyName);
        }
    }
}
