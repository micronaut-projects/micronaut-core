package io.micronaut.reflection;

/**
 * A class carrying the annotation that is not public, so that the compiler's own instance of it can be read.
 */
@Restricted(level = 7, name = "written")
public class RestrictedHolder {
}
