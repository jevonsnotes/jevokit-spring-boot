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

import javax.persistence.Column;
import javax.persistence.Table;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * 基于实体注解的预处理 SQL 拼装工具：反射读取字段与 JPA 注解（@Table/@Column），
 * 生成带 ? 占位符的 insert/update/select 预处理语句，配合 {@link SqlBundleDto}（SQL + 有序参数）直接交给 JdbcTemplate 执行。
 * <p>常用入口：{@link #generateInsertSelectivePreSql(Object)}、
 * {@link #generateUpdateSelectivePreSql(Object, String[])}、{@link #generateSelectSelectivePreSql(Object, String[])}。
 * <p>关键语义：表名取 @Table.name，缺省用类简单名；列名取 @Column.name，缺省驼峰转下划线并统一小写；
 * 只遍历非 static 的已声明字段。所谓 selective：null/空值字段不参与拼接（select 的查询列除外，查询列总是全量列出）。
 * update 带防御性断言——whereColumns 为空、无字段可更新或拼不出任何条件时抛 IllegalArgumentException，拒绝全表更新。
 */
public class SqlUtil {

    /**
     * 生成选择性更新预处理 SQL：非空字段进 set 子句，whereColumns 指定且非空的字段进 where 子句，其余非空字段进 set 子句。
     *
     * @param object       实体对象，字段当前值即绑定参数
     * @param whereColumns 条件字段名（与实体属性名/列名比对，忽略大小写）
     * @param <T>          实体类型
     * @return SQL 与占位参数包（参数顺序：set 子句在前、where 子句在后）
     * @throws IllegalArgumentException whereColumns 为空、实体无可更新字段或拼不出任何 where 条件（等价全表更新）时抛出
     */
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

    /**
     * 生成选择性插入预处理 SQL：仅非空字段进列清单与 values，null 字段交由数据库默认值兜底。
     *
     * @param object 实体对象，字段当前值即绑定参数
     * @param <T>    实体类型
     * @return SQL 与占位参数包
     * @throws IllegalArgumentException 实体所有字段均为空（无数据可新增）时抛出
     */
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

    /**
     * 生成选择性查询预处理 SQL：查询列包含全部非 static 字段（含空值字段）；
     * whereColumns 指定且字段值非空的才拼接等值条件，全部为空时 where 退化为 "1=1"（全表查询）。
     *
     * @param object       实体对象，作为查询条件的样例（取字段当前值）
     * @param whereColumns 条件字段名（与实体属性名/列名比对，忽略大小写）
     * @param <T>          实体类型
     * @return SQL 与占位参数包
     */
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

    /**
     * 解析表名：优先 @Table.name，缺省用类简单名
     *
     * @param clazz 实体类
     * @param <T>   实体类型
     * @return 表名
     */
    private static <T>String getTableName(Class<T> clazz){
        String tableName = clazz.getSimpleName();
        Table annotation = clazz.getAnnotation(Table.class);
        if(annotation != null && StrUtil.isNotBlank(annotation.name())){
            tableName = annotation.name();
        }
        return tableName;
    }
    /**
     * 解析列名：优先 @Column.name，缺省驼峰转下划线，统一小写
     *
     * @param field        实体字段
     * @param toUnderscore 缺省命名是否转下划线（字段名已含下划线则不再转换）
     * @return 列名
     */
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
