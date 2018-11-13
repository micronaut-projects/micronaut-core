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

package io.micronaut.configuration.dbmigration.liquibase.management.endpoint;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;
import io.micronaut.context.annotation.Type;
import liquibase.changelog.RanChangeSet;

import javax.inject.Singleton;
import java.io.IOException;
import java.time.Instant;

/**
 * Jackson serializer for {@link RanChangeSet}
 *
 * @author Iván López
 * @since 1.1
 */
@Singleton
@Type(RanChangeSet.class)
public class RanChangeSetSerializer extends JsonSerializer<RanChangeSet> {

    @Override
    public void serialize(RanChangeSet value, JsonGenerator gen, SerializerProvider provider) throws IOException {
        gen.writeStartObject();
        gen.writeStringField("author", value.getAuthor());
        gen.writeStringField("changeLog", value.getChangeLog());
        gen.writeStringField("comments", value.getComments());
        gen.writeArrayFieldStart("contexts");
        for (String c : value.getContextExpression().getContexts()) {
            gen.writeString(c);
        }
        gen.writeEndArray();
        gen.writeStringField("dateExecuted", Instant.ofEpochMilli(value.getDateExecuted().getTime()).toString());
        gen.writeStringField("deploymentId", value.getDeploymentId());
        gen.writeStringField("description", value.getDescription());
        gen.writeStringField("execType", value.getExecType().toString());
        gen.writeStringField("id", value.getId());
        gen.writeArrayFieldStart("labels");
        for (String c : value.getLabels().getLabels()) {
            gen.writeString(c);
        }
        gen.writeEndArray();
        gen.writeStringField("checksum", ((value.getLastCheckSum() != null) ? value.getLastCheckSum().toString() : null));
        gen.writeNumberField("orderExecuted", value.getOrderExecuted());
        gen.writeStringField("tag", value.getTag());
        gen.writeEndObject();
    }
}
