package io.micronaut.inject.requires.configprops;

import io.micronaut.context.annotation.Context;
import io.micronaut.context.annotation.Requirements;
import io.micronaut.context.annotation.Requires;
import jakarta.inject.Singleton;

@Singleton
@Requires(configProperties = OuterConfig.class, configProperty = "outerProperty")
class A {}

@Singleton
@Requires(configProperties = OuterConfig.InnerConfig.class, configProperty = "innerProperty")
class B {}

@Singleton
@Requires(configProperties = InheritedConfig.class, configProperty = "inheritedProperty")
class C {}

@Singleton
@Requires(configProperties = OuterConfig.class, configProperty = "outerProperty", value = "true")
class D {}

@Singleton
@Requirements({
    @Requires(configProperties = OuterConfig.class, configProperty = "outerProperty", notEquals = "enabled"),
    @Requires(configProperties = OuterConfig.InnerConfig.class, configProperty = "innerProperty", notEquals = "enabled")
})
class E {}

@Singleton
@Requirements({
    @Requires(configProperties = TypesConfig.class, configProperty = "intProperty", value = "1"),
    @Requires(configProperties = TypesConfig.class, configProperty = "boolProperty", value = "true"),
    @Requires(configProperties = TypesConfig.class, configProperty = "stringProperty", value = "test")
})
class F {}

@Singleton
@Requires(configProperties = NotConfigurationProperties.class)
class G {}

@Singleton
@Requires(configProperties = ToggleableConfig.class)
class H {}

@Singleton
@Requires(configProperties =  ToggleableConfig.class, configProperty = "property", value = "true")
class I {}