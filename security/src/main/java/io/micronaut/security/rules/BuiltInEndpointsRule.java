package io.micronaut.security.rules;

import io.micronaut.context.BeanContext;
import io.micronaut.http.HttpMethod;
import io.micronaut.management.endpoint.EndpointConfiguration;
import io.micronaut.security.config.InterceptUrlMapPattern;
import io.micronaut.security.token.configuration.TokenConfiguration;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

@Singleton
public class BuiltInEndpointsRule extends InterceptUrlMapRule {

    public static final int ORDER = 0;
    private final List<InterceptUrlMapPattern> patternList;


    public BuiltInEndpointsRule(TokenConfiguration tokenConfiguration, BeanContext beanContext) {
        super(tokenConfiguration);
        this.patternList = createPatternList(beanContext.getBeansOfType(EndpointConfiguration.class));
    }

    protected List<InterceptUrlMapPattern> createPatternList(Collection<EndpointConfiguration> endpointConfigurations) {
        if (endpointConfigurations == null || endpointConfigurations.isEmpty()) {
            return new ArrayList<>();
        }
        List<InterceptUrlMapPattern> patterns = new ArrayList<>();
        List<String> anonymousAccess = Collections.singletonList(SecurityRule.IS_ANONYMOUS);
        List<String> authenticatedAccess = Collections.singletonList(SecurityRule.IS_AUTHENTICATED);
        for (HttpMethod method : Arrays.asList(HttpMethod.GET, HttpMethod.POST)) {
            patterns.addAll(endpointConfigurations.stream()
                    .filter(ec -> ec.isEnabled().isPresent() ? ec.isEnabled().get() : false)
                    .map(ec -> new InterceptUrlMapPattern(endpointPattern(ec), (ec.isSensitive().isPresent() ? ec.isSensitive().get() : false) ? authenticatedAccess : anonymousAccess, method))
                    .collect(Collectors.toList()));
        }
        return patterns;
    }

    /**
     *
     * @param ec Instance of {@link EndpointConfiguration}
     * @return / + endpoint.id
     */
    public static String endpointPattern(EndpointConfiguration ec) {
        StringBuilder sb = new StringBuilder();
        sb.append("/");
        sb.append(ec.getId());
        return sb.toString();
    }

    @Override
    protected List<InterceptUrlMapPattern> getPatternList() {
        return patternList;
    }

    @Override
    public int getOrder() {
        return ORDER;
    }
}
