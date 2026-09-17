package io.github.jevonsnotes.jevokit.core.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Filter;
import io.github.jevonsnotes.jevokit.core.annotation.BeanField;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * 反射字段工具：按 {@link BeanField#order()} 整理实体字段顺序，供 Excel 导入导出等依赖字段次序的组件使用。
 * <p>常用入口：{@link #getOrderDeclaredField(Class)}。
 * <p>排序规则：任一字段带 @BeanField 即视为"启用排序"，按 order 升序、无注解字段排最末（Integer.MAX_VALUE）；
 * 全部字段都未标注时保持 JVM 反射返回的原始声明顺序。静态与 final 字段一律剔除。
 */
public class FieldUtil {
    /**
     * 获取排序后的已声明字段列表
     *
     * @param clazz 目标类
     * @param <T>   目标类型
     * @return 已剔除静态/final 字段的字段列表；类中存在任一 @BeanField 注解时按其 order 升序排列，否则保持声明顺序
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
