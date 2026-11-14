package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.*;
import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.service.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import cn.naiblog.carsearch.db.GeneratedInsert;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import tools.jackson.core.type.TypeReference;
import tools.jackson.databind.ObjectMapper;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.nio.file.*;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.*;
import java.util.regex.Pattern;

@RestController @RequestMapping("/admin-api")
public class ServiceAdminController {
    private static final Logger log=LoggerFactory.getLogger(ServiceAdminController.class);
    private static final Set<String>TYPES=Set.of("text","name","plate","plate_prefix","plate_or_vin","vin","idcard","identity","image","select");
    private static final Set<String>BUILTIN_ICONS=Set.of("car","vin","plate","verified","location","transfer","owner","etc","insurance","search","document","shield");
    private final JdbcTemplate jdbc;private final CryptoService crypto;private final ObjectMapper mapper;private final ProviderService provider;private final AuditService audit;private final Path icons;private final SecureRandom random=new SecureRandom();private final QueryJobService jobs;
    public ServiceAdminController(JdbcTemplate jdbc,CryptoService crypto,ObjectMapper mapper,ProviderService provider,AuditService audit,@Value("${car.storage-path:./runtime/uploads}")String storage,QueryJobService jobs){this.jobs=jobs;this.jdbc=jdbc;this.crypto=crypto;this.mapper=mapper;this.provider=provider;this.audit=audit;this.icons=Path.of(storage).toAbsolutePath().normalize().resolve("service-icons");}

