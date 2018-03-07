package io.micronaut.web.router.naming;

import io.micronaut.context.annotation.Primary;
import io.micronaut.context.annotation.Primary;
import io.micronaut.web.router.RouteBuilder;

import javax.inject.Singleton;

/**
 * The default {@link io.micronaut.web.router.RouteBuilder.UriNamingStrategy} if none is provided by the application
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Primary
public class CamelCaseUriNamingsStrategy implements RouteBuilder.UriNamingStrategy {
}
