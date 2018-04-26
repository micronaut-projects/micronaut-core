package io.micronaut.security.config;

import io.micronaut.core.util.Toggleable;

import java.util.List;

/**
 * Defines security configuration properties.
 *
 * @author Sergio del Amo
 * @since 1.0
 */
public interface SecurityConfiguration extends Toggleable {

    /**
     * The name in the claims object that stores the granted roles.
     *
     * @return The roles claim name, e.g. roles.
     */
    String getRolesName();

    /**
     * ipPatterns getter.
     * @return a list of IP Regex patterns. e.g. [192.168.1.*]
     */
    List<String> getIpPatterns();

    /**
     * interceptUrlMap getter.
     * @return a list of {@link InterceptUrlMapPattern}
     */
    List<InterceptUrlMapPattern> getInterceptUrlMap();


    /**
     * securityConfigType getter.
     * @return an enum containing the type of security configuration
     */
    SecurityConfigType getSecurityConfigType();
}
