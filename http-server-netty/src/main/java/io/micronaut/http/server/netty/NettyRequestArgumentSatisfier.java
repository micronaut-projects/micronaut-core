package io.micronaut.http.server.netty;

import io.micronaut.context.annotation.Primary;
import io.micronaut.core.type.Argument;
import io.micronaut.http.HttpRequest;
import io.micronaut.http.server.binding.RequestArgumentSatisfier;
import io.micronaut.http.server.binding.RequestBinderRegistry;

import javax.inject.Singleton;
import java.util.Optional;

/**
 * A class containing methods to aid in satisfying arguments of a {@link io.micronaut.web.router.Route}.
 *
 * Contains Netty specific extensions - setting the body required for blocking binders.
 *
 * @author Graeme Rocher
 * @author Vladimir Orany
 * @since 1.0
 */
@Primary
@Singleton
public class NettyRequestArgumentSatisfier extends RequestArgumentSatisfier {

    /**
     * Constructor.
     *
     * @param requestBinderRegistry The request argument binder
     */
    public NettyRequestArgumentSatisfier(RequestBinderRegistry requestBinderRegistry) {
        super(requestBinderRegistry);
    }

    @Override
    protected Optional<Object> getValueForArgument(Argument argument, HttpRequest<?> request, boolean satisfyOptionals) {
        if (request instanceof NettyHttpRequest) {
            NettyHttpRequest nettyHttpRequest = (NettyHttpRequest) request;
            nettyHttpRequest.setBodyRequired(true);
        }
        return super.getValueForArgument(argument, request, satisfyOptionals);
    }
}
