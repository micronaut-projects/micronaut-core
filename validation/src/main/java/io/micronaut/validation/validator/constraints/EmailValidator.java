package io.micronaut.validation.validator.constraints;

import io.micronaut.core.annotation.AnnotationValue;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Singleton;
import javax.validation.constraints.Email;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static java.util.regex.Pattern.CASE_INSENSITIVE;

/**
 * Provides Email validation. Largely based off the Hibernate validator implementation.
 *
 * @author graemerocher
 * @author Emmanuel Bernard
 * @author Hardy Ferentschik
 * @author Guillaume Smet
 */
@Singleton
public class EmailValidator extends AbstractPatternValidator<Email> {
    private static final int MAX_LOCAL_PART_LENGTH = 64;

    private static final String LOCAL_PART_ATOM = "[a-z0-9!#$%&'*+/=?^_`{|}~\u0080-\uFFFF-]";
    private static final String LOCAL_PART_INSIDE_QUOTES_ATOM = "([a-z0-9!#$%&'*.(),<>\\[\\]:;  @+/=?^_`{|}~\u0080-\uFFFF-]|\\\\\\\\|\\\\\\\")";

    /**
     * Regular expression for the local part of an email address (everything before '@').
     */
    private static final Pattern LOCAL_PART_PATTERN = Pattern.compile(
            "(" + LOCAL_PART_ATOM + "+|\"" + LOCAL_PART_INSIDE_QUOTES_ATOM + "+\")" +
                    "(\\." + "(" + LOCAL_PART_ATOM + "+|\"" + LOCAL_PART_INSIDE_QUOTES_ATOM + "+\")" + ")*", CASE_INSENSITIVE
    );

    @Override
    public boolean isValid(
            @Nullable CharSequence value,
            @Nonnull AnnotationValue<Email> annotationMetadata,
            @Nonnull ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        String stringValue = value.toString();
        int i = stringValue.lastIndexOf('@');

        // no @ character
        if (i < 0) {
            return false;
        }

        String localPart = stringValue.substring(0, i);
        String domainPart = stringValue.substring(i + 1);

        boolean isValid;
        if (!isValidEmailLocalPart(localPart)) {
            isValid = false;
        } else {
            isValid = DomainNameUtil.isValidEmailDomainAddress(domainPart);
        }

        final Pattern pattern = getPattern(annotationMetadata, true);
        if (pattern == null || !isValid) {
            return isValid;
        }

        Matcher m = pattern.matcher(value);
        return m.matches();
    }

    private boolean isValidEmailLocalPart(String localPart) {
        if (localPart.length() > MAX_LOCAL_PART_LENGTH) {
            return false;
        }
        Matcher matcher = LOCAL_PART_PATTERN.matcher(localPart);
        return matcher.matches();
    }
}
