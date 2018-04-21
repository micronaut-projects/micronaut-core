package io.micronaut.security.rules;

/**
 * The result of a security rule check.
 *
 * @author James Kleeh
 * @since 1.0
 */
public enum SecurityRuleResult {

    /**
     * The rule explicitly allows this request.
     */
    ALLOWED,

    /**
     * The rule explicitly rejects this request.
     */
    REJECTED,

    /**
     * The rule has no information to make the determination.
     */
    UNKNOWN
}
