package org.particleframework.inject.value;

import org.particleframework.context.AbstractBeanDefinition;
import org.particleframework.context.BeanContext;
import org.particleframework.context.BeanResolutionContext;
import org.particleframework.context.DefaultBeanContext;
import org.particleframework.inject.BeanDefinition;
import org.particleframework.inject.BeanFactory;

import javax.inject.Singleton;
import java.lang.annotation.Annotation;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Created by graemerocher on 14/06/2017.
 */
public class ValueBeanDefTest extends AbstractBeanDefinition<ValueSpec.A> implements BeanFactory<ValueSpec.A> {
    public ValueBeanDefTest() throws NoSuchMethodException, NoSuchFieldException {
        super(ValueSpec.A.class.getAnnotation(Singleton.class), true, ValueSpec.A.class, ValueSpec.A.class.getConstructor(new Class[]{Integer.TYPE}), AbstractBeanDefinition.createMap(new Object[]{"port", Integer.TYPE}), (Map)null, (Map)null);
        Field var1 = ValueSpec.A.class.getDeclaredField("optionalPort");
        this.addInjectionPoint(var1, ValueSpec.A.class.getDeclaredMethod("setOptionalPort", new Class[]{Optional.class}), (Annotation)null, Arrays.asList(Integer.class), false);
        Field var2 = ValueSpec.A.class.getDeclaredField("optionalPort2");
        this.addInjectionPoint(var2, ValueSpec.A.class.getDeclaredMethod("setOptionalPort2", new Class[]{Optional.class}), (Annotation)null, Arrays.asList(Integer.class), false);
        Field var3 = ValueSpec.A.class.getDeclaredField("port");
        this.addInjectionPoint(var3, ValueSpec.A.class.getDeclaredMethod("setPort", new Class[]{Integer.TYPE}), (Annotation)null, (List)null, false);
        Field var4 = ValueSpec.A.class.getDeclaredField("fieldPort");
        this.addInjectionPoint(var4, (Annotation)null, false);
        Field var5 = ValueSpec.A.class.getDeclaredField("defaultPort");
        this.addInjectionPoint(var5, (Annotation)null, false);
        this.addInjectionPoint(ValueSpec.A.class.getDeclaredMethod("setAnotherPort", new Class[]{Integer.TYPE}), AbstractBeanDefinition.createMap(new Object[]{"port", Integer.TYPE}), (Map)null, (Map)null, false);
    }

    public ValueSpec.A build(BeanResolutionContext var1, BeanContext var2, BeanDefinition<ValueSpec.A> var3) {
        ValueSpec.A var4 = new ValueSpec.A(((Integer)this.getBeanForConstructorArgument(var1, var2, 0)).intValue());
        var4 = (ValueSpec.A)this.injectBean(var1, var2, var4);
        return var4;
    }

    protected Object injectBean(BeanResolutionContext var1, BeanContext var2, Object var3) {
        ValueSpec.A var4 = (ValueSpec.A)var3;
        this.injectBeanFields(var1, (DefaultBeanContext)var2, var3);
        try {
            Object var5 = this.getValueForMethodArgument(var1, var2, 0, 0);
            var4.setOptionalPort((Optional)var5);
            var5 = this.getValueForMethodArgument(var1, var2, 1, 0);
            var4.setOptionalPort2((Optional)var5);
            var5 = this.getValueForMethodArgument(var1, var2, 2, 0);
            var4.setPort(((Integer)var5).intValue());
            var5 = this.getValueForField(var1, var2, 0);
            if(var5 == null) {
                var4.fieldPort = ((Integer)var5).intValue();
            }

            var5 = this.getValueForField(var1, var2, 1);
            if(var5 == null) {
                var4.defaultPort = ((Integer)var5).intValue();
            }

            var4.setAnotherPort(((Integer)this.getBeanForMethodArgument(var1, var2, 3, 0)).intValue());
            this.injectBeanMethods(var1, (DefaultBeanContext)var2, var3);
        } catch (Throwable throwable) {
            // ignore
        }
        return var3;
    }
}

