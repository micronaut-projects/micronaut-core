package io.micronaut.upload;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.validation.Validated;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

@Controller("/upload/validated")
@Validated
public class ValidatedController {

    @Post(value = "/receive-file-upload", consumes = MediaType.MULTIPART_FORM_DATA)
    public Publisher<HttpResponse> receiveFileUpload(StreamingFileUpload data) {
        data.subscribe(new Subscriber<PartData>() {
            @Override
            public void onSubscribe(Subscription s) {
                s.cancel();
            }

            @Override
            public void onNext(PartData partData) {

            }

            @Override
            public void onError(Throwable t) {

            }

            @Override
            public void onComplete() {

            }
        });
        return Flowable.just(HttpResponse.ok());
    }
}
