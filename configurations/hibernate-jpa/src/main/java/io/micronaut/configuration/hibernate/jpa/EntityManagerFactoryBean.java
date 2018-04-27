/*
 * Copyright 2018 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.configuration.hibernate.jpa;

import io.micronaut.context.annotation.*;
import io.micronaut.context.env.Environment;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.orm.hibernate5.SpringSessionContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.persistence.Entity;
import javax.sql.DataSource;
import javax.validation.ValidatorFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A factory bean for constructing the {@link javax.persistence.EntityManagerFactory}
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
@EachBean(DataSource.class)
public class EntityManagerFactoryBean {


    private final JpaConfiguration jpaConfiguration;
    private final Environment environment;
    private final String sessionFactoryName;

    public EntityManagerFactoryBean(@Parameter String name,JpaConfiguration jpaConfiguration, Environment environment) {
        this.jpaConfiguration = jpaConfiguration;
        this.environment = environment;
        this.sessionFactoryName = name;
    }


    @Context
    @Requires(entities = Entity.class)
    @Bean(preDestroy = "close")
    protected SessionFactory entityManager(
            DataSource dataSource,
            @Nullable ValidatorFactory validatorFactory) {

        Map<String,Object > additionalSettings = new LinkedHashMap<>();
        additionalSettings.put(AvailableSettings.DATASOURCE, dataSource);
        additionalSettings.put(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, SpringSessionContext.class.getName());
        additionalSettings.put(AvailableSettings.SESSION_FACTORY_NAME,sessionFactoryName);
        additionalSettings.put(AvailableSettings.SESSION_FACTORY_NAME_IS_JNDI,false);
        StandardServiceRegistry serviceRegistry = jpaConfiguration.buildStandardServiceRegistry(
                additionalSettings
        );
        MetadataSources metadataSources = createMetadataSources(serviceRegistry);
        environment.scan(Entity.class).forEach(metadataSources::addAnnotatedClass);
        Metadata metadata = metadataSources.buildMetadata();
        SessionFactoryBuilder sessionFactoryBuilder = metadata.getSessionFactoryBuilder();
        if(validatorFactory != null) {
            sessionFactoryBuilder.applyValidatorFactory(validatorFactory);
        }
        customizeSessionFactory(sessionFactoryBuilder);

        return sessionFactoryBuilder.build();
    }

    /**
     * Sub classes can override to customise the session factory
     *
     * @param sessionFactoryBuilder The builder
     */
    @SuppressWarnings("WeakerAccess")
    protected void customizeSessionFactory(@Nonnull SessionFactoryBuilder sessionFactoryBuilder) {
        //no-op
    }


    /**
     * Creates the {@link MetadataSources} for the given registry
     *
     * @param serviceRegistry The registry
     * @return The sources
     */
    @SuppressWarnings("WeakerAccess")
    protected MetadataSources createMetadataSources(@Nonnull StandardServiceRegistry serviceRegistry) {
        return new MetadataSources(serviceRegistry);
    }
}
