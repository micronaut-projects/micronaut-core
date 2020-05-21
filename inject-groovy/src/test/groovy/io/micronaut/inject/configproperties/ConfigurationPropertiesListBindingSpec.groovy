package io.micronaut.inject.configproperties

import io.micronaut.context.ApplicationContext
import io.micronaut.context.annotation.ConfigurationProperties
import io.micronaut.context.env.PropertySource
import io.micronaut.context.env.yaml.YamlPropertySourceLoader
import io.netty.handler.codec.http.HttpMethod
import org.yaml.snakeyaml.Yaml
import spock.lang.Specification

class ConfigurationPropertiesListBindingSpec extends Specification {

    void "test bind to list of POJO"() {
        given:
        def map = new YamlPropertySourceLoader().read("myconfig.yml", '''
my:
  security:
    intercept-url-map:
      graphql:
        -
          pattern: /api/public/graphql
          access:
            - isAnonymous()
        -
          pattern: /api/public/graphiql
          access:
            - isAnonymous()
'''.bytes)
        ApplicationContext context = ApplicationContext.builder()
            .propertySources(PropertySource.of(map))
            .start()

        SecurityConfig config = context.getBean(SecurityConfig)

        expect:
        config.interceptUrlMap['graphql'].size() == 2
        config.interceptUrlMap['graphql'][0] instanceof BindableInterceptUrlMapPattern
        config.interceptUrlMap['graphql'][0].pattern == '/api/public/graphql'

        cleanup:
        context.close()
    }

    @ConfigurationProperties("my.security")
    static class SecurityConfig {
        Map<String, List<BindableInterceptUrlMapPattern>> interceptUrlMap
    }

    static class BindableInterceptUrlMapPattern {
        String pattern;
        List<String> access = new ArrayList<>();
        HttpMethod httpMethod = null;
    }
}
