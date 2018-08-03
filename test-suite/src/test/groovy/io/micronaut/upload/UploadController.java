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
import io.micronaut.http.MutableHttpResponse;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.ReplaySubject;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.inject.Singleton;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Controller("/upload")
public class UploadController {

    @Post(value = "/receive-json", consumes = MediaType.MULTIPART_FORM_DATA)
    public String receiveJson(Data data, String title) {
        return title + ": " + data.toString();
    }

    @Post(value = "/receive-plain", consumes = MediaType.MULTIPART_FORM_DATA)
    public String receivePlain(String data, String title) {
        return title + ": " + data;
    }

    @Post(value = "/receive-bytes", consumes = MediaType.MULTIPART_FORM_DATA)
    public String receiveBytes(byte[] data, String title) {
        return title + ": " + data.length;
    }

    @Post(value = "/receive-file-upload", consumes = MediaType.MULTIPART_FORM_DATA)
    public Publisher<HttpResponse> receiveFileUpload(StreamingFileUpload data, String title) {
        return Flowable.fromPublisher(data.transferTo(title + ".json"))
                       .map(success -> success ? HttpResponse.ok( "Uploaded" ) : HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Something bad happened"));
    }

    @Post(value = "/receive-completed-file-upload", consumes = MediaType.MULTIPART_FORM_DATA)
    public String receiveCompletedFileUpload(CompletedFileUpload data) {
        try {
            return data.getFilename() + ": " + data.getBytes().length;
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    @Post(value = "/receive-publisher", consumes = MediaType.MULTIPART_FORM_DATA)
    public Single<HttpResponse> receivePublisher(Flowable<byte[]> data) {
        return data.reduce(new StringBuilder(), (stringBuilder, bytes) ->

                {
                    StringBuilder append = stringBuilder.append(new String(bytes));
                    System.out.println("bytes.length = " + bytes.length);
                    return append;
                }
        )
        .map((Function<StringBuilder, HttpResponse>) stringBuilder ->
                {
                    System.out.println("stringBuilder.length() = " + stringBuilder.length());
                    MutableHttpResponse<String> res = HttpResponse.ok(stringBuilder.toString());
                    System.out.println("res = " + res);
                    return res;
                }
        );
    }

    @Post(value = "/receive-flow-data", consumes = MediaType.MULTIPART_FORM_DATA)
    public Publisher<HttpResponse> receiveFlowData(Data data) {
        return Flowable.just(HttpResponse.ok(data.toString()));
    }

    @Post(value = "/receive-multiple-flow-data", consumes = MediaType.MULTIPART_FORM_DATA)
    public Single<HttpResponse> receiveMultipleFlowData(Publisher<Data> data) {
        return Flowable.fromPublisher(data).toList().map(list -> HttpResponse.ok(list.toString()));
    }

    @Post(value = "/receive-two-flow-parts", consumes = MediaType.MULTIPART_FORM_DATA)
    public Publisher<HttpResponse> receiveTwoFlowParts(
            @Part("data") Flowable<String> dataPublisher,
            @Part("title") Flowable<String> titlePublisher) {
        return titlePublisher.zipWith(dataPublisher, (title, data) -> HttpResponse.ok( title + ": " + data ));
    }

    @Post(value = "/receive-multiple-completed", consumes = MediaType.MULTIPART_FORM_DATA)
    public Publisher<HttpResponse> receiveMultipleCompleted(
            Flowable<CompletedFileUpload> data,
            String title) {
        List<Map> results = new ArrayList<>();

        ReplaySubject<HttpResponse> subject = ReplaySubject.create();
        data.subscribeOn(Schedulers.io())
                .subscribe(new Subscriber<CompletedFileUpload>() {
                    Subscription subscription;
                    @Override
                    public void onSubscribe(Subscription s) {
                        s.request(1);
                        this.subscription = s;
                    }

                    @Override
                    public void onNext(CompletedFileUpload upload) {
                        Map<String, Object> result = new LinkedHashMap<>();
                        result.put("name", upload.getFilename());
                        result.put("size", upload.getSize());
                        results.add(result);
                        subscription.request(1);
                    }

                    @Override
                    public void onError(Throwable t) {
                        subject.onError(t);
                    }

                    @Override
                    public void onComplete() {
                        Map<String, Object> body = new LinkedHashMap<>();
                        body.put("files", results);
                        body.put("title", title);
                        subject.onNext(HttpResponse.ok(body));
                        subject.onComplete();
                    }
                });
        return subject.toFlowable(BackpressureStrategy.ERROR);
    }

    @Post(value = "/receive-multiple-streaming", consumes = MediaType.MULTIPART_FORM_DATA)
    public Single<HttpResponse> receiveMultipleStreaming(
            Flowable<StreamingFileUpload> data) {
        return data.subscribeOn(Schedulers.io()).flatMap((StreamingFileUpload upload) -> {
            return Flowable.fromPublisher(upload).map(PartData::getBytes);
        }).collect(LongAdder::new, (adder, bytes) -> adder.add((long)bytes.length))
                .map((adder) -> {
                    return HttpResponse.ok(adder.longValue());
                });
    }

    @Post(value = "/receive-multiple-publishers", consumes = MediaType.MULTIPART_FORM_DATA)
    public Single<HttpResponse> receiveMultiplePublishers(Flowable<Flowable<byte[]>> data) {
        return data.subscribeOn(Schedulers.io()).flatMap((Flowable<byte[]> upload) -> {
            return upload.map((bytes) -> bytes);
        }).collect(LongAdder::new, (adder, bytes) -> adder.add((long)bytes.length))
                .map((adder) -> {
                    return HttpResponse.ok(adder.longValue());
                });
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



