package io.micronaut.configurations.aws;/*
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

import com.amazonaws.SdkClientException;
import com.amazonaws.auth.AWSCredentials;
import com.amazonaws.auth.AWSCredentialsProvider;
import com.amazonaws.auth.BasicAWSCredentials;
import com.amazonaws.auth.BasicSessionCredentials;
import com.amazonaws.util.StringUtils;
import io.micronaut.context.env.Environment;
import io.micronaut.context.env.Environment;


/**
 * A {@link AWSCredentialsProvider} that reads from the {@link Environment}
 *
 * @author graemerocher
 * @since 1.0
 */
public class EnvironmentAWSCredentialsProvider implements AWSCredentialsProvider {
    /** Environment variable name for the AWS access key ID */
    public static final String ACCESS_KEY_ENV_VAR = "aws.accessKeyId";

    /** Alternate environment variable name for the AWS access key ID */
    public static final String ALTERNATE_ACCESS_KEY_ENV_VAR = "aws.accessKey";

    /** Environment variable name for the AWS secret key */
    public static final String SECRET_KEY_ENV_VAR = "aws.secretKey";

    /** Alternate environment variable name for the AWS secret key */
    public static final String ALTERNATE_SECRET_KEY_ENV_VAR = "aws.secretAccessKey";

    /** Environment variable name for the AWS session token */
    public static final String AWS_SESSION_TOKEN_ENV_VAR = "aws.sessionToken";

    private final Environment environment;

    public EnvironmentAWSCredentialsProvider(Environment environment) {
        this.environment = environment;
    }

    @Override
    public AWSCredentials getCredentials() {
        String accessKey = environment.getProperty(ACCESS_KEY_ENV_VAR, String.class, environment.getProperty(ALTERNATE_ACCESS_KEY_ENV_VAR, String.class, (String)null));

        String secretKey = environment.getProperty(SECRET_KEY_ENV_VAR, String.class, environment.getProperty(ALTERNATE_SECRET_KEY_ENV_VAR, String.class, (String)null));
        accessKey = StringUtils.trim(accessKey);
        secretKey = StringUtils.trim(secretKey);
        String sessionToken = StringUtils.trim(environment.getProperty(AWS_SESSION_TOKEN_ENV_VAR, String.class, (String)null));

        if (StringUtils.isNullOrEmpty(accessKey) || StringUtils.isNullOrEmpty(secretKey)) {

            throw new SdkClientException(
                    "Unable to load AWS credentials from environment " +
                            "(" + ACCESS_KEY_ENV_VAR + " (or " + ALTERNATE_ACCESS_KEY_ENV_VAR + ") and " +
                            SECRET_KEY_ENV_VAR + " (or " + ALTERNATE_SECRET_KEY_ENV_VAR + "))");
        }

        return sessionToken == null ?
                new BasicAWSCredentials(accessKey, secretKey)
                :
                new BasicSessionCredentials(accessKey, secretKey, sessionToken);
    }

    @Override
    public void refresh() {

    }

    @Override
    public String toString() {
        return getClass().getSimpleName();
    }
}
