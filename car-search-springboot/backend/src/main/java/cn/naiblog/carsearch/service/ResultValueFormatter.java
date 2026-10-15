package cn.naiblog.carsearch.service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Normalizes documented provider enum values for both new and already stored results. */
public final class ResultValueFormatter {
    private ResultValueFormatter() { }

    public static Object format(String key,Object value){
        String path=key==null?"":key;
        String last=path.substring(path.lastIndexOf('.')+1);
        if(!List.of("state","status").contains(last))return value;
        Integer code=null;
        if(value instanceof Number n)code=n.intValue();
        else if(value instanceof String s)try{code=Integer.parseInt(s.trim());}catch(NumberFormatException ignored){}
        if(code==null)return value;
        return switch(code){case 1->"匹配";case 2->"不匹配";case 3->"查询成功无结果";default->"未知";};
    }

    public static List<Map<String,Object>> formatItems(Object raw){
        if(!(raw instanceof List<?> list))return List.of();
        List<Map<String,Object>> result=new ArrayList<>(list.size());
        for(Object item:list){
            if(!(item instanceof Map<?,?> source))continue;
            Map<String,Object> copy=new LinkedHashMap<>();
            source.forEach((key,value)->copy.put(String.valueOf(key),value));
            String key=String.valueOf(copy.getOrDefault("key",""));
            if(copy.containsKey("value"))copy.put("value",format(key,copy.get("value")));
            result.add(copy);
        }
        return result;
    }
}
