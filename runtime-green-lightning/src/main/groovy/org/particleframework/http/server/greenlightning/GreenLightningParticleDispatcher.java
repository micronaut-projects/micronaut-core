package org.particleframework.http.server.greenlightning;

import com.ociweb.gl.api.*;
import com.ociweb.pronghorn.network.config.HTTPContentTypeDefaults;
import org.particleframework.context.ApplicationContext;
import org.particleframework.http.HttpMethod;
import org.particleframework.web.router.RouteMatch;
import org.particleframework.web.router.Router;

import java.util.Optional;

class GreenLightningParticleDispatcher implements RestListener {

    protected final GreenCommandChannel greenCommandChannel;
    protected final ApplicationContext applicationContext;

    public GreenLightningParticleDispatcher(final GreenRuntime runtime, final ApplicationContext applicationContext) {
        greenCommandChannel = runtime.newCommandChannel(NET_RESPONDER);
        this.applicationContext = applicationContext;
    }

    @Override
    public boolean restRequest(final HTTPRequestReader request) {
        final Appendable routePath = new StringBuilder("/");
        request.getText("path".getBytes(), routePath);

        final Optional<Router> routerBean = applicationContext.findBean(Router.class);

        final Optional<RouteMatch> routeMatch = routerBean.flatMap((router) -> {
                    return router.find(HttpMethod.GET, routePath.toString())
//                            .filter((match) -> match.test( ?? ))
                            .findFirst();
                }
        );

        routeMatch.ifPresent((RouteMatch route) -> {
            final Object result = route.execute();
            final NetWritable responseWritable = writer -> writer.writeUTF8Text(result.toString());
            greenCommandChannel.publishHTTPResponse(request, 200,
                    request.getRequestContext() | HTTPFieldReader.END_OF_RESPONSE,
                    HTTPContentTypeDefaults.TXT,
                    responseWritable);
        });

        return true;
    }
}
