package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;

/**
 * Combination of {@link CloseableInboundByteBody} and {@link ImmediateInboundByteBody}. See their
 * documentation for details.
 *
 * @author Jonas Konrad
 * @since 4.5.0
 */
@Experimental
public interface CloseableImmediateInboundByteBody extends ImmediateInboundByteBody, CloseableInboundByteBody {
}
