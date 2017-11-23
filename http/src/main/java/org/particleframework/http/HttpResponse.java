package org.particleframework.http;


import org.particleframework.http.exceptions.UriSyntaxException;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Set;

/**
 * <p>Common interface for HTTP response implementations</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
public interface HttpResponse<B> extends HttpMessage<B> {

    /**
     * @return The current status
     */
    HttpStatus getStatus();

    /**
     * Return an {@link HttpStatus#OK} response with an empty body
     *
     * @return The ok response
     */
    static <T> MutableHttpResponse<T> ok() {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.ok();
    }

    /**
     * Return an {@link HttpStatus#NOT_FOUND} response with an empty body
     *
     * @return The response
     */
    static <T> MutableHttpResponse<T> notFound() {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.status(HttpStatus.NOT_FOUND);
    }

    /**
     * Return an {@link HttpStatus#NOT_FOUND} response with a body
     *
     * @return The response
     */
    static <T> MutableHttpResponse<T> notFound(T body) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.<T>status(HttpStatus.NOT_FOUND)
                .body(body);
    }

    /**
     * Return an {@link HttpStatus#BAD_REQUEST} response with an empty body
     *
     * @return The response
     */
    static <T> MutableHttpResponse<T> badRequest() {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.status(HttpStatus.BAD_REQUEST);
    }

    /**
     * Return an {@link HttpStatus#BAD_REQUEST} response with an empty body
     *
     * @return The response
     */
    static <T> MutableHttpResponse<T> badRequest(T body) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return  factory.status(HttpStatus.BAD_REQUEST,body);
    }

    /**
     * Return an {@link HttpStatus#UNPROCESSABLE_ENTITY} response with an empty body
     *
     * @return The response
     */
    static <T> MutableHttpResponse<T> unprocessableEntity() {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.status(HttpStatus.UNPROCESSABLE_ENTITY);
    }

    /**
     * Return an {@link HttpStatus#METHOD_NOT_ALLOWED} response with an empty body
     *
     * @return The response
     */
    static <T> MutableHttpResponse<T> notAllowed(HttpMethod... allowed) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );
        return factory.<T>status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers((headers) -> headers.allow(allowed));
    }

    /**
     * Return an {@link HttpStatus#METHOD_NOT_ALLOWED} response with an empty body
     *
     * @return The response
     */
    static <T> MutableHttpResponse<T> notAllowed(Set<HttpMethod> allowed) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );
        return factory.<T>status(HttpStatus.METHOD_NOT_ALLOWED)
                .headers((headers) -> headers.allow(allowed));
    }
    /**
     * Return an {@link HttpStatus#INTERNAL_SERVER_ERROR} response with an empty body
     *
     * @return The response
     */
    static <T> MutableHttpResponse<T> serverError() {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.status(HttpStatus.INTERNAL_SERVER_ERROR);
    }

    /**
     * Return an {@link HttpStatus#ACCEPTED} response with an empty body
     *
     * @return The response
     */
    static <T> MutableHttpResponse<T> accepted() {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.status(HttpStatus.ACCEPTED);
    }


    /**
     * Return an {@link HttpStatus#NO_CONTENT} response with an empty body
     *
     * @return The response
     */
    static <T> MutableHttpResponse<T> noContent() {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.status(HttpStatus.NO_CONTENT);
    }

    /**
     * Return an {@link HttpStatus#OK} response with a body
     *
     * @return The ok response
     */
    static <T> MutableHttpResponse<T> ok(T body) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );
        return factory.ok(body);
    }

    /**
     * Return an {@link HttpStatus#CREATED} response with a body
     *
     * @return The created response
     */
    static <T> MutableHttpResponse<T> created(T body) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.<T>status(HttpStatus.CREATED)
                .body(body);
    }

    /**
     * Return an {@link HttpStatus#CREATED} response with the location of the new resource
     *
     * @return The created response
     */
    static <T> MutableHttpResponse<T> created(URI location) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.<T>status(HttpStatus.CREATED)
                .headers((headers) ->
                        headers.location(location)
                );
    }

    /**
     * Return an {@link HttpStatus#SEE_OTHER} response with the location of the new resource
     *
     * @return The response
     */
    static <T> MutableHttpResponse<T> seeOther(URI location) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.<T>status(HttpStatus.SEE_OTHER)
                .headers((headers) ->
                        headers.location(location)
                );
    }

    /**
     * Return an {@link HttpStatus#TEMPORARY_REDIRECT} response with the location of the new resource
     *
     * @return The response
     */
    static <T> MutableHttpResponse<T> temporaryRedirect(URI location) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.<T>status(HttpStatus.TEMPORARY_REDIRECT)
                .headers((headers) ->
                        headers.location(location)
                );
    }

    /**
     * Return an {@link HttpStatus#PERMANENT_REDIRECT} response with the location of the new resource
     *
     * @return The response
     */
    static <T> MutableHttpResponse<T> permanentRedirect(URI location) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.<T>status(HttpStatus.PERMANENT_REDIRECT)
                .headers((headers) ->
                        headers.location(location)
                );
    }

    /**
     * Return a response for the given status
     *
     * @param status The status
     * @return The response
     */
    static <T> MutableHttpResponse<T> status(HttpStatus status) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.status(status);
    }

    /**
     * Return a response for the given status
     *
     * @param status The status
     * @param reason An alternatively reason message
     * @return The response
     */
    static <T> MutableHttpResponse<T> status(HttpStatus status, String reason) {
        HttpResponseFactory factory = HttpResponseFactory.INSTANCE.orElseThrow(() ->
                new IllegalStateException("No Server implementation found on classpath")
        );

        return factory.status(status, reason);
    }


    /**
     * Helper method for defining URIs. Rethrows checked exceptions as
     *
     * @param uri The URI char sequence
     * @return The URI
     */
    static URI uri(CharSequence uri) {
        try {
            return new URI(uri.toString());
        } catch (URISyntaxException e) {
            throw new UriSyntaxException(e);
        }
    }


}