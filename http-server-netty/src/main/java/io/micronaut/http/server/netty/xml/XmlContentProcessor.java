package io.micronaut.http.server.netty.xml;

import com.fasterxml.aalto.AsyncByteArrayFeeder;
import com.fasterxml.aalto.AsyncXMLInputFactory;
import com.fasterxml.aalto.AsyncXMLStreamReader;
import com.fasterxml.aalto.stax.InputFactoryImpl;
import io.micronaut.core.async.publisher.Publishers;
import io.micronaut.core.async.subscriber.SingleThreadedBufferingSubscriber;
import io.micronaut.core.async.subscriber.TypedSubscriber;
import io.micronaut.core.type.Argument;
import io.micronaut.http.server.HttpServerConfiguration;
import io.micronaut.http.server.netty.AbstractBufferingHttpContentProcessor;
import io.micronaut.http.server.netty.NettyHttpRequest;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufHolder;
import io.netty.buffer.ByteBufUtil;
import io.netty.util.ReferenceCountUtil;
import org.reactivestreams.Subscriber;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.xml.stream.XMLStreamException;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;

import static javax.xml.stream.XMLStreamConstants.*;

public class XmlContentProcessor extends AbstractBufferingHttpContentProcessor<Object> {

    private static final Logger LOG = LoggerFactory.getLogger(XmlContentProcessor.class);

    private AsyncXMLStreamReader<AsyncByteArrayFeeder> parser;
    private AsyncByteArrayFeeder feeder;
    private boolean streamArray;
    private LinkedList<Map.Entry<String, Map<String, Object>>> data;
    private volatile String currentElement;
    private AtomicInteger elementNestLevel = new AtomicInteger();
    private volatile String arrayName;


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
        feeder = parser.getInputFeeder();

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
                currentElement = parser.getLocalName();
                Map.Entry<String, Map<String, Object>> last = data.peekLast();
                if (last != null && last.getValue().containsKey(currentElement) && streamArray && elementNestLevel.get() == 1) {
                    arrayName = currentElement;
                    Object lastValue = data.peekLast().getValue().remove(currentElement);
                    currentDownstreamSubscriber().ifPresent(subscriber -> subscriber.onNext(lastValue));
                }
                Map<String, Object> attributes = new LinkedHashMap<>();
                int attributeCount = parser.getAttributeCount();
                for (int i = 0; i < attributeCount; i++) {
                    attributes.put(parser.getAttributeName(i).getLocalPart(), parser.getAttributeValue(i));
                }
                data.offer(new AbstractMap.SimpleEntry<>(currentElement, attributes));
                elementNestLevel.getAndIncrement();
            } else if (event == CHARACTERS) {
                Map<String, Object> lastValue = data.getLast().getValue();
                if (lastValue.isEmpty()) {
                    data.pollLast();
                }
                data.getLast().getValue().compute(currentElement, (key, value) -> {
                    String text = parser.getText();
                    if (value == null) {
                        return text;
                    } else {
                        return value.toString() + text;
                    }
                });
            } else if (event == END_ELEMENT) {
                elementNestLevel.decrementAndGet();
                Map.Entry<String, Map<String, Object>> last = data.peekLast();
                if (last.getKey().equals(currentElement)) {
                    last = data.removeLast();
                    if (data.peekLast() == null) {
                        data.offer(last);
                    } else {
                        Object lastValue = last.getValue();
                        data.peekLast().getValue().compute(last.getKey(), (key, value) -> {
                            if (value == null) {
                                return lastValue;
                            } else if (value instanceof List) {
                                ((List) value).add(lastValue);
                                arrayName = currentElement;
                                return value;
                            } else {
                                List<Object> list = new ArrayList<>();
                                list.add(value);
                                list.add(lastValue);
                                arrayName = currentElement;
                                return list;
                            }
                        });
                    }
                }
                currentElement = data.peekLast().getKey();
            } else if (event == END_DOCUMENT) {
                Map<String, Object> last = data.removeLast().getValue();
                currentDownstreamSubscriber().ifPresent(subscriber -> {
                    if (arrayName != null) {
                        subscriber.onNext(last.get(arrayName));
                    } else {
                        subscriber.onNext(last);
                    }
                });
                break;
            }

            event = parser.next();
        }
    }
}
