package io.micronaut.http.client.bind;

import io.micronaut.context.annotation.BootstrapContextCompatible;
import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.Indexed;
import io.micronaut.http.MutableHttpRequest;

/**
 * A binder that binds to a {@link MutableHttpRequest}.
 * This class is extended by {@link ClientArgumentRequestBinder} and {@link AnnotatedClientRequestBinder}
 * to account for different binding behaviours
 *
 * @author Andriy Dmytruk
 * @since 2.1.0
 */
@Experimental
@BootstrapContextCompatible
@Indexed(ClientRequestBinder.class)
public interface ClientRequestBinder {
}
