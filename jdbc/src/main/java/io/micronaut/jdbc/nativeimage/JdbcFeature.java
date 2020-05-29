/*
 * Copyright 2017-2020 original authors
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
package io.micronaut.jdbc.nativeimage;

import com.oracle.svm.core.annotate.AutomaticFeature;
import com.oracle.svm.core.configure.ResourcesRegistry;
import io.micronaut.core.annotation.Internal;
import org.graalvm.nativeimage.ImageSingletons;
import org.graalvm.nativeimage.hosted.Feature;
import org.graalvm.nativeimage.hosted.RuntimeClassInitialization;
import org.graalvm.nativeimage.hosted.RuntimeReflection;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/**
 * A JDBC feature that configures JDBC drivers correctly for native image.
 *
 * @author graemerocher
 * @author Iván López
 * @since 1.3.6
 */
@AutomaticFeature
@Internal
final class JdbcFeature implements Feature {

    private static final String H2_DRIVER = "org.h2.Driver";
    private static final String POSTGRESQL_DRIVER = "org.postgresql.Driver";
    private static final String SQL_SERVER_DRIVER = "com.microsoft.sqlserver.jdbc.SQLServerDriver";
    private static final String MARIADB_DRIVER = "org.mariadb.jdbc.Driver";
    private static final String ORACLE_DRIVER = "oracle.jdbc.OracleDriver";

    private ResourcesRegistry resourcesRegistry;

    @Override
    public void beforeAnalysis(BeforeAnalysisAccess access) {
        // h2
        handleH2(access);

        // postgres
        handlePostgres(access);

        // sql server
        handleSqlServer(access);

        // mariadb
        handleMariadb(access);

        // oracle
        handleOracle(access);

    }

    private void handleH2(BeforeAnalysisAccess access) {
        Class<?> h2Driver = access.findClassByName(H2_DRIVER);
        if (h2Driver != null) {
            registerAllIfPresent(access, "org.h2.mvstore.db.MVTableEngine");

            RuntimeReflection.register(h2Driver);
            RuntimeClassInitialization.initializeAtBuildTime(h2Driver);

            ResourcesRegistry resourcesRegistry = getResourceRegistry();
            if (resourcesRegistry != null) {
                resourcesRegistry.addResources("META-INF/services/java.sql.Driver");
                resourcesRegistry.addResources("org/h2/util/data.zip");
            }
        }
    }

    private void handlePostgres(BeforeAnalysisAccess access) {
        Class<?> postgresDriver = access.findClassByName(POSTGRESQL_DRIVER);
        if (postgresDriver != null) {
            RuntimeReflection.register(postgresDriver);
            RuntimeClassInitialization.initializeAtBuildTime(postgresDriver);

            initializeAtBuildTime(access,
                    POSTGRESQL_DRIVER,
                    "org.postgresql.util.SharedTimer"
            );

            ResourcesRegistry resourcesRegistry = getResourceRegistry();
            if (resourcesRegistry != null) {
                resourcesRegistry.addResources("META-INF/services/java.sql.Driver");
            }
        }
    }

    private void handleOracle(BeforeAnalysisAccess access) {
        Class<?> oracleDriver = access.findClassByName(ORACLE_DRIVER);
        if (oracleDriver != null) {
            registerAllIfPresent(access, "oracle.jdbc.driver.T4CDriverExtension");
            registerAllIfPresent(access, "oracle.jdbc.driver.T2CDriverExtension");
            registerAllIfPresent(access, "oracle.net.ano.Ano");
            registerAllIfPresent(access, "oracle.net.ano.AuthenticationService");
            registerAllIfPresent(access, "oracle.net.ano.DataIntegrityService");
            registerAllIfPresent(access, "oracle.net.ano.EncryptionService");
            registerAllIfPresent(access, "oracle.net.ano.SupervisorService");

            ResourcesRegistry resourcesRegistry = getResourceRegistry();
            if (resourcesRegistry != null) {
                resourcesRegistry.addResources("META-INF/services/java.sql.Driver");
                resourcesRegistry.addResources("oracle/sql/converter_xcharset/lx20002.glb");
                resourcesRegistry.addResources("oracle/sql/converter_xcharset/lx2001f.glb");
                resourcesRegistry.addResources("oracle/sql/converter_xcharset/lx200b2.glb");
                resourcesRegistry.addResourceBundles("oracle.net.jdbc.nl.mesg.NLSR");
                resourcesRegistry.addResourceBundles("oracle.net.mesg.Message");
            }

            initializeAtBuildTime(
                    access,
                    "oracle.net.jdbc.nl.mesg.NLSR_en",
                    "oracle.jdbc.driver.DynamicByteArray",
                    "oracle.sql.ConverterArchive",
                    "oracle.sql.converter.CharacterConverterJDBC",
                    "oracle.sql.converter.CharacterConverter1Byte"
            );

            initializeAtRuntime(access, "java.sql.DriverManager");
        }
    }

