## Getting Started Docs

Until the AsciiDoc is updated, these docs will help get you started with
Micrometer metrics and will form the basis for the new docs.

### Intro

Key `Micrometer.io` [concepts](http://micrometer.io/docs/concepts) include
a [MeterRegistry](http://micrometer.io/docs/concepts#_registry) to register and use 
meters. A [Meter](http://micrometer.io/docs/concepts#_meters) is something that produces metrics.

A MeterRegistry can have some customizations automatically applied:

* MeterRegistryCustomizer
    * Any bean that implements MeterRegistryCustomizer gets applied to every _applicable_ 
    MeterRegistry bean on creation
    * These beans should be declared with `@Context` to ensure they are available when the 
    bean context is built
    * The implementation of the MeterRegistryCustomizer `supports()` method determines 
    if the customizer is applied to a particular registry
        * If you want all registries to get the customization, simply return return `true`
        * Otherwize, you can evaluate the registry for its class type, its class hierarchy, or 
        other criteria.
        * Rememebr you only get one shot for autoconfiguration; i.e. when the bean context is started.
        * However, in code, you can apply additional customizations to the registry config
    
    ```
        package io.micronaut.configuration.metrics.micrometer;
        
        import io.micrometer.core.instrument.MeterRegistry;
        
        /**
         * Meter registry customizations defined by implementers.
         * Customizers are called on meter registry bean creation.
         */
        public interface MeterRegistryCustomizer {
        
            /**
             * Apply customizations to the specified {@code registry} of the specified type T.
             *
             * @see <a href="http://micrometer.io/docs/registry/graphite">http://micrometer.io/docs/registry/graphite</a>
             * for an example of how to use a meter registry customizer.
             *
             * @param registry the meter registry to customize
             */
            void customize(MeterRegistry registry);
        
            /**
             * Determines if the specified registry should be customized by this customizer.
             * @param registry The registry to check
             * @return true if this customer applies to the registry; false otherwise
             */
            boolean supports(MeterRegistry registry);
        }    
    ```
    
* MeterFilter
    * A MeterFilter can be used to determine if a Meter is to be added to the registry. See 
    [Meter Filters](http://micrometer.io/docs/concepts#_meter_filters)
    * Any bean that implements MeterFilter will be applied to all registries when the registry is first created
    * These beans should be declared with `@Context` to ensure they are available when the bean context is built
        
* MeterBinder
    
    Meter Binders get applied to Meter Registry to _mix in_ metrics producers. Micrometer.io defines 
    several of these for cross-cutting meterics related to JVM metrics, caches, classloaders, etc.
      
    Also autoconfigured are all beans of type MeterBinder which are _bound_ to the registry upon its creation. 
    
    This includes the `micrometer.io` JVM meter bindings: 
    * See [JVM Meter Bindings](http://micrometer.io/docs/ref/jvm) 
    * Each of these is registered as a bean and can be disable individually or turned off altogether.
    * Consider the following to see how to do that:
    
        ```
        package io.micronaut.configuration.metrics.micrometer;
        
        import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
        import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
        import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
        import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
        import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
        import io.micronaut.context.annotation.Bean;
        import io.micronaut.context.annotation.Context;
        import io.micronaut.context.annotation.Factory;
        import io.micronaut.context.annotation.Primary;
        import io.micronaut.context.annotation.Requires;
        
        import javax.inject.Singleton;
        
        @Factory
        public class JvmMetricsFactory {
            public static final String JVM_CLASSLOADER_METRICS_ENABLED = "metrics.jvm.classloader.enabled";
            public static final String JVM_GC_METRICS_ENABLED = "metrics.jvm.gc.enabled";
            public static final String JVM_MEM_METRICS_ENABLED = "metrics.jvm.mem.enabled";
            public static final String JVM_METRICS_ENABLED = "metrics.jvm.enabled";
            public static final String JVM_PROCESSOR_METRICS_ENABLED = "metrics.jvm.processor.enabled";
            public static final String JVM_THREAD_METRICS_ENABLED = "metrics.jvm.thread.enabled";
        
            @Bean
            @Primary
            @Context
            @Requires(property = JVM_METRICS_ENABLED, value = "true", defaultValue = "true")
            @Requires(property = JVM_CLASSLOADER_METRICS_ENABLED, value = "true", defaultValue = "true")
            public ClassLoaderMetrics classLoaderMetrics() {
                return new ClassLoaderMetrics();
            }
        
            @Bean
            @Primary
            @Context
            @Requires(property = JVM_METRICS_ENABLED, value = "true", defaultValue = "true")
            @Requires(property = JVM_GC_METRICS_ENABLED, value = "true", defaultValue = "true")
            public JvmGcMetrics jvmGcMetrics() {
                return new JvmGcMetrics();
            }
        
            @Bean
            @Primary
            @Context
            @Requires(property = JVM_METRICS_ENABLED, value = "true", defaultValue = "true")
            @Requires(property = JVM_MEM_METRICS_ENABLED, value = "true", defaultValue = "true")
            public JvmMemoryMetrics jvmMemoryMetrics() {
                return new JvmMemoryMetrics();
            }
        
            @Bean
            @Primary
            @Context
            @Requires(property = JVM_METRICS_ENABLED, value = "true", defaultValue = "true")
            @Requires(property = JVM_PROCESSOR_METRICS_ENABLED, value = "true", defaultValue = "true")
            public ProcessorMetrics processorMetrics() {
                return new ProcessorMetrics();
            }
        
            @Bean
            @Primary
            @Context
            @Requires(property = JVM_METRICS_ENABLED, value = "true", defaultValue = "true")
            @Requires(property = JVM_THREAD_METRICS_ENABLED, value = "true", defaultValue = "true")
            public JvmThreadMetrics jvmThreadMetrics() {
                return new JvmThreadMetrics();
            }
        }
        ```        

### Autoconfigured Meter Registries

Metrics autoconfiguration is about configuring and registering one or more meter registries as 
Micronaut beans. These beans can be injected or pulled from the bean context. They can be
further configured and are factories for different kinds of meters; i.e. counters, gauges, etc. 

**A quick note about the bean scopes:** The metric-related beans are all declared as @Context 
scoped to ensure they are all available when the application context is built. This ensures that the 
autoconfiguration will have all available registry configuration elements at the time the registry 
bean is created. These configuration elements include MeterRegistryCustomizers, MeterBinders, and 
MeterFilters. 

Currently, _autoconfiguration_ handles four Meter Registries:

* SimpleMeterRegistry
    * This is a simple registry useful for experimentation. No external metrics report needed.
    * This registry is enabled by default, but can be disabled throuh config:
    
        `metrics.simple-meter-registry.enabled: false`
     
* CompositeMeterRegistry
    * This holds multiple MeterRegistries and starts holding the SimpleMeterRegistry
    * This is enabled by default, but can be turned off with this config:
    
        `metrics.composite-meter-registry.enabled: false`
    
* DropwizardMeterRegistry
    * This registry does simple console reporting using the DropWizard ConsoleReporter
    * To automatically register a bean for this registry, simply
        * Include Dropwizard as a dependency; e.g.
        
            `compile "io.dropwizard.metrics:metrics-core:$dropwizardVersion"`
        
        * Enable it in config:
        
            `metrics.export.dropwizard-console.enabled: true`
            
        * This requires two conditions because its very likely you'll want to keep the noise in the 
        console down by default, so you have to work a little harder to turn the noise up.
        
* GraphiteMeterRegistry
    * This registry publishes through to Graphite
    * To enable it, you need to add the micrometer-graphite dependency as a dependency:
    
        `compile "io.micronaut.configuration:micrometer-graphite"`
    
    * It is enabled by default if the dependency is present, but you can turn it off through config:
    
        `metrics.export.graphite.enabled: false`
    
    * Interestingly, and somewhat problematically, it extends from the DropwizardMeterRegistry
        * When finding the DropwizardMeterRegistry bean with this registry enabled, 
        you can't just go off the type:
        
            `// isPresent() true even if DropwizardMeterRegistry beam disabled`
            `ctx.findBean(DropwizardMeterRegistry)`
        
        * Instead, you'll have to be explicit about it by including the bean name:
        
            `ctx.findBean(DropwizardMeterRegistry, Qualifiers.byName('dropwizardMeterRegistry'))`
        
    * If you want to see it in action, you can download a Graphite/Statsd Docker image from [Docker Hub](https://hub.docker.com)
    * Run it with this command:
    
        ```
        docker run -d\
         --name graphite\
         --restart=always\
         -p 80:80\
         -p 2003-2004:2003-2004\
         -p 2023-2024:2023-2024\
         -p 8125:8125/udp\
         -p 8126:8126\
         -v /tmp/graphite/conf:/opt/graphite/conf\
         -v /tmp/graphite/storage:/opt/graphite/storage\
         -v /tmp/graphite/statsd:/opt/statsd\
         graphiteapp/graphite-statsd
        ```
        
    * The -v options map the config and data files to your local file system. This will come in handy if you want to clear 
    the data
    
    * Go to localhost:80 to see the Graphite browser
    
### A Simple Example

Armed with the preceding, you can now create a small application to demonstrate a couple of basics. This is not a comprehensive tutorial, but 
there are plenty of those out on the Interwebs.

* First, use the Micronaut CLI to create a new application: `mn create-app MetricsDemo`
* Add metric-related dependencies to `build.gradle` like this:

    ```
    dependencies {
        annotationProcessor "io.micronaut:inject-java"
        compile "io.micronaut:http-server-netty"
        compile "io.micronaut:inject"
        compile "io.micronaut:runtime"
        compile "io.micronaut:http-client"
    
        compile "io.micronaut.configuration:micrometer"
        compile "io.micrometer:micrometer-registry-graphite:1.0.4"
        compile 'io.dropwizard.metrics:metrics-core:3.2.6'
        compile "io.micronaut:management"
    
        compileOnly "io.micronaut:inject-java"
        runtime "ch.qos.logback:logback-classic:1.2.3"
        testCompile "junit:junit:4.12"
    }
    ```  
* Add the following configuration to `application.yml` to use Graphite:

    ```
    metrics:
      enable: true
      export:
        graphite:
          enabled: true
          port: 2004
          tagsAsPrefix: ["application", "environment", "region"]
          step: "PT1S"
    ``` 
     
* If you want to see Dropwizard ConsoleReporter in action, add this config:

    `metrics.export.dropwizard-console.enabled: true`
    
* To see a customizer in action, add this class:

    ```
    package com.example;
    
    import io.micrometer.core.instrument.MeterRegistry;
    import io.micrometer.graphite.GraphiteMeterRegistry;
    import io.micronaut.configuration.metrics.micrometer.MeterRegistryCustomizer;
    import io.micronaut.context.annotation.Context;
    
    @Context
    public class GraphiteMeterCustomizer implements MeterRegistryCustomizer {
    
        @Override
        public void customize(MeterRegistry registry) {
            registry.config().commonTags("environment", "QA", "application", "HelloApp", "region", "us-east-2");
        }
    
        @Override
        public boolean supports(MeterRegistry registry) {
            return registry instanceof GraphiteMeterRegistry;
        }
    }
    ```
    
*  Finally, add a StatsController to kick off some metric emitting:

    ```
    package com.example;
    
    import io.micrometer.core.instrument.Counter;
    import io.micrometer.core.instrument.composite.CompositeMeterRegistry;
    import io.micrometer.graphite.GraphiteMeterRegistry;
    import io.micronaut.http.annotation.Controller;
    import io.micronaut.http.annotation.Get;
    
    import javax.inject.Inject;
    import java.util.Random;
    import java.util.Timer;
    import java.util.TimerTask;
    
    @Controller("/stats")
    public class StatsController {
    
        private Counter counter;
        private Timer timer;
    
        @Inject
        StatsController(CompositeMeterRegistry compositeMeterRegistry) {
            counter = compositeMeterRegistry.counter("mystats.start.counter");
    
            timer = new Timer("StatsController.start");
        }
    
        @Get("/start")
        public String start() {
            final Random rand = new Random(System.currentTimeMillis());
            TimerTask task = new TimerTask() {
                @Override
                public void run() {
                    for (int i = 0; i < rand.nextInt(100); i++) {
                        counter.increment();
                    }
                }
            };
    
            timer.scheduleAtFixedRate(task, 100, 500);
    
            return "Stats started";
        }
    
        @Get("/stop")
        public String stop() {
            timer.cancel();
            return "Stats stopped";
        }
    }
    ```
    
* Now, run the application and go to the `/stats/start` endpoint. A TimerTask will start and emit some 
pseudo-random count metrics

* In the Graphite browser, you'll see some metrics at:

    ```
        mystatsStartCounter
          environment
            QA
              region
                us-east-2
                    count       // raw counts
                    m15_rate    // counts per 15 mins
                    m1_rate     // counts per 1 min
                    m5_rate     // counts per 5 mins
                    mean_rate   // mean count raterate                                        
    ```                

* Select one or more of the rate items and Graphite will show a nice graphic
    * You'll want to click the auto refresh button
    * You'll want to adjust the time window to something like 5 - 15 minutes
    
* When you're done, hit the `stats/stop` endpoint to cancel the TimerTask