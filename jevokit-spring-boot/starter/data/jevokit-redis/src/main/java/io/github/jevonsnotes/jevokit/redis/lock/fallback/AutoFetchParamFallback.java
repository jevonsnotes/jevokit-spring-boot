package io.github.jevonsnotes.jevokit.redis.lock.fallback;

import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Collection;
import java.util.Map;

 /**
 * 入参回传降级实现：按方法返回类型在入参中找类型相容的参数原样返回
 * （含 Collection/Map 泛型实参匹配，找不到返回 null）——语义是"把调用方的请求当作
 * 幂等结果还给调用方"，仅适用于"入参即出参"形态的接口。
 */
public class AutoFetchParamFallback implements LockFallback<Object>{


    /**
    * 按返回类型回传入参：泛型/集合逐层匹配类型相容参数，无匹配返回 null。
    * @param signature 被拦截方法签名（取返回/入参类型）
    * @param args 被拦截方法入参
    * @return 类型相容的入参原样回传；无相容参数或返回 void 时 null
    */
    public Object fallback(MethodSignature signature, Object... args) {
        final Type resType = signature.getMethod().getGenericReturnType();
        Class resClass = getClass(resType);
        boolean hasArgs = args != null && args.length>0;
        if(!resClass.isAssignableFrom(void.class)){
            if(hasArgs){
                if(Object.class.equals(resClass)){
                    final int index = getObjectClassIndex(signature.getMethod().getGenericParameterTypes());
                    return args[index];
                } else {
                    final int index = getIndex(resType,  signature.getMethod().getGenericParameterTypes());
                    return index == -1 ? null : args[index];
                }
            }
        }
        return null;
    }
    /**
    * 找 Object 型参数下标（无可辨类型时回退首参）。
    */
    private int getObjectClassIndex(Type[] argTypes){
        for(int i = 0;i<argTypes.length;i++){
            if(Object.class.equals(getClass(argTypes[i]))){
                return i;
            }
        }
        return 0;
    }

    /**
    * 取原始类型（泛型取 RawType）。
    */
    private Class getClass(Type resType){
        if(resType instanceof ParameterizedType){
            return (Class) ((ParameterizedType)resType).getRawType();
        } else {
            return (Class)resType;
        }
    }
    /**
    * 取泛型实参（非泛型时按开关回落 Object 或 null）。
    */
    private Class getActualTypeArguments(Type resType,int index,boolean nullToObject){
        if(resType instanceof ParameterizedType){
            return (Class) ((ParameterizedType)resType).getActualTypeArguments()[index];
        }
        return nullToObject ? Object.class : null;
    }
    /**
    * 取泛型实参（非泛型返回 null）。
    */
    private Class getActualTypeArguments(Type resType,int index){
        return getActualTypeArguments(resType,index,false);
    }

    /**
    * 按返回类型在入参中找类型相容项（集合/Map 按泛型实参逐层比对）。
    */
    private int getIndex(Type returnType,Type[] argTypes){
        final Class resClass = getClass(returnType);
        for(int i = 0;i<argTypes.length;i++){
            final Type argType = argTypes[i];
            if(resClass.isAssignableFrom(getClass(argType))){
                if(Collection.class.isAssignableFrom(resClass)){
                    if(getActualTypeArguments(returnType,0) == null
                            || getActualTypeArguments(returnType,0,true).isAssignableFrom(getActualTypeArguments(argType,0))){
                        return i;
                    }
                } else if(Map.class.isAssignableFrom(getClass(argType))){

                    final Class keyClass1 = getActualTypeArguments(returnType,0,true);
                    final Class valueClass1 = getActualTypeArguments(returnType,1,true);

                    final Class keyClass2 = getActualTypeArguments(argType,0,true);
                    final Class valueClass2 = getActualTypeArguments(argType,1,true);

                    if(keyClass1.isAssignableFrom(keyClass2) && valueClass1.isAssignableFrom(valueClass2)){
                        return i;
                    }
                } else if(resClass.isAssignableFrom(getClass(argType))){
                    return i;
                }

            }
        }
        return -1;
    }
}
