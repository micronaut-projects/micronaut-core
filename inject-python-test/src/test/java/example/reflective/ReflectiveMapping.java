package example.reflective;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A runtime annotation in the style of JPA {@code @Entity}/{@code @Column}: read reflectively from the
 * generated Java class, its fields and its accessors.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.FIELD, ElementType.METHOD})
public @interface ReflectiveMapping {
    String name() default "";

    Fetch fetch() default Fetch.EAGER;

    Class<?> target() default Object.class;

    ReflectiveColumn[] columns() default {};

    enum Fetch {
        EAGER, LAZY
    }
}
