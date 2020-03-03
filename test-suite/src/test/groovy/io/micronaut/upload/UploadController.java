/*
 * Copyright 2017-2020 original authors
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
import io.micronaut.http.annotation.Body;
import io.micronaut.http.annotation.Controller;
import io.micronaut.http.annotation.Part;
import io.micronaut.http.annotation.Post;
import io.micronaut.http.multipart.CompletedFileUpload;
import io.micronaut.http.multipart.CompletedPart;
import io.micronaut.http.multipart.PartData;
import io.micronaut.http.multipart.StreamingFileUpload;
import io.micronaut.http.server.multipart.MultipartBody;
import io.reactivex.BackpressureStrategy;
import io.reactivex.Flowable;
import io.reactivex.Single;
import io.reactivex.exceptions.Exceptions;
import io.reactivex.functions.Function;
import io.reactivex.schedulers.Schedulers;
import io.reactivex.subjects.ReplaySubject;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import javax.inject.Singleton;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
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
    public Publisher<MutableHttpResponse<?>> receiveFileUpload(StreamingFileUpload data, String title) {
        long size = data.getDefinedSize();
        return Flowable.fromPublisher(data.transferTo(title + ".json"))
                       .map(success -> success ? HttpResponse.ok( "Uploaded " + size  ) : HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Something bad happened")).onErrorReturnItem(HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Something bad happened"));
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

    @Post(value = "/receive-flow-parts", consumes = MediaType.MULTIPART_FORM_DATA)
    public Single<HttpResponse> receiveFlowParts(Flowable<PartData> data) {
        return data.toList().doOnSuccess(parts -> {
            for (PartData part : parts) {
                part.getBytes(); //intentionally releasing the parts after all data has been received
            }
        }).map(parts -> HttpResponse.ok());
    }

    @Post(value = "/receive-flow-data", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    public Publisher<HttpResponse> receiveFlowData(Data data) {
        return Flowable.just(HttpResponse.ok(data.toString()));
    }

    @Post(value = "/receive-multiple-flow-data", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    public Single<HttpResponse> receiveMultipleFlowData(Publisher<Data> data) {
        return Single.create(emitter -> {
           data.subscribe(new Subscriber<Data>() {
               private Subscription s;
               List<Data> datas = new ArrayList<>();
               @Override
               public void onSubscribe(Subscription s) {
                   this.s = s;
                   s.request(1);
               }

               @Override
               public void onNext(Data data) {
                   datas.add(data);
                   s.request(1);
               }

               @Override
               public void onError(Throwable t) {
                    emitter.onError(t);
               }

               @Override
               public void onComplete() {
                    emitter.onSuccess(HttpResponse.ok(datas.toString()));
               }
           });
        });
    }

    @Post(value = "/receive-two-flow-parts", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
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
            return Flowable.fromPublisher(upload)
                    .map((pd) -> {
                        try {
                            return pd.getBytes();
                        } catch (IOException e) {
                            throw Exceptions.propagate(e);
                        }
                    });
        }).collect(LongAdder::new, (adder, bytes) -> adder.add((long)bytes.length))
                .map((adder) -> {
                    return HttpResponse.ok(adder.longValue());
                });
    }

    @Post(value = "/receive-partdata", consumes = MediaType.MULTIPART_FORM_DATA)
    public Single<HttpResponse> receivePartdata(
            Flowable<PartData> data) {
        return data.subscribeOn(Schedulers.io())
                .map((pd) -> {
                    try {
                        return pd.getBytes();
                    } catch (IOException e) {
                        throw Exceptions.propagate(e);
                    }
                })
                .collect(LongAdder::new, (adder, bytes) -> adder.add((long)bytes.length))
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

    @Post(value =  "/receive-flow-control", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    Single<String> go(Map json, Flowable<byte[]> file) {
        return Single.create(singleEmitter -> {
            file.subscribe(new Subscriber<byte[]>() {
                private Subscription subscription;
                private LongAdder longAdder = new LongAdder();
                @Override
                public void onSubscribe(Subscription subscription) {
                    this.subscription = subscription;
                    subscription.request(1);
                }

                @Override
                public void onNext(byte[] bytes) {
                    longAdder.add(bytes.length);
                    subscription.request(1);
                }

                @Override
                public void onError(Throwable throwable) {
                    singleEmitter.onError(throwable);
                }

                @Override
                public void onComplete() {
                    singleEmitter.onSuccess(Long.toString(longAdder.longValue()));
                }
            });
        });
    }

    @Post(value = "/receive-big-attribute", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    public Single<HttpResponse> receiveBigAttribute(Publisher<PartData> data) {
        return Single.create(emitter -> {
            data.subscribe(new Subscriber<PartData>() {
                private Subscription s;
                List<String> datas = new ArrayList<>();
                @Override
                public void onSubscribe(Subscription s) {
                    this.s = s;
                    s.request(1);
                }

                @Override
                public void onNext(PartData data) {
                    try {
                        datas.add(new String(data.getBytes(), StandardCharsets.UTF_8));
                        s.request(1);
                    } catch (IOException e) {
                        s.cancel();
                        emitter.onError(e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    emitter.onError(t);
                }

                @Override
                public void onComplete() {
                    emitter.onSuccess(HttpResponse.ok(String.join("", datas)));
                }
            });
        });
    }

    @Post(value =  "/receive-multipart-body", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    Single<String> go(@Body MultipartBody multipartBody) {
        return Single.create(emitter -> {
            multipartBody.subscribe(new Subscriber<CompletedPart>() {
                private Subscription s;
                List<String> datas = new ArrayList<>();
                @Override
                public void onSubscribe(Subscription s) {
                    this.s = s;
                    s.request(1);
                }

                @Override
                public void onNext(CompletedPart data) {
                    try {
                        datas.add(new String(data.getBytes(), StandardCharsets.UTF_8));
                        s.request(1);
                    } catch (IOException e) {
                        s.cancel();
                        emitter.onError(e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    emitter.onError(t);
                }

                @Override
                public void onComplete() {
                    emitter.onSuccess(String.join("|", datas));
                }
            });
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



