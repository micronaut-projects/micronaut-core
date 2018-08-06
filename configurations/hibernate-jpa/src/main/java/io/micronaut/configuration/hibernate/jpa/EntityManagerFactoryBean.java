/*
 * Copyright 2017-2018 original authors
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

import io.micronaut.configuration.hibernate.jpa.scope.CurrentSession;
import io.micronaut.context.BeanLocator;
import io.micronaut.context.annotation.Bean;
import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.EachBean;
import io.micronaut.context.annotation.Factory;
import io.micronaut.context.annotation.Parameter;
import io.micronaut.context.annotation.Requires;
import io.micronaut.context.env.Environment;
import io.micronaut.core.util.ArrayUtils;
import io.micronaut.inject.qualifiers.Qualifiers;
import org.hibernate.Interceptor;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.SessionFactoryBuilder;
import org.hibernate.boot.registry.StandardServiceRegistry;
import org.hibernate.cfg.AvailableSettings;
import org.springframework.orm.hibernate5.SpringSessionContext;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import javax.inject.Inject;
import javax.inject.Singleton;
import javax.persistence.Entity;
import javax.sql.DataSource;
import javax.validation.ValidatorFactory;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * A factory bean for constructing the {@link javax.persistence.EntityManagerFactory}.
 *
 * @author graemerocher
 * @since 1.0
 */
@Factory
public class EntityManagerFactoryBean {

    private final JpaConfiguration jpaConfiguration;
    private final Environment environment;
    private final BeanLocator beanLocator;
    private Interceptor hibernateInterceptor;

    /**
     * @param jpaConfiguration The JPA configuration
     * @param environment      The environment
     * @param beanLocator      The bean locator
     */
    public EntityManagerFactoryBean(
        JpaConfiguration jpaConfiguration,
        Environment environment,
        BeanLocator beanLocator) {

        this.jpaConfiguration = jpaConfiguration;
        this.environment = environment;
        this.beanLocator = beanLocator;
    }

    /**
     * Sets the {@link Interceptor} to use.
     *
     * @param hibernateInterceptor The hibernate interceptor
     */
    @Inject
    public void setHibernateInterceptor(@Nullable Interceptor hibernateInterceptor) {
        this.hibernateInterceptor = hibernateInterceptor;
    }

    /**
     * Builds the {@link StandardServiceRegistry} bean for the given {@link DataSource}.
     *
     * @param dataSourceName The data source name
     * @param dataSource     The data source
     * @return The {@link StandardServiceRegistry}
     */
    @Singleton
    @EachBean(DataSource.class)
    protected StandardServiceRegistry hibernateStandardServiceRegistry(
        @Parameter String dataSourceName,
        DataSource dataSource) {

        Map<String, Object> additionalSettings = new LinkedHashMap<>();
        additionalSettings.put(AvailableSettings.DATASOURCE, dataSource);
        additionalSettings.put(AvailableSettings.CURRENT_SESSION_CONTEXT_CLASS, SpringSessionContext.class.getName());
        additionalSettings.put(AvailableSettings.SESSION_FACTORY_NAME, dataSourceName);
        additionalSettings.put(AvailableSettings.SESSION_FACTORY_NAME_IS_JNDI, false);
        JpaConfiguration jpaConfiguration = beanLocator.findBean(JpaConfiguration.class, Qualifiers.byName(dataSourceName))
            .orElse(this.jpaConfiguration);
        return jpaConfiguration.buildStandardServiceRegistry(
            additionalSettings
        );
    }

    /**
     * Builds the {@link MetadataSources} for the given {@link StandardServiceRegistry}.
     *
     * @param dataSourceName          The data source name
     * @param standardServiceRegistry The standard service registry
     * @return The {@link MetadataSources}
     */
    @Singleton
    @EachBean(StandardServiceRegistry.class)
    @Requires(entities = Entity.class)
    protected MetadataSources hibernateMetadataSources(
        @Parameter String dataSourceName,
        StandardServiceRegistry standardServiceRegistry) {

        MetadataSources metadataSources = createMetadataSources(standardServiceRegistry);
        JpaConfiguration jpaConfiguration = beanLocator.findBean(JpaConfiguration.class, Qualifiers.byName(dataSourceName))
            .orElse(this.jpaConfiguration);

        String[] packagesToScan = jpaConfiguration.getPackagesToScan();
        if (ArrayUtils.isNotEmpty(packagesToScan)) {
            environment.scan(Entity.class, packagesToScan).forEach(metadataSources::addAnnotatedClass);
        } else {
            environment.scan(Entity.class).forEach(metadataSources::addAnnotatedClass);
        }
        return metadataSources;
    }

    /**
     * Builds the {@link SessionFactoryBuilder} to use.
     *
     * @param metadataSources  The {@link MetadataSources}
     * @param validatorFactory The {@link ValidatorFactory}
     * @return The {@link SessionFactoryBuilder}
     */
    @Singleton
    @EachBean(MetadataSources.class)
    @Requires(beans = MetadataSources.class)
    protected SessionFactoryBuilder hibernateSessionFactoryBuilder(
        MetadataSources metadataSources,
        @Nullable ValidatorFactory validatorFactory) {
        Metadata metadata = metadataSources.buildMetadata();
        SessionFactoryBuilder sessionFactoryBuilder = metadata.getSessionFactoryBuilder();
        if (validatorFactory != null) {
            sessionFactoryBuilder.applyValidatorFactory(validatorFactory);
        }

        if (hibernateInterceptor != null) {
            sessionFactoryBuilder.applyInterceptor(hibernateInterceptor);
        }
        return sessionFactoryBuilder;
    }

    /**
     * Builds the actual {@link SessionFactory} from the builder.
     *
     * @param sessionFactoryBuilder The {@link SessionFactoryBuilder}
     * @return The {@link SessionFactory}
     */
    @Context
    @Requires(beans = SessionFactoryBuilder.class)
    @Bean(preDestroy = "close")
    @EachBean(SessionFactoryBuilder.class)
    protected SessionFactory hibernateSessionFactory(SessionFactoryBuilder sessionFactoryBuilder) {
        return sessionFactoryBuilder.build();
    }

    /**
     * Obtains the current session for teh given session factory.
     *
     * @param sessionFactory The session factory
     * @param dataSource The name of the data source.
     * @return The current session
     */
    @EachBean(SessionFactory.class)
    @CurrentSession
    protected Session currentSession(@Parameter String dataSource, SessionFactory sessionFactory) {
        return sessionFactory.getCurrentSession();
    }


    /**
     * Creates the {@link MetadataSources} for the given registry.
     *
     * @param serviceRegistry The registry
     * @return The sources
     */
    @SuppressWarnings("WeakerAccess")
    protected MetadataSources createMetadataSources(@Nonnull StandardServiceRegistry serviceRegistry) {
        return new MetadataSources(serviceRegistry);
    }
}
