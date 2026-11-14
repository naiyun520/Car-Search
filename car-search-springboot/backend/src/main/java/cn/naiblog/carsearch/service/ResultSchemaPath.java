package cn.naiblog.carsearch.service;

/** 配置保存和结果提取使用相同的数组路径规范。 */
public final class ResultSchemaPath {
    private ResultSchemaPath(){}
    public static String canonical(Object key){return String.valueOf(key==null?"":key).trim().replaceAll("\\s*\\[\\s*\\]","[]");}
    public static boolean valid(String key){return key.length()<=200&&key.matches("[A-Za-z0-9_-]+(?:\\.[A-Za-z0-9_-]+)*(?:\\[\\](?:\\.[A-Za-z0-9_-]+)*)?");}
}
