package cn.naiblog.carsearch.service;

import cn.naiblog.carsearch.security.CryptoService;
import org.springframework.stereotype.Service;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import java.net.*;
import java.net.http.*;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.*;

@Service
public class ProviderService {
    private final CryptoService crypto; private final ObjectMapper mapper;
    private final HttpClient http=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NEVER).build();
    public ProviderService(CryptoService crypto,ObjectMapper mapper){this.crypto=crypto;this.mapper=mapper;}
    public Result query(Map<String,Object> service,Map<String,Object> input){
        try {String configured=String.valueOf(crypto.decrypt(String.valueOf(service.getOrDefault("request_url_cipher",""))).getOrDefault("url","")).trim();if(configured.isBlank())throw new IllegalStateException("查询接口未配置");URI uri=new URI(configured);assertPublicHttps(uri);
            String method=String.valueOf(service.getOrDefault("request_method","GET")).toUpperCase();if(!List.of("GET","POST").contains(method))throw new IllegalStateException("查询接口请求方式无效");
            HttpRequest.Builder builder=HttpRequest.newBuilder().timeout(Duration.ofSeconds(20)).header("Accept","application/json").header("User-Agent","CarInquiry/2.0");
            if("GET".equals(method))builder.uri(withQuery(uri,input)).GET();else builder.uri(withoutQueryKeys(uri,input.keySet())).header("Content-Type","application/x-www-form-urlencoded").POST(HttpRequest.BodyPublishers.ofString(form(input)));
            HttpResponse<byte[]> response=http.send(builder.build(),HttpResponse.BodyHandlers.ofByteArray());if(response.statusCode()!=200)throw new IllegalStateException("供应商 HTTP 状态异常："+response.statusCode());if(response.body().length>1_048_576)throw new IllegalStateException("供应商响应内容过大");
            Map<String,Object> payload=mapper.readValue(response.body(),new TypeReference<LinkedHashMap<String,Object>>(){});String codePath=String.valueOf(service.getOrDefault("response_code_path","code")).trim();String success=String.valueOf(service.getOrDefault("response_success_value","200"));String code=codePath.isBlank()?success:String.valueOf(path(payload,codePath));String rid=truncate(String.valueOf(Optional.ofNullable(payload.get("request_id")).orElse(Optional.ofNullable(payload.get("requestId")).orElse(payload.getOrDefault("rid","")))).trim(),100);
            if(!codePath.isBlank()&&!success.equals(code)){String message=truncate(String.valueOf(Optional.ofNullable(payload.get("msg")).orElse(payload.getOrDefault("message",""))).trim(),160);throw new ProviderQueryException("供应商查询未成功"+(message.isBlank()?"":"："+message),code.isBlank()?"missing_code":code,rid);}
            Object data=String.valueOf(service.getOrDefault("response_data_path","data")).isBlank()?payload:path(payload,String.valueOf(service.getOrDefault("response_data_path","data")));if(!(data instanceof Map<?,?> map))throw new IllegalStateException("供应商结果数据格式无效");return new Result(code.isBlank()?success:code,rid,normalize(service,cast(map)));
        }catch(RuntimeException e){throw e;}catch(Exception e){throw new IllegalStateException("供应商连接失败",e);}
    }
    private void assertPublicHttps(URI uri)throws Exception{if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null)throw new IllegalStateException("查询接口必须使用无账号信息的 HTTPS 地址");String host=uri.getHost().toLowerCase();if(host.equals("localhost")||host.endsWith(".local"))throw new IllegalStateException("查询接口地址不安全");InetAddress[]addresses=InetAddress.getAllByName(host);if(addresses.length==0)throw new IllegalStateException("查询接口域名无法解析");for(InetAddress a:addresses)if(a.isAnyLocalAddress()||a.isLoopbackAddress()||a.isLinkLocalAddress()||a.isSiteLocalAddress()||a.isMulticastAddress())throw new IllegalStateException("查询接口禁止访问内网地址");}
    private URI withQuery(URI u,Map<String,Object> input)throws Exception{Map<String,String> q=new LinkedHashMap<>();if(u.getRawQuery()!=null)for(String part:u.getRawQuery().split("&",-1)){String[] p=part.split("=",2);q.put(URLDecoder.decode(p[0],StandardCharsets.UTF_8),p.length>1?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"");}input.forEach((k,v)->q.put(k,String.valueOf(v)));return new URI(u.getScheme(),u.getAuthority(),u.getPath(),form(q),null);}
    private URI withoutQueryKeys(URI u,Set<String>remove)throws Exception{Map<String,String>q=new LinkedHashMap<>();if(u.getRawQuery()!=null)for(String part:u.getRawQuery().split("&",-1)){String[]p=part.split("=",2);String key=URLDecoder.decode(p[0],StandardCharsets.UTF_8);if(!remove.contains(key))q.put(key,p.length>1?URLDecoder.decode(p[1],StandardCharsets.UTF_8):"");}String path=u.getPath()==null||u.getPath().isBlank()?"/":u.getPath();return new URI(u.getScheme(),u.getAuthority(),path,q.isEmpty()?null:form(q),null);}
    private String form(Map<String,?> m){return m.entrySet().stream().map(e->URLEncoder.encode(e.getKey(),StandardCharsets.UTF_8)+"="+URLEncoder.encode(String.valueOf(e.getValue()),StandardCharsets.UTF_8)).reduce((a,b)->a+"&"+b).orElse("");}
    private Object path(Object value,String path){for(String s:path.split("\\.")){if(!(value instanceof Map<?,?> m)||!m.containsKey(s))return null;value=m.get(s);}return value;}
    private List<Map<String,Object>> normalize(Map<String,Object> service,Map<String,Object> data)throws Exception{Map<String,Object> schema=mapper.readValue(String.valueOf(service.get("result_schema")),new TypeReference<LinkedHashMap<String,Object>>(){});List<Map<String,Object>> out=new ArrayList<>();for(var e:schema.entrySet()){Object v=path(data,e.getKey());String last=e.getKey().substring(e.getKey().lastIndexOf('.')+1);if(List.of("state","status").contains(last)&&v instanceof Number n)v=switch(n.intValue()){case 1->"一致";case 2->"不一致";case 3->"查询成功无结果";default->"未知";};if(v instanceof Map<?,?>)v=mapper.writeValueAsString(v);if(v instanceof String s)v=truncate(s,2000);out.add(Map.of("key",e.getKey(),"label",e.getValue(),"value",v==null||"".equals(v)?"暂无数据":v));}return out;}
    @SuppressWarnings("unchecked")private Map<String,Object> cast(Map<?,?>m){return(Map<String,Object>)m;}private String truncate(String s,int n){return s.codePointCount(0,s.length())<=n?s:s.substring(0,s.offsetByCodePoints(0,n));}
    public record Result(String code,String requestId,List<Map<String,Object>> data){}
}
