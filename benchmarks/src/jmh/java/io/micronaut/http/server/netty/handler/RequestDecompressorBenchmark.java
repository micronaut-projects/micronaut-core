package io.micronaut.http.server.netty.handler;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufAllocator;
import io.netty.buffer.PooledByteBufAllocator;
import io.netty.buffer.Unpooled;
import io.netty.channel.embedded.EmbeddedChannel;
import io.netty.handler.codec.compression.Decompressor;
import io.netty.handler.codec.compression.JdkZlibDecompressor;
import io.netty.handler.codec.compression.ZlibCodecFactory;
import io.netty.handler.codec.compression.ZlibWrapper;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Level;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Param;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.TearDown;
import org.openjdk.jmh.annotations.Warmup;
import org.openjdk.jmh.infra.Blackhole;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * Compares forwarding the output of {@link RequestDecompressor} through a callback (method
 * reference created per call, as the handlers do) with a pull-style drain loop.
 */
@State(Scope.Thread)
@Fork(1)
@Warmup(iterations = 5, time = 1)
@Measurement(iterations = 5, time = 1)
public class RequestDecompressorBenchmark {
    /** Size of each compressed input piece, like the content of one HTTP/1 chunk or HTTP/2 frame. */
    @Param({"64", "1024", "16384"})
    int pieceSize;

    /** Total uncompressed body size. */
    @Param({"65536"})
    int bodySize;

    private final ByteBufAllocator alloc = PooledByteBufAllocator.DEFAULT;
    private List<ByteBuf> pieces;
    private Blackhole blackhole;
    private long sink;

    @Setup(Level.Trial)
    public void setup(Blackhole blackhole) {
        this.blackhole = blackhole;
        // moderately compressible text-like data
        byte[] body = new byte[bodySize];
        Random random = new Random(0);
        for (int i = 0; i < body.length; i++) {
            body[i] = (byte) ('a' + random.nextInt(8));
        }
        EmbeddedChannel enc = new EmbeddedChannel(ZlibCodecFactory.newZlibEncoder(ZlibWrapper.GZIP));
        enc.writeOutbound(Unpooled.wrappedBuffer(body));
        enc.finish();
        ByteBuf compressed = Unpooled.buffer();
        ByteBuf part;
        while ((part = enc.readOutbound()) != null) {
            compressed.writeBytes(part);
            part.release();
        }
        pieces = new ArrayList<>();
        while (compressed.isReadable()) {
            pieces.add(Unpooled.unreleasableBuffer(compressed.readRetainedSlice(Math.min(pieceSize, compressed.readableBytes())).asReadOnly()));
        }
    }

    @TearDown(Level.Trial)
    public void tearDown() {
        blackhole.consume(sink);
    }

    private void forward(ByteBuf decompressed) {
        sink += decompressed.readableBytes();
        decompressed.release();
    }

    @Benchmark
    public long callback() {
        RequestDecompressor decompressor = RequestDecompressor.create(alloc, "gzip");
        for (ByteBuf piece : pieces) {
            decompressor.add(piece.duplicate(), this::forward);
        }
        decompressor.finish(this::forward);
        return sink;
    }

    @Benchmark
    public long drain() {
        DrainDecompressor decompressor = new DrainDecompressor(JdkZlibDecompressor.builder()
            .wrapper(ZlibWrapper.GZIP)
            .decompressConcatenated(true)
            .maxAllocation(64 * 1024)
            .build(alloc));
        for (ByteBuf piece : pieces) {
            decompressor.add(piece.duplicate());
            ByteBuf out;
            while ((out = decompressor.poll()) != null) {
                forward(out);
            }
        }
        decompressor.endOfInput();
        ByteBuf out;
        while ((out = decompressor.poll()) != null) {
            forward(out);
        }
        decompressor.close();
        return sink;
    }

    /**
     * The same logic as {@link RequestDecompressor}, with a pull-style output.
     */
    static final class DrainDecompressor {
        private final Decompressor decompressor;
        private boolean closed;
        private boolean receivedInput;

        DrainDecompressor(Decompressor decompressor) {
            this.decompressor = decompressor;
        }

        void add(ByteBuf compressed) {
            if (closed || !compressed.isReadable()) {
                compressed.release();
                return;
            }
            receivedInput = true;
            if (decompressor.status() == Decompressor.Status.COMPLETE) {
                compressed.release();
                return;
            }
            decompressor.addInput(compressed);
        }

        void endOfInput() {
            if (!closed && receivedInput && decompressor.status() == Decompressor.Status.NEED_INPUT) {
                decompressor.endOfInput();
            }
        }

        ByteBuf poll() {
            while (!closed && decompressor.status() == Decompressor.Status.NEED_OUTPUT) {
                ByteBuf decompressed = decompressor.takeOutput();
                if (decompressed.isReadable()) {
                    return decompressed;
                }
                decompressed.release();
            }
            return null;
        }

        void close() {
            if (!closed) {
                closed = true;
                decompressor.close();
            }
        }
    }
}
