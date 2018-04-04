package io.micronaut.management.endpoint.info.source;

import io.micronaut.context.annotation.Requires;
import io.micronaut.context.annotation.Value;
import io.micronaut.context.env.*;
import io.micronaut.core.async.SupplierUtil;
import io.micronaut.core.io.ResourceResolver;
import io.micronaut.management.endpoint.info.InfoEndpoint;
import io.micronaut.management.endpoint.info.InfoSource;
import io.reactivex.Flowable;
import org.reactivestreams.Publisher;

import javax.inject.Singleton;
import java.io.IOException;
import java.net.URL;
import java.util.*;
import java.util.function.Supplier;

/**
 * <p>An {@link InfoSource} that retrieves info from Git properties. </p>
 *
 * @author Zachary Klein
 * @since 1.0
 */
//TODO: @Refreshable
@Singleton
@Requires(beans = InfoEndpoint.class)
@Requires(property = "endpoints.info.git.enabled", notEquals = "false")
public class GitInfoSource implements InfoSource {

    @Value("${endpoints.info.git.location:git.properties}")
    private String gitPropertiesPath;

    private ResourceResolver resourceResolver;
    private final Supplier<MapPropertySource> supplier;

    public GitInfoSource(ResourceResolver resourceResolver) {
        this.resourceResolver = resourceResolver;
        this.supplier = SupplierUtil.memoized(this::retrieveGitInfo);
    }

    @Override
    public Publisher<PropertySource> getSource() {
        return Flowable.just(supplier.get());
    }


    private MapPropertySource retrieveGitInfo() {
        Optional<URL> url = resourceResolver.getResource("classpath:" + gitPropertiesPath);
        if (url.isPresent()) {
            PropertiesPropertySourceLoader propertySourceLoader = new PropertiesPropertySourceLoader();
            String propertySourceName = gitPropertiesPath;

            try {
                if (propertySourceName != null) {
                    Map<String, Object> properties = propertySourceLoader.read(propertySourceName, url.get().openStream());
                    return new MapPropertySource("git", new LinkedHashMap<>(properties));
                }
            } catch (IOException ex) {
            }

        }
        return null;
    }
}

