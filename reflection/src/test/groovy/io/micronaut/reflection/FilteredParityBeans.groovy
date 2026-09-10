package io.micronaut.reflection

import io.micronaut.core.annotation.Introspected

/**
 * Types declaring the {@link Introspected} members that say which properties are described and whether they
 * carry their annotations, so that a generated description of them can be compared with a reflective one.
 */
class FilteredParityBeans {

    @Introspected(excludes = "password")
    static class Excludes {
        String kept
        String password
        @Hidden("x")
        String secret
    }

    @Introspected(includes = "kept")
    static class Includes {
        String kept
        String password
        @Hidden("x")
        String secret
    }

    @Introspected(excludedAnnotations = Hidden)
    static class ExcludedAnnotations {
        String kept
        String password
        @Hidden("x")
        String secret
    }

    @Introspected(includedAnnotations = Hidden)
    static class IncludedAnnotations {
        String kept
        String password
        @Hidden("x")
        String secret
    }

    @Introspected(annotationMetadata = false)
    static class NoMetadata {
        String kept
        @Hidden("x")
        String secret
    }
}
