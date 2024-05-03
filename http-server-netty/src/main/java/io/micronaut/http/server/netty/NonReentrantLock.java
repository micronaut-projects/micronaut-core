package io.micronaut.http.server.netty;

import io.micronaut.core.annotation.NonNull;

import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;

/**
 * Non-reentrant {@link Lock} implementation based on a semaphore.
 *
 * @author Jonas Konrad
 */
final class NonReentrantLock extends Semaphore implements Lock {
    public NonReentrantLock() {
        super(1);
    }

    @Override
    public void lock() {
        acquireUninterruptibly();
    }

    @Override
    public void lockInterruptibly() throws InterruptedException {
        acquire();
    }

    @Override
    public boolean tryLock() {
        return tryAcquire();
    }

    @Override
    public boolean tryLock(long time, @NonNull TimeUnit unit) throws InterruptedException {
        return tryAcquire(time, unit);
    }

    @Override
    public void unlock() {
        release();
    }

    @NonNull
    @Override
    public Condition newCondition() {
        throw new UnsupportedOperationException();
    }
}
