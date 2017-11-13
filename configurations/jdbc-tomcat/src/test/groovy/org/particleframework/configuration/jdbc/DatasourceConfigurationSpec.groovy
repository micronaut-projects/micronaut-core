package org.particleframework.configuration.jdbc

import org.apache.tomcat.jdbc.pool.DataSource
import org.particleframework.context.ApplicationContext
import org.particleframework.context.DefaultApplicationContext
import org.particleframework.context.env.MapPropertySource
import spock.lang.Specification

import java.sql.ResultSet

class DatasourceConfigurationSpec extends Specification {

    void "test no configuration"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.start()

        expect: "No beans are created"
        !applicationContext.containsBean(DataSource)
        !applicationContext.containsBean(DatasourceConfiguration)

        cleanup:
        applicationContext.close()
    }

    void "test blank configuration"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'datasources.default.jndiName': 'x'
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(DataSource)
        applicationContext.containsBean(DatasourceConfiguration)

        when:
        DataSource dataSource = applicationContext.getBean(DataSource)

        then: //The default configuration is supplied because H2 is on the classpath
        dataSource.url == 'jdbc:h2:mem:default;DB_CLOSE_DELAY=-1;DB_CLOSE_ON_EXIT=FALSE'
        dataSource.username == 'sa'
        dataSource.poolProperties.password == ''
        dataSource.name == 'default'
        dataSource.driverClassName == 'org.h2.Driver'
        dataSource.abandonWhenPercentageFull == 0
        dataSource.accessToUnderlyingConnectionAllowed


        cleanup:
        applicationContext.close()
    }

    void "test operations with a blank connection"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'datasources.default': [:]
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(DataSource)
        applicationContext.containsBean(DatasourceConfiguration)

        when:
        DataSource dataSource = applicationContext.getBean(DataSource)
        ResultSet resultSet = dataSource.getConnection().prepareStatement("SELECT H2VERSION() FROM DUAL").executeQuery()
        resultSet.next()
        String version = resultSet.getString(1)

        then:
        version == '1.4.196'

        cleanup:
        applicationContext.close()
    }

    void "test all properties are bindable"() {
        given:
        ApplicationContext applicationContext = new DefaultApplicationContext("test")
        applicationContext.environment.addPropertySource(MapPropertySource.of(
                'datasources.default.abandonWhenPercentageFull': 99,
                'datasources.default.accessToUnderlyingConnectionAllowed': false,
                'datasources.default.alternateUsernameAllowed': true,
                'datasources.default.commitOnReturn': true,
                'datasources.default.connectionProperties': 'prop1=value1;prop2=value2',
                'datasources.default.jndiName': 'java:comp/env/FooBarPool',
                'datasources.default.dbProperties.DB_CLOSE_ON_EXIT': true,
                'datasources.default.dbProperties.DB_CLOSE_DELAY': 1,
        ))
        applicationContext.start()

        expect:
        applicationContext.containsBean(DataSource)
        applicationContext.containsBean(DatasourceConfiguration)

        when:
        DataSource dataSource = applicationContext.getBean(DataSource)

        then:
        dataSource.abandonWhenPercentageFull == 99
        !dataSource.accessToUnderlyingConnectionAllowed
        dataSource.alternateUsernameAllowed
        dataSource.commitOnReturn
        dataSource.connectionProperties == 'prop1=value1;prop2=value2'
        dataSource.dataSourceJNDI == 'java:comp/env/FooBarPool'
        dataSource.dbProperties.get('DB_CLOSE_ON_EXIT') == 'TRUE'
        dataSource.dbProperties.get('DB_CLOSE_DELAY') == '1'
        /*//dataSource.setDefaultAutoCommit();
        //dataSource.setDefaultCatalog();
        //dataSource.setDefaultReadOnly();
        dataSource.setDefaultTransactionIsolation();
        dataSource.setDriverClassName(); //determine
        dataSource.setFairQueue();
        dataSource.setIgnoreExceptionOnPreLoad();
        dataSource.setInitialSize();
        dataSource.setInitSQL();
        dataSource.setJdbcInterceptors();
        dataSource.setJmxEnabled();
        dataSource.setLogAbandoned();
        dataSource.setLoginTimeout();
        dataSource.setLogValidationErrors();
        dataSource.setMaxActive();
        dataSource.setMaxAge();
        dataSource.setMaxIdle();
        dataSource.setMaxWait();
        dataSource.setMinEvictableIdleTimeMillis();
        dataSource.setMinIdle();
        dataSource.setName();
        dataSource.setNumTestsPerEvictionRun();
        dataSource.setPassword();
        dataSource.setPropagateInterruptState();
        dataSource.setRemoveAbandoned();
        dataSource.setRemoveAbandonedTimeout();
        dataSource.setRollbackOnReturn();
        dataSource.setSuspectTimeout();
        dataSource.setTestOnBorrow();
        dataSource.setTestOnConnect();
        dataSource.setTestOnReturn();
        dataSource.setTestWhileIdle();
        dataSource.setTimeBetweenEvictionRunsMillis();
        dataSource.setUrl();
        dataSource.setUseDisposableConnectionFacade();
        dataSource.setUseEquals();
        dataSource.setUseLock();
        dataSource.setUsername();
        dataSource.setUseStatementFacade();
        dataSource.setValidationInterval();
        dataSource.setValidationQuery();
        dataSource.setValidationQueryTimeout();
        dataSource.setValidator();
        dataSource.setValidatorClassName();*/

        cleanup:
        applicationContext.close()
    }
}
