package io.micronaut.reactive.rxjava2;

import io.micronaut.core.annotation.Internal;
import io.reactivex.disposables.Disposable;
import io.reactivex.exceptions.ProtocolViolationException;
import io.reactivex.plugins.RxJavaPlugins;
import org.reactivestreams.Subscription;

/**
 * Common interface for all instrumented RxJava components.
 *
 * @author graemerocher
 * @since 1.1
 */
@Internal
interface RxInstrumentedComponent extends InstrumentedComponent {

    @Override
    default void onStateError(Throwable t) {
        RxJavaPlugins.onError(t);
    }

    @Override
    default boolean validate(Subscription current, Subscription next) {
        if (next == null) {
            RxJavaPlugins.onError(new NullPointerException("next is null"));
            return false;
        }
        if (current != null) {
            next.cancel();
            RxJavaPlugins.onError(new ProtocolViolationException("Subscription already set!"));
            return false;
        }
        return true;
    }

    /**
     * Validates a disposable.
     * @param current The current
     * @param next The next
     * @return True if it is valid
     */
    default boolean validate(Disposable current, Disposable next) {
        if (next == null) {
            RxJavaPlugins.onError(new NullPointerException("next is null"));
            return false;
        }
        if (current != null) {
            next.dispose();
            RxJavaPlugins.onError(new ProtocolViolationException("Disposable already set!"));
            return false;
        }
        return true;
    }
}
