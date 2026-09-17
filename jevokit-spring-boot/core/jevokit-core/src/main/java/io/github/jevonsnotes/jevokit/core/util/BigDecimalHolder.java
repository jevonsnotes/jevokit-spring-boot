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

    /**
     * 全参私有构造（缺省项走默认值）
     *
     * @param current       初始值
     * @param scale         最终保留小数位
     * @param roundingMode  舍入模式
     */
    private BigDecimalHolder(BigDecimal current , Integer scale, RoundingMode roundingMode){
        this.current = current;
        this.scale = ObjectUtil.defaultIfNull(scale,this.scale);
        this.calcScale = Math.max(this.scale * 2, 16);
        this.roundingMode = ObjectUtil.defaultIfNull(roundingMode,this.roundingMode);
    }
    /**
     * 私有构造：无初始值、默认精度
     */
    private BigDecimalHolder(){
        this(null,null,null);
    }
    /**
     * 私有构造：无初始值、指定精度
     *
     * @param scale 最终保留小数位
     */
    private BigDecimalHolder(int scale){
        this(null,scale,null);
    }
    /**
     * 私有构造：无初始值、指定精度与舍入模式
     *
     * @param scale         最终保留小数位
     * @param roundingMode  舍入模式
     */
    private BigDecimalHolder(int scale, RoundingMode roundingMode){
        this(null,scale,roundingMode);
    }
    /**
     * 创建默认精度的计算器（保留 8 位小数、四舍五入）
     *
     * @return 新计算器实例
     */
    public static BigDecimalHolder getHolder(){
        return new BigDecimalHolder();
    }
    /**
     * 创建指定最终保留精度的计算器
     *
     * @param scale 最终保留小数位数（运算精度自动取 max(scale*2, 16) 防除不尽报错）
     * @return 新计算器实例
     */
    public static BigDecimalHolder getHolder(int scale){
        return new BigDecimalHolder(scale);
    }
    /**
     * 创建指定精度与舍入模式的计算器
     *
     * @param scale         最终保留小数位数
     * @param roundingMode  舍入模式（运算与最终保留均生效）
     * @return 新计算器实例
     */
    public static BigDecimalHolder getHolder(int scale, RoundingMode roundingMode){
        return new BigDecimalHolder(scale,roundingMode);
    }
    /**
     * 除法：当前值 ÷ v1，按运算精度执行
     *
     * @param v1 除数，支持 Number/String/char 数组等（见 {@link #convert(Object)}）
     * @return 本计算器（链式）
     */
    public BigDecimalHolder divide(Object v1){
        current = current.divide(convert(v1), calcScale, roundingMode);
        return this;
    }
    /**
     * 加法：当前值 + v1
     *
     * @param v1 加数，支持类型见 {@link #convert(Object)}
     * @return 本计算器（链式）
     */
    public BigDecimalHolder add(Object v1){
        current = current.add(convert(v1));
        return this;
    }
    /**
     * 减法：当前值 - v1
     *
     * @param v1 减数，支持类型见 {@link #convert(Object)}
     * @return 本计算器（链式）
     */
    public BigDecimalHolder subtract(Object v1){
        current = current.subtract(convert(v1));
        return this;
    }
    /**
     * 乘法：当前值 × v1
     *
     * @param v1 乘数，支持类型见 {@link #convert(Object)}
     * @return 本计算器（链式）
     */
    public BigDecimalHolder multiply(Object v1){
        current = current.multiply(convert(v1));
        return this;
    }
    /**
     * 乘方：当前值的 v1 次方
     *
     * @param v1 指数（非负）
     * @return 本计算器（链式）
     */
    public BigDecimalHolder pow(int v1){
        current = current.pow(v1);
        return this;
    }
    /**
     * 类型转换器：把 Number/String/char 数组等统一转为 BigDecimal
     *
     * @param o 原值，支持 Integer/Double/Long/BigInteger/BigDecimal/String/Character 数组/char 数组
     * @return 转换结果
     * @throws RuntimeException 无匹配转换器时抛出
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
     *
     * @return 按最终保留精度与舍入模式收敛后的结果
     */
    public BigDecimal done(){
        return current.setScale(scale, roundingMode);
    }

    /**
     * 结束计算并输出纯数字字符串（不走科学计数法）
     *
     * @return 收敛结果的 toPlainString 形式
     */
    public String doneToPlanStr(){
        return done().toPlainString();
    }

    /**
     * 创建一个带有初始值的holder（继承本计算器的精度配置）
     *
     * @param current 初始值，支持类型见 {@link #convert(Object)}
     * @return 新的计算器实例
     */
    public BigDecimalHolder create(Object current) {
        return getHolder(convert(current),this);
    }

    /**
     * 创建一个带有初始值的holder（继承本计算器的精度配置，取对方当前值）
     *
     * @param holder 另一计算器，其当前值作为初始值
     * @return 新的计算器实例
     * @throws cn.hutool.core.lang.AssertException 对方当前值为 null 时抛出
     */
    public BigDecimalHolder create(BigDecimalHolder holder) {
        Assert.notNull(holder.getCurrent(),"holder value can not be null!");
        return getHolder(convert(holder.getCurrent()),this);
    }

    /**
     * 以显式初始值与既有计算器的精度配置创建新计算器
     *
     * @param current 初始值
     * @param holder  精度配置来源
     * @return 新计算器实例
     */
    public static BigDecimalHolder getHolder(BigDecimal current, BigDecimalHolder holder){
        return new BigDecimalHolder(current,holder.scale, holder.roundingMode);
    }

    /**
     * 用法演示（可运行示例，非单元测试）
     *
     * @param args 未使用
     */
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
