package io.micronaut.security.session

import io.micronaut.context.annotation.Replaces
import io.micronaut.context.annotation.Requires
import javax.inject.Singleton

@Requires(property = 'spec.name', value = "RejectionHandlerResolutionSpec")
@Singleton
@Replaces(SessionSecurityfilterRejectionHandler)
class ExtendedSessionSecurityfilterRejectionHandler extends SessionSecurityfilterRejectionHandler {

    /**
     * Constructor.
     *
     * @param securitySessionConfiguration Security Session Configuration session store
     */
    ExtendedSessionSecurityfilterRejectionHandler(SecuritySessionConfiguration securitySessionConfiguration) {
        super(securitySessionConfiguration)
    }
}
