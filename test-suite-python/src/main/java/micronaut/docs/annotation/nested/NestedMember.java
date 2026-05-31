package micronaut.docs.annotation.nested;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;

@Retention(RetentionPolicy.RUNTIME)
@Target(ANNOTATION_TYPE)
public @interface NestedMember {
    String value();
}
