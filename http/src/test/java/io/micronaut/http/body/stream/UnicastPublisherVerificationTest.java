package io.micronaut.http.body.stream;

import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;

/**
 * The Reactive Streams TCK for {@link BodyPublishers.Unicast}.
 */
public class UnicastPublisherVerificationTest extends PublisherVerification<Long> {

    public UnicastPublisherVerificationTest() {
        super(new TestEnvironment(1000));
    }

    @Override
    public Publisher<Long> createPublisher(long elements) {
        BodyPublishers.Unicast<Long> unicast = new BodyPublishers.Unicast<>() {
        };
        for (long i = 0; i < elements; i++) {
            unicast.tryNext(i);
        }
        unicast.tryComplete();
        return unicast;
    }

    @Override
    public Publisher<Long> createFailedPublisher() {
        BodyPublishers.Unicast<Long> unicast = new BodyPublishers.Unicast<>() {
        };
        unicast.tryError(new IllegalStateException("failed"));
        return unicast;
    }

    @Override
    public long maxElementsFromPublisher() {
        return 1024;
    }
}
