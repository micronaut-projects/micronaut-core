package io.micronaut.http.client.netty.dns;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import io.netty.channel.Channel;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.MultiThreadIoEventLoopGroup;
import io.netty.channel.SimpleChannelInboundHandler;
import io.netty.channel.nio.NioIoHandler;
import io.netty.channel.socket.DatagramChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsQueryDecoder;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * A minimal UDP DNS responder for the tests: answers from a mutable table, NXDOMAIN for an
 * unknown name, and nothing at all while {@link #drop} is set.
 */
final class TestDnsServer implements AutoCloseable {

    record Answer(DnsRecordType type, long ttl, byte[] content) {
    }

    final Map<String, List<Answer>> records = new ConcurrentHashMap<>();
    final AtomicInteger queries = new AtomicInteger();
    volatile boolean drop;
    private final EventLoopGroup group = new MultiThreadIoEventLoopGroup(1, NioIoHandler.newFactory());
    private final Channel channel;

    TestDnsServer() throws InterruptedException {
        channel = new Bootstrap()
            .group(group)
            .channel(NioDatagramChannel.class)
            .handler(new ChannelInitializer<DatagramChannel>() {
                @Override
                protected void initChannel(DatagramChannel ch) {
                    ch.pipeline().addLast(new DatagramDnsQueryDecoder(), new DatagramDnsResponseEncoder(), new Handler());
                }
            })
            .bind(new InetSocketAddress("127.0.0.1", 0)).sync().channel();
    }

    String address() {
        return "127.0.0.1:" + ((InetSocketAddress) channel.localAddress()).getPort();
    }

    void set(String name, Answer... answers) {
        records.put(name, List.of(answers));
    }

    static Answer a(String address, long ttl) throws Exception {
        return new Answer(DnsRecordType.A, ttl, InetAddress.getByName(address).getAddress());
    }

    static Answer srv(int priority, int weight, int port, String target, long ttl) {
        ByteBuf buf = Unpooled.buffer();
        buf.writeShort(priority).writeShort(weight).writeShort(port);
        for (String label : target.split("\\.")) {
            byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
            buf.writeByte(bytes.length).writeBytes(bytes);
        }
        buf.writeByte(0);
        byte[] content = new byte[buf.readableBytes()];
        buf.readBytes(content);
        return new Answer(DnsRecordType.SRV, ttl, content);
    }

    @Override
    public void close() {
        channel.close().syncUninterruptibly();
        group.shutdownGracefully();
    }

    private final class Handler extends SimpleChannelInboundHandler<DatagramDnsQuery> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DatagramDnsQuery query) {
            queries.incrementAndGet();
            if (drop) {
                return;
            }
            DnsQuestion question = query.recordAt(DnsSection.QUESTION);
            DatagramDnsResponse response = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
            response.setAuthoritativeAnswer(true);
            response.addRecord(DnsSection.QUESTION, question);
            String name = question.name().endsWith(".") ? question.name().substring(0, question.name().length() - 1) : question.name();
            List<Answer> answers = records.get(name);
            if (answers == null) {
                response.setCode(DnsResponseCode.NXDOMAIN);
            } else {
                for (Answer answer : answers) {
                    if (answer.type().equals(question.type())) {
                        response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(question.name(), answer.type(), answer.ttl(), Unpooled.wrappedBuffer(answer.content())));
                    }
                }
            }
            ctx.writeAndFlush(response);
        }
    }
}
