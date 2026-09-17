package io.github.jevonsnotes.jevokit.configuration.holder;


import io.github.jevonsnotes.jevokit.configuration.security.pathtraversal.PathTraversalMeta;
import io.github.jevonsnotes.jevokit.configuration.security.sql.inject.SqlInjectMeta;
import io.github.jevonsnotes.jevokit.configuration.security.xss.XssMeta;

/**
 * 安全清洗请求级上下文：以 ThreadLocal 承载三类安全组件（路径穿越/XSS/SQL 注入）在单次请求内的元数据，
 * 供切面把 @PtClean/@XssClean/@SIClean 注解信息传递给清洗过滤器。
 * <p>get*Meta 取值时懒初始化，当前线程没有元数据会新建并绑定；remove* 必须在请求结束时调用，
 * 否则线程池复用线程会读到上一请求的残留配置（脏上下文）。
 */
public class ConfigHolder {
    /**
     * path traversal holder
     */
    private static final ThreadLocal<PathTraversalMeta> TL_PT = new ThreadLocal<>();
    /**
     * xss holder
     */
    private static final ThreadLocal<XssMeta> TL_XSS = new ThreadLocal<>();
    /**
     * sql inject holder
     */
    private static final ThreadLocal<SqlInjectMeta> TL_SQL_INJECT = new ThreadLocal<>();

    public static void removePt() {
        TL_PT.remove();
    }
    public static void removeXss() {
        TL_XSS.remove();
    }
    public static void removeSI() {
        TL_SQL_INJECT.remove();
    }

    public static PathTraversalMeta getPtMeta(){
        PathTraversalMeta meta = TL_PT.get();
        if(meta == null){
            TL_PT.set(meta =new PathTraversalMeta());
        }
        return meta;
    }

    public static XssMeta getXssMeta(){
        XssMeta meta = TL_XSS.get();
        if(meta == null){
            TL_XSS.set(meta =new XssMeta());
        }
        return meta;
    }
    public static SqlInjectMeta getSIMeta(){
        SqlInjectMeta meta = TL_SQL_INJECT.get();
        if(meta == null){
            TL_SQL_INJECT.set(meta =new SqlInjectMeta());
        }
        return meta;
    }
}
