package io.micronaut.http.server.netty.handler.accesslog.element;

import io.micronaut.core.order.Ordered;

public class NotImplementedElementBuilder implements LogElementBuilder {

    private static final String[] NOT_IMPLEMENTED = new String[] { "l", "u" };

    @Override
    public int getOrder() {
        return Ordered.LOWEST_PRECEDENCE;
    }

    @Override
    public LogElement build(String token, String param) {
        for (String element: NOT_IMPLEMENTED) {
            if (token.equals(element)) {
                return ConstantElement.UNKNOWN;
            }
        }
        return null;
    }
}
