package pythontest.introduction.reflective;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * A framework annotation of an interface whose implementation the framework builds reflectively, mapped to
 * {@code AllowsReflection} by {@link FakeAiServiceMapper} the way a module integration would map its own.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface FakeAiService {
    String value() default "";
}
