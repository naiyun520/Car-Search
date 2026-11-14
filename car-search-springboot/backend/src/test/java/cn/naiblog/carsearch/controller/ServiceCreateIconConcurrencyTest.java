package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.GlobalExceptionHandler;
import cn.naiblog.carsearch.config.CarProperties;
import cn.naiblog.carsearch.security.CryptoService;
import cn.naiblog.carsearch.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import tools.jackson.databind.ObjectMapper;
import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.*;
import java.util.*;
import java.util.concurrent.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ServiceCreateIconConcurrencyTest {
    @TempDir Path storage;
    JdbcTemplate db;MockMvc mvc;ObjectMapper mapper=new ObjectMapper();
    ProviderService provider;
    @BeforeEach void setup(){
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
        db.execute("""
            create table ci_service(
              id int auto_increment primary key,code varchar unique,payment_product_id varchar,
              payment_product_id_android varchar,payment_product_id_ios varchar,provider_api_id int,category_id int,
              name varchar,short_name varchar,description varchar,icon varchar,input_schema varchar,result_schema varchar,
              provider_key_cipher varchar,request_url_cipher varchar,api_path varchar,request_method varchar,
              response_code_path varchar,response_success_value varchar,response_data_path varchar,
              sale_price decimal(10,2),sale_price_android decimal(10,2),sale_price_ios decimal(10,2),cost_price decimal(10,4),
              sort int,status int,query_mode varchar,fetch_api_path varchar,poll_interval_sec int,poll_timeout_sec int,
              api_version varchar,fetch_api_version varchar,poll_max_attempts int,id_field_path varchar,fetch_api_param_name varchar,
              created_at timestamp,updated_at timestamp)
            """);
        db.execute("create table ci_service_category(id int primary key)");
        db.update("insert into ci_service_category values(1)");
        db.update("insert into ci_service(id,code,icon,sort) values(1,'existing_service','builtin:shield',1)");
        db.execute("alter table ci_service alter column id restart with 100");
        var crypto=mock(CryptoService.class);when(crypto.encrypt(any())).thenReturn("LOCAL-CIPHER");
        provider=mock(ProviderService.class);
        var controller=new ServiceAdminController(db,crypto,mapper,provider,mock(AuditService.class),storage.toString(),mock(QueryJobService.class));
        mvc=MockMvcBuilders.standaloneSetup(controller,new PublicController(db,mapper,mock(CarProperties.class),storage.toString()))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    byte[] image(int marker)throws Exception{
        var bitmap=new BufferedImage(32,32,BufferedImage.TYPE_INT_RGB);
        bitmap.setRGB(0,0,marker+1);
        var output=new ByteArrayOutputStream();ImageIO.write(bitmap,"png",output);return output.toByteArray();
    }
    int createAndUpload(int number)throws Exception{
        String code="created_service_"+number;
        byte[] content=image(number);
        var response=mvc.perform(post("/admin-api/services").contentType("application/json")
            .content(mapper.writeValueAsString(CreatedIdConnectionAuditTest.serviceBody(code))))
            .andExpect(status().isOk()).andReturn();
        var body=mapper.readValue(response.getResponse().getContentAsByteArray(),Map.class);
        int returned=((Number)((Map<?,?>)body.get("data")).get("id")).intValue();
        assertEquals(db.queryForObject("select id from ci_service where code=?",Integer.class,code),returned);
        mvc.perform(multipart("/admin-api/service-icon-upload/"+returned)
            .file(new MockMultipartFile("icon","fixture.png","image/png",content))).andExpect(status().isOk());
        String filename=db.queryForObject("select icon from ci_service where code=?",String.class,code);
        assertNotNull(filename);assertTrue(filename.matches("[a-f0-9]{32}\\.png"));
        assertArrayEquals(content,Files.readAllBytes(storage.resolve("service-icons").resolve(filename)));
        mvc.perform(get("/api/v1/service-icon/"+filename)).andExpect(status().isOk()).andExpect(content().bytes(content));
        return returned;
    }
    @Test void newServiceReceivesItsOwnUploadedIcon()throws Exception{
        int id=createAndUpload(1);assertTrue(id>1);
        assertEquals("builtin:shield",db.queryForObject("select icon from ci_service where id=1",String.class));
        verifyNoInteractions(provider);
    }
    @Test void duplicateCreateDoesNotChangeTheExistingServiceOrItsIcon()throws Exception{
        int id=createAndUpload(1);
        String icon=db.queryForObject("select icon from ci_service where id=?",String.class,id);
        mvc.perform(post("/admin-api/services").contentType("application/json")
            .content(mapper.writeValueAsString(CreatedIdConnectionAuditTest.serviceBody("created_service_1"))))
            .andExpect(status().is4xxClientError());
        assertEquals(2,db.queryForObject("select count(*) from ci_service",Integer.class));
        assertEquals(icon,db.queryForObject("select icon from ci_service where id=?",String.class,id));
        verifyNoInteractions(provider);
    }
    @Test void concurrentNewServicesKeepDistinctIdsAndTheirOwnImages()throws Exception{
        try(var workers=Executors.newFixedThreadPool(4)){
            var start=new CountDownLatch(1);
            List<Future<Integer>> results=new ArrayList<>();
            for(int i=0;i<8;i++){
                final int number=i;
                results.add(workers.submit(()->{start.await();return createAndUpload(number);}));
            }
            start.countDown();
            Set<Integer> ids=new HashSet<>();
            for(var result:results)assertTrue(ids.add(result.get(15,TimeUnit.SECONDS)));
            assertEquals(8,ids.size());
        }
        assertEquals(9,db.queryForObject("select count(*) from ci_service",Integer.class));
        assertEquals(8,db.queryForObject("select count(distinct icon) from ci_service where id<>1",Integer.class));
        assertEquals("builtin:shield",db.queryForObject("select icon from ci_service where id=1",String.class));
        verifyNoInteractions(provider);
    }
}
