package io.micronaut.core.convert;

public enum Status {
    OK,
    N_OR_A {
        @Override
        public String toString() {
            return "N/A";
        }
    }
}
