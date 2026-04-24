package io.micronaut.http.server.multipart;

import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;

final class BlockingSubscriber<T> implements Subscriber<T> {
    private Subscription subscription;

    private final Lock lock = new ReentrantLock();
    private final Condition available = lock.newCondition();

    private T item;
    private boolean complete;
    private Throwable error;

    @Override
    public void onSubscribe(Subscription s) {
        this.subscription = s;
    }

    @Override
    public void onNext(T t) {
        lock.lock();
        try {
            item = t;
            available.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onError(Throwable t) {
        lock.lock();
        try {
            error = t;
            available.signal();
        } finally {
            lock.unlock();
        }
    }

    @Override
    public void onComplete() {
        lock.lock();
        try {
            complete = true;
            available.signal();
        } finally {
            lock.unlock();
        }
    }

    public T next() throws InterruptedException {
        subscription.request(1);
        lock.lock();
        try {
            while (true) {
                T i = item;
                if (i != null) {
                    item = null;
                    return i;
                } else if (error != null) {
                    throw new AssertionError("Unexpected error", error);
                } else if (complete) {
                    throw new AssertionError("Unexpected completion");
                }
                available.await();
            }
        } finally {
            lock.unlock();
        }
    }

    public void expectComplete() throws InterruptedException {
        subscription.request(1);
        lock.lock();
        try {
            while (true) {
                if (item != null) {
                    throw new AssertionError("Unexpected item");
                } else if (error != null) {
                    throw new AssertionError("Unexpected error", error);
                } else if (complete) {
                    return;
                }
                available.await();
            }
        } finally {
            lock.unlock();
        }
    }

    public Throwable expectException() throws InterruptedException {
        subscription.request(1);
        lock.lock();
        try {
            while (true) {
                if (item != null) {
                    throw new AssertionError("Unexpected item");
                } else if (error != null) {
                    return error;
                } else if (complete) {
                    throw new AssertionError("Unexpected completion");
                }
                available.await();
            }
        } finally {
            lock.unlock();
        }
    }
}
