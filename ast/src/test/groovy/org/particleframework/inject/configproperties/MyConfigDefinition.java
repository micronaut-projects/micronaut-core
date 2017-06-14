package org.particleframework.inject.configproperties;

import org.particleframework.config.ConfigurationProperties;
import org.particleframework.context.AbstractBeanDefinition;
import org.particleframework.context.BeanContext;
import org.particleframework.context.BeanResolutionContext;
import org.particleframework.context.DefaultBeanContext;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanFactory;

import java.lang.annotation.Annotation;
import java.lang.reflect.Field;

/**
 * Created by graemerocher on 14/06/2017.
 */
public class MyConfigDefinition extends AbstractBeanDefinition<ConfigurationPropertiesSpec.MyConfig> implements BeanFactory<ConfigurationPropertiesSpec.MyConfig> {
    public MyConfigDefinition() throws NoSuchMethodException, NoSuchFieldException {
        super(ConfigurationPropertiesSpec.MyConfig.class.getAnnotation(ConfigurationProperties.class), true, ConfigurationPropertiesSpec.MyConfig.class, ConfigurationPropertiesSpec.MyConfig.class.getConstructor(new Class[0]));
        Field var11 = ConfigurationPropertiesSpec.MyConfig.class.getDeclaredField("anotherPort");
        this.addInjectionPoint(var11, (Annotation)null, false);
    }

    public ConfigurationPropertiesSpec.MyConfig build(BeanResolutionContext var1, BeanContext var2, BeanDefinition<ConfigurationPropertiesSpec.MyConfig> var3) {
        ConfigurationPropertiesSpec.MyConfig var4 = new ConfigurationPropertiesSpec.MyConfig();
        var4 = (ConfigurationPropertiesSpec.MyConfig)this.injectBean(var1, var2, var4);
        return var4;
    }

    protected Object injectBean1(BeanResolutionContext var1, BeanContext var2, Object var3) throws Throwable {
        ConfigurationPropertiesSpec.MyConfig var4 = (ConfigurationPropertiesSpec.MyConfig)var3;
        this.injectBeanFields(var1, (DefaultBeanContext)var2, var3);
        Object var5 = this.getValueForField(var1, var2, 0);
        if(var5 != null) {
            var4.anotherPort = (Integer)var5;
        }
        this.injectBeanMethods(var1, (DefaultBeanContext)var2, var3);
        return var3;
    }
}

