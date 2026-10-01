package example.reflective;

import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;

/**
 * An annotation that is not retained at runtime and therefore never needed on the generated Java class.
 */
@Retention(RetentionPolicy.CLASS)
public @interface ClassRetainedMarker {
}
