package io.github.jevonsnotes.jevokit.core.util;


import cn.hutool.core.lang.Assert;
import cn.hutool.core.util.ObjectUtil;
import lombok.Getter;

import java.math.BigDecimal;
import java.math.BigInteger;
import java.math.RoundingMode;

/**
 * BigDecimal处理类
 * <p>用于简化大量计算时频繁手工创建对象及精度异常问题</p>
 * <p>默认最终保留精度：8</p>
 * <p>默认计算时精度：16</p>
 * <p>默认精度计算规则：四舍五入</p>
 */
public class BigDecimalHolder {
    private int scale = 8;//最终保留小数点位数（运行done方法后调用）
    private final int calcScale;//运算时保留小数点位数（防止除不尽报错）
    /**
     * -- GETTER --
     *  获取当前值
     */
    @Getter
    private BigDecimal current;
    private RoundingMode roundingMode =  RoundingMode.HALF_UP;

    private BigDecimalHolder(BigDecimal current , Integer scale, RoundingMode roundingMode){
        this.current = current;
        this.scale = ObjectUtil.defaultIfNull(scale,this.scale);
        this.calcScale = Math.max(this.scale * 2, 16);
        this.roundingMode = ObjectUtil.defaultIfNull(roundingMode,this.roundingMode);
    }
    private BigDecimalHolder(){
        this(null,null,null);
    }
    private BigDecimalHolder(int scale){
        this(null,scale,null);
    }
    private BigDecimalHolder(int scale, RoundingMode roundingMode){
        this(null,scale,roundingMode);
    }
    public static BigDecimalHolder getHolder(){
        return new BigDecimalHolder();
    }
    public static BigDecimalHolder getHolder(int scale){
        return new BigDecimalHolder(scale);
    }
    public static BigDecimalHolder getHolder(int scale, RoundingMode roundingMode){
        return new BigDecimalHolder(scale,roundingMode);
    }
    public BigDecimalHolder divide(Object v1){
        current = current.divide(convert(v1), calcScale, roundingMode);
        return this;
    }
    public BigDecimalHolder add(Object v1){
        current = current.add(convert(v1));
        return this;
    }
    public BigDecimalHolder subtract(Object v1){
        current = current.subtract(convert(v1));
        return this;
    }
    public BigDecimalHolder multiply(Object v1){
        current = current.multiply(convert(v1));
        return this;
    }
    public BigDecimalHolder pow(int v1){
        current = current.pow(v1);
        return this;
    }
    /**
     * 类型转换器
     * @param o
     * @return
     */
    private static BigDecimal convert( Object o){
        BigDecimal b = null;
        if(o instanceof  Integer){
            b =  new BigDecimal((int)o);
        }else if(o instanceof Double){
            b = new BigDecimal(Double.toString((double) o));
        }else if(o instanceof String){
            b = new BigDecimal((String)o);
        }else if(o instanceof Long){
            b = new BigDecimal((long)o);
        }else if(o instanceof BigInteger){
            b = new BigDecimal((BigInteger)o);
        }else if(o instanceof BigDecimal){
            b = (BigDecimal)o;
        }else if(o.getClass().getComponentType()!=null){
            if(o.getClass().getComponentType()==Character.class){
                //Character不能直接转
                Character[] o1 = (Character[]) o;
                char[] chars = new char[o1.length];
                for(int i =0;i<o1.length;i++){
                    chars[i]= o1[i];
                }
                b = new BigDecimal(chars);
            }else if(o.getClass().getComponentType()==char.class){
                b = new BigDecimal((char[]) o);
            }
        }
        if(b==null){
            throw new RuntimeException("No converter can be matched!");
        }
        return b;
    }

    /**
     * 结束计算并进行精度统筹
     * @return
     */
    public BigDecimal done(){
        return current.setScale(scale, roundingMode);
    }

    /**
     * 结束计算并进行精度统筹
     * @return
     */
    public String doneToPlanStr(){
        return done().toPlainString();
    }

    /**
     * 创建一个带有初始值的holder
     * @param current
     * @return
     */
    public BigDecimalHolder create(Object current) {
        return getHolder(convert(current),this);
    }

    /**
     * 创建一个带有初始值的holder
     * @return
     */
    public BigDecimalHolder create(BigDecimalHolder holder) {
        Assert.notNull(holder.getCurrent(),"holder value can not be null!");
        return getHolder(convert(holder.getCurrent()),this);
    }

    public static BigDecimalHolder getHolder(BigDecimal current, BigDecimalHolder holder){
        return new BigDecimalHolder(current,holder.scale, holder.roundingMode);
    }
    public static void main(String[] args) {
        //example 1
        BigDecimalHolder holder = BigDecimalHolder.getHolder(4);
        String demo1 = holder.create("12")
                .divide(holder.create("4").multiply(3).done())
                .divide(new char[]{'2'})
                .add(new BigDecimal("6666"))
                .done().toString();
        System.out.println(demo1);
        //example 2
        String demo2 = holder.create("3")
                .divide(new Character[]{'1','2'})
                .done().toPlainString();
        System.out.println(demo2);
        System.out.println(new BigDecimal("0.160").divide(new BigDecimal(2), 3, RoundingMode.HALF_UP).toPlainString());
        //example 3
        BigDecimalHolder demo3 = BigDecimalHolder.getHolder(0).create(new BigDecimal("132.695"));
        System.out.println(demo3.multiply(100).done().toPlainString());


        //example 4
        BigDecimalHolder demo4 = BigDecimalHolder.getHolder(2).create(holder.create(8)).divide("5");
        System.out.println(demo4.doneToPlanStr());
    }

}
