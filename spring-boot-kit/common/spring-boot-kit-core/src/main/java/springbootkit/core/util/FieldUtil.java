package springbootkit.core.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Filter;
import springbootkit.core.annotation.BeanField;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * @Description TODO
 * @Date 2022/7/6 9:25
 * @Version 0.0.1
 */
public class FieldUtil {
    /**
     * 结合BeanField进行排序
     * 自动剔除静态字段、final字段
     * @param clazz
     * @param <T>
     * @return
     */
    public static <T> List<Field> getOrderDeclaredField(Class<T> clazz){
        List<Field> fields = new ArrayList<>();
        CollectionUtil.addAll(fields,clazz.getDeclaredFields());

        AtomicBoolean hasOrder = new AtomicBoolean(false);
        CollectionUtil.filter(fields, field -> {
            if(!Modifier.isStatic(field.getModifiers()) && !Modifier.isFinal(field.getModifiers())){
                if(!hasOrder.get() && field.getAnnotation(BeanField.class) != null){
                    hasOrder.set(true);
                }
                return true;
            }
            return false;
        });


        if( hasOrder.get() ){
            fields.sort(Comparator.comparingInt(field -> {
                BeanField beanFieldAnnotation = field.getAnnotation(BeanField.class);
                return beanFieldAnnotation == null ? Integer.MAX_VALUE : beanFieldAnnotation.order();
            }));
        }

        return fields;
    }

}
