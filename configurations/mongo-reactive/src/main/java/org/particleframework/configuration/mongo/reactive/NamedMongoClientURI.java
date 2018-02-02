package org.particleframework.configuration.mongo.reactive;

import com.mongodb.ConnectionString;
import org.particleframework.context.annotation.Argument;
import org.particleframework.context.annotation.EachProperty;

@EachProperty(value = "particle.mongo.servers")
public class NamedMongoClientURI {

    private final String serverName;
    private ConnectionString connectionString;

    public NamedMongoClientURI(@Argument String name) {
        this.serverName = name;
    }

    /**
     * @return The name of the server
     */
    public String getServerName() {
        return serverName;
    }

    public ConnectionString getUri() {
        return connectionString;
    }

    public void setUri(String uri) {
        this.connectionString = new ConnectionString(uri);
    }
}
