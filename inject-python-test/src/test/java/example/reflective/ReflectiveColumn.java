package example.reflective;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A runtime annotation nested in {@link ReflectiveMapping#columns()}, which may only be placed on fields.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ReflectiveColumn {
    String value();

    int length() default 0;

    long precision() default 0L;

    boolean nullable() default true;
}
