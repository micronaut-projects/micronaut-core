package io.micronaut.health;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.runtime.ApplicationConfiguration;
import io.micronaut.runtime.server.EmbeddedServer;

import java.lang.annotation.*;

/**
 * Annotation that expresses the requirements for enabling the heart beat.
 *
 * @author graemerocher
 * @since 1.1
 */
@Requires(property = ApplicationConfiguration.APPLICATION_NAME)
@Requires(condition = HeartbeatDiscoveryClientCondition.class)
@Requires(beans = EmbeddedServer.class)
@Requires(notEnv = {Environment.ANDROID, Environment.FUNCTION})
@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.PACKAGE, ElementType.TYPE, ElementType.ANNOTATION_TYPE, ElementType.METHOD})
public @interface HeartbeatEnabled {
}
