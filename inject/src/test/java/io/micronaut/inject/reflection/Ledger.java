package io.micronaut.inject.reflection;

import java.util.List;

/**
 * A class whose constructor carries no annotation of its own, but whose parameter declares a type-use one:
 * the generated introspection reads neither, so only reflection describes it.
 */
public class Ledger {

    private final List<String> entries;

    public Ledger(List<@Tag("entry") String> entries) {
        this.entries = entries;
    }

    public List<String> getEntries() {
        return entries;
    }
}
