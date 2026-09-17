package io.github.jevonsnotes.jevokit.workflow.consts;

import lombok.AllArgsConstructor;
import lombok.Getter;

 /**
 * 任务办结状态语义表：code 供引擎与持久化使用，msg 为中文含义。
 * 办结（taskCompleted）时按此表语义驱动流转：通过/驳回/逐级回退/回起草人/办结/加签。
 */
@AllArgsConstructor
public enum FlowState {
    SUBMIT("0","提交"),
    PASS("1","审核通过"),
    REJECT("2","审核不通过"),
    TO_LAST("3","返回上一级"),
    TO_SUBMIT("4","返回起草人"),
    FINISH("5","办结"),
    SIGN_BEFORE("6","加前签"),
    SIGN_AFTER("7","审核通过加后签"),
    ;
    private final String code;
    private final String msg;

    /**
    * 取状态码。
    * @return 状态码字符串
    */
    public String code(){
        return this.code;
    }
    /**
    * 取中文含义。
    * @return 含义文本
    */
    public String msg(){
        return this.msg;
    }
}
