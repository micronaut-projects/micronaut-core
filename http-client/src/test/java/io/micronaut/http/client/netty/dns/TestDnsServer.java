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
import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioDatagramChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.dns.DefaultDnsResponse;
import io.netty.handler.codec.dns.DnsQuery;
import io.netty.handler.codec.dns.DnsResponse;
import io.netty.handler.codec.dns.TcpDnsQueryDecoder;
import io.netty.handler.codec.dns.TcpDnsResponseEncoder;
import io.netty.handler.codec.dns.DatagramDnsQuery;
import io.netty.handler.codec.dns.DatagramDnsQueryDecoder;
import io.netty.handler.codec.dns.DatagramDnsResponse;
import io.netty.handler.codec.dns.DatagramDnsResponseEncoder;
import io.netty.handler.codec.dns.DefaultDnsRawRecord;
import io.netty.handler.codec.dns.DnsQuestion;
import io.netty.handler.codec.dns.DnsRecordType;
import io.netty.handler.codec.dns.DnsResponseCode;
import io.netty.handler.codec.dns.DnsSection;
import io.netty.resolver.dns.DnsServerAddressStreamProvider;
import io.netty.resolver.dns.SingletonDnsServerAddressStreamProvider;

import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
    /**
     * The names answered over UDP with the truncation flag and no record: only TCP has them.
     */
    final Set<String> truncated = ConcurrentHashMap.newKeySet();
    final AtomicInteger tcpQueries = new AtomicInteger();
    private final Channel tcpChannel;
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
        tcpChannel = new ServerBootstrap()
            .group(group)
            .channel(NioServerSocketChannel.class)
            .childHandler(new ChannelInitializer<SocketChannel>() {
                @Override
                protected void initChannel(SocketChannel ch) {
                    ch.pipeline().addLast(new TcpDnsQueryDecoder(), new TcpDnsResponseEncoder(), new TcpHandler());
                }
            })
            .bind(channel.localAddress()).sync().channel();
    }

    /**
     * @return The name servers to give the discovery client: this server
     */
    DnsServerAddressStreamProvider nameServers() {
        return new SingletonDnsServerAddressStreamProvider((InetSocketAddress) channel.localAddress());
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
        writeName(buf, target);
        return new Answer(DnsRecordType.SRV, ttl, bytes(buf));
    }

    static Answer cname(String target, long ttl) {
        ByteBuf buf = Unpooled.buffer();
        writeName(buf, target);
        return new Answer(DnsRecordType.CNAME, ttl, bytes(buf));
    }

    /**
     * An SOA record, sent in the authority section of an answer without records.
     */
    static Answer soa(long minimum, long ttl) {
        ByteBuf buf = Unpooled.buffer();
        writeName(buf, "ns.test.local");
        writeName(buf, "admin.test.local");
        buf.writeInt(1).writeInt(3600).writeInt(600).writeInt(86400).writeInt((int) minimum);
        return new Answer(DnsRecordType.SOA, ttl, bytes(buf));
    }

    private static void writeName(ByteBuf buf, String name) {
        if (!name.isEmpty()) {
            for (String label : name.split("\\.")) {
                byte[] bytes = label.getBytes(StandardCharsets.US_ASCII);
                buf.writeByte(bytes.length).writeBytes(bytes);
            }
        }
        buf.writeByte(0);
    }

    private static byte[] bytes(ByteBuf buf) {
        byte[] content = new byte[buf.readableBytes()];
        buf.readBytes(content);
        return content;
    }

    @Override
    public void close() {
        tcpChannel.close().syncUninterruptibly();
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
            DatagramDnsResponse response = new DatagramDnsResponse(query.recipient(), query.sender(), query.id());
            DnsQuestion question = query.recordAt(DnsSection.QUESTION);
            if (truncated.contains(strip(question.name()))) {
                response.setTruncated(true);
                response.addRecord(DnsSection.QUESTION, question);
            } else {
                answer(query, response);
            }
            ctx.writeAndFlush(response);
        }
    }

    private final class TcpHandler extends SimpleChannelInboundHandler<DnsQuery> {
        @Override
        protected void channelRead0(ChannelHandlerContext ctx, DnsQuery query) {
            tcpQueries.incrementAndGet();
            DefaultDnsResponse response = new DefaultDnsResponse(query.id());
            answer(query, response);
            ctx.writeAndFlush(response);
        }
    }

    private static String strip(String name) {
        return name.endsWith(".") ? name.substring(0, name.length() - 1) : name;
    }

    private void answer(DnsQuery query, DnsResponse response) {
        DnsQuestion question = query.recordAt(DnsSection.QUESTION);
        response.setAuthoritativeAnswer(true);
        response.addRecord(DnsSection.QUESTION, question);
        List<Answer> answers = records.get(strip(question.name()));
        if (answers == null) {
            response.setCode(DnsResponseCode.NXDOMAIN);
        } else {
            boolean answered = false;
            for (Answer answer : answers) {
                if (answer.type().equals(question.type()) || answer.type().equals(DnsRecordType.CNAME)) {
                    response.addRecord(DnsSection.ANSWER, new DefaultDnsRawRecord(question.name(), answer.type(), answer.ttl(), Unpooled.wrappedBuffer(answer.content())));
                    answered = true;
                }
            }
            for (Answer answer : answers) {
                if (!answered && answer.type().equals(DnsRecordType.SOA)) {
                    response.addRecord(DnsSection.AUTHORITY, new DefaultDnsRawRecord(question.name(), answer.type(), answer.ttl(), Unpooled.wrappedBuffer(answer.content())));
                }
            }
        }
    }
}
