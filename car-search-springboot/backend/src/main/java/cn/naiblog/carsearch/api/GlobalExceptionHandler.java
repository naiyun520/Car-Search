package cn.naiblog.carsearch.api;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

@RestControllerAdvice
public class GlobalExceptionHandler {
    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);
    private static final SecureRandom RANDOM = new SecureRandom();

    @ExceptionHandler(BusinessException.class)
    ResponseEntity<ApiResponse<Object>> business(BusinessException error,HttpServletRequest request) {
        Object details=error instanceof AdminValidationException validation?validation.details():null;
        String message=isAdmin(request)?operation(request)+"："+error.getMessage():error.getMessage();
        return ResponseEntity.status(error.status()).body(new ApiResponse<>(error.code(),message,details));
    }

    @ExceptionHandler({HttpMessageNotReadableException.class, MethodArgumentNotValidException.class})
    ResponseEntity<ApiResponse<Void>> invalidRequest(Exception error, HttpServletRequest request) {
        log.warn("[422] 请求解析失败 method={} uri={} error={} message={}", request.getMethod(), request.getRequestURI(), error.getClass().getSimpleName(), error.getMessage());
        String message="请求数据格式错误，请更新小程序后重试";
        if(isAdmin(request)){
            message=operation(request)+"：提交内容无法读取，请重新打开当前编辑窗口并检查填写内容。";
            if(error instanceof MethodArgumentNotValidException validation&&!validation.getBindingResult().getFieldErrors().isEmpty()){
                var field=validation.getBindingResult().getFieldErrors().getFirst();
                message=operation(request)+"：字段“"+field.getField()+"”未填写或格式不正确，请检查该项。";
            }
        }
        return ResponseEntity.unprocessableEntity().body(ApiResponse.error(422,message));
    }

    @ExceptionHandler(NoResourceFoundException.class)
    ResponseEntity<ApiResponse<Void>> notFound(NoResourceFoundException ignored,HttpServletRequest request) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(ApiResponse.error(404,isAdmin(request)?operation(request)+"：后台找不到此操作入口，请刷新后台；若刚更新程序，请核对前后端是否一起更新。":"请求地址不存在"));
    }

    @ExceptionHandler(Exception.class)
    ResponseEntity<ApiResponse<Void>> unexpected(Exception error, HttpServletRequest request) {
        String errorId = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMddHHmmss"))
                + "-" + String.format("%06X", RANDOM.nextInt(0x1000000));
        log.error("[{}] request failed method={} uri={}", errorId, request.getMethod(), request.getRequestURI(), error);
        String message=isAdmin(request)?operation(request)+"时发生异常。请先刷新核对是否已生效，再决定是否重试；排查编号："+errorId:"服务暂时不可用，请稍后重试（编号 " + errorId + "）";
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE).body(ApiResponse.error(503,message));
    }

    private boolean isAdmin(HttpServletRequest request){return request.getRequestURI().startsWith("/admin-api/");}
    private String operation(HttpServletRequest request){
        String path=request.getRequestURI().substring("/admin-api/".length()),action="GET".equals(request.getMethod())?"读取":"保存";
        if(path.contains("/result-revisions"))return path.endsWith("/publish")?"发布订单报告修订":action+"订单报告修订";
        if(path.contains("/fulfillment"))return path.endsWith("/stop")?"停止订单后续查询":path.endsWith("/fetch-now")?"检查已提交的报告":"查看订单履约诊断";
        if(path.endsWith("/refund"))return "申请订单退款";
        if(path.endsWith("/refund-status"))return "同步订单退款结果";
        if(path.endsWith("/retry"))return "继续检查订单任务";
        if(path.endsWith("/delivery"))return "确认微信发货";
        if(path.endsWith("/sync-payment"))return "核对订单付款";
        if(path.startsWith("orders/batch-delete"))return "删除所选订单";
        if(path.contains("balance/refresh"))return "刷新供应商余额";
        if(path.startsWith("service-icon-upload"))return "上传业务图标";
        if(path.startsWith("service-test"))return "提交业务测试任务";
        if(path.equals("email-test"))return "发送测试邮件";
        if(path.equals("password"))return "修改管理员密码";
        if(path.equals("login"))return "登录后台";
        if(path.startsWith("analytics/export"))return "导出经营数据";
        String section=path.split("/")[0];
        String name=switch(section){case "services"->"接口服务";case "service-categories"->"服务分类";case "providers"->"供应商";case "orders","order-detail"->"订单";case "users"->"用户资料";case "announcements"->"公告";case "settings"->"运营与隐私设置";case "payment-settings"->"微信与支付设置";case "email-settings"->"邮件设置";case "android-auto-refund-settings"->"安卓自动退款设置";case "analytics"->"经营看板";case "wechat-messages"->"微信消息";default->"当前后台操作";};
        if("DELETE".equals(request.getMethod()))action="删除";else if(path.endsWith("/sort"))action="调整排序：";
        return action+name;
    }
}
