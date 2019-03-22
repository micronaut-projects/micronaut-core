package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.ValidationException;
import javax.validation.constraints.Pattern;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.PatternSyntaxException;

/**
 * Validator for the {@link Pattern} annotation.
 *
 * @author graemerocher
 * @since 1.2
 */
@Singleton
public class PatternValidator implements ConstraintValidator<Pattern, CharSequence> {

    private static final Pattern.Flag[] ZERO_FLAGS = new Pattern.Flag[0];
    private static final Map<PatternKey, java.util.regex.Pattern> COMPUTED_PATTERNS = new ConcurrentHashMap<>(10);

    @Nonnull
    @Override
    public final Class<Pattern> getAnnotationType() {
        return Pattern.class;
    }

    @Override
    public boolean isValid(@Nullable CharSequence value, @Nonnull AnnotationValue<Pattern> annotationMetadata, @Nonnull ConstraintValidatorContext context) {
        if (value == null) {
            // null valid according to spec
            return true;
        }
        final String pattern = annotationMetadata.get("regexp", String.class)
                .orElseThrow(() -> new ValidationException("No pattern specified"));
        final Pattern.Flag[] flags = annotationMetadata.get("flags", Pattern.Flag[].class).orElse(ZERO_FLAGS);

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
        return regex.matcher(value).matches();
    }

    /**
     * Key used to cache patterns.
     */
    private final class PatternKey {
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
