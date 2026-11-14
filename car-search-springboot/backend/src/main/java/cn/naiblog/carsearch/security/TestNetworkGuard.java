package cn.naiblog.carsearch.security;

import java.net.URI;

/** 仅由启动参数开启的隔离测试模式，不从数据库读取，导入线上配置也不能关闭。 */
public final class TestNetworkGuard {
    private TestNetworkGuard(){}
    public static boolean enabled(){return Boolean.parseBoolean(System.getProperty("car.local-test-only",System.getenv().getOrDefault("CAR_LOCAL_TEST_ONLY","false")));}
    public static boolean allowedSupplier(URI uri){
        String host=uri.getHost();
        return uri.getUserInfo()==null&&"testapi.naiblog.cn".equalsIgnoreCase(host)&&(uri.getPort()==-1||uri.getPort()==80||uri.getPort()==443);
    }
    public static void blockExternalWechat(){
        if(enabled())throw new IllegalStateException("本地隔离测试禁止请求真实微信接口");
    }
    public static void checkMail(String host){
        if(enabled()&&!"127.0.0.1".equals(host))throw new IllegalStateException("本地隔离测试仅允许本机邮件接收器");
    }
}
