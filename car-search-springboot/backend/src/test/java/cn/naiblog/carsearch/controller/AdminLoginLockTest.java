package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.BusinessException;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;
import java.time.LocalDateTime;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class AdminLoginLockTest {
    void locked(Object until){
        JdbcTemplate db=mock(JdbcTemplate.class);
        when(db.queryForList(anyString(),any(Object[].class))).thenReturn(List.of(Map.of("locked_until",until,"fail_count",5)));
        var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");
        var controller=new AdminController(db,null,null,null);
        var e=assertThrows(BusinessException.class,()->controller.login(Map.of("username","test","password","irrelevant"),request));
        assertEquals(429,e.code());verify(db,times(1)).queryForList(anyString(),any(Object[].class));verify(db,never()).update(anyString(),any(Object[].class));
    }
    @Test void mysqlLocalDateTimeLocksBeforeCheckingPassword(){locked(LocalDateTime.now().plusMinutes(15));}
    @Test void timestampDatesAlsoRemainLocked(){locked(java.sql.Timestamp.valueOf(LocalDateTime.now().plusMinutes(15)));}
    @Test void expiredLocalDateTimeAllowsPasswordCheck(){
        JdbcTemplate db=mock(JdbcTemplate.class);
        when(db.queryForList(anyString(),any(Object[].class))).thenAnswer(c->c.<String>getArgument(0).contains("ci_admin_login_attempt")?List.of(Map.of("locked_until",LocalDateTime.now().minusMinutes(1),"fail_count",5)):List.of());
        var request=new MockHttpServletRequest();request.setRemoteAddr("127.0.0.1");
        var e=assertThrows(BusinessException.class,()->new AdminController(db,null,null,null).login(Map.of("username","test","password","bad"),request));
        assertEquals(401,e.code());verify(db).update(contains("fail_count+1"),any(Object[].class));
    }
}
