package io.micronaut.docs.config.builder;

import java.util.Optional;

public class CrankShaft {
    final Optional<Double> rodLength;

    CrankShaft(Optional<Double> rodLength) { this.rodLength = rodLength; }

    public Optional<Double> getRodLength() {
        return rodLength;
    }

    static Builder builder() {
        return new Builder();
    }

    static final class Builder {
        private Optional<Double> rodLength = Optional.empty();

        public Builder withRodLength(Double rodLength) {
            this.rodLength = Optional.ofNullable(rodLength);
            return this;
        }

        CrankShaft build() {
            return new CrankShaft(rodLength);
        }
    }
}
