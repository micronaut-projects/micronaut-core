package io.micronaut.reflection

import io.micronaut.core.annotation.AccessorsStyle
import io.micronaut.core.annotation.Introspected

/**
 * A type built through the builder class it names.
 */
@Introspected(builder = @Introspected.IntrospectionBuilder(builderClass = Built.Builder))
class Built {

    private final String name
    private final int count

    private Built(String name, int count) {
        this.name = name
        this.count = count
    }

    String getName() {
        return name
    }

    int getCount() {
        return count
    }

    static class Builder {

        private String name
        private int count

        Builder name(String name) {
            this.name = name
            return this
        }

        Builder count(int count) {
            this.count = count
            return this
        }

        Built build() {
            return new Built(name, count)
        }
    }
}

/**
 * A type built through the builder a static method of it returns, written with a prefix and built by a method of
 * another name.
 */
@Introspected(builder = @Introspected.IntrospectionBuilder(builderMethod = "shape", creatorMethod = "create", accessorStyle = @AccessorsStyle(writePrefixes = "with")))
class Shaped {

    private final String label

    private Shaped(String label) {
        this.label = label
    }

    String getLabel() {
        return label
    }

    static Maker shape() {
        return new Maker()
    }

    static class Maker {

        private String label

        private Maker() {
        }

        Maker withLabel(String label) {
            this.label = label
            return this
        }

        Shaped create() {
            return new Shaped(label)
        }
    }
}