    private void handleMariadb(BeforeAnalysisAccess access) {
        Class<?> mariaDriver = access.findClassByName(MARIADB_DRIVER);
        if (mariaDriver != null) {
            RuntimeReflection.register(mariaDriver);
            registerAllAccess(mariaDriver);

            ResourcesRegistry resourcesRegistry = getResourceRegistry();
            if (resourcesRegistry != null) {
                resourcesRegistry.addResources("META-INF/services/java.sql.Driver");
            }

            registerAllIfPresent(access, "org.mariadb.jdbc.util.Options");

            RuntimeClassInitialization.initializeAtBuildTime("org.mariadb");
            RuntimeClassInitialization.initializeAtRunTime("org.mariadb.jdbc.credential.aws");

            initializeAtRuntime(access, "org.mariadb.jdbc.internal.failover.impl.MastersSlavesListener");
            initializeAtRuntime(access, "org.mariadb.jdbc.internal.com.send.authentication.SendPamAuthPacket");
        }
    }

    private void initializeAtRuntime(BeforeAnalysisAccess access, String n) {
        Class<?> t = access.findClassByName(n);
        if (t != null) {
            RuntimeClassInitialization.initializeAtRunTime(t);
        }
    }

    private void initializeAtBuildTime(BeforeAnalysisAccess access, String... names) {
        for (String name : names) {
            Class<?> t = access.findClassByName(name);
            if (t != null) {
                RuntimeClassInitialization.initializeAtBuildTime(t);
            }
        }
    }

    private void registerAllIfPresent(BeforeAnalysisAccess access, String n) {
        Class<?> t = access.findClassByName(n);
        if (t != null) {
            registerAllAccess(t);
        }
    }

    private void registerAllAccess(Class<?> t) {
        RuntimeReflection.register(t);
        RuntimeReflection.registerForReflectiveInstantiation(t);
        for (Method method : t.getMethods()) {
            RuntimeReflection.register(method);
        }
        Field[] fields = t.getFields();
        for (Field field : fields) {
            RuntimeReflection.register(field);
        }
    }

    private void handleSqlServer(BeforeAnalysisAccess access) {
        Class<?> sqlServerDriver = access.findClassByName(SQL_SERVER_DRIVER);
        if (sqlServerDriver != null) {
            RuntimeReflection.register(sqlServerDriver);
            registerAllAccess(sqlServerDriver);

            RuntimeClassInitialization.initializeAtBuildTime(SQL_SERVER_DRIVER);

            initializeAtBuildTime(access, "com.microsoft.sqlserver.jdbc.Util");
            initializeAtBuildTime(access, "com.microsoft.sqlserver.jdbc.SQLServerException");
            registerAllIfPresent(access, "com.microsoft.sqlserver.jdbc.SQLServerDriver");

            ResourcesRegistry resourcesRegistry = getResourceRegistry();
            if (resourcesRegistry != null) {
                resourcesRegistry.addResources("META-INF/services/java.sql.Driver");
                resourcesRegistry.addResources("javax.crypto.Cipher.class");
                resourcesRegistry.addResourceBundles("com.microsoft.sqlserver.jdbc.SQLServerResource");
            }
        }
    }

    private ResourcesRegistry getResourceRegistry() {
        if (resourcesRegistry == null) {
            resourcesRegistry = ImageSingletons.lookup(ResourcesRegistry.class);
        }
        return resourcesRegistry;
    }
}
