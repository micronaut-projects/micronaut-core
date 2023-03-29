package io.micronaut.http.server.netty.body;

import io.micronaut.core.annotation.Nullable;

abstract class ManagedBody<T> implements HttpBody {
    private final T value;
    private boolean claimed;
    private HttpBody next;

    public ManagedBody(T value) {
        this.value = value;
    }

    final T value() {
        if (claimed) {
            throw new IllegalStateException("Already claimed");
        }
        return value;
    }

    final T claim() {
        if (claimed) {
            throw new IllegalStateException("Already claimed");
        }
        claimed = true;
        return value;
    }

    final T prepareClaim() {
        if (claimed) {
            throw new IllegalStateException("Already claimed");
        }
        return value;
    }

    final <B extends HttpBody> B next(B next) {
        if (claimed) {
            throw new AssertionError("Should have called prepareClaim");
        }
        this.next = next;
        claim();
        return next;
    }

    @Override
    public final void release() {
        if (!claimed) {
            release(value);
        } else if (next != null) {
            next.release();
        }
    }

    abstract void release(T value);

    @Nullable
    @Override
    public HttpBody next() {
        return next;
    }
}
