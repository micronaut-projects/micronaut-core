package org.particleframework.http.server.netty;

import io.netty.channel.ChannelHandlerContext;
import io.netty.util.concurrent.Future;
import io.netty.util.concurrent.GenericFutureListener;
import org.particleframework.core.annotation.Internal;
import org.particleframework.core.convert.ConversionService;
import org.particleframework.http.binding.binders.request.BodyArgumentBinder;
import org.particleframework.http.server.HttpServerConfiguration;
import org.particleframework.inject.Argument;
import org.particleframework.web.router.RouteMatch;

import java.util.*;
import java.util.concurrent.Executor;

/**
 * A context object used to store information about the current state of the request
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
class NettyHttpRequestContext {

    private final ChannelHandlerContext context;
    private final NettyHttpRequest request;
    private final NettyHttpResponseTransmitter responseTransmitter;
    private RouteMatch matchedRoute;
    private Map<String, Object> routeArguments;
    private List<UnboundBodyArgument> unboundBodyArguments = new ArrayList<>();


    public NettyHttpRequestContext(ChannelHandlerContext context, NettyHttpRequest request, HttpServerConfiguration serverConfiguration, ConversionService conversionService) {
        this.context = context;
        this.request = request;
        this.responseTransmitter = new NettyHttpResponseTransmitter(serverConfiguration, conversionService);
    }

    public void processRequestBody() {
        // TODO: Allow customization of thread pool to execute actions
        Executor executor = context.channel().eventLoop();
        executor.execute(() -> {
            List<UnboundBodyArgument> unboundBodyArguments = getUnboundBodyArguments();
            Map<String, Object> resolvedArguments = getRouteArguments();

            for (UnboundBodyArgument unboundBodyArgument : unboundBodyArguments) {
                Argument argument = unboundBodyArgument.argument;
                BodyArgumentBinder argumentBinder = unboundBodyArgument.argumentBinder;
                Optional bound = argumentBinder.bind(argument, getRequest());
                if (bound.isPresent()) {
                    resolvedArguments.put(argument.getName(), bound.get());
                } else {
                    getResponseTransmitter().sendBadRequest(context);
                    return;
                }
            }

            RouteMatch route = getMatchedRoute();
            Object result = route.execute(resolvedArguments);
            if(result != null) {
                context.writeAndFlush(result);
            }
            else {
                context.flush();
            }
        });
    }

    public ChannelHandlerContext getContext() {
        return context;
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
