package springbootkit.configuration.holder;


import springbootkit.configuration.security.pathtraversal.PathTraversalMeta;
import springbootkit.configuration.security.sql.inject.SqlInjectMeta;
import springbootkit.configuration.security.xss.XssMeta;

/**
 * @Description TODO
 * @Date 2022/5/24 14:22
 * @Version 0.0.1
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
