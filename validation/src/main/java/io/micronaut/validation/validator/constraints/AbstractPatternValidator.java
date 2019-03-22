package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import javax.annotation.Nonnull;
import javax.validation.ValidationException;
import javax.validation.constraints.Pattern;
import java.lang.annotation.Annotation;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.PatternSyntaxException;

/**
 * Abstract pattern validator.
 *
 * @param <A> The annotation type.
 *
 * @author graemerocher
 * @since 1.2
 */
abstract class AbstractPatternValidator<A extends Annotation> implements ConstraintValidator<A, CharSequence> {
    private static final Pattern.Flag[] ZERO_FLAGS = new Pattern.Flag[0];
    private static final Map<PatternKey, java.util.regex.Pattern> COMPUTED_PATTERNS = new ConcurrentHashMap<>(10);

    /**
     * Gets the pattern for the given annotation metadata.
     * @param annotationMetadata The metadata
     * @param isOptional Whether the pattern is required to be returned
     * @return The pattern
     */
    java.util.regex.Pattern getPattern(
            @Nonnull AnnotationValue<?> annotationMetadata,
            boolean isOptional) {
        final Optional<String> regexp = annotationMetadata.get("regexp", String.class);
        final String pattern;

        if (isOptional) {
            pattern = regexp.orElse(".*");
        } else {
            pattern = regexp
                    .orElseThrow(() -> new ValidationException("No pattern specified"));
        }

        final Pattern.Flag[] flags = annotationMetadata.get("flags", Pattern.Flag[].class).orElse(ZERO_FLAGS);
        if (isOptional && pattern.equals(".*") && flags.length == 0) {
            return null;
        }

        int computedFlag = 0;
        for (Pattern.Flag flag : flags) {
            computedFlag = computedFlag | flag.getValue();
        }

        final PatternKey key = new PatternKey(pattern, computedFlag);
        java.util.regex.Pattern regex = COMPUTED_PATTERNS.get(key);
        if (regex == null) {
            try {
                if (computedFlag != 0) {
                    regex = java.util.regex.Pattern.compile(pattern, computedFlag);
                } else {
                    regex = java.util.regex.Pattern.compile(pattern);
                }
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException("Invalid regular expression", e);
            }
            COMPUTED_PATTERNS.put(key, regex);
        }
        return regex;
    }

    /**
     * Key used to cache patterns.
     */
    private static final class PatternKey {
        final String pattern;
        final int flags;

        PatternKey(String pattern, int flags) {
            this.pattern = pattern;
            this.flags = flags;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            PatternKey that = (PatternKey) o;
            return flags == that.flags &&
                    pattern.equals(that.pattern);
        }

        @Override
        public int hashCode() {
            return Objects.hash(pattern, flags);
        }
    }
}
