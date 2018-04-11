/*
 * Copyright 2017-2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.upload;

import io.micronaut.http.HttpResponse;
import io.micronaut.http.HttpStatus;
import io.micronaut.http.MediaType;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.ReplaySubject;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicLong;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Controller
public class UploadController {

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public String receiveJson(Data data, String title) {
        return title + ": " + data.toString();
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public String receivePlain(String data, String title) {
        return title + ": " + data;
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public String receiveBytes(byte[] data, String title) {
        return title + ": " + data.length;
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public Publisher<HttpResponse> receiveFileUpload(StreamingFileUpload data, String title) {
        return Flowable.fromPublisher(data.transferTo(title + ".json"))
                       .map(success -> success ? HttpResponse.ok( "Uploaded" ) : HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Something bad happened"));
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public String receiveCompletedFileUpload(CompletedFileUpload data) {
        try {
            return data.getFilename() + ": " + data.getBytes().length;
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public Publisher<HttpResponse> receivePublisher(Flowable<byte[]> data) {
        StringBuilder builder = new StringBuilder();
        AtomicLong length = new AtomicLong(0);
        ReplaySubject<HttpResponse> subject = ReplaySubject.create();
        data
            .subscribeOn(Schedulers.io())
            .subscribe(
        new Subscriber<byte[]>() {
            Subscription subscription;
            @Override
            public void onSubscribe(Subscription s) {
                s.request(1);
                this.subscription = s;
            }

            @Override
            public void onNext(byte[] bytes) {
                builder.append(new String(bytes));
                length.addAndGet(bytes.length);
                subscription.request(1);
            }

            @Override
            public void onError(Throwable t) {
                subject.onError(t);
            }

            @Override
            public void onComplete() {
                subject.onNext(HttpResponse.ok(builder.toString()));
                subject.onComplete();
            }
        });
        return subject.toFlowable(BackpressureStrategy.ERROR);
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public Publisher<HttpResponse> recieveFlowData(Data data) {
        return Flowable.just(HttpResponse.ok(data.toString()));
    }

    @Post(consumes = MediaType.MULTIPART_FORM_DATA)
    public Publisher<HttpResponse> receiveTwoFlowParts(@Part("data") Flowable<String> dataPublisher, @Part("title") Flowable<String> titlePublisher) {
        return titlePublisher.zipWith(dataPublisher, (title, data) -> HttpResponse.ok( title + ": " + data ));
    }

    public static class Data {
        String title;

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        @Override
        public String toString() {
            return "Data{" +
                    "title='" + title + '\'' +
                    '}';
        }
    }
}



