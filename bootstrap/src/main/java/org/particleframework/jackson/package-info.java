/**
 * Configuration for the Jackson JSON parser
 */
@Configuration
@Requires(classes = ObjectMapper.class)
package org.particleframework.jackson;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.particleframework.context.annotation.Configuration;
import org.particleframework.context.annotation.Requires;