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
package io.micronaut.http.server.netty.xml;

import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.async.AsyncByteArrayScanner;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.subscriber.SingleThreadedBufferingSubscriber;
import io.micronaut.core.async.subscriber.TypedSubscriber;
import io.micronaut.core.type.Argument;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.AbstractBufferingHttpContentProcessor;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.micronaut.jackson.convert.XmlStreamToObjectConverter.ByteArrayXmlStreamReader;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.ReferenceCountUtil;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import javax.xml.stream.XMLStreamReader;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;

import static javax.xml.stream.XMLStreamConstants.END_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.END_ELEMENT;
import static javax.xml.stream.XMLStreamConstants.START_DOCUMENT;
import static javax.xml.stream.XMLStreamConstants.START_ELEMENT;

/**
 * This class will handle subscribing to a Xml stream and binding once the events are complete in a non-blocking
 * manner.
 *
 * @author svishnyakov
 * @author jameskleeh
 * @since 1.2
 */
public class XmlContentProcessor extends AbstractBufferingHttpContentProcessor<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(XmlContentProcessor.class);

    private final InputFactoryImpl xmlStreamFactory = new InputFactoryImpl();
    private List<byte[]> xmlChunkedInput = new ArrayList<>();
    private int streamElementStartIndex = 0;
    private LinkedList<Map.Entry<Integer, Integer>> elementRanges = new LinkedList<>();
    private AtomicInteger streamElementCounter = new AtomicInteger();
    private AsyncXMLStreamReader<AsyncByteArrayFeeder> parser;
    private AtomicInteger bytesCleaned = new AtomicInteger();
    private AsyncByteArrayScanner feeder;
    private boolean streamArray;
    private Deque<String> data;
    private volatile String currentElement;


    /**
     * @param nettyHttpRequest The {@link NettyHttpRequest}
     * @param configuration    The {@link HttpServerConfiguration}
     */
    public XmlContentProcessor(NettyHttpRequest<?> nettyHttpRequest, HttpServerConfiguration configuration) {
        super(nettyHttpRequest, configuration);
    }

    @Override
    public void subscribe(Subscriber<? super Object> downstreamSubscriber) {
        AsyncXMLInputFactory inputFactory = new InputFactoryImpl();
        parser = inputFactory.createAsyncForByteArray();
        feeder = (AsyncByteArrayScanner) parser.getInputFeeder();

        if (downstreamSubscriber instanceof TypedSubscriber) {
            TypedSubscriber typedSubscriber = (TypedSubscriber) downstreamSubscriber;
            Argument typeArgument = typedSubscriber.getTypeArgument();

            Class targetType = typeArgument.getType();
            if (Publishers.isConvertibleToPublisher(targetType) && !Publishers.isSingle(targetType)) {
                Optional<Argument<?>> genericArgument = typeArgument.getFirstTypeVariable();
                if (genericArgument.isPresent() && !Iterable.class.isAssignableFrom(genericArgument.get().getType())) {
                    // if the generic argument is not a iterable type them stream the array into the publisher
                    streamArray = true;
                }
            }
        }

        super.subscribe(downstreamSubscriber);
    }

    @Override
    protected void onUpstreamMessage(ByteBufHolder message) {
        ByteBuf content = message.content();
        try {
            byte[] bytes = ByteBufUtil.getBytes(content);
            xmlChunkedInput.add(bytes);
            if (bytes.length == 0) {
                if (feeder.needMoreInput()) {
                    if (LOG.isTraceEnabled()) {
                        LOG.trace("More input required to parse XML. Demanding more.");
                    }
                    upstreamSubscription.request(1);
                }
                return;
            }
            if (feeder.needMoreInput()) {
                parser.getInputFeeder().feedInput(bytes, 0, bytes.length);
                processXml();
            }
            if (feeder.needMoreInput()) {
                if (LOG.isTraceEnabled()) {
                    LOG.trace("More input required to parse XML. Demanding more.");
                }
                cleanUpBufferedInput();
                upstreamSubscription.request(1);
                upstreamDemand++;
            }
        } catch (XMLStreamException e) {
            upstreamState = SingleThreadedBufferingSubscriber.BackPressureState.DONE;
            upstreamSubscription.cancel();
            currentDownstreamSubscriber().ifPresent(subscriber -> subscriber.onError(e));
        } finally {
            ReferenceCountUtil.release(content);
        }
    }

    private void cleanUpBufferedInput() {
        // We want to clean up everything but the last element. Last element might not have been processed yet so we cannot
        // drop it.
        if (elementRanges.size() >= 2) {
            Map.Entry<Integer, Integer> firstElementRange = elementRanges.pollFirst();
            int startIndex = firstElementRange.getKey() - bytesCleaned.get();
            int endIndex = firstElementRange.getValue() - bytesCleaned.get();

            while (elementRanges.size() >= 2) {
                endIndex = elementRanges.pollFirst().getValue();
            }

            byte[] oldInput = flatInput();
            byte[] newInput = new byte[oldInput.length - (endIndex - startIndex)];

            System.arraycopy(oldInput, 0, newInput, 0, startIndex);
            System.arraycopy(oldInput, endIndex, newInput, startIndex, oldInput.length - endIndex);

            xmlChunkedInput.clear();
            xmlChunkedInput.add(newInput);
            bytesCleaned.addAndGet(endIndex - startIndex);
        }
    }

    @Override
    protected void doOnComplete() {
        feeder.endOfInput();
        if (feeder.needMoreInput()) {
            doOnError(new XMLStreamException("Unexpected end-of-input"));
        } else {
            try {
                processXml();
                parser.close();
                super.doOnComplete();
            } catch (XMLStreamException e) {
                doOnError(e);
            }
        }
    }

    private synchronized void processXml() throws XMLStreamException {
        int event = parser.next();
        while (event != AsyncXMLStreamReader.EVENT_INCOMPLETE) {
            if (event == START_DOCUMENT) {
                data = new LinkedList<>();
            } else if (event == START_ELEMENT) {
                String newCurrentElement = parser.getLocalName();
                if (newCurrentElement.equals(currentElement) && streamArray && parser.getDepth() == 2) {
                    currentDownstreamSubscriber().ifPresent(
                            subscriber -> subscriber.onNext(poolLastElementStream()));
                }

                if (streamArray && parser.getDepth() == 2) {
                    streamElementStartIndex = (int) feeder.getStartingByteOffset();
                }

                data.offer(newCurrentElement);
                currentElement = newCurrentElement;
            } else if (event == END_ELEMENT) {

                if (parser.getDepth() == 2 && streamArray) {
                    int elementEndIndex = (int) feeder.getEndingByteOffset();
                    elementRanges.add(new HashMap.SimpleEntry<>(streamElementStartIndex, elementEndIndex));
                    streamElementCounter.incrementAndGet();
                }
                currentElement = data.pollLast();
            } else if (event == END_DOCUMENT) {
                currentDownstreamSubscriber().ifPresent(subscriber -> {
                    if (streamElementCounter.get() <= 1) {
                        subscriber.onNext(createXmlStream(flatInput()));
                    } else {
                        subscriber.onNext(poolLastElementStream());
                    }
                });
                break;
            }

            event = parser.next();
        }
    }

    private XMLStreamReader createXmlStream(byte[] bytes) {
        try {
            return new ByteArrayXmlStreamReader(xmlStreamFactory.createAsyncFor(bytes), bytes);
        } catch (XMLStreamException e) {
           throw new RuntimeException(e);
        }
    }

    private XMLStreamReader poolLastElementStream() {
        Map.Entry<Integer, Integer> elementInputRange = elementRanges.peekLast();
        byte[] flattenInput = flatInput();

        return createXmlStream(Arrays.copyOfRange(
                flattenInput,
                elementInputRange.getKey() - bytesCleaned.get(),
                elementInputRange.getValue() - bytesCleaned.get())
        );
    }

    private synchronized byte[] flatInput() {
        if (xmlChunkedInput.size() == 1) {
            return xmlChunkedInput.get(0);
        }

        int totalBytes = xmlChunkedInput.stream()
                .map(array -> array.length)
                .mapToInt(Integer::intValue)
                .sum();

        byte[] flattenInput = new byte[totalBytes];
        int currentIndex = 0;
        for (byte[] in : xmlChunkedInput) {
            System.arraycopy(in, 0, flattenInput, currentIndex, in.length);
            currentIndex += in.length;
        }

        xmlChunkedInput.clear();
        xmlChunkedInput.add(flattenInput);
        return flattenInput;
    }
}
