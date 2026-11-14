package cn.naiblog.carsearch.security;

import cn.naiblog.carsearch.config.SettingService;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import jakarta.servlet.FilterChain;
import java.nio.charset.StandardCharsets;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class AdminPathFilterTest {
    private AdminPathFilter filter(String slug){
        SettingService settings=mock(SettingService.class);
        when(settings.value("admin_path","admin")).thenReturn(slug);
        return new AdminPathFilter(settings);
    }

    @Test void orderToolsScriptIsAvailableAtConfiguredAdminPath() throws Exception {
        var response=new MockHttpServletResponse();
        filter("console").doFilter(new MockHttpServletRequest("GET","/console/order-tools.js"),response,mock(FilterChain.class));
        assertEquals(200,response.getStatus());
        assertEquals("/admin/order-tools.js",response.getForwardedUrl());
        assertEquals("no-store",response.getHeader("Cache-Control"));
    }

    @Test void everyReferencedAssetIsBothPackagedAndAllowedForDefaultAndCustomPaths() throws Exception {
        var html=new ClassPathResource("static/admin/index.html");
        String text=new String(html.getInputStream().readAllBytes(),StandardCharsets.UTF_8);
        var matcher=Pattern.compile("(?:src|href)=\"\\./([^\"?]+)").matcher(text);
        int count=0;
        while(matcher.find()){
            String asset=matcher.group(1);count++;
            assertTrue(new ClassPathResource("static/admin/"+asset).exists(),"Missing packaged asset: "+asset);
            for(String slug:new String[]{"admin","car-console"}){
                var response=new MockHttpServletResponse();
                var request=new MockHttpServletRequest("GET","/"+slug+"/"+asset);
                request.setQueryString("v=asset-contract-test");
                filter(slug).doFilter(request,response,mock(FilterChain.class));
                assertEquals("/admin/"+asset,response.getForwardedUrl(),"Blocked referenced asset: "+asset+" under "+slug);
                assertEquals(200,response.getStatus());
            }
        }
        assertTrue(count>=10,"HTML asset enumeration must cover scripts and styles");
    }

    @Test void unknownAndTraversalPathsRemainBlocked() throws Exception {
        for(String asset:new String[]{"private.js","../application.yml","nested/order-tools.js"}){
            var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
            filter("console").doFilter(new MockHttpServletRequest("GET","/console/"+asset),response,chain);
            assertEquals(404,response.getStatus());verifyNoInteractions(chain);
        }
    }

    @Test void customPathDoesNotExposeDefaultAdminPath() throws Exception {
        var response=new MockHttpServletResponse();
        filter("console").doFilter(new MockHttpServletRequest("GET","/admin/order-tools.js"),response,mock(FilterChain.class));
        assertEquals(404,response.getStatus());
    }

    @Test void headWorksButPostCannotServeAssets() throws Exception {
        var response=new MockHttpServletResponse();
        filter("console").doFilter(new MockHttpServletRequest("HEAD","/console/order-tools.js"),response,mock(FilterChain.class));
        assertEquals("/admin/order-tools.js",response.getForwardedUrl());
        var post=new MockHttpServletResponse();
        filter("console").doFilter(new MockHttpServletRequest("POST","/console/order-tools.js"),post,mock(FilterChain.class));
        assertEquals(405,post.getStatus());
    }

    @Test void apiRequestsContinueThroughTheirExistingAuthenticationChain() throws Exception {
        var response=new MockHttpServletResponse();var chain=mock(FilterChain.class);
        var request=new MockHttpServletRequest("GET","/admin-api/orders/ORDER/fulfillment");
        filter("console").doFilter(request,response,chain);
        verify(chain).doFilter(request,response);
        assertNull(response.getForwardedUrl());
    }
}

