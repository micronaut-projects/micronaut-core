package io.micronaut.http;

/**
 * A {@link HttpRequest} that is potentially capable of HTTP2 server push. Code should check
 * {@link #isServerPushSupported()} before attempting to send any server push.
 *
 * @param <B> The Http message body
 * @author Jonas Konrad
 * @since 3.2
 */
public interface PushCapableHttpRequest<B> extends HttpRequest<B> {
    /**
     * Check whether HTTP2 server push is supported by the remote client. Only HTTP2 clients that indicate support
     * through HTTP2 settings have this method return {@code true}.
     *
     * @return {@code true} iff server push is supported.
     */
    boolean isServerPushSupported();

    /**
     * Initiate a HTTP2 server push for the given request. The information from the given request (i.e. path, headers)
     * will be passed on to the client immediately so that it does not send the request itself. Then, the given request
     * will be handled as if it was initiated by the client, and the response will be passed back to the client.
     *
     * @param request The request to respond to using a server push.
     * @throws UnsupportedOperationException if the client does not support server push. Check beforehand using
     * {@link #isServerPushSupported()}.
     */
    void serverPush(HttpRequest<?> request);
}
