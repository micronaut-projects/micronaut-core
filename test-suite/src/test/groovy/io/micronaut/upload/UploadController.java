/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
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
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;
import io.micronaut.core.async.annotation.SingleResult;
import jakarta.inject.Singleton;
import reactor.core.Exceptions;
import reactor.core.publisher.Flux;
import reactor.core.publisher.FluxSink;
import reactor.core.publisher.Mono;
import reactor.core.publisher.ReplayProcessor;
import reactor.core.publisher.Sinks;
import reactor.core.scheduler.Schedulers;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.Principal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import java.util.function.Function;

/**
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@Controller("/upload")
public class UploadController {

    @Post(value = "/receive-json", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    public String receiveJson(Data data, String title) {
        return title + ": " + data.toString();
    }

    @Post(value = "/receive-plain", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    public String receivePlain(String data, String title) {
        return title + ": " + data;
    }

    @Post(value = "/receive-bytes", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    public String receiveBytes(byte[] data, String title) {
        return title + ": " + data.length;
    }

    @Post(value = "/receive-file-upload", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    public Publisher<MutableHttpResponse<?>> receiveFileUpload(StreamingFileUpload data, String title) {
        long size = data.getDefinedSize();
        return Flux.from(data.transferTo(title + ".json"))
                       .map(success -> success ? HttpResponse.ok( "Uploaded " + size ) :  HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Something bad happened"))
                .onErrorReturn((MutableHttpResponse<?>) HttpResponse.status(HttpStatus.INTERNAL_SERVER_ERROR, "Something bad happened"));
    }

    @Post(value = "/receive-completed-file-upload", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    public String receiveCompletedFileUpload(CompletedFileUpload data) {
        try {
            return data.getFilename() + ": " + data.getBytes().length;
        } catch (IOException e) {
            return e.getMessage();
        }
    }

    @Post(value = "/receive-completed-file-upload-stream", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    public String receiveCompletedFileUploadStream(CompletedFileUpload data) {
        try {
            InputStream is = data.getInputStream();
            int size = 1024;
            byte[] buf = new byte[size];
            int total = 0;
            int len;
            while ((len = is.read(buf, 0, size)) != -1) {
                total += len;
            }
            is.close();
            is.close(); //intentionally close the stream twice to ensure it doesn't throw an exception
            return data.getFilename() + ": " + total;
        } catch (IOException e) {
            return e.getMessage();
        }
    }


    @Post(value = "/receive-publisher", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    @SingleResult
    public Publisher<HttpResponse> receivePublisher(Publisher<byte[]> data) {
        return Flux.from(data).reduce(new StringBuilder(), (stringBuilder, bytes) ->

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
    @SingleResult
    public Publisher<HttpResponse> receiveFlowParts(Publisher<PartData> data) {
        return Flux.from(data).collectList().doOnSuccess(parts -> {
            for (PartData part : parts) {
                try {
                    part.getBytes(); //intentionally releasing the parts after all data has been received
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
        }).map(parts -> HttpResponse.ok());
    }

    @Post(value = "/receive-flow-data", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    public Publisher<HttpResponse> receiveFlowData(Data data) {
        return Flux.just(HttpResponse.ok(data.toString()));
    }

    @Post(value = "/receive-multiple-flow-data", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    @SingleResult
    public Publisher<HttpResponse> receiveMultipleFlowData(Publisher<Data> data) {
        return Mono.create(emitter -> {
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
                    emitter.error(t);
               }

               @Override
               public void onComplete() {
                    emitter.success(HttpResponse.ok(datas.toString()));
               }
           });
        });
    }

    @Post(value = "/receive-two-flow-parts", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    public Publisher<HttpResponse> receiveTwoFlowParts(
            @Part("data") Publisher<String> dataPublisher,
            @Part("title") Publisher<String> titlePublisher) {
        return Flux.from(titlePublisher).zipWith(dataPublisher, (title, data) -> HttpResponse.ok( title + ": " + data ));
    }

    @Post(value = "/receive-multiple-completed", consumes = MediaType.MULTIPART_FORM_DATA)
    public Publisher<HttpResponse> receiveMultipleCompleted(
            Publisher<CompletedFileUpload> data,
            String title) {
        List<Map> results = new ArrayList<>();

        ReplayProcessor<HttpResponse> subject = ReplayProcessor.create();
        Flux.from(data).subscribeOn(Schedulers.boundedElastic())
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
        return subject.asFlux();
    }

    @Post(value = "/receive-multiple-streaming", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    @SingleResult
    public Publisher<HttpResponse> receiveMultipleStreaming(
            Publisher<StreamingFileUpload> data) {
        return Flux.from(data).subscribeOn(Schedulers.boundedElastic()).flatMap((StreamingFileUpload upload) -> {
            return Flux.from(upload)
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

    @Post(value = "/receive-partdata", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    @SingleResult
    public Publisher<HttpResponse> receivePartdata(
            Publisher<PartData> data) {
        return Flux.from(data).subscribeOn(Schedulers.boundedElastic())
                .map((pd) -> {
                    try {
                        final byte[] bytes = pd.getBytes();
                        System.out.println("received " + bytes.length + " bytes");
                        return bytes;
                    } catch (IOException e) {
                        System.out.println("caught exception");
                        System.out.println(e);
                        throw Exceptions.propagate(e);
                    }
                })
                .collect(LongAdder::new, (adder, bytes) -> adder.add((long)bytes.length))
                .map((adder) -> {
                    return HttpResponse.ok(adder.longValue());
                });
    }

    @Post(value = "/receive-multiple-publishers", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    @SingleResult
    public Publisher<HttpResponse> receiveMultiplePublishers(Publisher<Publisher<byte[]>> data) {
        return Flux.from(data)
                .subscribeOn(Schedulers.boundedElastic())
                .flatMap((Publisher<byte[]> upload) -> {
                    return Flux.from(upload).map((bytes) -> bytes);
                }).collect(LongAdder::new, (adder, bytes) -> adder.add((long)bytes.length))
                .map((adder) -> {
                    return HttpResponse.ok(adder.longValue());
                });
    }

    @Post(value =  "/receive-flow-control", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    @SingleResult
    public Publisher<String> go(Map json, Publisher<byte[]> file) {
        return Mono.create(singleEmitter -> {
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
                    singleEmitter.error(throwable);
                }

                @Override
                public void onComplete() {
                    singleEmitter.success(Long.toString(longAdder.longValue()));
                }
            });
        });
    }

    @Post(value = "/receive-big-attribute", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    @SingleResult
    public Publisher<HttpResponse> receiveBigAttribute(Publisher<PartData> data) {
        return Mono.create(emitter -> {
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
                        emitter.error(e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    emitter.error(t);
                }

                @Override
                public void onComplete() {
                    emitter.success(HttpResponse.ok(String.join("", datas)));
                }
            });
        });
    }

    @Post(value =  "/receive-multipart-body", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    @SingleResult
    public Publisher<String> go(@Body MultipartBody multipartBody) {
        return Mono.create(emitter -> {
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
                        emitter.error(e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    emitter.error(t);
                }

                @Override
                public void onComplete() {
                    emitter.success(String.join("|", datas));
                }
            });
        });
    }

    @Post(value =  "/receive-multipart-body-principal", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    @SingleResult
    public Publisher<String> multipartBodyWithPrincipal(Principal principal, @Body MultipartBody multipartBody) {
        return Mono.create(emitter -> {
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
                        emitter.error(e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    emitter.error(t);
                }

                @Override
                public void onComplete() {
                    emitter.success(String.join("|", datas));
                }
            });
        });
    }

    @Post(value = "/publisher-completedpart", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    @SingleResult
    public Publisher<String> publisherCompletedPart(Publisher<CompletedPart> recipients) {
        return Mono.create(emitter -> {
            recipients.subscribe(new Subscriber<CompletedPart>() {
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
                        emitter.error(e);
                    }
                }

                @Override
                public void onError(Throwable t) {
                    emitter.error(t);
                }

                @Override
                public void onComplete() {
                    emitter.success(String.join("|", datas));
                }
            });
        });
    }

    @Post(uri = "/receive-multipart-body-as-mono", consumes = MediaType.MULTIPART_FORM_DATA, produces = MediaType.TEXT_PLAIN)
    @SingleResult
    public Publisher<String> multipartAsSingle(@Body io.micronaut.http.server.multipart.MultipartBody body) {
        //This will throw an exception because it caches the first result and does not emit it until
        //the publisher completes. By this time the data has been freed. The data is freed immediately
        //after the onNext call to prevent memory leaks
        return Mono.from(body).map(single -> {
            try {
                return single.getBytes() == null ? "FAIL" : "OK";
            } catch (IOException e) {
                e.printStackTrace();
            }
            return "FAIL";
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



