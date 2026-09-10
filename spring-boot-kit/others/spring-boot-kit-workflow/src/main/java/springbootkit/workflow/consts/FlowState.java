package springbootkit.workflow.consts;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @Description TODO
 * @Date 2023/5/11 9:18
 * @Version 0.0.1
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

    public String code(){
        return this.code;
    }
    public String msg(){
        return this.msg;
    }
}
