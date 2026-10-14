package io.micronaut.http.server;

import io.micronaut.core.execution.ExecutionFlow;
import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.body.ByteBody;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.body.CloseableByteBody;
import org.reactivestreams.Publisher;
import org.reactivestreams.tck.PublisherVerification;
import org.reactivestreams.tck.TestEnvironment;
import reactor.core.publisher.Flux;

import java.util.Objects;

/**
 * The Reactive Streams TCK for the pieces of a streamed response, {@link ResponsePieces}.
 */
public class ResponsePiecesVerificationTest extends PublisherVerification<ByteBody> {
    private static final ByteBodyFactory FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);

    public ResponsePiecesVerificationTest() {
        super(new TestEnvironment(1000));
    }

    @Override
    public Publisher<ByteBody> createPublisher(long elements) {
        ExecutionFlow<Publisher<ByteBody>> flow = ResponsePieces.write(Flux.range(0, (int) elements),
            i -> ExecutionFlow.<CloseableByteBody>just(FACTORY.adapt(new byte[] {(byte) (int) i})), () -> { });
        return Objects.requireNonNull(flow.tryCompleteValue());
    }

    @Override
    public Publisher<ByteBody> createFailedPublisher() {
        // a failure before the first piece fails the flow of the pieces, not the pieces
        return null;
    }

    @Override
    public long maxElementsFromPublisher() {
        return 1024;
    }
}
