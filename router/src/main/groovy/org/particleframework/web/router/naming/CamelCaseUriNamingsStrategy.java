package org.particleframework.web.router.naming;

import org.particleframework.context.annotation.Primary;
import org.particleframework.web.router.RouteBuilder;

import javax.inject.Singleton;

/**
 * The default {@link org.particleframework.web.router.RouteBuilder.UriNamingStrategy} if none is provided by the application
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Primary
public class CamelCaseUriNamingsStrategy implements RouteBuilder.UriNamingStrategy {
}
