/*
 * Copyright 2017-2024 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
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
