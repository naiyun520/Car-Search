package cn.naiblog.carsearch.db;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import java.sql.Statement;

/** 从本次INSERT的Statement取得编号，不再另借连接查询会话中的上次编号。 */
public final class GeneratedInsert {
    private GeneratedInsert() {}

    public static int insert(JdbcTemplate jdbc,String sql,Object... values) {
        GeneratedKeyHolder keys=new GeneratedKeyHolder();
        int rows=jdbc.update(connection->{
            var statement=connection.prepareStatement(sql,Statement.RETURN_GENERATED_KEYS);
            for(int i=0;i<values.length;i++)statement.setObject(i+1,values[i]);
            return statement;
        },keys);
        Number key=keys.getKey();
        if(rows!=1||key==null||key.longValue()<=0)throw new IllegalStateException("新增记录未返回有效编号");
        return Math.toIntExact(key.longValue());
    }
}
