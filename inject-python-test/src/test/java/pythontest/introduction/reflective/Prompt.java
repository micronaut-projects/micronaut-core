package pythontest.introduction.reflective;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A method annotation read with {@link java.lang.reflect.Method#getAnnotation(Class)}.
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Prompt {
    String value();

    Class<?>[] tools() default {};

    Priority priority() default Priority.NORMAL;

    enum Priority { LOW, NORMAL, HIGH }
}
