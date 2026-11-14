package audit.carsearch;

import cn.naiblog.carsearch.config.CarProperties;
import cn.naiblog.carsearch.security.*;
import cn.naiblog.carsearch.service.ProviderService;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import tools.jackson.databind.ObjectMapper;
import java.net.URI;
import java.nio.file.*;
import java.util.*;

/** 显式启动的本机探针；仅允许现存 testapi.naiblog.cn，不是自动运行的单元测试。 */
public final class ExistingFakeProviderProbe {
    public static void main(String[] args)throws Exception{
        System.setProperty("car.local-test-only","true");
        var mapper=new ObjectMapper();
        var jdbc=new JdbcTemplate(new DriverManagerDataSource(LocalAuditServer.URL,"root",""));
        var crypto=new CryptoService(mapper,new CarProperties("20260921.1",System.getenv("DATA_ENCRYPT_KEY"),false,null));
        var provider=new ProviderService(crypto,mapper,jdbc);
        List<Map<String,Object>> checks=new ArrayList<>();
        try{
            Map<String,Object> direct=service(jdbc,"decode_normal");
            long start=System.currentTimeMillis();
            var r=provider.executeDirect(direct,Map.of("vin","LTESTA00000007001"));
            checks.add(Map.of("stage","direct","code",r.code(),"items",r.data().size(),"durationMs",System.currentTimeMillis()-start));
            Map<String,Object> async=service(jdbc,"transfer_order");start=System.currentTimeMillis();
            var submitted=provider.submitAsync(async,Map.of("vin","LTESTA00000007002"));
            checks.add(Map.of("stage","submit","code",submitted.businessCode(),"hasRequestId",!submitted.requestId().isBlank(),"durationMs",System.currentTimeMillis()-start));
            for(int attempt=1;attempt<=3;attempt++){
                Thread.sleep(60000);start=System.currentTimeMillis();
                var fetched=provider.fetchReport(async,submitted.requestId());
                checks.add(Map.of("stage","fetch","attempt",attempt,"state",fetched.state().name(),"code",fetched.businessCode(),"items",fetched.data().size(),"durationMs",System.currentTimeMillis()-start));
                if(fetched.state()!=ProviderService.FetchState.RUNNING)break;
            }
        }catch(Exception e){checks.add(Map.of("stage","error","type",e.getClass().getSimpleName(),"message",String.valueOf(e.getMessage())));}
        Files.writeString(Path.of(args[0]).resolve("existing-fake-provider.json"),mapper.writeValueAsString(checks));
        System.out.println("Existing fake provider probe finished; no real supplier/payment/mail clients instantiated");
    }
    static Map<String,Object> service(JdbcTemplate jdbc,String code){
        Map<String,Object> row=jdbc.queryForMap("select * from ci_service where code=?",code);
        int id=((Number)row.get("provider_api_id")).intValue();
        Map<String,Object> configured=jdbc.queryForMap("select * from ci_provider where id=?",id);
        URI base=URI.create(String.valueOf(configured.get("base_url")));
        URI target=base.resolve(String.valueOf(row.get("api_path")));
        if(id!=2||!TestNetworkGuard.allowedSupplier(base)||!TestNetworkGuard.allowedSupplier(target))
            throw new IllegalStateException("Refused non-existing-fake-provider configuration");
        return row;
    }
}