    @GetMapping("/services")public ApiResponse<List<Map<String,Object>>>list(){List<Map<String,Object>>rows=jdbc.queryForList("select s.id,s.code,s.category_id,c.name category_name,s.payment_product_id_android,s.payment_product_id_ios,s.name,s.short_name,s.description,s.icon,s.input_schema,s.result_schema,s.request_url_cipher,s.api_path,s.provider_api_id,s.request_method,s.response_code_path,s.response_success_value,s.response_data_path,s.sale_price_android,s.sale_price_ios,s.cost_price,s.sort,s.status,s.query_mode,s.fetch_api_path,s.poll_interval_sec,s.poll_timeout_sec,s.api_version,s.fetch_api_version,s.poll_max_attempts,s.id_field_path,s.fetch_api_param_name,s.updated_at from ci_service s left join ci_service_category c on c.id=s.category_id order by s.sort,s.id");rows.forEach(this::safe);return ApiResponse.ok(rows);}
    @GetMapping("/encryption-health")public ApiResponse<Map<String,Object>>encryptionHealth(){List<String>values=jdbc.query("select request_url_cipher from ci_service where request_url_cipher is not null and request_url_cipher<>''",(rs,n)->rs.getString(1));long readable=values.stream().filter(crypto::canDecrypt).count();return ApiResponse.ok(Map.of("key_fingerprint",crypto.keyFingerprint(),"encrypted_count",values.size(),"decryptable_count",readable,"healthy",readable==values.size()));}
    @PostMapping("/services")public ApiResponse<Map<String,Object>>create(@RequestBody Map<String,Object>b,HttpServletRequest req){int supplied=integer(b.get("service_id"));if(supplied>0)return update(supplied,b,req);Validated v=validate(b,null);if(scalar("select count(*) from ci_service where code=?",v.code)>0)throw new BusinessException("服务编码已存在");LocalDateTime now=LocalDateTime.now();int id=GeneratedInsert.insert(jdbc,"insert into ci_service(code,payment_product_id,payment_product_id_android,payment_product_id_ios,provider_api_id,category_id,name,short_name,description,icon,input_schema,result_schema,provider_key_cipher,request_url_cipher,api_path,request_method,response_code_path,response_success_value,response_data_path,sale_price,sale_price_android,sale_price_ios,cost_price,sort,status,query_mode,fetch_api_path,poll_interval_sec,poll_timeout_sec,api_version,fetch_api_version,poll_max_attempts,id_field_path,fetch_api_param_name,created_at,updated_at) values(?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?,?)",v.code,v.productAndroid,v.productAndroid,v.productIos,v.providerApiId,v.categoryId,v.name,v.shortName,v.description,"builtin:"+v.icon,json(v.inputs),json(v.results),crypto.encrypt(Map.of("key","")),v.providerApiId>0?"":crypto.encrypt(Map.of("url",v.url)),v.apiPath,v.method,v.codePath,v.successValue,v.dataPath,v.saleAndroid,v.saleAndroid,v.saleIos,v.cost,scalar("select coalesce(max(sort),0)+1 from ci_service"),v.status,v.queryMode,v.fetchApiPath,v.pollIntervalSec,v.pollTimeoutSec,v.apiVersion,v.fetchApiVersion,v.pollMaxAttempts,v.idFieldPath,v.fetchApiParamName,now,now);audit.record(req,"service.create","service",String.valueOf(id),Map.of("code",v.code));return ApiResponse.ok(Map.of("id",id),"接口服务已创建");}
    @PostMapping({"/services/{id}","/services/{id}/update"})public ApiResponse<Map<String,Object>>update(@PathVariable int id,@RequestBody Map<String,Object>b,HttpServletRequest req){Map<String,Object>old=find(id);if(integer(b.get("service_id"))>0&&integer(b.get("service_id"))!=id)throw new BusinessException("接口服务ID不一致，请刷新页面后重试");Validated v=validate(b,old);String icon=text(b.get("builtin_icon"));if(!BUILTIN_ICONS.contains(icon))icon=text(old.get("icon"));else icon="builtin:"+icon;
        Object urlCipher;
        if(v.providerApiId>0) urlCipher=""; else urlCipher=v.url.isBlank()?old.get("request_url_cipher"):crypto.encrypt(Map.of("url",v.url));
        jdbc.update("update ci_service set payment_product_id=?,payment_product_id_android=?,payment_product_id_ios=?,provider_api_id=?,category_id=?,name=?,short_name=?,description=?,icon=?,input_schema=?,result_schema=?,request_method=?,response_code_path=?,response_success_value=?,response_data_path=?,sale_price=?,sale_price_android=?,sale_price_ios=?,cost_price=?,status=?,request_url_cipher=?,api_path=?,query_mode=?,fetch_api_path=?,poll_interval_sec=?,poll_timeout_sec=?,api_version=?,fetch_api_version=?,poll_max_attempts=?,id_field_path=?,fetch_api_param_name=?,updated_at=? where id=?",v.productAndroid,v.productAndroid,v.productIos,v.providerApiId,v.categoryId,v.name,v.shortName,v.description,icon,json(v.inputs),json(v.results),v.method,v.codePath,v.successValue,v.dataPath,v.saleAndroid,v.saleAndroid,v.saleIos,v.cost,v.status,urlCipher,v.apiPath,v.queryMode,v.fetchApiPath,v.pollIntervalSec,v.pollTimeoutSec,v.apiVersion,v.fetchApiVersion,v.pollMaxAttempts,v.idFieldPath,v.fetchApiParamName,LocalDateTime.now(),id);audit.record(req,"service.update","service",String.valueOf(id),Map.of("fields",b.keySet()));return ApiResponse.ok(Map.of("id",id),"接口服务已保存");}
    /** 测试请求同样生成持久任务，仅允许隔离环境中的假数据供应商。 */
    @PostMapping("/service-test/{id}") @Transactional
    public ApiResponse<Map<String,Object>> test(@PathVariable int id,@RequestBody(required=false)Map<String,Object>b,HttpServletRequest req){
        if(!cn.naiblog.carsearch.security.TestNetworkGuard.enabled())throw new BusinessException("请在本地隔离测试环境执行，正式环境不直接测试收费接口");
        Map<String,Object> service=find(id);
        int providerId=integer(service.get("provider_api_id"));
        String url=providerId>0?text(jdbc.queryForMap("select base_url from ci_provider where id=?",providerId).get("base_url")):text(crypto.decrypt(text(service.get("request_url_cipher"))).get("url"));
        if(!cn.naiblog.carsearch.security.TestNetworkGuard.allowedSupplier(URI.create(url)))throw new BusinessException("只允许假数据测试供应商，禁止汽修宝及其他真实接口");
        Map<String,Object> input=InputValidator.validate(parseInputs(text(service.get("input_schema"))),map(b==null?null:b.get("input")));
        LocalDateTime now=LocalDateTime.now();
        jdbc.update("insert ignore into ci_user(openid,nickname,status,created_at,updated_at) values('local-interface-test','本地接口测试',1,?,?)",now,now);
        Long userId=jdbc.queryForObject("select id from ci_user where openid='local-interface-test'",Long.class);
        String no="TEST"+UUID.randomUUID().toString().replace("-","").substring(0,24);
        jdbc.update("insert into ci_order(order_no,request_key,user_id,service_id,service_name,amount,cost_amount,client_platform,status,input_cipher,service_snapshot_cipher,expired_at,created_at,updated_at) values(?,?,?,?,?,0,0,'test','paid',?,?,?,?,?)",
            no,no,userId,id,service.get("name"),crypto.encrypt(input),crypto.encrypt(service),now.plusMinutes(30),now,now);
        Long orderId=jdbc.queryForObject("select id from ci_order where order_no=?",Long.class,no);
        jobs.enqueuePaidOrder(orderId);
        audit.record(req,"service.test.queued","order",no,Map.of("service_id",id));
        return ApiResponse.ok(Map.of("queued",true,"order_no",no,"status","querying"),"已加入查询任务，请在订单管理查看报告和履约诊断");
    }

