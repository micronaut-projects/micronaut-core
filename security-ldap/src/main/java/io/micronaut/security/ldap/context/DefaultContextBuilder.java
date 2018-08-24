package io.micronaut.security.ldap.context;

import io.micronaut.security.ldap.LdapAuthenticationProvider;
import io.micronaut.security.ldap.LdapConfigurationProperties;
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

    private final LdapConfigurationProperties ldap;

    DefaultContextBuilder(LdapConfigurationProperties ldap) {
        this.ldap = ldap;
    }

    @Override
    public DirContext buildManager() throws NamingException {
        return build(ldap.getContext().getManagerDn(),
                ldap.getContext().getManagerPassword(),
                true);
    }

    @Override
    public DirContext build(String user, String password) throws NamingException {
        return build(user, password, false);
    }

    protected DirContext build(String user, String password, boolean pooled) throws NamingException {
        return build(ldap.getContext().getFactory(),
                ldap.getContext().getServer(),
                user,
                password,
                pooled);
    }

    protected DirContext build(String factory, String server, String user, String password, boolean pooled) throws NamingException {
        Properties props = new Properties();
        props.put(Context.INITIAL_CONTEXT_FACTORY, factory);
        props.put(Context.PROVIDER_URL, server);
        props.put(Context.SECURITY_AUTHENTICATION, "simple");
        props.put(Context.SECURITY_PRINCIPAL, user);
        props.put(Context.SECURITY_CREDENTIALS, password);
        if (pooled) {
            props.put("com.sun.jndi.ldap.connect.pool", "true");
        }

        return new InitialDirContext(props);
    }

    @Override
    public void close(DirContext context) {
        if (context != null) {
            try {
                context.close();
            } catch (Throwable e) {
                if (LOG.isDebugEnabled()) {
                    LOG.debug("Exception occurred while closing the JNDI DirContext", e);
                }
            }
        }
    }
}
