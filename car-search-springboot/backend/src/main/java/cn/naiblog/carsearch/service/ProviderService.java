package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.security.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.math.BigInteger;
import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.CompletableFuture;

@Service
public class ProviderService {
    private static final Logger log=LoggerFactory.getLogger(ProviderService.class);
    private final CryptoService crypto; private final ObjectMapper mapper; private final JdbcTemplate jdbc;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    private final String callbackBaseUrl;
    public ProviderService(CryptoService crypto,ObjectMapper mapper,JdbcTemplate jdbc,
                           @Value("${car.callback-base-url:}")String callbackBaseUrl){
        this.crypto=crypto;this.mapper=mapper;this.jdbc=jdbc;this.callbackBaseUrl=callbackBaseUrl;
    }

    public Result query(Map<String,Object> service,Map<String,Object> input){
        log.info("[ProviderQuery] 开始查询 service_code={}, provider_api_id={}, query_mode={}, callback_mode={}, input={}",
                service.get("code"), service.get("provider_api_id"), service.get("query_mode"),
                service.getOrDefault("callback_mode","none"), input);
        try{
            int providerId=service.get("provider_api_id")instanceof Number n?n.intValue():0;
            String queryMode=String.valueOf(service.getOrDefault("query_mode","single"));
            String callbackMode=String.valueOf(service.getOrDefault("callback_mode","none"));
            Result result;
            // 回调模式优先：下单后立即返回，等回调或降级轮询
            if("callback".equals(callbackMode)&&providerId>0){
                result=queryCallbackMode(service,input,providerId);
            }
            else if("async".equals(queryMode)&&providerId>0)result=queryAsync(service,input,providerId);
            else if(providerId>0)result=queryViaProvider(service,input,providerId);
            else result=queryDirect(service,input);
            log.info("[ProviderQuery] 查询成功 code={}, requestId={}, dataCount={}", result.code(), result.requestId(), result.data().size());
            return result;
        }catch(RuntimeException e){
            log.error("[ProviderQuery] 查询失败 service_code={}, error={}", service.get("code"), e.getMessage(), e);
            throw e;
        }catch(Exception e){
            log.error("[ProviderQuery] 查询异常 service_code={}, error={}", service.get("code"), e.getMessage(), e);
            throw new IllegalStateException("供应商连接失败",e);
        }
    }

