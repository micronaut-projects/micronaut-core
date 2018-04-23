package io.micronaut.http.annotation;


import io.micronaut.core.bind.annotation.Bindable;

import java.lang.annotation.*;

/**
 * This lets you declare several headers for a client class and have them always included
 * Example usage:
 * @Headers({
 *         @Header(name="Content-type",value="application/octet-stream"),
 *         @Header(name="Content-length",value="2048")
 * })
 * @author rvanderwerf
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
@Bindable
public @interface Headers {
    Header[] value() default {};
}
