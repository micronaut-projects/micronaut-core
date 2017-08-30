package org.particleframework.http.server.netty;

import org.particleframework.core.annotation.Internal;
import org.particleframework.http.binding.binders.request.BodyArgumentBinder;
import org.particleframework.inject.Argument;
import org.particleframework.web.router.RouteMatch;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * A context object used to store information about the current state of the request
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class NettyHttpRequestContext {

    private final NettyHttpRequest request;
    private final NettyHttpResponseTransmitter responseTransmitter;
    private RouteMatch matchedRoute;
    private Map<String, Object> routeArguments;
    private List<UnboundBodyArgument> unboundBodyArguments = new ArrayList<>();

    public NettyHttpRequestContext(NettyHttpRequest request) {
        this.request = request;
        this.responseTransmitter = new NettyHttpResponseTransmitter();
    }

    public RouteMatch getMatchedRoute() {
        return matchedRoute;
    }

    /**
     * @param matchedRoute Set the matched rout
     */
    public void setMatchedRoute(RouteMatch matchedRoute) {
        this.matchedRoute = matchedRoute;
    }

    /**
     * @param routeArguments The current resolved arguments for the route
     */
    public void setRouteArguments(Map<String, Object> routeArguments) {
        this.routeArguments = routeArguments;
    }

    /**
     * @return The route arguments
     */
    public Map<String, Object> getRouteArguments() {
        return routeArguments;
    }

    /**
     * @return The response transmitter
     */
    public NettyHttpResponseTransmitter getResponseTransmitter() {
        return responseTransmitter;
    }

    public NettyHttpRequest getRequest() {
        return request;
    }

    public void addBodyArgument(Argument argument, BodyArgumentBinder bodyArgumentBinder) {
        unboundBodyArguments.add(new UnboundBodyArgument(argument, bodyArgumentBinder));
    }

    public List<UnboundBodyArgument> getUnboundBodyArguments() {
        return Collections.unmodifiableList(unboundBodyArguments);
    }

    class UnboundBodyArgument {
        final Argument argument;
        final BodyArgumentBinder argumentBinder;

        public UnboundBodyArgument(Argument argument, BodyArgumentBinder argumentBinder) {
            this.argument = argument;
            this.argumentBinder = argumentBinder;
        }
    }
}
