package io.github.jevonsnotes.jevokit.excel.resolver;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.date.DateUtil;
import cn.hutool.core.exceptions.UtilException;
import cn.hutool.core.util.ReflectUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import cn.hutool.poi.excel.ExcelUtil;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.io.IOUtils;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import io.github.jevonsnotes.jevokit.core.annotation.Excel;
import io.github.jevonsnotes.jevokit.core.exception.ExcelException;
import io.github.jevonsnotes.jevokit.core.util.FieldUtil;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.Part;
import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.util.*;

 /**
 * 上传excel解析器
 * 支持格式：json、multipart/form-data
 */
@Slf4j
public class ExcelResolver implements HandlerMethodArgumentResolver {
    private static final String CHARSET = "utf-8";

    @Override
    /**
    * 支持性判定：参数带 @Excel 注解即接管。
    * @param parameter 目标参数
    * @return true=带 @Excel 注解
    */
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(Excel.class) && List.class.isAssignableFrom(parameter.getParameter().getType());
    }

    @Override
    /**
    * 解析绑定：multipart 文件按 @BeanField 列序展开为 List&lt;T&gt;，JSON 数组请求体同样绑定；
    * @NotNull 等校验随绑定生效。
    * @param parameter 目标参数
    * @param modelAndViewContainer 视图容器（未参与）
    * @param webRequest 当前请求
    * @param webDataBinderFactory 绑定工厂（校验挂载）
    * @return 绑定完成的参数值（List 形态）
    * @throws Exception 解析/绑定失败
    */
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer modelAndViewContainer, NativeWebRequest webRequest, WebDataBinderFactory webDataBinderFactory) throws Exception {
        String msgTemplate = "ExcelResolver Servlet Path : {} ";
        Excel excel = parameter.getParameterAnnotation(Excel.class);
        try {
            HttpServletRequest request = webRequest.getNativeRequest(HttpServletRequest.class);
            log.debug(msgTemplate,request.getServletPath());
            Type actualType = getActualType(parameter);
            int firstRowNum = excel.ignoreRows();
            List<?> vos = new ArrayList<>();

            if(request.getContentType().contains(MediaType.APPLICATION_JSON_VALUE)){
                String jsonStr = IOUtils.toString(request.getInputStream(), CHARSET);
                vos = JSONUtil.toList(JSONUtil.parseArray(jsonStr), (Class<? extends Object>) actualType);
                if(vos.size() > firstRowNum){
                    vos = vos.subList(firstRowNum,vos.size());
                } else {
                    vos.clear();
                }
            } else if(request.getContentType().contains(MediaType.MULTIPART_FORM_DATA_VALUE)) {
                List<List<Object>> rows = new ArrayList<>();
                Collection<Part> parts = request.getParts();
                Part part = null;
                if (CollectionUtil.isNotEmpty(parts)) {
                    for (Part temp : parts) {
                        if ((StrUtil.isBlank(excel.param()) && (temp.getName().equals(parameter.getParameterName()) || parts.size() == 1))
                                || temp.getName().equals(excel.param())) {
                            part = temp;
                            break;
                        }
                    }
                }

                if (part != null) {
                    ExcelUtil.readBySax(part.getInputStream(), excel.sheet(), (sheetIndex, rowIndex, rowCells) -> {
                        if (rowIndex >= firstRowNum) {
                            rows.add(rowCells);
                        }
                    });
                    for (List<Object> row : rows) {
                        int counter = 0;
                        Class<?> clazz = ((Class<?>) actualType);
                        Object o = clazz.newInstance();
                        List<Field> orderDeclaredField = FieldUtil.getOrderDeclaredField((Class<? extends Object>) actualType);
                        for(Field field : orderDeclaredField){
                            if(counter < row.size()){
                                try {
                                    ReflectUtil.setFieldValue(o,field,valueConvert(field,row.get(counter++)));
                                } catch (Exception e) {
                                    log.error("data parse exception : value[{}] to type [{}]", row.get(counter - 1), field.getType().getName());
                                    throw e;
                                }
                            }
                        }
                        CollectionUtil.addAll(vos,o);
                    }
                }
            } else {
                throw new ExcelException("unsupported contentType: "+request.getContentType()+"! Allowed is "+ MediaType.APPLICATION_JSON_VALUE +" and "+MediaType.MULTIPART_FORM_DATA_VALUE+".");
            }
            return vos;
        } catch (Exception e) {
            throw new ExcelException(e);
        }
    }

    /**
     * 值转换
     * @param field
     * @param cellValue
     * @return
     */
    private Object valueConvert(Field field,Object cellValue){
        Class<?> type = field.getType();
        if(cellValue == null || field.getType().isAssignableFrom(cellValue.getClass())){
            return cellValue;
        } else if(cellValue instanceof Date){

            if(String.class.isAssignableFrom(type)){
                return DateUtil.formatDateTime((Date) cellValue);
            } else if(Long.class.isAssignableFrom(type)){
                return ((Date) cellValue).getTime();
            }
        } else if(Number.class.isAssignableFrom(cellValue.getClass())){

            if(String.class.isAssignableFrom(type)){
                return String.valueOf(cellValue);
            } else if (BigDecimal.class.isAssignableFrom(type)){
                return new BigDecimal(String.valueOf(cellValue));
            } else if (BigInteger.class.isAssignableFrom(type)){
                return new BigInteger(String.valueOf(cellValue));
            }
        } else if(cellValue instanceof String){
            if(Date.class.isAssignableFrom(type)){
                return DateUtil.parse((CharSequence) cellValue);
            } else if (Number.class.isAssignableFrom(type)) {
                try {
                    return ReflectUtil.newInstance(type,cellValue);
                } catch (UtilException e) {
                    log.debug("unsupported constructor type!",e);
                }
            } else if (BigDecimal.class.isAssignableFrom(type)){
                return new BigDecimal(String.valueOf(cellValue));
            } else if (BigInteger.class.isAssignableFrom(type)){
                return new BigInteger(String.valueOf(cellValue));
            }
        }
        return cellValue;
    }


    /**
     * 获取
     * @param parameter
     * @return
     */
    private Type getActualType(MethodParameter parameter){
        //解析泛型声明类
        Type genericParameterType = parameter.getGenericParameterType();
        Type actualType = null;
        if(ParameterizedType.class.isAssignableFrom(genericParameterType.getClass())) {
            Type[] actualTypeArguments = ((ParameterizedType)genericParameterType).getActualTypeArguments();
            if(actualTypeArguments.length!=1) {
                throw new RuntimeException("业务数据类型声明不规范！");
            }else {
                actualType = actualTypeArguments[0];
            }
        }

        return actualType;
    }
}
