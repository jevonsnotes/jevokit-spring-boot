package io.github.jevonsnotes.jevokit.workflow.consts;

 /**
 * 流程变量作用域：LOCAL 任务级（任务办结即失效）/ GLOBAL 实例级（随流程实例存活）。
 * 配合 @FlowVar#scope() 声明，FlowUtil 据此归类提取。
 */

public enum FlowScope {
    LOCAL,GLOBAL
}
