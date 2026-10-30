package io.micronaut.http.client.netty;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.http.body.ByteBodyFactory;
import org.junit.jupiter.api.Test;
import reactor.core.publisher.Flux;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

class StreamedBodyPiecesTest {
    @Test
    void eventStreamPublishersPreserveEmptyBoundaryLines() {
        ByteBodyFactory factory = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
        try (StreamedBodyPieces pieces = new StreamedBodyPieces(
            factory.copyOf("data: a\n\ndata: b\n\n", StandardCharsets.UTF_8), true, 1024)) {
            assertEquals(List.of("data: a", "", "data: b", ""), Flux.from(pieces.publisher())
                .map(buffer -> buffer.toString(StandardCharsets.UTF_8))
                .collectList().block(Duration.ofSeconds(10)));
        }
    }
}
