package io.micronaut.aop;

/**
 * Enum representing different interceptors kinds.
 *
 * @author graemerocher
 * @since 2.4.0
 */
public enum InterceptorKind {
    /**
     * Around advice interception.
     *
     * @see Around
     */
    AROUND,
    /**
     * Introduction advice interception.
     *
     * @see Introduction
     */
    INTRODUCTION
}
