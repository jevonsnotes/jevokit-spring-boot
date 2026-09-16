package springbootkit.core.holder;

/**
 * @Description TODO
 * @Date 2025/6/26
 * @Version 0.0.1
 */
public class ObjectHolder implements Holder{
    public Object value;
    public ObjectHolder(Object value) {
        this.value = value;
    }
    public ObjectHolder() {
    }
}
