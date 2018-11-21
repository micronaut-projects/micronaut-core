package io.micronaut.security.session;

import io.micronaut.context.annotation.Replaces;
import io.micronaut.context.annotation.Secondary;
import io.micronaut.security.handlers.ForbiddenRejectionUriProvider;
import io.micronaut.security.handlers.RedirectRejectionHandler;
import io.micronaut.security.handlers.UnauthorizedRejectionUriProvider;
import javax.inject.Singleton;

@Secondary
@Singleton
@Replaces(SessionSecurityfilterRejectionHandler.class)
public class SessionSecurityfilterRejectionHandlerReplacement extends RedirectRejectionHandler {

    /**
     * Constructor.
     *
     * @param unauthorizedRejectionUriProvider URI Provider to redirect to if unauthenticated
     * @param forbiddenRejectionUriProvider URI Provider to redirect to if authenticated but not enough authorization level.
     */
    public SessionSecurityfilterRejectionHandlerReplacement(UnauthorizedRejectionUriProvider unauthorizedRejectionUriProvider, ForbiddenRejectionUriProvider forbiddenRejectionUriProvider) {
        super(unauthorizedRejectionUriProvider, forbiddenRejectionUriProvider);
    }
}
