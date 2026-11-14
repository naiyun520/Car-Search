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
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

class ServiceIconUploadTest {
    @TempDir Path storage;
    JdbcTemplate db;MockMvc mvc;ObjectMapper mapper=new ObjectMapper();
    @BeforeEach void setup(){
        db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:"+UUID.randomUUID()+";MODE=MySQL;DATABASE_TO_LOWER=TRUE;DB_CLOSE_DELAY=-1","sa",""));
        db.execute("create table ci_service(id int primary key,code varchar,icon varchar,sort int,updated_at timestamp)");
        db.execute("create table ci_order(service_id int)");
        db.update("insert into ci_service values(1,'existing_one','builtin:car',1,current_timestamp),(2,'existing_two','builtin:vin',2,current_timestamp)");
        var controller=new ServiceAdminController(db,mock(CryptoService.class),mapper,mock(ProviderService.class),mock(AuditService.class),storage.toString(),mock(QueryJobService.class));
        mvc=MockMvcBuilders.standaloneSetup(controller,new PublicController(db,mapper,mock(CarProperties.class),storage.toString()))
            .setControllerAdvice(new GlobalExceptionHandler()).build();
    }
    byte[] png(int size)throws Exception{
        var out=new ByteArrayOutputStream();ImageIO.write(new BufferedImage(size,size,BufferedImage.TYPE_INT_RGB),"png",out);return out.toByteArray();
    }
    String icon(){return db.queryForObject("select icon from ci_service where id=1",String.class);}
    String upload(byte[] bytes)throws Exception{
        var result=mvc.perform(multipart("/admin-api/service-icon-upload/1").file(new MockMultipartFile("icon","fixture.png","image/png",bytes)))
            .andExpect(status().isOk()).andReturn();
        Map<?,?> body=mapper.readValue(result.getResponse().getContentAsByteArray(),Map.class);
        return String.valueOf(((Map<?,?>)body.get("data")).get("icon"));
    }
    @Test void multipartUploadPersistsInConfiguredDirectoryAndIsPubliclyReadable()throws Exception{
        byte[] bytes=png(32);String filename=upload(bytes);
        assertTrue(filename.matches("[a-f0-9]{32}\\.png"));assertEquals(filename,icon());
        assertArrayEquals(bytes,Files.readAllBytes(storage.resolve("service-icons").resolve(filename)));
        mvc.perform(get("/api/v1/service-icon/"+filename)).andExpect(status().isOk())
            .andExpect(content().contentType("image/png")).andExpect(content().bytes(bytes))
            .andExpect(header().string("X-Content-Type-Options","nosniff"));
    }
    @Test void replacingAnIconRemovesThePreviousImage()throws Exception{
        String old=upload(png(32)),current=upload(png(64));
        assertNotEquals(old,current);assertFalse(Files.exists(storage.resolve("service-icons").resolve(old)));
        assertTrue(Files.exists(storage.resolve("service-icons").resolve(current)));
    }
    @Test void deniedStorageExplainsPathConfigurationAndPreservesTheOldIcon()throws Exception{
        var file=new MockMultipartFile("icon","fixture.png","image/png",png(32)){
            @Override public void transferTo(Path target)throws java.io.IOException{throw new AccessDeniedException(target.toString());}
        };
        var response=mvc.perform(multipart("/admin-api/service-icon-upload/1").file(file))
                .andExpect(status().isUnprocessableEntity()).andReturn().getResponse();
        String message=String.valueOf(mapper.readValue(response.getContentAsByteArray(),Map.class).get("message"));
        assertTrue(message.contains("无权写入"));assertTrue(message.contains("CAR_STORAGE_PATH"));assertTrue(message.contains("工作目录"));
        assertTrue(message.contains(storage.resolve("service-icons").toString()));assertEquals("builtin:car",icon());
    }
    @Test void pathOccupiedByAFileNamesTheStorageProblem()throws Exception{
        Files.writeString(storage.resolve("service-icons"),"LOCAL-FIXTURE");
        var response=mvc.perform(multipart("/admin-api/service-icon-upload/1").file(new MockMultipartFile("icon","fixture.png","image/png",png(32))))
                .andExpect(status().isUnprocessableEntity()).andReturn().getResponse();
        String message=String.valueOf(mapper.readValue(response.getContentAsByteArray(),Map.class).get("message"));
        assertTrue(message.contains("同名文件"));assertTrue(message.contains("CAR_STORAGE_PATH"));assertEquals("builtin:car",icon());
    }
    @Test void storageIoFailureDoesNotReplaceAnExistingIcon()throws Exception{
        String original=upload(png(32));
        var file=new MockMultipartFile("icon","fixture.png","image/png",png(64)){
            @Override public void transferTo(Path target)throws java.io.IOException{throw new java.io.IOException("LOCAL STORAGE FAILURE");}
        };
        var response=mvc.perform(multipart("/admin-api/service-icon-upload/1").file(file))
                .andExpect(status().isUnprocessableEntity()).andReturn().getResponse();
        assertEquals(original,icon());assertTrue(Files.isRegularFile(storage.resolve("service-icons").resolve(original)));
        assertTrue(mapper.readValue(response.getContentAsByteArray(),Map.class).get("message").toString().contains("原图标未替换"));
    }
    @Test void forgedImageIsRejectedWithoutChangingTheService()throws Exception{
        mvc.perform(multipart("/admin-api/service-icon-upload/1").file(new MockMultipartFile("icon","bad.png","image/png","not an image".getBytes())))
            .andExpect(status().is4xxClientError());
        assertEquals("builtin:car",icon());assertFalse(Files.exists(storage.resolve("service-icons")));
    }
    @Test void unsupportedTypeIsRejected()throws Exception{
        mvc.perform(multipart("/admin-api/service-icon-upload/1").file(new MockMultipartFile("icon","bad.svg","image/svg+xml","<svg/>".getBytes())))
            .andExpect(status().is4xxClientError());assertEquals("builtin:car",icon());
    }
    @Test void emptyAndOversizedFilesAreRejected()throws Exception{
        for(byte[] bytes:List.of(new byte[0],new byte[75*1024+1])){
            mvc.perform(multipart("/admin-api/service-icon-upload/1").file(new MockMultipartFile("icon","bad.png","image/png",bytes)))
                .andExpect(status().is4xxClientError());
            assertEquals("builtin:car",icon());
        }
    }
    @Test void tooSmallImageIsRejected()throws Exception{
        mvc.perform(multipart("/admin-api/service-icon-upload/1").file(new MockMultipartFile("icon","small.png","image/png",png(16))))
            .andExpect(status().is4xxClientError());assertEquals("builtin:car",icon());
    }
    @Test void missingServiceDoesNotCreateAFile()throws Exception{
        mvc.perform(multipart("/admin-api/service-icon-upload/999").file(new MockMultipartFile("icon","fixture.png","image/png",png(32))))
            .andExpect(status().isNotFound());assertFalse(Files.exists(storage.resolve("service-icons")));
    }
    @Test void sortRouteRemainsAvailableAndRequiresTheWholeServiceList()throws Exception{
        mvc.perform(post("/admin-api/services/sort").contentType("application/json").content("{\"ids\":[2,1]}")).andExpect(status().isOk());
        assertEquals(List.of(2,1),db.queryForList("select id from ci_service order by sort",Integer.class));
        mvc.perform(post("/admin-api/services/sort").contentType("application/json").content("{\"ids\":[2,2]}")).andExpect(status().is4xxClientError());
        assertEquals(List.of(2,1),db.queryForList("select id from ci_service order by sort",Integer.class));
    }
    @Test void deleteRouteProtectsServicesWithOrderHistory()throws Exception{
        db.update("insert into ci_order values(1)");
        mvc.perform(delete("/admin-api/services/1")).andExpect(status().is4xxClientError());
        assertEquals(1,db.queryForObject("select count(*) from ci_service where id=1",Integer.class));
        mvc.perform(delete("/admin-api/services/2")).andExpect(status().isOk());
        assertEquals(0,db.queryForObject("select count(*) from ci_service where id=2",Integer.class));
    }
}
