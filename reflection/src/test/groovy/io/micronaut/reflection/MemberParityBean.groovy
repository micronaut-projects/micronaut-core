package io.micronaut.reflection

import io.micronaut.core.annotation.Introspected

/**
 * A bean the processor describes down to the members of its properties, so that the members the generated
 * introspection carries can be compared with the ones {@link ReflectionBeanIntrospection} reads.
 *
 * <p>Every property declares its field and its accessors itself rather than letting Groovy synthesise them,
 * so that an annotation can be written on one member alone.</p>
 */
@Introspected(members = true)
class MemberParityBean extends MemberParityBase {

    @Tag("field")
    private String value

    @Tag("read-only")
    private final int size = 3

    private List<String> tags = []

    private String writeOnly

    MemberParityBean() {
    }

    @Tag("getter")
    String getValue() {
        return value
    }

    @Tag("setter")
    void setValue(String value) {
        this.value = value
    }

    int getSize() {
        return size
    }

    List<String> getTags() {
        return tags
    }

    void setTags(List<String> tags) {
        this.tags = tags
    }

    void setWriteOnly(String writeOnly) {
        this.writeOnly = writeOnly
    }

    String writeOnlyValue() {
        return writeOnly
    }
}
