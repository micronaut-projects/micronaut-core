package example.crema.runtime;

import java.util.function.Predicate;

/**
 * A top-level class rather than a lambda: a lambda adds an InnerClasses attribute to the class that declares it, and
 * Crema in Oracle GraalVM 25.0.3 cannot define such a class at run time.
 */
public class NotEndingWithB implements Predicate<String> {

    @Override
    public boolean test(String name) {
        return !name.endsWith("B");
    }
}
