package io.github.jevonsnotes.jevokit.core.holder;

/**
 * 可变值信封标记接口：包内各 Holder（IntHolder/GenericHolder 等）的公共父类型。
 * <p>信封存在的意义：lambda/匿名类只能捕获 effectively final 的局部变量，
 * 把值装进 holder 对象后即可在回调内读写 value 字段，绕开该限制（可变捕获惯用法）。
 */
public interface Holder {
}