    /** 现有直连模式：从 request_url_cipher 解密 URL */
    private Result queryDirect(Map<String,Object> service,Map<String,Object> input)throws Exception{
        String configured=String.valueOf(crypto.decrypt(String.valueOf(service.getOrDefault("request_url_cipher",""))).getOrDefault("url","")).trim();
        if(configured.isBlank())throw new IllegalStateException("查询接口未配置");
        URI uri=new URI(configured);assertPublicHttps(uri);
        String method=String.valueOf(service.getOrDefault("request_method","GET")).toUpperCase();if(!List.of("GET","POST").contains(method))throw new IllegalStateException("查询接口请求方式无效");
        log.info("[DirectQuery] 直连模式 url={}, method={}, input={}", uri, method, input);
        HttpRequest.Builder builder=HttpRequest.newBuilder().timeout(Duration.ofSeconds(20)).header("Accept","application/json").header("User-Agent","CarInquiry/2.0");
        if("GET".equals(method))builder.uri(withQuery(uri,input)).GET();else builder.uri(withoutQueryKeys(uri,input.keySet())).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(input)));
        return executeAndParse(builder,service);
    }

    /** 供应商模式：加载 provider 配置，注入签名，拼接 URL */
    private Result queryViaProvider(Map<String,Object> service,Map<String,Object> input,int providerId)throws Exception{
        List<Map<String,Object>> rows=jdbc.queryForList("select * from ci_provider where id=? and status=1",providerId);
        if(rows.isEmpty())throw new IllegalStateException("供应商配置不存在或已禁用");
        Map<String,Object> provider=rows.getFirst();

        String baseUrl=String.valueOf(provider.get("base_url")).trim();
        String apiPath=String.valueOf(service.getOrDefault("api_path","")).trim();
        if(baseUrl.isBlank())throw new IllegalStateException("供应商基础URL未配置");
        if(apiPath.isBlank())throw new IllegalStateException("服务接口路径未配置");

        // 拼接完整 URL
        String fullUrl=baseUrl.endsWith("/")?baseUrl.substring(0,baseUrl.length()-1):baseUrl;
        fullUrl+=apiPath.startsWith("/")?apiPath:"/"+apiPath;
        URI uri=new URI(fullUrl);

        // 检查协议
        boolean allowHttp=provider.get("allow_http")instanceof Number n&&n.intValue()==1;
        if(!allowHttp)assertPublicHttps(uri);

        // 构建请求参数
        String serviceVersion=String.valueOf(service.getOrDefault("api_version","")).trim();
        Map<String,String>params=buildSignedParams(provider,input,serviceVersion);
        log.info("[ProviderQuery] 供应商模式 url={}, params={}", uri, params);

        // 发送 POST 请求
        HttpRequest.Builder builder=HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(20))
                .header("Accept","application/json").header("User-Agent","CarInquiry/2.0")
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(params)));
        return executeAndParse(builder,service);
    }

    /** 回调模式：下单后立即返回，等回调或降级轮询 */
    private Result queryCallbackMode(Map<String,Object> service,Map<String,Object> input,int providerId)throws Exception{
        List<Map<String,Object>> rows=jdbc.queryForList("select * from ci_provider where id=? and status=1",providerId);
        if(rows.isEmpty())throw new IllegalStateException("供应商配置不存在或已禁用");
        Map<String,Object> provider=rows.getFirst();

        String baseUrl=String.valueOf(provider.get("base_url")).trim();
        String apiPath=String.valueOf(service.getOrDefault("api_path","")).trim();
        if(baseUrl.isBlank())throw new IllegalStateException("供应商基础URL未配置");
        if(apiPath.isBlank())throw new IllegalStateException("服务接口路径未配置");

        boolean allowHttp=provider.get("allow_http")instanceof Number n&&n.intValue()==1;

        // 拼接完整 URL
        String fullUrl=buildFullUrl(baseUrl,apiPath);
        URI uri=new URI(fullUrl);
        if(!allowHttp)assertPublicHttps(uri);

        // 构建回调 URL（供应商会 POST 结果到此地址）
        int serviceId=((Number)service.get("id")).intValue();
        String callbackSecret=String.valueOf(service.getOrDefault("callback_secret","")).trim();
        String callbackUrl=buildCallbackUrl(serviceId);
        log.info("[CallbackMode] callbackUrl={}, callbackSecret={}", callbackUrl,
                callbackSecret.isBlank()?"(无)":"****");

        // 构建回调参数
        Map<String,Object> callbackParam=new LinkedHashMap<>();
        callbackParam.put("callback_url",callbackUrl);
        if(!callbackSecret.isBlank()){
            callbackParam.put("HTTP-X-CA-PROXY-SIGNATURE",callbackSecret);
        }

        // 合并输入参数，添加 callback
        Map<String,Object> mergedInput=new LinkedHashMap<>(input);
        mergedInput.put("callback",toJson(callbackParam));

        // 构建带签名的请求参数
        String serviceVersion=String.valueOf(service.getOrDefault("api_version","")).trim();
        Map<String,String>params=buildSignedParams(provider,mergedInput,serviceVersion);
        log.info("[CallbackMode] 下单 url={}, params={}", uri, params);

        // 发送下单请求
        HttpRequest.Builder builder=HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(20))
                .header("Accept","application/json").header("User-Agent","CarInquiry/2.0")
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(params)));

        HttpResponse<byte[]> response=http.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());
        if(response.statusCode()!=200)throw new IllegalStateException("下单接口 HTTP 状态异常："+response.statusCode());

        String body=new String(response.body(),StandardCharsets.UTF_8);
        log.info("[CallbackMode] 下单响应 body={}", body);
        Map<String,Object> payload=mapper.readValue(response.body(),new TypeReference<LinkedHashMap<String,Object>>(){});
        String number=String.valueOf(payload.getOrDefault("number",""));
        if(!"200".equals(number)){
            String msg=truncate(String.valueOf(payload.getOrDefault("message","")),160);
            throw new ProviderQueryException("下单失败"+(msg.isBlank()?"":"："+msg),number,"");
        }

        // 提取 query_id
        String idFieldPath=String.valueOf(service.getOrDefault("id_field_path","")).trim();
        String queryId=extractQueryId(payload,idFieldPath);
        if(queryId.isBlank())throw new IllegalStateException("下单成功但未返回 query_id");

        // 保存查询跟踪记录
        LocalDateTime now=LocalDateTime.now();
        jdbc.update(
                "insert into ci_query(query_id,service_id,status,request_body,created_at,updated_at) values(?,?,?,?,?,?)",
                queryId,serviceId,"pending",toJson(input),now,now);
        log.info("[CallbackMode] 下单成功 queryId={}，等待回调", queryId);

        // 立即返回（不等待回调）
        // 返回 "processing" 标记，QueryRunnerService 会识别并保持订单为 querying 状态
        log.info("[CallbackMode] 返回 processing 标记，等待回调或降级轮询");
        return new Result("processing",queryId,List.of(Map.of("key","_status","label","查询状态","value","供应商处理中，结果将通过回调返回")));
    }

    /** 提取 query_id（支持配置路径或兼容不同字段名） */
    private String extractQueryId(Map<String,Object> payload, String fieldPath){
        // 如果配置了路径，按路径提取
        if(fieldPath!=null&&!fieldPath.isBlank()){
            log.info("[ExtractQueryId] 使用配置路径: {}", fieldPath);
            Object value=path(payload,fieldPath);
            if(value!=null){
                String val=String.valueOf(value).trim();
                if(!val.isBlank()){
                    log.info("[ExtractQueryId] 按路径提取成功: {}={}", fieldPath, val);
                    return val;
                }
            }
            log.warn("[ExtractQueryId] 配置路径 {} 未提取到值，payload keys={}", fieldPath, payload.keySet());
            return "";
        }

        // 兼容逻辑：自动尝试常见字段名
        log.info("[ExtractQueryId] 未配置路径，使用兼容逻辑");
        Object resultObj=payload.get("result");
        if(resultObj instanceof Map){
            @SuppressWarnings("unchecked")Map<String,Object> resultMap=(Map<String,Object>)resultObj;
            for(String key:new String[]{"query_id","code_id","order_id","task_id","report_id","id"}){
                String val=String.valueOf(resultMap.getOrDefault(key,"")).trim();
                if(!val.isBlank()){
                    log.info("[ExtractQueryId] 在 result.{} 找到值: {}", key, val);
                    return val;
                }
            }
        }
        // 尝试顶层
        for(String key:new String[]{"query_id","code_id","order_id","task_id","report_id","id"}){
            String val=String.valueOf(payload.getOrDefault(key,"")).trim();
            if(!val.isBlank()){
                log.info("[ExtractQueryId] 在顶层 {} 找到值: {}", key, val);
                return val;
            }
        }
        log.warn("[ExtractQueryId] 未找到任何ID字段, payload={}", payload);
        return "";
    }

    /** 构建回调 URL */
    private String buildCallbackUrl(int serviceId){
        // 回调 URL 格式：https://your-domain/api/callback/provider/{serviceId}
        String baseUrl=callbackBaseUrl;
        if(baseUrl==null||baseUrl.isBlank()){
            log.warn("[CallbackMode] callback-base-url 未配置，使用相对路径（仅本地测试有效）");
            return "/api/callback/provider/"+serviceId;
        }
        baseUrl=baseUrl.endsWith("/")?baseUrl.substring(0,baseUrl.length()-1):baseUrl;
        return baseUrl+"/api/callback/provider/"+serviceId;
    }

    /** 降级轮询：当回调超时后，主动轮询供应商获取结果 */
    public void pollForResult(int serviceId,String queryId)throws Exception{
        log.info("[Fallback] 开始降级轮询 serviceId={}, queryId={}", serviceId, queryId);

        List<Map<String,Object>> services=jdbc.queryForList("select * from ci_service where id=?",serviceId);
        if(services.isEmpty())throw new IllegalStateException("服务不存在");
        Map<String,Object> service=services.getFirst();

        int providerId=service.get("provider_api_id")instanceof Number n?n.intValue():0;
        if(providerId<=0)throw new IllegalStateException("服务未配置供应商");

        List<Map<String,Object>> providers=jdbc.queryForList("select * from ci_provider where id=? and status=1",providerId);
        if(providers.isEmpty())throw new IllegalStateException("供应商不存在或已禁用");
        Map<String,Object> provider=providers.getFirst();

        String baseUrl=String.valueOf(provider.get("base_url")).trim();
        String fetchApiPath=String.valueOf(service.getOrDefault("fetch_api_path","")).trim();
        if(fetchApiPath.isBlank())throw new IllegalStateException("获取报告接口路径未配置");

        boolean allowHttp=provider.get("allow_http")instanceof Number n&&n.intValue()==1;
        String fetchUrl=buildFullUrl(baseUrl,fetchApiPath);
        URI fetchUri=new URI(fetchUrl);
        if(!allowHttp)assertPublicHttps(fetchUri);

        String serviceVersion=String.valueOf(service.getOrDefault("api_version","")).trim();
        String fetchVersion=String.valueOf(service.getOrDefault("fetch_api_version","")).trim();
        if(fetchVersion.isBlank())fetchVersion=serviceVersion; // 回退到 api_version
        // 报告接口的查询ID参数名可配置，默认 query_id，部分供应商使用 order_id
        String paramName=String.valueOf(service.getOrDefault("fetch_api_param_name","query_id")).trim();
        if(paramName.isBlank())paramName="query_id";
        int pollInterval=service.get("poll_interval_sec")instanceof Number n?Math.max(2,n.intValue()):5;
        int pollTimeout=service.get("poll_timeout_sec")instanceof Number n?Math.max(10,n.intValue()):120;
        long deadline=System.currentTimeMillis()+pollTimeout*1000L;

        int pollCount=0;
        while(System.currentTimeMillis()<deadline){
            pollCount++;
            Map<String,String>fetchParams=buildSignedParams(provider,Map.of(paramName,queryId),fetchVersion);
            log.info("[Fallback] 轮询 pollCount={}, url={}, params={}, version={}", pollCount, fetchUri, fetchParams, fetchVersion);

            HttpRequest.Builder fetchBuilder=HttpRequest.newBuilder().uri(fetchUri).timeout(Duration.ofSeconds(20))
                    .header("Accept","application/json").header("User-Agent","CarInquiry/2.0")
                    .header("Content-Type","application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form(fetchParams)));

            HttpResponse<byte[]> fetchResponse=http.send(fetchBuilder.build(),HttpResponse.BodyHandlers.ofByteArray());
            if(fetchResponse.statusCode()!=200)throw new IllegalStateException("获取报告接口 HTTP 状态异常："+fetchResponse.statusCode());

            String fetchBody=new String(fetchResponse.body(),StandardCharsets.UTF_8);
            log.info("[Fallback] 轮询响应 pollCount={}, body={}", pollCount, fetchBody);
            Map<String,Object> fetchPayload=mapper.readValue(fetchResponse.body(),new TypeReference<LinkedHashMap<String,Object>>(){});
            String fetchNumber=String.valueOf(fetchPayload.getOrDefault("number",""));

            if(!"200".equals(fetchNumber)){
                String msg=truncate(String.valueOf(fetchPayload.getOrDefault("message","")),160);
                throw new ProviderQueryException("获取报告失败"+(msg.isBlank()?"":"："+msg),fetchNumber,"");
            }

            // 检查查询状态
            Object fetchResult=fetchPayload.get("result");
            if(fetchResult instanceof Map){
                @SuppressWarnings("unchecked")Map<String,Object> fr=(Map<String,Object>)fetchResult;
                String statusField="";
                String statusValue="";
                for(String key:new String[]{"query_status","result_status","order_status"}){
                    String val=String.valueOf(fr.getOrDefault(key,"")).trim();
                    if(!val.isBlank()){statusField=key;statusValue=val;break;}
                }

                boolean isSuccess;
                boolean isFailed;
                if("order_status".equals(statusField)){
                    // order_status: 0=查询中, 1=查询成功, 2=查询失败
                    isSuccess="1".equals(statusValue);
                    isFailed="2".equals(statusValue);
                }else if("result_status".equals(statusField)){
                    isSuccess="1".equals(statusValue);
                    isFailed="3".equals(statusValue);
                }else{
                    isSuccess="3".equals(statusValue);
                    isFailed="7".equals(statusValue);
                }

                if(isSuccess){
                    // 成功：更新查询记录
                    jdbc.update(
                            "update ci_query set status='completed', result_json=?, result_code='200', " +
                                    "result_message='降级轮询成功', completed_at=?, updated_at=? where query_id=? and status in ('pending','processing')",
                            fetchBody,LocalDateTime.now(),LocalDateTime.now(),queryId);
                    log.info("[Fallback] 降级轮询成功 queryId={}", queryId);
                    return;
                }

                if(isFailed){
                    String statusText=String.valueOf(fr.getOrDefault("query_status_text",fr.getOrDefault("result_status_text","查询失败")));
                    jdbc.update(
                            "update ci_query set status='failed', result_code='query_failed', " +
                                    "result_message=?, completed_at=?, updated_at=? where query_id=? and status in ('pending','processing')",
                            statusText,LocalDateTime.now(),LocalDateTime.now(),queryId);
                    throw new ProviderQueryException(statusText,"query_failed",queryId);
                }
            }

            Thread.sleep(pollInterval*1000L);
        }

        // 超时
        jdbc.update(
                "update ci_query set status='timeout', result_code='timeout', " +
                        "result_message='查询超时', completed_at=?, updated_at=? where query_id=? and status in ('pending','processing')",
                LocalDateTime.now(),LocalDateTime.now(),queryId);
        throw new ProviderQueryException("查询超时，请稍后重试","timeout",queryId);
    }

    private String toJson(Object obj){
        try{return mapper.writeValueAsString(obj);}catch(Exception e){return "{}";}
    }

    /** 异步两步式查询：下单→轮询获取报告 */
    private Result queryAsync(Map<String,Object> service,Map<String,Object> input,int providerId)throws Exception{
        List<Map<String,Object>> rows=jdbc.queryForList("select * from ci_provider where id=? and status=1",providerId);
        if(rows.isEmpty())throw new IllegalStateException("供应商配置不存在或已禁用");
        Map<String,Object> provider=rows.getFirst();

        String baseUrl=String.valueOf(provider.get("base_url")).trim();
        String orderApiPath=String.valueOf(service.getOrDefault("api_path","")).trim();
        String fetchApiPath=String.valueOf(service.getOrDefault("fetch_api_path","")).trim();
        if(baseUrl.isBlank())throw new IllegalStateException("供应商基础URL未配置");
        if(orderApiPath.isBlank())throw new IllegalStateException("下单接口路径未配置");
        if(fetchApiPath.isBlank())throw new IllegalStateException("获取报告接口路径未配置");

        boolean allowHttp=provider.get("allow_http")instanceof Number n&&n.intValue()==1;

        // 第一步：调用下单接口
        String orderUrl=buildFullUrl(baseUrl,orderApiPath);
        URI orderUri=new URI(orderUrl);
        if(!allowHttp)assertPublicHttps(orderUri);

        String serviceVersion=String.valueOf(service.getOrDefault("api_version","")).trim();
        String fetchVersion=String.valueOf(service.getOrDefault("fetch_api_version","")).trim();
        if(fetchVersion.isBlank())fetchVersion=serviceVersion; // 回退到 api_version
        Map<String,String>orderParams=buildSignedParams(provider,input,serviceVersion);
        log.info("[AsyncQuery] 第一步：下单 url={}, params={}, version={}", orderUri, orderParams, serviceVersion);
        HttpRequest.Builder orderBuilder=HttpRequest.newBuilder().uri(orderUri).timeout(Duration.ofSeconds(20))
                .header("Accept","application/json").header("User-Agent","CarInquiry/2.0")
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(orderParams)));

        HttpResponse<byte[]> orderResponse=http.send(orderBuilder.build(),HttpResponse.BodyHandlers.ofByteArray());
        if(orderResponse.statusCode()!=200)throw new IllegalStateException("下单接口 HTTP 状态异常："+orderResponse.statusCode());

        String orderBody=new String(orderResponse.body(),StandardCharsets.UTF_8);
        log.info("[AsyncQuery] 下单响应 body={}", orderBody);
        Map<String,Object> orderPayload=mapper.readValue(orderResponse.body(),new TypeReference<LinkedHashMap<String,Object>>(){});
        String orderNumber=String.valueOf(orderPayload.getOrDefault("number",""));
        if(!"200".equals(orderNumber)){
            String msg=truncate(String.valueOf(orderPayload.getOrDefault("message","")),160);
            throw new ProviderQueryException("下单失败"+(msg.isBlank()?"":"："+msg),orderNumber,"");
        }

        // 提取 query_id（兼容不同字段名：query_id/code_id/order_id）
        Object resultObj=orderPayload.get("result");
        String queryId="";
        if(resultObj instanceof Map){
            @SuppressWarnings("unchecked")Map<String,Object> resultMap=(Map<String,Object>)resultObj;
            for(String key:new String[]{"query_id","code_id","order_id"}){
                String val=String.valueOf(resultMap.getOrDefault(key,"")).trim();
                if(!val.isBlank()){queryId=val;break;}
            }
        }
        if(queryId.isBlank())throw new IllegalStateException("下单成功但未返回 query_id");

        // 第二步：轮询获取报告
        int pollInterval=service.get("poll_interval_sec")instanceof Number n?Math.max(2,n.intValue()):5;
        int pollTimeout=service.get("poll_timeout_sec")instanceof Number n?Math.max(10,n.intValue()):120;
        long deadline=System.currentTimeMillis()+pollTimeout*1000L;

        String fetchUrl=buildFullUrl(baseUrl,fetchApiPath);
        URI fetchUri=new URI(fetchUrl);
        if(!allowHttp)assertPublicHttps(fetchUri);

        String codePath=String.valueOf(service.getOrDefault("response_code_path","code")).trim();
        String successValue=String.valueOf(service.getOrDefault("response_success_value","200"));
        String dataPath=String.valueOf(service.getOrDefault("response_data_path","data")).trim();

        int pollCount=0;
        while(System.currentTimeMillis()<deadline){
            pollCount++;
            // 构建获取报告的请求参数（使用 fetchVersion）
            Map<String,String>fetchParams=buildSignedParams(provider,Map.of("query_id",queryId),fetchVersion);
            log.info("[AsyncQuery] 第二步：获取报告 pollCount={}, url={}, params={}, version={}", pollCount, fetchUri, fetchParams, fetchVersion);

            HttpRequest.Builder fetchBuilder=HttpRequest.newBuilder().uri(fetchUri).timeout(Duration.ofSeconds(20))
                    .header("Accept","application/json").header("User-Agent","CarInquiry/2.0")
                    .header("Content-Type","application/x-www-form-urlencoded")
                    .POST(HttpRequest.BodyPublishers.ofString(form(fetchParams)));

            HttpResponse<byte[]> fetchResponse=http.send(fetchBuilder.build(),HttpResponse.BodyHandlers.ofByteArray());
            if(fetchResponse.statusCode()!=200)throw new IllegalStateException("获取报告接口 HTTP 状态异常："+fetchResponse.statusCode());

            String fetchBody=new String(fetchResponse.body(),StandardCharsets.UTF_8);
            log.info("[AsyncQuery] 获取报告响应 pollCount={}, body={}", pollCount, fetchBody);
            Map<String,Object> fetchPayload=mapper.readValue(fetchResponse.body(),new TypeReference<LinkedHashMap<String,Object>>(){});
            String fetchNumber=String.valueOf(fetchPayload.getOrDefault("number",""));

            if(!"200".equals(fetchNumber)){
                String msg=truncate(String.valueOf(fetchPayload.getOrDefault("message","")),160);
                throw new ProviderQueryException("获取报告失败"+(msg.isBlank()?"":"："+msg),fetchNumber,"");
            }

            // 检查查询状态（兼容不同字段名和值）
            Object fetchResult=fetchPayload.get("result");
            if(fetchResult instanceof Map){
                @SuppressWarnings("unchecked")Map<String,Object> fr=(Map<String,Object>)fetchResult;
                // 获取状态字段和值（兼容 query_status、result_status、order_status）
                String statusField="";
                String statusValue="";
                for(String key:new String[]{"query_status","result_status","order_status"}){
                    String val=String.valueOf(fr.getOrDefault(key,"")).trim();
                    if(!val.isBlank()){statusField=key;statusValue=val;break;}
                }

                // 判断是否成功
                boolean isSuccess;
                boolean isFailed;
                if("order_status".equals(statusField)){
                    // order_status: 0=查询中, 1=查询成功, 2=查询失败
                    isSuccess="1".equals(statusValue);
                    isFailed="2".equals(statusValue);
                }else if("result_status".equals(statusField)){
                    // result_status: 1=成功, 2=处理中, 3=失败
                    isSuccess="1".equals(statusValue);
                    isFailed="3".equals(statusValue);
                }else{
                    // query_status: 3=成功, 7=失败
                    isSuccess="3".equals(statusValue);
                    isFailed="7".equals(statusValue);
                }

                if(isSuccess){
                    Object reportData=fr.get("result");
                    if(!(reportData instanceof Map))throw new IllegalStateException("报告数据格式无效");
                    @SuppressWarnings("unchecked")Map<String,Object> report=(Map<String,Object>)reportData;
                    return new Result(successValue,queryId,normalize(service,report));
                }

                if(isFailed){
                    String statusText=String.valueOf(fr.getOrDefault("query_status_text",fr.getOrDefault("result_status_text","查询失败")));
                    throw new ProviderQueryException(statusText,"query_failed",queryId);
                }

                // 其他状态=查询中，继续轮询
            }

            // 等待后重试
            Thread.sleep(pollInterval*1000L);
        }

        // 超时
        throw new ProviderQueryException("查询超时，请稍后重试","timeout",queryId);
    }

    /** 构建带签名的请求参数（带服务级版本覆盖） */
    private Map<String,String> buildSignedParams(Map<String,Object> provider,Map<String,Object> input,String apiVersion)throws Exception{
        Map<String,String>params=new LinkedHashMap<>();
        String authType=String.valueOf(provider.getOrDefault("auth_type","none"));
        log.info("[BuildParams] authType={}, input={}, apiVersion={}", authType, input, apiVersion);

        // 提取 callback 参数（不参与签名，但需要添加到请求中）
        String callbackValue=null;
        Object callbackObj=input.get("callback");
        if(callbackObj!=null){
            callbackValue=String.valueOf(callbackObj).trim();
            if(callbackValue.isBlank())callbackValue=null;
        }

        if("sign_md5".equals(authType)){
            Map<String,Object>config=crypto.decrypt(String.valueOf(provider.getOrDefault("config_cipher","")));
            String appid=String.valueOf(config.getOrDefault("appid","")).trim();
            String secret=String.valueOf(config.getOrDefault("secret","")).trim();
            // 优先使用服务级版本覆盖，否则使用供应商默认版本
            String version=!apiVersion.isBlank()?apiVersion:String.valueOf(config.getOrDefault("version","1.0.0")).trim();
            if(appid.isBlank()||secret.isBlank())throw new IllegalStateException("供应商AppID或Secret未配置");
            log.info("[BuildParams] appid={}, version={}{}, secret={}", appid, version, !apiVersion.isBlank()?" (服务覆盖)":" (供应商默认)", secret.substring(0,Math.min(4,secret.length()))+"****");

            long timestamp=System.currentTimeMillis()/1000;
            params.put("appid",appid);
            params.put("version",version);
            params.put("timestamp",String.valueOf(timestamp));

            // 合并用户输入参数（callback 不参与签名）
            for(var e:input.entrySet()){
                String key=e.getKey();
                String v=String.valueOf(e.getValue()).trim();
                if("callback".equals(key))continue; // callback 不参与签名
                if(!v.isBlank())params.put(key,v);
            }

            // 生成签名（不含 callback）
            String sign=signMd5(params,secret);
            params.put("sign",sign);

            // 签名后添加 callback 参数到请求中
            if(callbackValue!=null){
                params.put("callback",callbackValue);
            }
            log.info("[BuildParams] 最终参数={}, sign={}", params, sign);
        }else{
            for(var e:input.entrySet())params.put(e.getKey(),String.valueOf(e.getValue()));
            log.info("[BuildParams] 无签名，参数={}", params);
        }

        return params;
    }

    /** 拼接完整 URL */
    private String buildFullUrl(String baseUrl,String apiPath){
        String url=baseUrl.endsWith("/")?baseUrl.substring(0,baseUrl.length()-1):baseUrl;
        url+=apiPath.startsWith("/")?apiPath:"/"+apiPath;
        return url;
    }

    /** 判断使用哪个状态字段 */
    private String getStatusField(Map<String,Object> result){
        if(result.containsKey("query_status"))return "query_status";
        if(result.containsKey("result_status"))return "result_status";
        if(result.containsKey("order_status"))return "order_status";
        return "query_status"; // 默认
    }

    /** 汽修宝 sign_md5 签名算法 */
    private String signMd5(Map<String,String>params,String secret)throws Exception{
        // 合并 secret 到参数集
        TreeMap<String,String> sorted=new TreeMap<>(params);
        sorted.put("secret",secret);

        // 移除空值（callback 已在调用方排除）
        sorted.values().removeIf(v->v==null||v.isBlank());

        // 按 key ASCII 排序，value URL-encode，拼接
        StringBuilder sb=new StringBuilder();
        boolean first=true;
        for(var e:sorted.entrySet()){
            if(!first)sb.append('&');
            sb.append(e.getKey()).append('=').append(URLEncoder.encode(e.getValue(),StandardCharsets.UTF_8));
            first=false;
        }

        String signStr=sb.toString();
        log.info("[SignMD5] 签名字符串={}", signStr);

        // MD5 → 大写
        MessageDigest md=MessageDigest.getInstance("MD5");
        byte[] digest=md.digest(signStr.getBytes(StandardCharsets.UTF_8));
        String sign=String.format("%032x",new BigInteger(1,digest)).toUpperCase();
        log.info("[SignMD5] 签名结果={}", sign);
        return sign;
    }

    /** 执行 HTTP 请求并解析响应 */
    private Result executeAndParse(HttpRequest.Builder builder,Map<String,Object> service)throws Exception{
        HttpResponse<byte[]> response=http.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());
        if(response.statusCode()!=200)throw new IllegalStateException("供应商 HTTP 状态异常："+response.statusCode());
        if(response.body().length>1_048_576)throw new IllegalStateException("供应商响应内容过大");
        String responseBody=new String(response.body(),StandardCharsets.UTF_8);
        log.info("[ExecuteAndParse] 响应 body={}", responseBody);
        Map<String,Object> payload=mapper.readValue(response.body(),new TypeReference<LinkedHashMap<String,Object>>(){});
        log.info("[ExecuteAndParse] 解析后 payload={}", payload);
        String codePath=String.valueOf(service.getOrDefault("response_code_path","code")).trim();
        String success=String.valueOf(service.getOrDefault("response_success_value","200"));
        String code=codePath.isBlank()?success:String.valueOf(path(payload,codePath));
        // 如果配置的codePath返回null，尝试使用number字段（汽修宝等平台使用number作为状态码）
        if("null".equals(code)||code.isBlank()){
            String numberValue=String.valueOf(payload.getOrDefault("number",""));
            if(!numberValue.isBlank()&&!"null".equals(numberValue)){
                log.info("[ExecuteAndParse] codePath={} 返回null，回退使用 number={}", codePath, numberValue);
                code=numberValue;
            }
        }
        log.info("[ExecuteAndParse] codePath={}, success={}, code={}", codePath, success, code);
        String rid=truncate(String.valueOf(Optional.ofNullable(payload.get("request_id")).orElse(Optional.ofNullable(payload.get("requestId")).orElse(payload.getOrDefault("rid","")))).trim(),100);
        if(!codePath.isBlank()&&!success.equals(code)){
            String message=truncate(String.valueOf(Optional.ofNullable(payload.get("msg")).orElse(payload.getOrDefault("message",""))).trim(),160);
            String errorCode=code.equals("null")||code.isBlank()?"missing_code":code;
            log.error("[ExecuteAndParse] 查询失败 errorCode={}, message={}", errorCode, message);
            throw new ProviderQueryException("供应商查询未成功"+(message.isBlank()?"":"："+message),errorCode,rid);
        }
        String dataPath=String.valueOf(service.getOrDefault("response_data_path","data")).trim();
        log.info("[ExecuteAndParse] dataPath={}", dataPath);
        Object data=dataPath.isBlank()?payload:path(payload,dataPath);
        // 如果配置的dataPath返回null，尝试使用result字段（汽修宝等平台使用result作为数据路径）
        if(data==null){
            Object resultFallback=payload.get("result");
            if(resultFallback instanceof Map<?,?>){
                log.info("[ExecuteAndParse] dataPath={} 返回null，回退使用 result", dataPath);
                data=resultFallback;
            }
        }
        log.info("[ExecuteAndParse] data={}", data);
        if(!(data instanceof Map<?,?> map)){
            log.error("[ExecuteAndParse] 数据格式无效，期望Map，实际类型={}, 值={}", data==null?"null":data.getClass().getName(), data);
            throw new IllegalStateException("供应商结果数据格式无效");
        }
        List<Map<String,Object>> normalized=normalize(service,cast(map));
        log.info("[ExecuteAndParse] normalize结果={}", normalized);
        return new Result(code.isBlank()?success:code,rid,normalized);
    }

    private void assertPublicHttps(URI uri)throws Exception{if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalStateException("查询接口必须使用无账号信息的 HTTPS 地址");String host=uri.getHost().toLowerCase();if(host.equals("localhost")||host.endsWith(".local"))throw new IllegalStateException("查询接口地址不安全");InetAddress[]addresses=InetAddress.getAllByName(host);if(addresses.length==0)throw new IllegalStateException("查询接口域名无法解析");for(InetAddress a:addresses)if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())throw new IllegalStateException("查询接口禁止访问内网地址");}
    URI withQuery(URI u,Map<String,Object> input)throws Exception{Map<String,String> q=new LinkedHashMap<>();if(u.getRawQuery()!=null)for(String part:u.getRawQuery().split("&",-1)){String[] p=part.split("=",2);q.put(URLDecoder.decode(p[0],StandardCharsets.UTF_8),p.length>1?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"");}input.forEach((k,v)->q.put(k,String.valueOf(v)));return withEncodedQuery(u,form(q));}
    URI withoutQueryKeys(URI u,Set<String>remove)throws Exception{Map<String,String>q=new LinkedHashMap<>();if(u.getRawQuery()!=null)for(String part:u.getRawQuery().split("&",-1)){String[]p=part.split("=",2);String key=URLDecoder.decode(p[0],StandardCharsets.UTF_8);if(!remove.contains(key))q.put(key,p.length>1?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"");}return withEncodedQuery(u,q.isEmpty()?null:form(q));}
    private URI withEncodedQuery(URI u,String encodedQuery)throws Exception{String path=u.getRawPath()==null||u.getRawPath().isBlank()?"/":u.getRawPath();StringBuilder target=new StringBuilder().append(u.getScheme()).append("://").append(u.getRawAuthority()).append(path);if(encodedQuery!=null&&!encodedQuery.isBlank())target.append('?').append(encodedQuery);return new URI(target.toString());}
    private String form(Map<String,?> m){return m.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(String.valueOf(e.getValue()),StandardCharsets.UTF_8)).reduce((a,b)->a+"&"+b).orElse("");}
    private Object path(Object value,String path){return path(value,path,true);}
    /** @param keepList true=最终List取第一个元素(默认), false=保留完整List */
    private Object path(Object value,String path,boolean keepList){
        log.debug("[Path] 提取路径={}, 输入={}, keepList={}", path, value, keepList);
        String[]segments=path.split("\\.");
        for(int i=0;i<segments.length;i++){
            String s=segments[i];
            // 如果当前值是 List/数组，自动取第一个非空元素继续遍历
            if(value instanceof List<?>list){
                if(list.isEmpty()){
                    log.debug("[Path] 路径中断，List为空，key={}", s);
                    return null;
                }
                value=list.getFirst();
                log.debug("[Path] List自动取第一个元素，类型={}", value==null?"null":value.getClass().getName());
                // 取出第一个元素后，需要重新处理当前 segment
                i--;
                continue;
            }
            if(!(value instanceof Map<?,?> m)){
                log.debug("[Path] 路径中断，当前值不是Map，类型={}, key={}", value==null?"null":value.getClass().getName(), s);
                return null;
            }
            if(!m.containsKey(s)){
                log.debug("[Path] 路径中断，key不存在={}, 可用keys={}", s, m.keySet());
                return null;
            }
            value=m.get(s);
        }
        // 最终结果如果是 List，根据 keepList 决定是否取第一个元素
        if(value instanceof List<?>list&&!list.isEmpty()&&keepList){
            value=list.getFirst();
            log.debug("[Path] 最终结果是List，取第一个元素");
        }
        log.debug("[Path] 提取结果={}", value);
        return value;
    }
    public List<Map<String,Object>> normalize(Map<String,Object> service,Map<String,Object> data)throws Exception{
        String schemaJson=String.valueOf(service.get("result_schema"));
        log.info("[Normalize] result_schema={}", schemaJson);
        log.info("[Normalize] 原始数据 keys={}, data={}", data.keySet(), data);
        Map<String,Object> schema=mapper.readValue(schemaJson,new TypeReference<LinkedHashMap<String,Object>>(){});
        List<Map<String,Object>> out=new ArrayList<>();
        for(var e:schema.entrySet()){
            String key=e.getKey();
            boolean isArray=key.endsWith("[]");
            String actualKey=isArray?key.substring(0,key.length()-2):key;

            if(isArray){
                // 数组字段：保留完整 List，格式化全部元素
                Object raw=path(data,actualKey,false);
                log.info("[Normalize] 数组字段 key={}, raw={}", key, raw);
                if(raw instanceof List<?> list){
                    List<Object> formatted=ResultValueFormatter.formatList(actualKey,list);
                    Map<String,Object> item=new LinkedHashMap<>();
                    item.put("key",key);item.put("label",e.getValue());
                    item.put("value",formatted.isEmpty()?"暂无数据":formatted);
                    item.put("display","array");
                    out.add(item);
                }else{
                    // 值不是数组，降级为普通字段处理
                    Object v=ResultValueFormatter.format(key,raw);
                    if(v instanceof String s)v=truncate(s,2000);
                    Map<String,Object> item=new LinkedHashMap<>();
                    item.put("key",key);item.put("label",e.getValue());
                    item.put("value",v==null||"".equals(v)?"暂无数据":v);
                    out.add(item);
                }
            }else{
                // 普通字段：单值提取（原有逻辑）
                Object raw=path(data,actualKey);
                Object v=ResultValueFormatter.format(key,raw);
                log.info("[Normalize] key={}, raw={}, formatted={}", key, raw, v);
                if(v instanceof String s)v=truncate(s,2000);
                Map<String,Object> item=new LinkedHashMap<>();
                item.put("key",key);item.put("label",e.getValue());
                item.put("value",v==null||"".equals(v)?"暂无数据":v);
                out.add(item);
            }
        }
        log.info("[Normalize] 最终结果 count={}, data={}", out.size(), out);
        return out;
    }
    @SuppressWarnings("unchecked")private Map<String,Object> cast(Map<?,?>m){return(Map<String,Object>)m;}private String truncate(String s,int n){return s.codePointCount(0,s.length())<=n?s:s.substring(0,s.offsetByCodePoints(0,n));}
    public record Result(String code,String requestId,List<Map<String,Object>> data){}
}