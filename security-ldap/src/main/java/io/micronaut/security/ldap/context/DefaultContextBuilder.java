package io.micronaut.security.ldap.context;

import io.micronaut.http.ssl.SslConfiguration;
import io.micronaut.security.ldap.ssl.LdapSslSocketFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.inject.Singleton;
import javax.naming.Context;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.naming.directory.InitialDirContext;
import java.util.Properties;

@Singleton
public class DefaultContextBuilder implements ContextBuilder {

    private static final Logger LOG = LoggerFactory.getLogger(DefaultContextBuilder.class);

    public DirContext build(ContextSettings contextSettings) throws NamingException {
        return build(contextSettings.getFactory(),
                contextSettings.getUrl(),
                contextSettings.getDn(),
                contextSettings.getPassword(),
                contextSettings.getPooled(),
                contextSettings.getSslConfiguration());
    }

    public DirContext build(String factory, String server, String user, String password, boolean pooled, SslConfiguration sslConfiguration) throws NamingException {
        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, factory);
        props.put(Context.PROVIDER_URL, server);
        props.put(Context.SECURITY_AUTHENTICATION, "simple");
        props.put(Context.SECURITY_PRINCIPAL, user);
        props.put(Context.SECURITY_CREDENTIALS, password);
        if (pooled) {
            props.put("com.sun.jndi.ldap.connect.pool", "true");
        }

        /*if (server.startsWith("ldaps")) {
            props.put( "java.naming.ldap.factory.socket", LdapSslSocketFactory.class.getName());
            LdapSslSocketFactory.configurationProvider.set(sslConfiguration);
        }

        try {
            return new InitialDirContext(props);
        } finally {
            LdapSslSocketFactory.configurationProvider.remove();
        }*/

        return new InitialDirContext(props);
    }

    @Override
    public void close(DirContext context) {
        if (context != null) {
            try {
                context.close();
            } catch (Throwable e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Exception occurred while closing an LDAP context", e);
                }
            }
        }
    }
}
