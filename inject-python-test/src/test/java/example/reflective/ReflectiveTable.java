package example.reflective;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A runtime annotation whose metadata {@link ReflectiveTableTransformer} extends with a member the type does
 * not declare, the way annotation mappers of data frameworks do.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface ReflectiveTable {
    String name();

    String catalog() default "";
}
