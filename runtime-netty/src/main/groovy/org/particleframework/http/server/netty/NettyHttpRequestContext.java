package org.particleframework.http.server.netty;

import io.netty.channel.ChannelFutureListener;
import io.netty.channel.ChannelHandlerContext;
import org.particleframework.core.annotation.Internal;
import org.particleframework.http.HttpResponse;
import org.particleframework.http.binding.binders.request.BodyArgumentBinder;
import org.particleframework.inject.Argument;
import org.particleframework.web.router.RouteMatch;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger LOG = LoggerFactory.getLogger(NettyHttpServer.class);

    private final ChannelHandlerContext context;
    private final NettyHttpRequest request;
    private RouteMatch matchedRoute;
    private Map<String, Object> routeArguments;
    private List<UnboundBodyArgument> unboundBodyArguments = new ArrayList<>();


    public NettyHttpRequestContext(ChannelHandlerContext context, NettyHttpRequest request) {
        this.context = context;
        this.request = request;
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
                    context.writeAndFlush(HttpResponse.badRequest())
                           .addListener(ChannelFutureListener.CLOSE);
                    return;
                }
            }

            RouteMatch route = getMatchedRoute();
            try {
                Object result = route.execute(resolvedArguments);
                if(result != null) {
                    context.writeAndFlush(result)
                            .addListener(future -> {
                                if(!future.isSuccess()) {
                                    Throwable cause = future.cause();
                                    if(LOG.isErrorEnabled()) {
                                        LOG.error("Error encoding response: " + cause.getMessage(), cause);
                                    }
                                    if(context.channel().isWritable()) {
                                        context.pipeline().fireExceptionCaught(cause);
                                    }
                                }
                            });
                }
                else {
                    context.flush();
                }
            } catch (Throwable e) {
                context.pipeline().fireExceptionCaught(e);
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
