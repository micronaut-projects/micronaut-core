package io.micronaut.reflection

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected

/**
 * A method declared by an interface and by a super class and overridden by the type, each level annotating the
 * return value. The processors merge what every level declares into the metadata of the overriding method, so a
 * reflective description has to merge it too.
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
}
