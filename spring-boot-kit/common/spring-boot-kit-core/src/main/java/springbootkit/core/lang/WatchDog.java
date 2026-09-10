package springbootkit.core.lang;

import java.util.Timer;
import java.util.TimerTask;

/**
 * @Description 监视器
 * @Date 2024/12/13
 * @Version 0.0.1
 */
public abstract class WatchDog extends TimerTask{
    private final Timer timer;
    private boolean completed;

    public WatchDog(String name){
        timer = new Timer(name);
    }

    /**
     * 启动任务-执行一次
     * @param delay delay in milliseconds before task is to be executed.
     */
    public void start(long delay) {
        timer.schedule(this, delay);
    }

    /**
     * 启动任务-间隔重复执行
     * @param delay delay in milliseconds before task is to be executed.
     * @param period time in milliseconds between successive task executions.
     */
    public void start(long delay,long period) {
        timer.schedule(this, delay,period);
    }

    /**
     * 自定义实现监视逻辑
     */
    public abstract void watch();
    /**
     * 停止接收任务
     */
    public void stop() {
        completed = true;
        timer.cancel();
    }

    /**
     * 触发调用
     */
    @Override
    public void run() {
        if (!completed) {
            watch();
        }
    }


}
