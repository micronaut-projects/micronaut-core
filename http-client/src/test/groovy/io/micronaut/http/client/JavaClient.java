package io.micronaut.http.client;

import io.micronaut.http.annotation.Get;
import io.micronaut.http.client.annotation.Client;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

@Client("/")
public abstract class JavaClient {

    @Get(value ="/get/simple", single = true)
    abstract Publisher<Void> simple();

    @Get(value ="/test/redirect", single = true)
    abstract Publisher<Void> redirect();

    public void subscribe(Publisher<Void> publisher) {
        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<Throwable> error = new AtomicReference<>();
        publisher
                .subscribe(new Subscriber<Void>() {

                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(Long.MAX_VALUE);
                    }

                    @Override
                    public void onNext(Void unused) {
                        System.out.println("ok");
                    }

                    @Override
                    public void onError(Throwable t) {
                        error.set(t);
                        latch.countDown();
                    }

                    @Override
                    public void onComplete() {
                        latch.countDown();
                    }
                });
        try {
            latch.await();
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        Throwable t = error.get();
        if (t != null) {
            throw new RuntimeException(t);
        }
    }
}
