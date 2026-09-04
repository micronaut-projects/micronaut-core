package io.micronaut.reflection;

import io.micronaut.context.annotation.NonBinding;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * The shapes a nested annotation reaches the values of an annotation by: a member holding one, and a member
 * holding an array of them. The policies of the module - a member equal to its default left out, the defaults
 * of the type registered, the non binding members recorded, the customizers run - are the policies of an
 * annotation at any level, and the shared conversion of the core API applies none of them.
 */
public final class AnnNesting {

    private AnnNesting() {
    }

    /**
     * A nested annotation with a member that has a default and a member that is not binding.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Inner {

        String value() default "inner";

        @NonBinding
        String comment() default "";
    }

    /**
     * A nested annotation one holder alone reaches, so that the defaults of its type are registered by the
     * conversion of that holder and by nothing else.
     */
    @Retention(RetentionPolicy.RUNTIME)
    public @interface Registered {

        String value() default "none";

        int level() default 7;
    }

    /**
     * An annotation holding annotations: a member of a nested type, a member of an array of them, and a member
     * of the type the customizer of this module supports. The member holding a nested annotation has no default,
     * so it is never left out of the values.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target({ElementType.TYPE, ElementType.FIELD})
    public @interface Outer {

        Inner nested();

        Inner[] several() default {};

        Registered registered() default @Registered;

        Customized customized() default @Customized;
    }

    /**
     * A holder whose nested annotations leave a member at its default, once alone and once in an array.
     */
    @Outer(nested = @Inner("written"), several = {@Inner("first"), @Inner(value = "second", comment = "said")})
    public static class Holder {
    }

    /**
     * A holder of the nested annotation whose defaults nothing else registers.
     */
    @Outer(nested = @Inner, registered = @Registered("written"))
    public static class RegisteredHolder {
    }

    /**
     * A holder of the annotation the customizer supports, nested rather than on the element.
     */
    @Outer(nested = @Inner, customized = @Customized("inner"))
    public static class CustomizedHolder {
    }
}
