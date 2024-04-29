package io.micronaut.http.body;

import io.micronaut.core.annotation.Experimental;
import io.micronaut.core.annotation.NonNull;

/**
 * Combination of {@link CloseableInboundByteBody} and {@link ImmediateInboundByteBody}. See their
 * documentation for details.
 *
 * @author Jonas Konrad
 * @since 4.5.0
 */
@Experimental
public interface CloseableImmediateInboundByteBody extends ImmediateInboundByteBody, CloseableInboundByteBody {
    /**
     * {@inheritDoc}
     *
     * @deprecated This method is unnecessary for {@link ImmediateInboundByteBody}, it does nothing.
     */
    @SuppressWarnings("deprecation")
    @Override
    @NonNull
    @Deprecated
    default CloseableImmediateInboundByteBody allowDiscard() {
        return this;
    }
}
