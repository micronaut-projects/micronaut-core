package org.particleframework.configuration.hibernate.validator

import groovy.transform.CompileStatic
import org.particleframework.context.env.Environment

import javax.inject.Provider
import javax.inject.Singleton
import javax.validation.Configuration
import javax.validation.Validation
import javax.validation.ValidatorFactory

/**
 * Provides a {@link ValidatorFactory} instance
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Singleton
@CompileStatic
class ValidatorFactoryProvider implements Provider<ValidatorFactory> {
    final Optional<Environment> environment

    ValidatorFactoryProvider(Optional<Environment> environment) {
        this.environment = environment
    }

    @Override
    ValidatorFactory get() {
        Configuration validatorConfiguration = Validation.byDefaultProvider()
                                                        .configure()

        validatorConfiguration = validatorConfiguration.ignoreXmlConfiguration()
        if(environment.isPresent()) {
            Environment env = environment.get()
            Optional<Map<String,String>> config = env.getProperty("hibernate.validator", Map, [K:String, V:String])
            if(config.isPresent()) {
                for(entry in config.get()) {
                    validatorConfiguration = validatorConfiguration.addProperty(
                        "hibernate.validator.${entry.key}".toString(),
                            entry.value.toString()
                    )
                }
            }

        }
        return validatorConfiguration.buildValidatorFactory()
    }
}
