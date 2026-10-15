package cn.naiblog.carsearch.controller;

import cn.naiblog.carsearch.config.CarProperties;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

class AuthControllerDevLoginTest {
    @Test
    void allowsExplicitDevelopmentLoginOnlyFromLoopback() {
        AuthController enabled = controller(true);
        MockHttpServletRequest local = new MockHttpServletRequest();
        local.setRemoteAddr("127.0.0.1");
        MockHttpServletRequest ipv6Local = new MockHttpServletRequest();
        ipv6Local.setRemoteAddr("::1");
        MockHttpServletRequest remote = new MockHttpServletRequest();
        remote.setRemoteAddr("192.168.1.10");

        assertThat(enabled.localDevelopmentLoginAllowed(local)).isTrue();
        assertThat(enabled.localDevelopmentLoginAllowed(ipv6Local)).isTrue();
        assertThat(enabled.localDevelopmentLoginAllowed(remote)).isFalse();
    }

    @Test
    void remainsDisabledByDefaultEvenOnLoopback() {
        MockHttpServletRequest local = new MockHttpServletRequest();
        local.setRemoteAddr("127.0.0.1");
        assertThat(controller(false).localDevelopmentLoginAllowed(local)).isFalse();
    }

    private AuthController controller(boolean enabled) {
        CarProperties properties = new CarProperties("test", "key", enabled, new CarProperties.Wechat("", ""));
        return new AuthController(null, null, null, null, properties);
    }
}
