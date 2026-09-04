package io.micronaut.reflection

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected

/**
 * A bean the processor describes, so that the description it generates can be compared with the one
 * {@link ReflectionBeanIntrospection} builds for the same type.
 */
@Introspected
class ParityBean {

    @Documented1
    String name

    int count

    List<String> tags

    final String constant = "fixed"

    private String secret

    ParityBean() {
    }

    ParityBean(String name, int count) {
        this.name = name
        this.count = count
    }

    String getDerived() {
        return name + count
    }

    @Executable
    String describe(String prefix) {
        return prefix + name
    }
}
