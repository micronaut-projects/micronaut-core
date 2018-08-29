package io.micronaut.security.ldap.ssl;

import io.micronaut.http.ssl.SslConfiguration;

import javax.net.SocketFactory;
import javax.net.ssl.SSLSocketFactory;
import java.io.IOException;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

public class LdapSslSocketFactory extends SSLSocketFactory {

    public static ThreadLocal<SslConfiguration> configurationProvider = new ThreadLocal<>();
    private final SSLSocketFactory socketFactory;

    public LdapSslSocketFactory() {
        SslConfiguration configuration = configurationProvider.get();
        socketFactory = new LdapClientSslBuilder(configuration).build().orElseThrow(() -> new RuntimeException("Could not build the SSL socket factory"));
    }

    public static SocketFactory getDefault() {
        return new LdapSslSocketFactory();
    }

    @Override
    public String[] getDefaultCipherSuites() {
        return socketFactory.getDefaultCipherSuites();
    }

    @Override
    public String[] getSupportedCipherSuites() {
        return configurationProvider.get().getCiphers().orElse(socketFactory.getSupportedCipherSuites());
    }

    @Override
    public Socket createSocket(Socket socket, String s, int i, boolean b) throws IOException {
        return socketFactory.createSocket(socket, s, i , b);
    }

    @Override
    public Socket createSocket(String s, int i) throws IOException, UnknownHostException {
        return socketFactory.createSocket(s, i);
    }

    @Override
    public Socket createSocket(String s, int i, InetAddress inetAddress, int i1) throws IOException, UnknownHostException {
        return socketFactory.createSocket(s, i, inetAddress, i1);
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i) throws IOException {
        return socketFactory.createSocket(inetAddress, i);
    }

    @Override
    public Socket createSocket(InetAddress inetAddress, int i, InetAddress inetAddress1, int i1) throws IOException {
        return socketFactory.createSocket(inetAddress, i, inetAddress1, i1);
    }

}
