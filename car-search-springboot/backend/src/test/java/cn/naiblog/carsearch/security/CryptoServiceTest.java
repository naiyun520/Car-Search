package cn.naiblog.carsearch.security;

import cn.naiblog.carsearch.config.CarProperties;
import tools.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class CryptoServiceTest {
    @Test void roundTripUsesPhpCompatibleEnvelope() throws Exception {
        CryptoService crypto = new CryptoService(new ObjectMapper(), new CarProperties("test", "12345678901234567890123456789012", new CarProperties.Wechat("", "")));
        String encrypted = crypto.encrypt(Map.of("value", "中文数据"));
        assertThat(crypto.decrypt(encrypted)).containsEntry("value", "中文数据");
    }
}