    @PostMapping("/service-icon-upload/{id}")public ApiResponse<Map<String,Object>>upload(@PathVariable int id,@RequestParam("icon")MultipartFile file,HttpServletRequest req)throws Exception{Map<String,Object>s=find(id);if(file.isEmpty()||file.getSize()>75*1024)throw new BusinessException("图标大小需在 75KB 以内");String content=Optional.ofNullable(file.getContentType()).orElse("").toLowerCase(Locale.ROOT);String ext=switch(content){case"image/png"->"png";case"image/jpeg"->"jpg";case"image/webp"->"webp";default->throw new BusinessException("图标仅支持 PNG、JPG 或 WebP 格式");};byte[]data=file.getBytes();int[]dimensions=imageDimensions(data,ext);if(dimensions==null||dimensions[0]<32||dimensions[1]<32||dimensions[0]>1024||dimensions[1]>1024)throw new BusinessException("图标文件内容无效，或尺寸不在 32×32 至 1024×1024 像素之间");byte[]bytes=new byte[16];random.nextBytes(bytes);String filename=HexFormat.of().formatHex(bytes)+"."+ext;Path target=icons.resolve(filename).normalize();if(!target.getParent().equals(icons))throw new BusinessException("图标文件名无效");writeIcon(file,target);jdbc.update("update ci_service set icon=?,updated_at=? where id=?",filename,LocalDateTime.now(),id);deleteIcon(text(s.get("icon")),filename);audit.record(req,"service.icon.upload","service",String.valueOf(id),Map.of("filename",filename,"mime",content,"size",file.getSize(),"width",dimensions[0],"height",dimensions[1]));return ApiResponse.ok(Map.of("id",id,"icon",filename,"icon_url","/api/v1/service-icon/"+filename),"服务图标已更新");}
    @PostMapping("/services/sort")@Transactional public ApiResponse<Object>sort(@RequestBody Map<String,Object>b,HttpServletRequest req){List<Integer>ids=b.get("ids")instanceof List<?>l?l.stream().map(this::integer).filter(i->i>0).toList():List.of();List<Integer>current=jdbc.queryForList("select id from ci_service order by id",Integer.class);List<Integer>a=new ArrayList<>(ids),c=new ArrayList<>(current);Collections.sort(a);Collections.sort(c);if(ids.size()<2||!a.equals(c))throw new BusinessException("排序数据不完整，请刷新页面后重试");for(int i=0;i<ids.size();i++)jdbc.update("update ci_service set sort=?,updated_at=? where id=?",i+1,LocalDateTime.now(),ids.get(i));audit.record(req,"service.sort","service","",Map.of("ids",ids));return ApiResponse.ok(null,"排序已保存");}
    @DeleteMapping("/services/{id}")public ApiResponse<Object>delete(@PathVariable int id,HttpServletRequest req){Map<String,Object>s=find(id);if(scalar("select count(*) from ci_order where service_id=?",id)>0)throw new BusinessException("该接口已有历史订单，不能删除，请将状态设为隐藏");jdbc.update("delete from ci_service where id=?",id);deleteIcon(text(s.get("icon")),"");audit.record(req,"service.delete","service",String.valueOf(id),Map.of("code",s.get("code")));return ApiResponse.ok(null,"接口服务已删除");}

