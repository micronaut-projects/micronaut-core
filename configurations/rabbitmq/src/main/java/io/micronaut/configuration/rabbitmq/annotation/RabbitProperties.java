package io.micronaut.configuration.rabbitmq.annotation;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE})
public @interface RabbitProperties {

    /***
     * This allows you to have multiple @Property annotations set at the class level with Java and Groovy.
     * Example usage:
     * <pre><code>
     *{@literal @}Properties({
     *     {@literal @}Property(name="userId",value="jsmith"),
     *     {@literal @}Property(name="correlationId",value="123")
     * })
     * </code></pre>
     * @return The properties
     */
    RabbitProperty[] value() default {};
}
