package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.AliasFor;
import io.micronaut.context.annotation.ConfigurationReader;
import io.micronaut.context.annotation.Requires;

import javax.inject.Singleton;
import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;

import static java.lang.annotation.RetentionPolicy.RUNTIME;

@Documented
@Retention(RUNTIME)
@Target(ElementType.TYPE)
@Singleton
@ConfigurationReader(prefix = "endpoints")
public @interface TestEndpoint {
    /**
     * @return The ID of the endpoint
     */
    @AliasFor(annotation = ConfigurationReader.class, member = "value")
    @AliasFor(member = "id")
    String value() default "";

    /**
     * @return The ID of the endpoint
     */
    @AliasFor(member = "value")
    @AliasFor(annotation = ConfigurationReader.class, member = "value")
    String id() default "";

}
