package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.security.CryptoService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
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

@Service
public class ProviderService {
    private static final Logger log=LoggerFactory.getLogger(ProviderService.class);
    private final CryptoService crypto; private final ObjectMapper mapper; private final JdbcTemplate jdbc;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public ProviderService(CryptoService crypto,ObjectMapper mapper,JdbcTemplate jdbc){
        this.crypto=crypto;this.mapper=mapper;this.jdbc=jdbc;
    }

    private Result queryDirect(Map<String,Object> service,Map<String,Object> input)throws Exception{
        String configured=String.valueOf(crypto.decrypt(String.valueOf(service.getOrDefault("request_url_cipher",""))).getOrDefault("url","")).trim();
        if(configured.isBlank())throw new IllegalStateException("查询接口未配置");
        URI uri=new URI(configured);assertPublicHttps(uri);
        String method=String.valueOf(service.getOrDefault("request_method","GET")).toUpperCase();if(!List.of("GET","POST").contains(method))throw new IllegalStateException("查询接口请求方式无效");
        log.info("[DirectQuery] 直连模式 host={}, path={}, method={}, inputKeys={}", uri.getHost(), uri.getPath(), method, input.keySet());
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
        assertProviderUri(uri,allowHttp);

        // 构建请求参数
        String serviceVersion=String.valueOf(service.getOrDefault("api_version","")).trim();
        Map<String,String>params=buildSignedParams(provider,input,serviceVersion);
        log.info("[ProviderQuery] 供应商模式 host={}, path={}, paramKeys={}", uri.getHost(), uri.getPath(), params.keySet());

        // 发送 POST 请求
        HttpRequest.Builder builder=HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(20))
                .header("Accept","application/json").header("User-Agent","CarInquiry/2.0")
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(params)));
        return executeAndParse(builder,service);
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
                    log.info("[ExtractQueryId] 按路径提取成功 path={}, requestId={}", fieldPath,mask(val));
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
                    log.info("[ExtractQueryId] 在 result.{} 找到值 requestId={}", key,mask(val));
                    return val;
                }
            }
        }
        // 尝试顶层
        for(String key:new String[]{"query_id","code_id","order_id","task_id","report_id","id"}){
            String val=String.valueOf(payload.getOrDefault(key,"")).trim();
            if(!val.isBlank()){
                log.info("[ExtractQueryId] 在顶层 {} 找到值 requestId={}", key,mask(val));
                return val;
            }
        }
        log.warn("[ExtractQueryId] 未找到任何ID字段, payloadKeys={}", payload.keySet());
        return "";
    }

    /** 执行一次同步查询，忽略异步/回调模式标记；供持久化任务的 DIRECT 阶段调用。 */
    public Result executeDirect(Map<String,Object> service,Map<String,Object> input)throws Exception{
        int providerId=number(service.get("provider_api_id"));
        return providerId>0?queryViaProvider(service,input,providerId):queryDirect(service,input);
    }

    /** 单次异步下单。只提交一次并返回供应商请求 ID，不轮询、不写订单状态。 */
    public SubmitResult submitAsync(Map<String,Object> service,Map<String,Object> input)throws Exception{
        int providerId=number(service.get("provider_api_id"));
        if(providerId<=0)throw new IllegalStateException("异步服务未绑定供应商");
        Map<String,Object> provider=provider(providerId);
        URI uri=providerUri(provider,String.valueOf(service.getOrDefault("api_path","")));
        Map<String,Object> requestInput=new LinkedHashMap<>(input);
        String apiVersion=String.valueOf(service.getOrDefault("api_version","")).trim();
        Map<String,String> params=buildSignedParams(provider,requestInput,apiVersion);
        HttpResponse<byte[]> response=sendForm(uri,params);
        Map<String,Object> payload=parsePayload(response,"下单");
        String success=String.valueOf(service.getOrDefault("response_success_value","200"));
        String code=String.valueOf(payload.getOrDefault("number",payload.getOrDefault("code",""))).trim();
        if(!success.equals(code)&&!"200".equals(code)){
            String message=truncate(String.valueOf(payload.getOrDefault("message",payload.getOrDefault("msg",""))),160);
            throw new ProviderQueryException("下单失败"+(message.isBlank()?"":"："+message),code.isBlank()?"missing_code":code,"");
        }
        String requestId=extractQueryId(payload,String.valueOf(service.getOrDefault("id_field_path","")));
        if(requestId.isBlank())throw new IllegalStateException("下单成功但未返回供应商请求 ID");
        log.info("[ProviderSubmit] 成功 service={}, requestId={}, responseKeys={}",service.get("code"),mask(requestId),payload.keySet());
        return new SubmitResult(code.isBlank()?success:code,requestId);
    }

    /** 单次获取报告。一次调用只返回一个状态，调度与等待由数据库任务负责。 */
    public FetchResult fetchReport(Map<String,Object> service,String providerRequestId)throws Exception{
        int providerId=number(service.get("provider_api_id"));
        if(providerId<=0)throw new IllegalStateException("异步服务未绑定供应商");
        Map<String,Object> provider=provider(providerId);
        URI uri=providerUri(provider,String.valueOf(service.getOrDefault("fetch_api_path","")));
        String fetchVersion=String.valueOf(service.getOrDefault("fetch_api_version","")).trim();
        if(fetchVersion.isBlank())fetchVersion=String.valueOf(service.getOrDefault("api_version","")).trim();
        String paramName=String.valueOf(service.getOrDefault("fetch_api_param_name","query_id")).trim();
        if(paramName.isBlank())paramName="query_id";
        Map<String,String> params=buildSignedParams(provider,Map.of(paramName,providerRequestId),fetchVersion);
        HttpResponse<byte[]> response=sendForm(uri,params);
        Map<String,Object> payload=parsePayload(response,"获取报告");
        return parseReport(service,payload,providerRequestId);
    }

    public FetchResult parseReport(Map<String,Object> service,Map<String,Object> payload,String providerRequestId)throws Exception{
        String code=String.valueOf(payload.getOrDefault("number",payload.getOrDefault("code",""))).trim();
        if(!"200".equals(code)){
            String message=truncate(String.valueOf(payload.getOrDefault("message",payload.getOrDefault("msg","查询失败"))),160);
            return new FetchResult(FetchState.FAILED,code,providerRequestId,List.of(),message);
        }
        if(!(payload.get("result") instanceof Map<?,?> raw))
            return new FetchResult(FetchState.RUNNING,code,providerRequestId,List.of(),"供应商处理中");
        Map<String,Object> result=flattenReport(cast(raw));
        QueryStatusResolver status=QueryStatusResolver.resolve(result);
        if(status==QueryStatusResolver.FAILED){
            String message=truncate(String.valueOf(result.getOrDefault("query_status_text",result.getOrDefault("result_status_text","查询失败"))),160);
            return new FetchResult(FetchState.FAILED,code,providerRequestId,List.of(),message);
        }
        if(status!=QueryStatusResolver.SUCCESS)
            return new FetchResult(FetchState.RUNNING,code,providerRequestId,List.of(),"供应商处理中");
        Map<String,Object> report=flattenReport(result);
        Map<String,Object> schema=mapper.readValue(String.valueOf(service.getOrDefault("result_schema","{}")),new TypeReference<LinkedHashMap<String,Object>>(){});
        if(!QueryStatusResolver.reportHasContent(schema,report))
            return new FetchResult(FetchState.NO_DATA,code,providerRequestId,List.of(),"查询成功但无可展示结果");
        return new FetchResult(FetchState.SUCCESS,code,providerRequestId,normalize(service,report),"");
    }

    /** One independent, signed balance request. The caller is responsible for the provider-level feature switch. */
    public BalanceResult queryBalance(Map<String,Object> provider)throws Exception{
        if(!"independent_api".equals(String.valueOf(provider.getOrDefault("balance_mode","unsupported"))))
            throw new IllegalStateException("该供应商未提供独立余额接口，请停用它的余额查询功能");
        URI uri=providerUri(provider,String.valueOf(provider.getOrDefault("balance_api_path","")));
        String version=String.valueOf(provider.getOrDefault("balance_api_version","")).trim();
        Map<String,Object> payload=parsePayload(sendForm(uri,buildSignedParams(provider,Map.of(),version)),"balance");
        String code=String.valueOf(payload.getOrDefault("number",payload.getOrDefault("code",""))).trim();
        if(!"200".equals(code)) throw new ProviderQueryException("供应商余额接口未成功，请核对余额接口的认证配置（业务码："+code+"）",code.isBlank()?"missing_code":code,"");
        String valuePath=String.valueOf(provider.getOrDefault("balance_value_path","")).trim();
        if(valuePath.isBlank()) throw new IllegalStateException("余额字段路径未填写，请编辑供应商的余额配置，例如 result.balance");
        Object raw=path(payload,valuePath);
        if(raw==null) throw new IllegalStateException("余额接口响应中没有找到字段“"+valuePath+"”，请核对余额字段路径");
        try{return new BalanceResult(new java.math.BigDecimal(String.valueOf(raw).trim()),code);}
        catch(NumberFormatException e){throw new IllegalStateException("余额字段“"+valuePath+"”不是数字，无法保存余额，请核对字段配置");}
    }

    private Map<String,Object> provider(int providerId){
        List<Map<String,Object>> rows=jdbc.queryForList("select * from ci_provider where id=? and status=1",providerId);
        if(rows.isEmpty())throw new IllegalStateException("供应商配置不存在或已禁用");
        return rows.getFirst();
    }

    private URI providerUri(Map<String,Object> provider,String apiPath)throws Exception{
        String baseUrl=String.valueOf(provider.getOrDefault("base_url","")).trim();
        apiPath=apiPath==null?"":apiPath.trim();
        if(baseUrl.isBlank()||apiPath.isBlank())throw new IllegalStateException("供应商地址或接口路径未配置");
        URI uri=new URI(buildFullUrl(baseUrl,apiPath));
        boolean allowHttp=number(provider.get("allow_http"))==1;
        assertProviderUri(uri,allowHttp);
        return uri;
    }

    private HttpResponse<byte[]> sendForm(URI uri,Map<String,String>params)throws Exception{
        HttpRequest request=HttpRequest.newBuilder().uri(uri).timeout(Duration.ofSeconds(20))
                .header("Accept","application/json").header("User-Agent","CarInquiry/2.0")
                .header("Content-Type","application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(form(params))).build();
        return http.send(request,HttpResponse.BodyHandlers.ofByteArray());
    }

    private Map<String,Object> parsePayload(HttpResponse<byte[]> response,String action)throws Exception{
        if(response.statusCode()!=200)throw new IllegalStateException(action+"接口 HTTP 状态异常："+response.statusCode());
        if(response.body().length>1_048_576)throw new IllegalStateException(action+"接口响应内容过大");
        Map<String,Object> payload=mapper.readValue(response.body(),new TypeReference<LinkedHashMap<String,Object>>(){});
        log.info("[ProviderCall] action={}, status={}, bytes={}, keys={}",action,response.statusCode(),response.body().length,payload.keySet());
        return payload;
    }

    private Map<String,String> buildSignedParams(Map<String,Object> provider,Map<String,Object> input,String apiVersion)throws Exception{
        input=new LinkedHashMap<>(input);
        input.remove("callback");
        Map<String,String>params=new LinkedHashMap<>();
        String authType=String.valueOf(provider.getOrDefault("auth_type","none"));
        log.debug("[BuildParams] authType={}, inputKeys={}, apiVersion={}",authType,input.keySet(),apiVersion);

        if("sign_md5".equals(authType)){
            Map<String,Object>config=crypto.decrypt(String.valueOf(provider.getOrDefault("config_cipher","")));
            String appid=String.valueOf(config.getOrDefault("appid","")).trim();
            String secret=String.valueOf(config.getOrDefault("secret","")).trim();
            // 优先使用服务级版本覆盖，否则使用供应商默认版本
            String version=!apiVersion.isBlank()?apiVersion:String.valueOf(config.getOrDefault("version","1.0.0")).trim();
            if(appid.isBlank()||secret.isBlank())throw new IllegalStateException("供应商AppID或Secret未配置");
            log.debug("[BuildParams] appid={}, version={}{}",mask(appid),version,!apiVersion.isBlank()?" (服务覆盖)":" (供应商默认)");

            long timestamp=System.currentTimeMillis()/1000;
            params.put("appid",appid);
            params.put("version",version);
            params.put("timestamp",String.valueOf(timestamp));

            // 合并用户输入参数
            for(var e:input.entrySet()){
                String key=e.getKey();
                String v=String.valueOf(e.getValue()).trim();
                if("callback".equals(key))continue; // 禁止调用方重新启用供应商报告回调
                if(!v.isBlank())params.put(key,v);
            }

            // 生成签名
            String sign=signMd5(params,secret);
            params.put("sign",sign);

            log.debug("[BuildParams] 参数构建完成 keys={}",params.keySet());
        }else{
            for(var e:input.entrySet())params.put(e.getKey(),String.valueOf(e.getValue()));
            log.debug("[BuildParams] 无签名参数 keys={}",params.keySet());
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

        // 移除空值
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

        // MD5 → 大写
        MessageDigest md=MessageDigest.getInstance("MD5");
        byte[] digest=md.digest(signStr.getBytes(StandardCharsets.UTF_8));
        String sign=String.format("%032x",new BigInteger(1,digest)).toUpperCase();
        return sign;
    }

    /** 执行 HTTP 请求并解析响应 */
    private Result executeAndParse(HttpRequest.Builder builder,Map<String,Object> service)throws Exception{
        HttpResponse<byte[]> response=http.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());
        if(response.statusCode()!=200)throw new IllegalStateException("供应商 HTTP 状态异常："+response.statusCode());
        if(response.body().length>1_048_576)throw new IllegalStateException("供应商响应内容过大");
        Map<String,Object> payload=mapper.readValue(response.body(),new TypeReference<LinkedHashMap<String,Object>>(){});
        log.info("[ExecuteAndParse] 响应 status={}, bytes={}, keys={}",response.statusCode(),response.body().length,payload.keySet());
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
        log.debug("[ExecuteAndParse] dataType={}, dataKeys={}",data==null?"null":data.getClass().getSimpleName(),data instanceof Map<?,?>m?m.keySet():Set.of());
        if(!(data instanceof Map<?,?> map)){
            log.error("[ExecuteAndParse] 数据格式无效，期望Map，实际类型={}",data==null?"null":data.getClass().getName());
            throw new IllegalStateException("供应商结果数据格式无效");
        }
        List<Map<String,Object>> normalized=normalize(service,cast(map));
        log.info("[ExecuteAndParse] normalize完成 itemCount={}",normalized.size());
        return new Result(code.isBlank()?success:code,rid,normalized);
    }

    private void assertPublicHttps(URI uri)throws Exception{assertProviderUri(uri,false);}
    private void assertProviderUri(URI uri,boolean allowHttp)throws Exception{
        if(cn.naiblog.carsearch.security.TestNetworkGuard.enabled()){
            if(!cn.naiblog.carsearch.security.TestNetworkGuard.allowedSupplier(uri))throw new IllegalStateException("本地隔离测试已拦截真实供应商请求");
            if("127.0.0.1".equals(uri.getHost())&&"http".equals(uri.getScheme()))return;
        }
        String scheme=uri.getScheme()==null?"":uri.getScheme().toLowerCase(Locale.ROOT);
        if((!"https".equals(scheme)&&!(allowHttp&&"http".equals(scheme)))||uri.getHost()==null||uri.getUserInfo()!=null)
            throw new IllegalStateException(allowHttp?"查询接口必须使用无账号信息的 HTTP/HTTPS 地址":"查询接口必须使用无账号信息的 HTTPS 地址");
        int port=uri.getPort();
        if(port!=-1&&port!=80&&port!=443)throw new IllegalStateException("查询接口端口不在允许范围");
        String host=uri.getHost().toLowerCase(Locale.ROOT);
        if(host.equals("localhost")||host.endsWith(".local"))throw new IllegalStateException("查询接口地址不安全");
        InetAddress[]addresses=InetAddress.getAllByName(host);
        if(addresses.length==0)throw new IllegalStateException("查询接口域名无法解析");
        for(InetAddress a:addresses)if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())throw new IllegalStateException("查询接口禁止访问内网地址");
    }
    URI withQuery(URI u,Map<String,Object> input)throws Exception{Map<String,String> q=new LinkedHashMap<>();if(u.getRawQuery()!=null)for(String part:u.getRawQuery().split("&",-1)){String[] p=part.split("=",2);q.put(URLDecoder.decode(p[0],StandardCharsets.UTF_8),p.length>1?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"");}input.forEach((k,v)->q.put(k,String.valueOf(v)));return withEncodedQuery(u,form(q));}
    URI withoutQueryKeys(URI u,Set<String>remove)throws Exception{Map<String,String>q=new LinkedHashMap<>();if(u.getRawQuery()!=null)for(String part:u.getRawQuery().split("&",-1)){String[]p=part.split("=",2);String key=URLDecoder.decode(p[0],StandardCharsets.UTF_8);if(!remove.contains(key))q.put(key,p.length>1?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"");}return withEncodedQuery(u,q.isEmpty()?null:form(q));}
    private URI withEncodedQuery(URI u,String encodedQuery)throws Exception{String path=u.getRawPath()==null||u.getRawPath().isBlank()?"/":u.getRawPath();StringBuilder target=new StringBuilder().append(u.getScheme()).append("://").append(u.getRawAuthority()).append(path);if(encodedQuery!=null&&!encodedQuery.isBlank())target.append('?').append(encodedQuery);return new URI(target.toString());}
    private String form(Map<String,?> m){return m.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(String.valueOf(e.getValue()),StandardCharsets.UTF_8)).reduce((a,b)->a+"&"+b).orElse("");}
    private Object path(Object value,String path){return path(value,path,true);}
    /** @param keepList true=最终List取第一个元素(默认), false=保留完整List */
    private Object path(Object value,String path,boolean keepList){
        log.debug("[Path] 提取路径={}, keepList={}",path,keepList);
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
        log.debug("[Path] 提取完成 path={}, resultType={}",path,value==null?"null":value.getClass().getSimpleName());
        return value;
    }
    /** fetchReport 扁平维修标志 → 官方 *_repair_detail 嵌套结构（键对：扁平键, 嵌套键） */
    private static final String[][] REPAIR_DETAIL_COMPAT={
            {"structure_is_repair","structure_repair_detail"},
            {"appearance_is_repair","appearance_repair_detail"},
            {"important_parts_is_repair","important_parts_repair_detail"},
    };

    /**
     * 拍平回调/轮询报告结构：外层元数据（query_id/vin/result_status 等）与内层 result 报告体合并，
     * 内层字段提升到顶层，同时**保留原始 result 键**——部分服务（如新能源车信息查询）的
     * schema 按 {@code result.vin} 这类嵌套路径取值，删掉 result 会让整份报告变“暂无数据”。
     * 无嵌套 result（官方平铺结构）时原样返回，兼容 history_order 等平铺接口。
     * 另做 CarMaintain/fetchReport ↔ getDetailV2 结构兼容：扁平 *_is_repair 补出
     * 官方 *_repair_detail.is_repair（嵌套已存在时不覆盖，见 compatRepairDetail）。
     */
    public static Map<String,Object> flattenReport(Map<String,Object> data){
        if(data==null)return Map.of();
        if(!(data.get("result")instanceof Map<?,?>inner))return compatRepairDetail(data);
        Map<String,Object> flat=new LinkedHashMap<>(data);
        // 内层报告字段覆盖同名外层回显字段（与外层 result 直接取报告体的旧行为一致）
        for(var e:inner.entrySet()){
            flat.put(String.valueOf(e.getKey()),e.getValue());
        }
        return compatRepairDetail(flat);
    }

    /**
     * fetchReport 回调只回扁平 structure_is_repair/appearance_is_repair/important_parts_is_repair，
     * getDetailV2 回嵌套 *_repair_detail{is_repair,have_detail,...}。schema 按官方嵌套路径配置，
     * 此处仅在嵌套键缺失时用扁平值补出 {is_repair}，保证两条通路同一 schema 都能取到值。
     */
    private static Map<String,Object> compatRepairDetail(Map<String,Object> flat){
        boolean need=false;
        for(var pair:REPAIR_DETAIL_COMPAT){
            if(flat.get(pair[0])!=null&&flat.get(pair[1])==null){need=true;break;}
        }
        if(!need)return flat;
        Map<String,Object> out=new LinkedHashMap<>(flat);
        for(var pair:REPAIR_DETAIL_COMPAT){
            if(out.get(pair[0])!=null&&out.get(pair[1])==null){
                out.put(pair[1],Map.of("is_repair",out.get(pair[0])));
            }
        }
        return out;
    }

    public List<Map<String,Object>> normalize(Map<String,Object> service,Map<String,Object> data)throws Exception{
        String schemaJson=String.valueOf(service.get("result_schema"));
        log.debug("[Normalize] service={}, schemaBytes={}, dataKeys={}",service.get("code"),schemaJson.length(),data.keySet());
        Map<String,Object> schema=mapper.readValue(schemaJson,new TypeReference<LinkedHashMap<String,Object>>(){});
        // 收集数组子字段映射：X[].field 归属 X[] 分组，配置顺序即展示顺序
        Map<String,List<String[]>> groupFields=new LinkedHashMap<>();
        for(var e:schema.entrySet()){
            String key=schemaKey(e.getKey());
            int idx=key.indexOf("[].");
            if(idx>0){
                String group=key.substring(0,idx)+"[]";
                groupFields.computeIfAbsent(group,g->new ArrayList<>()).add(new String[]{key.substring(idx+3),String.valueOf(e.getValue())});
            }
        }
        List<Map<String,Object>> out=new ArrayList<>();
        for(var e:schema.entrySet()){
            String key=schemaKey(e.getKey());
            if(key.contains("[]."))continue; // 子字段并入所属数组分组项
            boolean isArray=key.endsWith("[]");
            String actualKey=isArray?key.substring(0,key.length()-2):key;

            if(isArray){
                out.add(arrayItem(data,actualKey,key,String.valueOf(e.getValue()),groupFields.get(key)));
            }else{
                // 普通字段：单值提取（原有逻辑）
                Object raw=path(data,actualKey);
                Object v=ResultValueFormatter.format(String.valueOf(service.get("code")),key,raw);
                log.debug("[Normalize] key={}, valueType={}",key,v==null?"null":v.getClass().getSimpleName());
                if(v instanceof String s)v=truncate(s,2000);
                Map<String,Object> item=new LinkedHashMap<>();
                item.put("key",key);item.put("label",e.getValue());
                item.put("value",v==null||"".equals(v)?"暂无数据":v);
                out.add(item);
            }
        }
        // 仅有 X[].field 而缺 X[] 标题的分组：补一个分组项兜底
        for(var g:groupFields.entrySet()){
            String group=g.getKey();
            if(out.stream().noneMatch(m->group.equals(m.get("key")))){
                out.add(arrayItem(data,group.substring(0,group.length()-2),group,"查询数据",g.getValue()));
            }
        }
        applyTransferEmptyRecordText(service,data,out);
        log.info("[Normalize] 完成 service={}, itemCount={}",service.get("code"),out.size());
        return out;
    }
    /** Supplier-confirmed meaning: successful transfer report with data=[] means no transfer record yet. */
    private void applyTransferEmptyRecordText(Map<String,Object> service,Map<String,Object> report,List<Map<String,Object>> items){
        boolean transfer=apiPathEquals(service.get("fetch_api_path"),"/Car/transferReport")
                ||apiPathEquals(service.get("api_path"),"/Car/transferOrder");
        if(!transfer||QueryStatusResolver.resolve(report)!=QueryStatusResolver.SUCCESS)return;
        if(!(report.get("data") instanceof List<?> records)||!records.isEmpty())return;
        for(Map<String,Object> item:items){
            if(!Set.of("data[]","data").contains(String.valueOf(item.get("key"))))continue;
            item.put("label","过户记录");
            item.put("value","该车辆目前暂未出现过户记录");
        }
    }
    private boolean apiPathEquals(Object configured,String expected){
        if(configured==null)return false;
        try{
            String path=new URI(String.valueOf(configured).trim()).getPath();
            if(path==null)return false;
            return expected.equals(path.startsWith("/")?path:"/"+path);
        }catch(URISyntaxException ignored){return false;}
    }
    /** schema 键容错：去掉方括号前的空格（如 "tab []"→"tab[]"），避免路径带尾空格查不到数据 */
    private static String schemaKey(Object key){return ResultSchemaPath.canonical(key);}
    /** 构建数组展示项：无子字段映射时原样展示整组（兼容旧配置），有映射时按配置字段逐行提取 */
    private Map<String,Object> arrayItem(Map<String,Object> data,String actualKey,String key,String label,List<String[]>fields){
        Object raw=path(data,actualKey,false);
        log.debug("[Normalize] 数组字段 key={}, valueType={}, 子字段数={}",key,raw==null?"null":raw.getClass().getSimpleName(),fields==null?0:fields.size());
        Map<String,Object> item=new LinkedHashMap<>();
        item.put("key",key);item.put("label",label);
        if(raw instanceof List<?>list){
            item.put("display","array");
            if(fields==null||fields.isEmpty()){
                // 旧配置行为：整组原样输出
                List<Object> formatted=ResultValueFormatter.formatList(actualKey,list);
                item.put("value",formatted.isEmpty()?"暂无数据":formatted);
            }else{
                // 子字段映射：仅输出配置字段，列顺序=配置顺序
                List<Map<String,Object>>columns=new ArrayList<>(fields.size());
                for(String[]f:fields)columns.add(Map.of("key",f[0],"label",f[1]));
                item.put("columns",columns);
                List<Object>rows=new ArrayList<>(list.size());
                for(Object element:list){
                    Map<String,Object>row=new LinkedHashMap<>();
                    for(String[]f:fields){
                        // keepList=false：子字段指向数组时保留整组（如维修列表），不再只取第一个元素
                        Object v=ResultValueFormatter.format(f[0],element instanceof Map<?,?>m?path(m,f[0],false):null);
                        if(v instanceof String s)v=truncate(s,2000);
                        row.put(f[0],v==null||"".equals(v)?"暂无数据":v);
                    }
                    rows.add(row);
                }
                item.put("value",rows.isEmpty()?"暂无数据":rows);
            }
        }else{
            // 值不是数组，降级为普通字段处理
            Object v=ResultValueFormatter.format(key,raw);
            if(v instanceof String s)v=truncate(s,2000);
            item.put("value",v==null||"".equals(v)?"暂无数据":v);
        }
        return item;
    }
    @SuppressWarnings("unchecked")private Map<String,Object> cast(Map<?,?>m){return(Map<String,Object>)m;}private String truncate(String s,int n){if(s==null)return"";return s.codePointCount(0,s.length())<=n?s:s.substring(0,s.offsetByCodePoints(0,n));}
    private String mask(String value){String v=value==null?"":value.trim();if(v.length()<=6)return v.isBlank()?"":"***";return v.substring(0,3)+"***"+v.substring(v.length()-3);}
    private int number(Object value){try{return value instanceof Number n?n.intValue():Integer.parseInt(String.valueOf(value));}catch(Exception ignored){return 0;}}
    public record Result(String code,String requestId,List<Map<String,Object>> data){}
    public record SubmitResult(String businessCode,String requestId){}
    public enum FetchState{RUNNING,SUCCESS,FAILED,NO_DATA}
    public record FetchResult(FetchState state,String businessCode,String requestId,List<Map<String,Object>> data,String message){}
    public record BalanceResult(java.math.BigDecimal balance,String businessCode){}
}
