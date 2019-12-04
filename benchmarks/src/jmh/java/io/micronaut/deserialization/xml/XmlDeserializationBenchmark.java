/*
 * Copyright 2017-2019 original authors
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
package io.micronaut.deserialization.xml;

import io.micronaut.context.ApplicationContext;
import io.micronaut.core.async.subscriber.TypedSubscriber;
import io.micronaut.core.convert.ConversionService;
import io.micronaut.core.type.Argument;
import io.micronaut.http.netty.stream.DefaultStreamedHttpRequest;
import io.micronaut.http.server.netty.HttpContentProcessor;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.runtime.server.EmbeddedServer;
import io.micronaut.xml.server.XmlHttpContentSubscriberFactory;
import io.netty.buffer.Unpooled;
import io.netty.handler.codec.http.DefaultHttpContent;
import io.netty.handler.codec.http.HttpContent;
import io.netty.handler.codec.http.HttpVersion;
import io.reactivex.Flowable;
import org.apache.groovy.util.Maps;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.openjdk.jmh.annotations.*;
import org.openjdk.jmh.runner.Runner;
import org.openjdk.jmh.runner.RunnerException;
import org.openjdk.jmh.runner.options.Options;
import org.openjdk.jmh.runner.options.OptionsBuilder;
import org.reactivestreams.Publisher;
import org.reactivestreams.Subscriber;
import org.reactivestreams.Subscription;

import java.lang.reflect.Array;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

@State(Scope.Benchmark)
public class XmlDeserializationBenchmark {

    private EmbeddedServer embeddedServer;
    private XmlHttpContentSubscriberFactory xmlContentProcessorFactory;
    private ConversionService conversionService;
    private Random random = new Random();

    @Setup
    @Before
    public void prepare() {
        embeddedServer = ApplicationContext.run(EmbeddedServer.class, Maps.of("jackson.bean-introspection-module", true));
        xmlContentProcessorFactory = this.embeddedServer.getApplicationContext().getBean(XmlHttpContentSubscriberFactory.class);
        conversionService = this.embeddedServer.getApplicationContext().getBean(ConversionService.class);
    }

    @TearDown
    @After
    public void tearDown() {
        embeddedServer.stop();
    }

    @Benchmark
    @BenchmarkMode({Mode.SampleTime, Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public List<Book> xmlDeserialization() throws InterruptedException {
        String xmlRequestContent = getXml();

        SimplePublisher<HttpContent> publisher = new SimplePublisher<>(xmlRequestContent);
        NettyHttpRequest nettyHttpRequest = createNettyRequest(publisher, xmlRequestContent.getBytes().length);
        HttpContentProcessor processor = xmlContentProcessorFactory.build(nettyHttpRequest);

        DownstreamSubscriber<Book> downstreamSubscriber = new DownstreamSubscriber<>(conversionService, Book.class);
        processor.subscribe(downstreamSubscriber);

        downstreamSubscriber.latch.await(5, TimeUnit.SECONDS);

        return downstreamSubscriber.receivedItems;
    }

    @Benchmark
    @BenchmarkMode({Mode.SampleTime, Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    public List<IntrospectedBook> xmlIntrospectedDeserialization() throws InterruptedException {
        String xmlRequestContent = getXml();


        SimplePublisher<HttpContent> publisher = new SimplePublisher<>(xmlRequestContent);
        NettyHttpRequest nettyHttpRequest = createNettyRequest(publisher, xmlRequestContent.getBytes().length);
        HttpContentProcessor processor = xmlContentProcessorFactory.build(nettyHttpRequest);

        DownstreamSubscriber<IntrospectedBook> downstreamSubscriber = new DownstreamSubscriber<>(conversionService, IntrospectedBook.class);
        processor.subscribe(downstreamSubscriber);

        downstreamSubscriber.latch.await(5, TimeUnit.SECONDS);

        return downstreamSubscriber.receivedItems;
    }

    @Benchmark
    @BenchmarkMode({Mode.SampleTime, Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Test
    public List streamingXmlDeserialization() throws InterruptedException {

        List<String> xmlParts = new ArrayList<>(12);
        xmlParts.add("<books>");
        for (int i = 0; i < 10; i++) {
            xmlParts.add(getXml());
        }
        xmlParts.add("</books>");

        int contentLength = xmlParts.stream().map(String::getBytes).mapToInt(Array::getLength).sum();

        SimplePublisher<HttpContent> publisher = new SimplePublisher<>(xmlParts);
        NettyHttpRequest nettyHttpRequest = createNettyRequest(publisher, contentLength);
        HttpContentProcessor processor = xmlContentProcessorFactory.build(nettyHttpRequest);

        DownstreamTypedSubscriber downstreamSubscriber = new DownstreamTypedSubscriber<>(
                conversionService, Book.class, Argument.of(Flowable.class, Object.class)
        );
        processor.subscribe(downstreamSubscriber);

        downstreamSubscriber.latch.await(5, TimeUnit.SECONDS);

        return downstreamSubscriber.receivedItems;
    }

    @Benchmark
    @BenchmarkMode({Mode.SampleTime, Mode.Throughput})
    @OutputTimeUnit(TimeUnit.MILLISECONDS)
    @Test
    public List streamingXmlIntrospectedDeserialization() throws InterruptedException {

        List<String> xmlParts = new ArrayList<>(12);
        xmlParts.add("<books>");
        for (int i = 0; i < 10; i++) {
            xmlParts.add(getXml());
        }
        xmlParts.add("</books>");

        int contentLength = xmlParts.stream().map(String::getBytes).mapToInt(Array::getLength).sum();

        SimplePublisher<HttpContent> publisher = new SimplePublisher<>(xmlParts);
        NettyHttpRequest nettyHttpRequest = createNettyRequest(publisher, contentLength);
        HttpContentProcessor processor = xmlContentProcessorFactory.build(nettyHttpRequest);

        DownstreamTypedSubscriber downstreamSubscriber = new DownstreamTypedSubscriber<>(
                conversionService, IntrospectedBook.class, Argument.of(Flowable.class, Object.class)
        );
        processor.subscribe(downstreamSubscriber);

        downstreamSubscriber.latch.await(5, TimeUnit.SECONDS);

        return downstreamSubscriber.receivedItems;
    }

    private String getXml() {
        return "<book>\n" +
                "    <author>\n" +
                "        <name>Author" + random.nextInt() + "</name>\n" +
                "    </author>\n" +
                "    <bookName>bookName</bookName>\n" +
                "    <field1>field1</field1>\n" +
                "    <field2>field2</field2>\n" +
                "    <field3>field3</field3>\n" +
                "    <field4>field4</field4>\n" +
                "    <field5>field5</field5>\n" +
                "    <field6>field6</field6>\n" +
                "    <field7>field7</field7>\n" +
                "    <field8>field8</field8>\n" +
                "</book>";
    }

    private NettyHttpRequest createNettyRequest(Publisher<HttpContent> publisher, int contentLength) {
        DefaultStreamedHttpRequest streamedHttpRequest = new DefaultStreamedHttpRequest(
                HttpVersion.HTTP_1_1,
                io.netty.handler.codec.http.HttpMethod.POST,
                "/media/xml/benchmark",
                publisher
        );

        NettyHttpRequest nettyHttpRequest = Mockito.mock(NettyHttpRequest.class);
        Mockito.when(nettyHttpRequest.getContentLength()).thenReturn((long)contentLength);
        Mockito.when(nettyHttpRequest.getNativeRequest()).thenReturn(streamedHttpRequest);

        return nettyHttpRequest;
    }

    public static void main(String[] args) throws RunnerException {
        Options opt = new OptionsBuilder()
                .include(".*" + XmlDeserializationBenchmark.class.getSimpleName() + ".*")
                .warmupIterations(1)
                .measurementIterations(4)
                .shouldFailOnError(true)
                .forks(1)
                .threads(1)
                .build();

        new Runner(opt).run();
    }

    static class DownstreamTypedSubscriber<R, T> extends  TypedSubscriber<T> {

        private final CountDownLatch latch = new CountDownLatch(1);
        private final List<R> receivedItems = new LinkedList<>();
        private final ConversionService conversionService;
        private final Class<R> type;

        public DownstreamTypedSubscriber(ConversionService conversionService, Class<R> type, Argument<T> argument) {
            super(argument);
            this.type = type;
            this.conversionService = conversionService;
        }

        @Override
        protected void doOnSubscribe(Subscription subscription) {
            subscription.request(1);
        }

        @Override
        protected void doOnNext(Object message) {
            receivedItems.add((R)conversionService.convert(message, type).get());
        }

        @Override
        protected void doOnError(Throwable t) {
            latch.countDown();
        }

        @Override
        protected void doOnComplete() {
            latch.countDown();
        }
    }

    static class DownstreamSubscriber<T> implements Subscriber {
        private final CountDownLatch latch = new CountDownLatch(1);
        private final List<T> receivedItems = new LinkedList<>();
        private final ConversionService conversionService;
        private final Class<T> type;

        DownstreamSubscriber(ConversionService conversionService, Class<T> type) {
            this.conversionService = conversionService;
            this.type = type;
        }

        @Override
        public void onSubscribe(Subscription subscription) {
            subscription.request(1);
        }

        @Override
        public void onNext(Object o) {
            receivedItems.add((T)conversionService.convert(o, type).get());
        }

        @Override
        public void onError(Throwable t) {
            latch.countDown();
        }

        @Override
        public void onComplete() {
            latch.countDown();
        }
    }

    static class SimplePublisher<T> implements Publisher<T> {

        private Subscriber subscriber;
        private Queue<String> queue = new LinkedList<>();


        SimplePublisher(String message) {
            this.queue.add(message);
        }

        SimplePublisher(Collection<String> messages) {
            this.queue.addAll(messages);
        }

        @Override
        public void subscribe(Subscriber s) {
            this.subscriber = s;
            subscriber.onSubscribe(new Subscription() {
                @Override
                public synchronized void request(long n) {
                    if (!queue.isEmpty()) {
                        subscriber.onNext(new DefaultHttpContent(Unpooled.wrappedBuffer(queue.poll().getBytes())));

                    }
                    else {
                        subscriber.onComplete();
                    }
                }

                @Override
                public void cancel() {
                }
            });

        }
    }

    static class Book {
        Author author;
        String bookName;
        String field1;
        String field2;
        String field3;
        String field4;
        String field5;
        String field6;
        String field7;
        String field8;

        public Author getAuthor() {
            return this.author;
        }

        public void setAuthor(Author author) {
            this.author = author;
        }

        public String getBookName() {
            return this.bookName;
        }

        public void setBookName(String bookName) {
            this.bookName = bookName;
        }

        public String getField1() {
            return this.field1;
        }

        public void setField1(String field1) {
            this.field1 = field1;
        }

        public String getField2() {
            return this.field2;
        }

        public void setField2(String field2) {
            this.field2 = field2;
        }

        public String getField3() {
            return this.field3;
        }

        public void setField3(String field3) {
            this.field3 = field3;
        }

        public String getField4() {
            return this.field4;
        }

        public void setField4(String field4) {
            this.field4 = field4;
        }

        public String getField5() {
            return this.field5;
        }

        public void setField5(String field5) {
            this.field5 = field5;
        }

        public String getField6() {
            return this.field6;
        }

        public void setField6(String field6) {
            this.field6 = field6;
        }

        public String getField7() {
            return this.field7;
        }

        public void setField7(String field7) {
            this.field7 = field7;
        }

        public String getField8() {
            return this.field8;
        }

        public void setField8(String field8) {
            this.field8 = field8;
        }
    }

    static class Author {
        String name;

        public String getName() {
            return this.name;
        }

        public void setName(String name) {
            this.name = name;
        }
    }
}
