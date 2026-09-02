package io.micronaut.reflection;

import java.util.function.Supplier;

/**
 * A bean giving type arguments to its super types.
 */
public class Generic extends GenericBase<String> implements Supplier<Integer> {

    @Override
    public Integer get() {
        return 42;
    }
}
