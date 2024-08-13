package io.micronaut.http.body.stream

import io.micronaut.http.body.ByteBody
import spock.lang.Specification
import spock.lang.Timeout

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.ThreadLocalRandom
import java.util.concurrent.TimeUnit

@Timeout(10)
class StreamPairSpec extends Specification {
    private ExecutorService executor

    static byte[] bytes(int n) {
        def data = new byte[n]
        ThreadLocalRandom.current().nextBytes(data)
        return data
    }

    def setup() {
        executor = Executors.newCachedThreadPool()
    }

    def cleanup() {
        executor.shutdown()
    }

    def slowest() {
        given:
        def data = bytes(100)
        def p = StreamPair.createStreamPair(ExtendedInputStream.wrap(new ByteArrayInputStream(data)), ByteBody.SplitBackpressureMode.SLOWEST)
        def f1 = executor.submit {
            assert Arrays.equals(p.left().readAllBytes(), data)
        }
        TimeUnit.MILLISECONDS.sleep(10)
        expect:
        !f1.isDone()

        when:
        def f2 = executor.submit {
            assert Arrays.equals(p.right().readAllBytes(), data)
        }
        f1.get()
        f2.get()
        then:
        noExceptionThrown()
    }

    def fastest() {
        given:
        def data = bytes(100)
        def p = StreamPair.createStreamPair(ExtendedInputStream.wrap(new ByteArrayInputStream(data)), ByteBody.SplitBackpressureMode.FASTEST)
        assert Arrays.equals(p.left().readAllBytes(), data)
        assert Arrays.equals(p.right().readAllBytes(), data)
    }

    def original() {
        given:
        def data = bytes(100)
        def p = StreamPair.createStreamPair(ExtendedInputStream.wrap(new ByteArrayInputStream(data)), ByteBody.SplitBackpressureMode.ORIGINAL)
        assert Arrays.equals(p.left().readNBytes(50), Arrays.copyOf(data, 50))
        assert Arrays.equals(p.right().readNBytes(30), Arrays.copyOf(data, 30))
        def f1 = executor.submit {
            assert Arrays.equals(p.right().readAllBytes(), Arrays.copyOfRange(data, 30, 100))
        }
        TimeUnit.MILLISECONDS.sleep(10)
        expect:
        !f1.isDone()

        when:
        assert Arrays.equals(p.left().readNBytes(50), Arrays.copyOfRange(data, 50, 100))
        f1.get()
        then:
        noExceptionThrown()
    }

    def 'slowest cancellation'() {
        given:
        def data = bytes(100)
        def p = StreamPair.createStreamPair(ExtendedInputStream.wrap(new ByteArrayInputStream(data)), ByteBody.SplitBackpressureMode.SLOWEST)
        def f1 = executor.submit {
            assert Arrays.equals(p.left().readAllBytes(), data)
        }
        TimeUnit.MILLISECONDS.sleep(10)
        expect:
        !f1.isDone()

        when:
        p.right().cancelInput()
        f1.get()
        then:
        noExceptionThrown()
    }

    def 'original cancellation'() {
        given:
        def data = bytes(100)
        def p = StreamPair.createStreamPair(ExtendedInputStream.wrap(new ByteArrayInputStream(data)), ByteBody.SplitBackpressureMode.ORIGINAL)
        def f1 = executor.submit {
            assert Arrays.equals(p.right().readAllBytes(), data)
        }
        TimeUnit.MILLISECONDS.sleep(10)
        expect:
        !f1.isDone()

        when:
        p.left().cancelInput()
        f1.get()
        then:
        noExceptionThrown()
    }

    private class Data {
        final byte[] data;

        Data(int n) {
            this.data = new byte[n]
            ThreadLocalRandom.current().nextBytes(this.data)
        }

        InputStream input() {
            return new InputStream() {
                int i = 0

                @Override
                int read() throws IOException {
                    if (i >= data.length) {
                        return -1
                    } else {
                        return data[i++] & 0xff
                    }
                }
            }
        }

        InputStream check(InputStream s) {
            return new InputStream() {
                int i = 0

                @Override
                int read() throws IOException {

                }
            }
        }
    }
}
