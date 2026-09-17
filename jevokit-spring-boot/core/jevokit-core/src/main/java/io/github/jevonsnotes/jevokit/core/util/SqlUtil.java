package io.github.jevonsnotes.jevokit.core.util;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.lang.Assert;
import cn.hutool.core.text.StrBuilder;
import cn.hutool.core.util.ClassUtil;
import cn.hutool.core.util.ObjectUtil;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import org.springframework.data.util.ParsingUtils;
import io.github.jevonsnotes.jevokit.core.dto.SqlBundleDto;

import jakarta.persistence.Column;
import jakarta.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * @Description TODO
 * @Date 2023/3/10 17:42
 * @Version 0.0.1
 */
public class SqlUtil {

    public static <T> SqlBundleDto generateUpdateSelectivePreSql(T object, String[] whereColumns){
        List<Object> setArgs = new ArrayList<>();
        List<Object> whereArgs = new ArrayList<>();
        String tableName = getTableName(object.getClass());
        Assert.isTrue(whereColumns != null && whereColumns.length>0,"条件字段不能为空");
        Collection<String> whereColumnList = CollectionUtil.edit(CollectionUtil.toList(whereColumns), String::toLowerCase);
        Field[] declaredFields = ClassUtil.getDeclaredFields(object.getClass());
        StrBuilder setClause = StrBuilder.create(" Update ").append(tableName).append(" set ");
        StrBuilder whereClause = StrBuilder.create(" where 1 =1 ");
        Arrays.stream(declaredFields).forEach(field -> {
            Object value = ReflectUtil.getFieldValue(object, field);
            if(!Modifier.isStatic(field.getModifiers()) && ObjectUtil.isNotEmpty(value) ){
                String columnName = getColumnName(field, true);
                if(!whereColumnList.contains(columnName)){
                    setClause.append(getColumnName(field,true)).append(" = ? ,");
                    setArgs.add(value);
                } else {
                    whereClause.append(" and ").append(columnName).append(" = ? ") ;
                    whereArgs.add(value);
                }
            }
        });
        Assert.isTrue(!setArgs.isEmpty(),"无可更新字段");
        Assert.isTrue(!whereArgs.isEmpty(),"禁止全量更新");
        setClause.del(setClause.length() - 1,setClause.length()).append(whereClause.toString());
        setArgs.addAll(whereArgs);
        return new SqlBundleDto().setSql(setClause.toString()).setArgs(setArgs.toArray());
    }

    public static <T> SqlBundleDto generateInsertSelectivePreSql(T object){
        List<Object> setArgs = new ArrayList<>();
        String tableName = getTableName(object.getClass());
        Field[] declaredFields = ClassUtil.getDeclaredFields(object.getClass());
        StrBuilder columnClause = StrBuilder.create(" Insert into ").append(tableName).append(" ( ");
        StrBuilder valueClause = StrBuilder.create(" values ( ");
        Arrays.stream(declaredFields).forEach(field -> {
            Object value = ReflectUtil.getFieldValue(object, field);
            if(!Modifier.isStatic(field.getModifiers()) && ObjectUtil.isNotEmpty(value) ){
                String columnName = getColumnName(field, true);
                columnClause.append(columnName).append(",");
                valueClause.append("?,");
                setArgs.add(value);
            }
        });
        Assert.isTrue(!setArgs.isEmpty(),"无数据可新增");
        columnClause.del(columnClause.length() - 1,columnClause.length())
                .append(")")
                .append(valueClause.del(valueClause.length() - 1,valueClause.length()).append(" )"));
        return new SqlBundleDto().setSql(columnClause.toString()).setArgs(setArgs.toArray());
    }

    public static <T> SqlBundleDto generateSelectSelectivePreSql(T object, String[] whereColumns){
        List<Object> setArgs = new ArrayList<>();
        List<Object> whereArgs = new ArrayList<>();
        String tableName = getTableName(object.getClass());
        Collection<String> whereColumnList = CollectionUtil.edit(CollectionUtil.toList(whereColumns), String::toLowerCase);
        Field[] declaredFields = ClassUtil.getDeclaredFields(object.getClass());
        StrBuilder selectClause = StrBuilder.create(" Select ");
        StrBuilder whereClause = StrBuilder.create(" where 1 =1 ");
        Arrays.stream(declaredFields).forEach(field -> {
            Object value = ReflectUtil.getFieldValue(object, field);
            if(!Modifier.isStatic(field.getModifiers()) ){
                String columnName = getColumnName(field, true);
                selectClause.append(columnName).append(",");

                if(whereColumnList.contains(columnName) && ObjectUtil.isNotEmpty(value) ){
                    whereClause.append(" and ").append(columnName).append(" = ? ") ;
                    whereArgs.add(value);
                }
            }
        });
        selectClause.del(selectClause.length() - 1,selectClause.length()).append(" from ").append(tableName).append(whereClause.toString());
        setArgs.addAll(whereArgs);
        return new SqlBundleDto().setSql(selectClause.toString()).setArgs(setArgs.toArray());
    }

    private static <T>String getTableName(Class<T> clazz){
        String tableName = clazz.getSimpleName();
        Table annotation = clazz.getAnnotation(Table.class);
        if(annotation != null && StrUtil.isNotBlank(annotation.name())){
            tableName = annotation.name();
        }
        return tableName;
    }
    private static String getColumnName(Field field,boolean toUnderscore){
        String column = field.getName();
        Column annotation = field.getAnnotation(Column.class);
        if(annotation != null && StrUtil.isNotBlank(annotation.name())){
            column = annotation.name();
        } else if (toUnderscore){
            column = column.contains("_") ? column : ParsingUtils.reconcatenateCamelCase(column,"_");
        }
        return column.toLowerCase();
    }
}
