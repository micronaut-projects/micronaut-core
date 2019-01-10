package io.micronaut.http.annotation;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * This lets you declare several attributes for a client class and have them always included.
 * Example usage:
 * <pre><code>
 * {@literal @}Attributes({
 *     {@literal @}RequestAttribute(name="api-key",value="my-key-value"),
 *     {@literal @}RequestAttribute(name="api-name",value="my-api-name")
 * })
 * </pre></code>
 *
 * @author Ahmed Lafta
 * @since 1.0
 */
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface Attributes {

    /**
     * This allows you to have multiple @RequestAttribute's set at the class level with Java and Groovy.
     * Example usage:
     * <pre><code>
     * {@literal @}Attributes({
     *     {@literal @}RequestAttribute(name="api-key",value="my-key-value"),
     *     {@literal @}RequestAttribute(name="api-name",value="my-api-name")
     * })
     * </pre></code>
     * @return The attributes
     */
    RequestAttribute[] value() default {};
}
