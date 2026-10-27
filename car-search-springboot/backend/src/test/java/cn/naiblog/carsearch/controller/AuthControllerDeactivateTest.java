package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.api.ApiResponse;
import cn.naiblog.carsearch.api.ApiSupport;
import cn.naiblog.carsearch.api.BusinessException;
import cn.naiblog.carsearch.config.CarProperties;
import cn.naiblog.carsearch.security.CurrentUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletRequest;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class AuthControllerDeactivateTest {
    private JdbcTemplate jdbc;
    private ApiSupport api;
    private AuthController controller;
    private MockHttpServletRequest request;

    @BeforeEach
    void setUp() {
        jdbc = Mockito.mock(JdbcTemplate.class);
        api = Mockito.mock(ApiSupport.class);
        controller = new AuthController(jdbc, api, null, null,
                new CarProperties("test", "key", false, new CarProperties.Wechat("", "")));
        request = new MockHttpServletRequest();
        when(api.user(any())).thenReturn(new CurrentUser(7L, "openid", null, null, null, null));
    }

    /** 有进行中订单（已支付/查询中/待核对/退款中）→ 拒绝注销，不落任何更新 */
    @Test
    void deactivateRejectsWhenActiveOrderExists() {
        when(jdbc.queryForList(contains("ci_order"), eq(7L)))
                .thenReturn(List.of(Map.of("order_no", "NO1")));

        BusinessException error = assertThrows(BusinessException.class, () -> controller.deactivate(request));

        assertTrue(error.getMessage().contains("进行中"));
        verify(jdbc, never()).update(contains("set openid"), any(), any(), any());
    }

    /** 正常注销：openid 墓碑解绑 + 资料匿名化 + status=0，并删除该用户全部 token */
    @Test
    void deactivateAnonymizesUserAndRevokesTokens() {
        when(jdbc.queryForList(contains("ci_order"), eq(7L))).thenReturn(List.of());

        ApiResponse<Object> response = controller.deactivate(request);

        assertEquals(0, response.code());
        verify(jdbc).update(
                argThat((String sql) -> sql.contains("set openid=") && sql.contains("status=0")),
                startsWith("deleted_7_"), any(), eq(7L));
        verify(jdbc).update(contains("delete from ci_user_token"), eq(7L));
    }
}
