package io.micronaut.reflection

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected

/**
 * A method declared by an interface and by a super class and overridden by the type, each level annotating the
 * method with an annotation whose target includes {@code TYPE_USE}, next to a method whose return type is
 * annotated in a nested position and a method whose parameter is annotated. What the processors record for each
 * is what a reflective description has to answer.
 */
interface ReturnDeclarer {

    @Tag("from-interface")
    String place()
}

abstract class AbstractReturnDeclarer {

    @Tag("from-super")
    abstract String describe()
}

@Introspected
class OverriddenReturn extends AbstractReturnDeclarer implements ReturnDeclarer {

    @Executable
    @Override
    @Tag("from-impl")
    String place() {
        return ""
    }

    @Executable
    @Override
    @Tag("from-impl")
    String describe() {
        return ""
    }

    @Executable
    List<@Tag("nested") String> nested() {
        return []
    }

    @Executable
    void take(@Tag("param") String value) {
    }
}
