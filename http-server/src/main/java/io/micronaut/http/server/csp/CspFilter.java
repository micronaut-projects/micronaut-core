package io.micronaut.http.server.csp;

import io.micronaut.http.HttpRequest;
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Filter;
import io.micronaut.http.filter.HttpServerFilter;
import io.micronaut.http.filter.ServerFilterChain;
import io.micronaut.http.server.HttpServerConfiguration;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

/**
 * <p>
 *      Provides support for <a href="https://www.w3.org/TR/CSP2/">Content Security Policy (CSP) Level 2</a>.
 * </p>
 *
 * <p>
 *      Content Security Policy, a mechanism web applications can use to mitigate a broad class of content
 *      injection vulnerabilities, such as cross-site scripting (XSS). Content Security Policy is a declarative
 *      policy that lets the authors (or server administrators) if a web application inform the client about
 *      the sources from which the application expects to load resources.
 * </p>
 *
 * <p>
 *     To mitigate XSS attacks, for example, a web application can declare that it only expects to load
 *     scripts from specific, trusted sources. This declaration allows the client to detect and block
 *     malicious scripts injected into the application by an attacker.
 * </p>
 *
 * <p>
 *     This implementation of {@link HttpServerFilter} writes one of the following HTTP headers:
 * </p>
 *
 * <ul>
 *     <li>Content-Security-Policy</li>
 *     <li>Content-Security-Policy-Report-Only</li>
 * </ul>
 *
 * @author Arul Dhesiaseelan
 * @since 1.1
 */
@Filter("/**")
public class CspFilter implements HttpServerFilter {

    private static final String CSP_HEADER = "Content-Security-Policy";
    private static final String CSP_REPORT_ONLY_HEADER = "Content-Security-Policy-Report-Only";

    protected final HttpServerConfiguration.CspConfiguration cspConfiguration;

    /**
     * @param cspConfiguration The {@link HttpServerConfiguration.CspConfiguration} instance
     */
    public CspFilter(HttpServerConfiguration.CspConfiguration cspConfiguration) {
        this.cspConfiguration = cspConfiguration;
    }

    @Override
    public Publisher<MutableHttpResponse<?>> doFilter(HttpRequest<?> request, ServerFilterChain chain) {

        return Flowable.fromPublisher(chain.proceed(request))
                .switchMap(response -> {
                    if (cspConfiguration.isReportOnly()) {
                        response.header(CSP_REPORT_ONLY_HEADER, cspConfiguration.getPolicyDirectives());
                    } else {
                        response.header(CSP_HEADER, cspConfiguration.getPolicyDirectives());
                    }
                    return Flowable.just(response);
                });
    }

}