    private Validated validate(Map<String,Object>b,Map<String,Object>old){String code=old==null?text(b.get("code")):text(old.get("code"));String name=text(b.get("name")),shortName=text(b.get("short_name")),description=text(b.get("description")),productAndroid=text(b.get("payment_product_id_android")),productIos=text(b.get("payment_product_id_ios"));if(!code.matches("[a-z][a-z0-9_]{2,39}"))throw fieldError("code","服务编码","请填写3至40位小写字母、数字或下划线，并以小写字母开头。");if(name.isBlank()||name.length()>80)throw fieldError("name","服务名称","请填写1至80字。");if(shortName.isBlank()||shortName.length()>40)throw fieldError("short_name","服务简称","请填写1至40字。");if(description.length()>255)throw fieldError("description","服务说明","不能超过255字，请缩短说明。");if(!validProduct(productAndroid))throw fieldError("payment_product_id_android","安卓支付道具ID","不能包含空格或换行，最多128个字符。");if(!validProduct(productIos))throw fieldError("payment_product_id_ios","iOS支付道具ID","不能包含空格或换行，最多128个字符。");int categoryId=integer(b.get("category_id"));if(categoryId<1||scalar("select count(*) from ci_service_category where id=?",categoryId)==0)throw new BusinessException("请选择有效的服务分类");String icon=text(b.getOrDefault("builtin_icon",old==null?"car":""));if(old==null&&!BUILTIN_ICONS.contains(icon))throw new BusinessException("请选择有效的服务图标");int status=integer(b.getOrDefault("status",0));String method=text(b.getOrDefault("request_method","GET")).toUpperCase(Locale.ROOT);if(!Set.of(0,1,2).contains(status))throw fieldError("status","服务状态","请选择运行、维护或隐藏。");if(!Set.of("GET","POST").contains(method))throw fieldError("request_method","请求方式","请选择GET或POST。");if(status==1&&(productAndroid.isBlank()||productIos.isBlank()))throw new BusinessException("运行状态必须同时填写安卓与 iOS 微信支付道具ID");List<Map<String,Object>>inputs=validInputs(list(b.get("input_schema")));Map<String,Object>results=validResults(list(b.get("result_schema")));

        // 供应商模式 vs 直连模式
        int providerApiId=integer(b.get("provider_api_id"));
        String apiPath=cut(text(b.get("api_path")),500);
        String url=text(b.getOrDefault("api_url",b.get("request_url")));

        // 异步查询模式配置
        String queryMode=text(b.getOrDefault("query_mode","single"));
        if(!Set.of("single","async").contains(queryMode))throw fieldError("query_mode","查询模式","请选择直接查询或二级查询。");
        String fetchApiPath=cut(text(b.get("fetch_api_path")),500);
        int pollIntervalSec=integer(b.getOrDefault("poll_interval_sec",60));
        int pollTimeoutSec=integer(b.getOrDefault("poll_timeout_sec",0));
        int pollMaxAttempts=integer(b.getOrDefault("poll_max_attempts",20));
        String apiVersion=cut(text(b.get("api_version")),20);
        String fetchApiVersion=cut(text(b.get("fetch_api_version")),20);
        if("async".equals(queryMode)){
            if(providerApiId<=0)throw new BusinessException("异步查询模式必须选择供应商");
            if(fetchApiPath.isBlank())throw new BusinessException("异步查询模式必须填写获取报告接口路径");
            if(pollIntervalSec<60||pollIntervalSec>3600)throw new BusinessException("获取报告间隔范围为60-3600秒");
            if(pollMaxAttempts<1||pollMaxAttempts>100)throw new BusinessException("获取报告次数范围为1-100次");
            if(pollTimeoutSec!=0&&(pollTimeoutSec<60||pollTimeoutSec>86400))throw new BusinessException("查询超时为0（仅按次数停止），或60-86400秒");
        }

        String idFieldPath=cut(text(b.get("id_field_path")),100);
        String fetchApiParamName=cut(text(b.get("fetch_api_param_name")),50);

        if(providerApiId>0){
            // 供应商模式：校验供应商存在且启用了
            if(scalar("select count(*) from ci_provider where id=? and status=1",providerApiId)==0)
                throw new BusinessException("选择的供应商不存在或已禁用");
            if(apiPath.isBlank())throw new BusinessException("供应商模式下请填写接口路径");
            if(status==1&&apiPath.isBlank())throw new BusinessException("运行状态必须填写接口路径");
        }else{
            // 直连模式：现有逻辑
            if(old==null&&url.isBlank())throw new BusinessException("请填写完整接口 URL");
            if(!url.isBlank())validateUrl(url);
            if(status==1&&url.isBlank()&&(old==null||crypto.decrypt(text(old.get("request_url_cipher"))).getOrDefault("url","").toString().isBlank()))throw new BusinessException("运行状态必须配置完整接口 URL；当前密文不可解密时请重新填写后保存");
        }

        BigDecimal saleAndroid=price(b,"sale_price_android","安卓售价"),saleIos=price(b,"sale_price_ios","iOS售价"),cost=price(b,"cost_price","配置成本");if(!validPrice(saleAndroid)||(status==1&&saleAndroid.compareTo(BigDecimal.ONE)<0))throw fieldError("sale_price_android","安卓售价",status==1?"运行服务的售价须在1至99999元之间。":"请填写0.01至99999元之间的金额。");if(!validPrice(saleIos)||(status==1&&saleIos.compareTo(BigDecimal.ONE)<0))throw fieldError("sale_price_ios","iOS售价",status==1?"运行服务的售价须在1至99999元之间。":"请填写0.01至99999元之间的金额。");if(cost.signum()<0||cost.compareTo(new BigDecimal("99999"))>0)throw fieldError("cost_price","配置成本","请填写0至99999元之间的金额。");return new Validated(code,productAndroid,productIos,categoryId,icon,name,shortName,description,inputs,results,url,apiPath,providerApiId,method,cut(text(b.getOrDefault("response_code_path","code")),100),cut(text(b.getOrDefault("response_success_value","200")),50),cut(text(b.getOrDefault("response_data_path","data")),100),saleAndroid,saleIos,cost,status,queryMode,fetchApiPath,pollIntervalSec,pollTimeoutSec,apiVersion,fetchApiVersion,pollMaxAttempts,idFieldPath,fetchApiParamName);}
    private AdminValidationException fieldError(String field,String label,String message){return new AdminValidationException("服务配置",0,field,label+"："+message);}
    private BigDecimal price(Map<String,Object> body,String field,String label){try{return new BigDecimal(text(body.get(field)));}catch(Exception e){throw fieldError(field,label,"请填写数字金额，例如 9.90。");}}
    private boolean validProduct(String value){return value.length()<=128&&!value.matches(".*[\\x00-\\x20\\x7f].*");}
    private boolean validPrice(BigDecimal value){return value.compareTo(new BigDecimal("0.01"))>=0&&value.compareTo(new BigDecimal("99999"))<=0;}
    private List<Map<String,Object>>validInputs(List<?>rows){
        List<Map<String,Object>> out=new ArrayList<>();Map<String,Integer> positions=new HashMap<>();int n=0;
        for(Object value:rows){
            int row=++n;
            if(!(value instanceof Map<?,?>))throw new AdminValidationException("用户输入字段",row,"key","这一行的内容无法读取，请删除后重新添加。");
            Map<String,Object> r=map(value);String key=text(r.get("key")),label=text(r.get("label")),type=text(r.getOrDefault("type","text"));
            if(!key.matches("[A-Za-z][A-Za-z0-9_]{0,49}"))throw new AdminValidationException("用户输入字段",row,"key","参数名“"+key+"”不正确。请以英文字母开头，只用字母、数字或下划线，最多50个字符。");
            if(positions.containsKey(key))throw new AdminValidationException("用户输入字段",row,"key","参数名“"+key+"”与第"+positions.get(key)+"行重复，请修改或删除其中一行。");
            if(label.isBlank()||label.length()>50)throw new AdminValidationException("用户输入字段",row,"label","请填写不超过50字的前端名称。");
            if(!TYPES.contains(type))throw new AdminValidationException("用户输入字段",row,"type","“"+label+"”的输入类型不支持，请重新选择输入类型。");
            positions.put(key,row);Map<String,Object> field=new LinkedHashMap<>();field.put("key",key);field.put("label",label);field.put("type",type);field.put("required",Boolean.parseBoolean(text(r.getOrDefault("required",false))));
            if("select".equals(type)){
                if(!(r.get("options") instanceof List<?> opts)||opts.isEmpty())throw new AdminValidationException("用户输入字段",row,"options","“"+label+"”是下拉选择，请至少填写一个选项。");
                List<Map<String,Object>> options=new ArrayList<>();Set<String> values=new HashSet<>();int optionIndex=0;
                for(Object option:opts){Map<String,Object> o=map(option);String v=text(o.get("value")),l=text(o.get("label"));optionIndex++;
                    if(v.isBlank()||l.isBlank())throw new AdminValidationException("用户输入字段",row,"options","“"+label+"”的第"+optionIndex+"个选项缺少选项值或显示名称。");
                    if(!values.add(v))throw new AdminValidationException("用户输入字段",row,"options","“"+label+"”的选项值“"+v+"”重复，请保留一个。");
                    options.add(Map.of("value",v,"label",l));
                }
                field.put("options",options);String def=text(r.get("default"));
                if(!def.isBlank()){if(!values.contains(def))throw new AdminValidationException("用户输入字段",row,"default","“"+label+"”的默认值不在选项中，请选择已有选项值或清空默认值。");field.put("default",def);}
            }
            out.add(field);
        }
        if(out.isEmpty())throw new BusinessException("用户输入字段：请至少添加一行参数。");return out;
    }
    private Map<String,Object>validResults(List<?>rows){
        Map<String,Object> out=new LinkedHashMap<>();Map<String,Integer> positions=new HashMap<>();int n=0;
        for(Object value:rows){
            int row=++n;Map<String,Object> r=map(value);String key=cn.naiblog.carsearch.service.ResultSchemaPath.canonical(r.get("key")),label=text(r.get("label"));
            if(!cn.naiblog.carsearch.service.ResultSchemaPath.valid(key))throw new AdminValidationException("结果展示字段",row,"key","字段路径“"+key+"”无法识别。请填写如 vin、info.last_mileage、content[] 或 content[].mileage；不要填写连续点号或多层数组标记。");
            if(label.isBlank()||label.length()>50)throw new AdminValidationException("结果展示字段",row,"label","请填写不超过50字的展示名称（当前路径："+key+"）。");
            if(positions.containsKey(key))throw new AdminValidationException("结果展示字段",row,"key","字段路径“"+key+"”与第"+positions.get(key)+"行重复（数组标记前的空格不区分），请修改或删除其中一行。");
            positions.put(key,row);out.put(key,label);
        }
        if(out.isEmpty())throw new BusinessException("结果展示字段：请至少添加一行展示内容。");return out;
    }
    private void validateUrl(String value){try{URI uri=URI.create(value);if(!"https".equalsIgnoreCase(uri.getScheme())||uri.getHost()==null||uri.getUserInfo()!=null||value.length()>3000||value.chars().anyMatch(Character::isWhitespace))throw new Exception();}catch(Exception e){throw new BusinessException("接口 URL 必须是无账号信息的 HTTPS 地址");}}
    private Map<String,Object>find(int id){List<Map<String,Object>>r=jdbc.queryForList("select * from ci_service where id=?",id);if(r.isEmpty())throw new BusinessException(404,HttpStatus.NOT_FOUND,"接口服务不存在");return r.getFirst();}
    private void safe(Map<String,Object>r){
        int providerId=r.get("provider_api_id")instanceof Number n?n.intValue():0;
        String cipher=text(r.get("request_url_cipher"));
        Map<String,Object>d=crypto.decrypt(cipher);
        String url=text(d.get("url"));
        r.remove("request_url_cipher");
        if(providerId>0){
            // 加载供应商信息
            List<Map<String,Object>>prows=jdbc.queryForList("select id,name,code,base_url from ci_provider where id=?",providerId);
            if(!prows.isEmpty()){
                Map<String,Object>p=prows.getFirst();
                r.put("provider_name",p.get("name"));
                r.put("provider_code",p.get("code"));
                // 拼接完整URL用于显示
                String baseUrl=String.valueOf(p.get("base_url")).trim();
                String apiPath=String.valueOf(r.getOrDefault("api_path","")).trim();
                String fullUrl="";
                if(!baseUrl.isBlank()&&!apiPath.isBlank()){
                    fullUrl=baseUrl.endsWith("/")?baseUrl.substring(0,baseUrl.length()-1):baseUrl;
                    fullUrl+=apiPath.startsWith("/")?apiPath:"/"+apiPath;
                }
                r.put("api_url_configured",!fullUrl.isBlank());
                r.put("api_url",fullUrl);
                r.put("api_url_error",false);
            }else{
                r.put("api_url_configured",false);
                r.put("api_url","");
                r.put("api_url_error",true);
                r.put("provider_name","供应商不存在");
                r.put("provider_code","");
            }
        }else{
            r.put("api_url_configured",!url.isBlank());
            r.put("api_url",url);
            r.put("api_url_error",!cipher.isBlank()&&!d.containsKey("url"));
        }
        r.put("input_schema",parseInputs(text(r.get("input_schema"))));
        Map<String,Object>results=jsonMap(text(r.get("result_schema")));
        r.put("result_schema",results.entrySet().stream().map(e->Map.of("key",e.getKey(),"label",e.getValue())).toList());
        // 确保异步查询字段有默认值
        r.putIfAbsent("query_mode","single");
        r.putIfAbsent("fetch_api_path","");
        r.putIfAbsent("poll_interval_sec",60);
        r.putIfAbsent("poll_timeout_sec",0);
        r.putIfAbsent("poll_max_attempts",20);
        r.putIfAbsent("api_version","");
        r.putIfAbsent("fetch_api_version","");
        r.putIfAbsent("id_field_path","");
        r.putIfAbsent("fetch_api_param_name","");
    }
    private void writeIcon(MultipartFile file,Path target)throws IOException{
        try{Files.createDirectories(icons);file.transferTo(target);}
        catch(AccessDeniedException|SecurityException e){
            log.error("service icon directory access denied directory={}",icons,e);
            throw new BusinessException("业务图标未上传：服务器无权写入目录“"+icons+"”。请先核对宝塔 Java 项目的 CAR_STORAGE_PATH 和工作目录；路径正确后，再检查该目录是否允许 Java 运行用户写入。原图标未替换。");
        }catch(FileAlreadyExistsException e){
            log.error("service icon directory conflicts with a file directory={}",icons,e);
            throw new BusinessException("业务图标未上传：目录“"+icons+"”的路径中存在同名文件。请在宝塔核对 CAR_STORAGE_PATH，该路径及 service-icons 必须是文件夹。原图标未替换。");
        }catch(IOException e){
            log.error("service icon storage failed directory={}",icons,e);
            throw new BusinessException("业务图标未上传：服务器无法将文件保存到目录“"+icons+"”。请核对 CAR_STORAGE_PATH、Java 工作目录及磁盘可用空间。原图标未替换，具体原因已记录到服务器日志。");
        }
    }
    private int[]imageDimensions(byte[]data,String ext){try{if("png".equals(ext)&&!(data.length>24&&data[0]==(byte)0x89&&data[1]==0x50&&data[2]==0x4e&&data[3]==0x47))return null;if("jpg".equals(ext)&&!(data.length>3&&data[0]==(byte)0xff&&data[1]==(byte)0xd8))return null;if(!"webp".equals(ext)){BufferedImage image=ImageIO.read(new ByteArrayInputStream(data));return image==null?null:new int[]{image.getWidth(),image.getHeight()};}if(data.length<30||!ascii(data,0,"RIFF")||!ascii(data,8,"WEBP"))return null;if(ascii(data,12,"VP8X"))return new int[]{1+le24(data,24),1+le24(data,27)};if(ascii(data,12,"VP8 ")&&data.length>=30&&data[23]==(byte)0x9d&&data[24]==0x01&&data[25]==0x2a)return new int[]{le16(data,26)&0x3fff,le16(data,28)&0x3fff};if(ascii(data,12,"VP8L")&&data.length>=25&&data[20]==0x2f){int width=1+(((data[22]&0x3f)<<8)|(data[21]&0xff));int height=1+(((data[24]&0x0f)<<10)|((data[23]&0xff)<<2)|((data[22]&0xc0)>>6));return new int[]{width,height};}}catch(Exception ignored){}return null;}
    private boolean ascii(byte[]data,int at,String value){if(at<0||at+value.length()>data.length)return false;for(int i=0;i<value.length();i++)if((data[at+i]&0xff)!=value.charAt(i))return false;return true;}
    private int le16(byte[]data,int at){return(data[at]&0xff)|((data[at+1]&0xff)<<8);}private int le24(byte[]data,int at){return le16(data,at)|((data[at+2]&0xff)<<16);}
    private void deleteIcon(String name,String except){if(name.equals(except)||!name.matches("[a-f0-9]{32}\\.(png|jpg|webp)"))return;try{Files.deleteIfExists(icons.resolve(name).normalize());}catch(Exception ignored){}}
    private List<Map<String,Object>>parseInputs(String json){try{return mapper.readValue(json,new TypeReference<>(){});}catch(Exception e){return List.of();}}private Map<String,Object>jsonMap(String json){try{return mapper.readValue(json,new TypeReference<LinkedHashMap<String,Object>>(){});}catch(Exception e){return Map.of();}}private String json(Object o){try{return mapper.writeValueAsString(o);}catch(Exception e){throw new BusinessException("JSON 配置无效");}}private String safeError(Exception e){String v=text(e.getMessage());return cut(v.isBlank()?"接口连接失败":v,300);}private long scalar(String s,Object...a){Long v=jdbc.queryForObject(s,Long.class,a);return v==null?0:v;}private int integer(Object v){try{return v instanceof Number n?n.intValue():Integer.parseInt(text(v));}catch(Exception e){return 0;}}private BigDecimal decimal(Object v){try{return new BigDecimal(text(v));}catch(Exception e){return BigDecimal.ZERO;}}private String text(Object v){return v==null?"":String.valueOf(v).trim();}private String cut(String s,int n){return s.length()<=n?s:s.substring(0,n);}@SuppressWarnings("unchecked")private Map<String,Object>map(Object v){return v instanceof Map<?,?>m?(Map<String,Object>)m:Map.of();}private List<?>list(Object v){return v instanceof List<?>l?l:List.of();}
    private record Validated(String code,String productAndroid,String productIos,int categoryId,String icon,String name,String shortName,String description,List<Map<String,Object>>inputs,Map<String,Object>results,String url,String apiPath,int providerApiId,String method,String codePath,String successValue,String dataPath,BigDecimal saleAndroid,BigDecimal saleIos,BigDecimal cost,int status,String queryMode,String fetchApiPath,int pollIntervalSec,int pollTimeoutSec,String apiVersion,String fetchApiVersion,int pollMaxAttempts,String idFieldPath,String fetchApiParamName){}
}
