package io.micronaut.http.server.binding;

import io.micronaut.core.io.buffer.ByteArrayBufferFactory;
import io.micronaut.core.io.buffer.ReadBufferFactory;
import io.micronaut.http.body.ByteBodyFactory;
import io.micronaut.http.form.FormCapableHttpRequest;
import io.micronaut.http.form.FormParts;
import io.micronaut.http.multipart.FormFieldMetadata;
import io.micronaut.http.multipart.RawFormField;
import org.junit.jupiter.api.Test;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.reflect.Proxy;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.function.Supplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The cursor over the parts of a form, over a request whose raw form fields the test controls.
 */
class DefaultFormPartsTest {

    private static final ByteBodyFactory BODY_FACTORY = ByteBodyFactory.createDefault(ByteArrayBufferFactory.INSTANCE);
    private static final UploadContext CONTEXT = new UploadContext(Runnable::run, BODY_FACTORY, StandardCharsets.UTF_8, 1024, Long.MAX_VALUE);

    private static FormCapableHttpRequest<?> request(Supplier<Publisher<RawFormField>> fields) {
        return (FormCapableHttpRequest<?>) Proxy.newProxyInstance(DefaultFormPartsTest.class.getClassLoader(), new Class<?>[]{FormCapableHttpRequest.class}, (proxy, method, args) -> {
            if (method.getName().equals("getRawFormFields")) {
                return fields.get();
            }
            throw new UnsupportedOperationException(method.getName());
        });
    }

    private static Throwable failure(CompletionStage<?> stage) {
        CompletionException e = assertThrows(CompletionException.class, () -> stage.toCompletableFuture().join());
        return e.getCause();
    }

    private static RawFormField field(String name, String value) {
        return new RawFormField(new FormFieldMetadata(name, null, null), BODY_FACTORY.adapt(ReadBufferFactory.getJdkFactory().copyOf(value, StandardCharsets.UTF_8)));
    }

    @Test
    void aFormThatCannotBeReadFailsEveryOperationWithTheCause() {
        IllegalStateException claimed = new IllegalStateException("claimed by a filter");
        FormParts parts = new DefaultFormParts(request(() -> {
            throw claimed;
        }), CONTEXT);
        CompletionStage<Void> forEach = parts.forEach(part -> CompletableFuture.completedStage(null));
        assertSame(claimed, failure(forEach));
        // not left in progress
        assertSame(claimed, failure(parts.part("title", part -> CompletableFuture.completedStage(null))));
        parts.close();
    }

    /**
     * Emits the fields the test gives it, one per request.
     */
    private static final class FieldPublisher implements Publisher<RawFormField> {
        private Subscriber<? super RawFormField> subscriber;
        private final List<RawFormField> queued = new ArrayList<>();
        private long requested;
        private boolean completed;

        @Override
        public void subscribe(Subscriber<? super RawFormField> s) {
            subscriber = s;
            s.onSubscribe(new Subscription() {
                @Override
                public void request(long n) {
                    requested += n;
                    drain();
                }

                @Override
                public void cancel() {
                }
            });
        }

        void emit(RawFormField field) {
            queued.add(field);
            drain();
        }

        void complete() {
            completed = true;
            drain();
        }

        private void drain() {
            if (subscriber == null) {
                return;
            }
            while (requested > 0 && !queued.isEmpty()) {
                requested--;
                subscriber.onNext(queued.remove(0));
            }
            if (completed && queued.isEmpty()) {
                completed = false;
                subscriber.onComplete();
            }
        }
    }
}
