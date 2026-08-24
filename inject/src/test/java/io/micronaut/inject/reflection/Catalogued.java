package io.micronaut.inject.reflection;

import java.util.List;

/**
 * An interface declaring the type-use annotations the implementation drops: a generated introspection reads
 * the field, so only reflection over the hierarchy sees them.
 */
public interface Catalogued {

    List<@Tag("elem") String> getEntries();
}
