package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.*;
import cn.naiblog.carsearch.service.*;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.ObjectMapper;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class ServiceSchemaValidationTest {
    ServiceAdminController controller=new ServiceAdminController(null,null,new ObjectMapper(),null,null,"./runtime",null);
    @SuppressWarnings("unchecked") Map<String,Object> validate(List<?> rows){return (Map<String,Object>)ReflectionTestUtils.invokeMethod(controller,"validResults",rows);}
    @Test void administratorCanSaveExistingHistoryMileageArrayPaths()throws Exception{
        var schema=validate(List.of(Map.of("key","content []","label","里程记录"),Map.of("key","content [].mileage","label","里程"),Map.of("key","content [].reportTime","label","记录时间"),Map.of("key","decode_data []","label","车辆参数档案")));
        assertTrue(schema.containsKey("content[]"));assertTrue(schema.containsKey("decode_data[]"));
        var service=Map.<String,Object>of("code","history_order","result_schema",new ObjectMapper().writeValueAsString(schema));
        var result=new ProviderService(null,new ObjectMapper(),null).normalize(service,Map.of("content",List.of(Map.of("mileage",12000,"reportTime","2026-11-01")),"decode_data",List.of(Map.of("品牌","本地示例"))));
        var group=result.stream().filter(r->r.get("key").equals("content[]")).findFirst().orElseThrow();
        assertEquals("array",group.get("display"));assertTrue(group.get("value").toString().contains("12000"));assertTrue(group.get("columns").toString().contains("记录时间"));
    }
    @Test void canonicalDuplicatesNameBothRows(){
        var e=assertThrows(AdminValidationException.class,()->validate(List.of(Map.of("key","content[]","label","里程"),Map.of("key","content []","label","重复"))));
        assertEquals(2,e.details().get("row"));assertEquals("key",e.details().get("field"));assertTrue(e.getMessage().contains("第1行"));assertTrue(e.getMessage().contains("content[]"));
    }
    @Test void invalidPathAndEmptyDisplayNamePointToTheirOwnCells(){
        var path=assertThrows(AdminValidationException.class,()->validate(List.of(Map.of("key","info..mileage","label","里程"))));assertEquals("key",path.details().get("field"));
        var name=assertThrows(AdminValidationException.class,()->validate(List.of(Map.of("key","info.mileage","label",""))));assertEquals("label",name.details().get("field"));assertEquals(1,name.details().get("row"));
    }
    @Test void malformedSelectOptionsNeverSilentlyDisappear(){
        var e=assertThrows(AdminValidationException.class,()->ReflectionTestUtils.invokeMethod(controller,"validInputs",List.of(Map.of("key","type","label","车辆类型","type","select","options",List.of(Map.of("value","1","label","客车"),Map.of("value","1","label","重复"))))));
        assertTrue(e.getMessage().contains("车辆类型"));assertTrue(e.getMessage().contains("重复"));assertEquals("options",e.details().get("field"));
    }
}
