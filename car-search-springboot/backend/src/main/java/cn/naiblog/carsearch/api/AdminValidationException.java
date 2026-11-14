package cn.naiblog.carsearch.api;

import java.util.Map;

/** 管理员可读的校验说明，同时提供表单定位信息；不返回 SQL 或认证配置。 */
public class AdminValidationException extends BusinessException {
    private final Map<String,Object> details;
    public AdminValidationException(String section,int row,String field,String message){
        super(section+(row>0?"第"+row+"行":"")+"："+message);
        details=Map.of("section",section,"row",row,"field",field);
    }
    public Map<String,Object> details(){return details;}
}
