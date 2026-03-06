package io.micronaut.inject.configproperties;

import io.micronaut.context.annotation.ConfigurationProperties;

@ConfigurationProperties("configuration-example")
class DigitsConfigurationExample {
    int camelCaseProp;
    int camelCase1234Prop;
    int snakeCaseProp;
    int snakeCase1234Prop;
    int kebabCaseProp;
    int kebabCase1234Prop;

}
