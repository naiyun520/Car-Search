package cn.naiblog.carsearch.config;
import cn.naiblog.carsearch.security.TestNetworkGuard;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;
import java.util.Arrays;
@Component
public class IsolatedTestProfileGuard {
 public IsolatedTestProfileGuard(Environment env){
  if(Arrays.asList(env.getActiveProfiles()).contains("isolated-test")){
   String db=env.getProperty("spring.datasource.url","");
   if(!TestNetworkGuard.enabled()||!db.startsWith("jdbc:mysql://127.0.0.1:13316/car_search_unified_test?"))
    throw new IllegalStateException("隔离测试只允许本机专用数据库，且必须开启 CAR_LOCAL_TEST_ONLY，已拒绝启动");
  }
 }
}
