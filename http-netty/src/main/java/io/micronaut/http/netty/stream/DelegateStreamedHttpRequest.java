package io.micronaut.http.netty.stream;

import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpRequest;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;

final class DelegateStreamedHttpRequest extends DelegateHttpRequest implements StreamedHttpRequest {

    private final Publisher<HttpContent> stream;

    public DelegateStreamedHttpRequest(HttpRequest request, Publisher<HttpContent> stream) {
        super(request);
        this.stream = stream;
    }

    @Override
    public void subscribe(Subscriber<? super HttpContent> subscriber) {
        stream.subscribe(subscriber);
    }
}
