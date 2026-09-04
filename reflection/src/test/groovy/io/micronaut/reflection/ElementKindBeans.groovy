package io.micronaut.reflection

import io.micronaut.context.annotation.Executable
import io.micronaut.core.annotation.Introspected

/**
 * A super class and an interface declaring annotations, so that what a subtype keeps from each is compared.
 */
@InheritedMark("from-super")
@Tag("super-tag")
class InheritedParent {
}

@Tag("interface-tag")
interface Marked {
}

/**
 * Elements of every kind the builders describe: the class itself, a field, a method, its parameters and the
 * constructor, each carrying an annotation with a member of every kind.
 */
@Introspected
@Every(anInt = 11, aString = "type", anEnum = Level.HIGH)
class EveryKindBean extends InheritedParent implements Marked {

    @Every(anInt = 22, strings = ["x", "y"], classes = [Integer, Long])
    String annotated

    @Every
    String defaulted

    EveryKindBean(@Every(anInt = 33) String annotated) {
        this.annotated = annotated
    }

    @Executable
    @Every(anInt = 44, anAnnotation = @Stereo(kind = "on-method"))
    String describe(@Every(anInt = 55) String prefix, String plain) {
        return prefix + annotated
    }
}
