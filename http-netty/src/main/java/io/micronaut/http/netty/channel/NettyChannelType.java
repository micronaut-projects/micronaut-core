package io.micronaut.http.netty.channel;

/**
 * Different netty channel types.
 *
 * @since 4.0.0
 */
public enum NettyChannelType {
    /**
     * @see io.netty.channel.socket.ServerSocketChannel
     */
    SERVER_SOCKET,
    /**
     * @see io.netty.channel.socket.SocketChannel
     */
    CLIENT_SOCKET,
    /**
     * @see io.netty.channel.unix.ServerDomainSocketChannel
     */
    DOMAIN_SERVER_SOCKET,
    /**
     * @see io.netty.channel.socket.DatagramChannel
     */
    DATAGRAM_SOCKET,
}
