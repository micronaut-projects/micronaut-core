package io.micronaut.context.python;

import io.micronaut.core.annotation.NonNull;
import org.graalvm.polyglot.Value;

/**
 * A type that is coercible to a Truffle Value.
 */
public interface ValueCoercible {
    /**
     * Converts the type to a Truffle value.
     * @return The value
     */
    @NonNull Value asPolyglotValue();
}
