package es.kitti.gateway.resource;

import io.vertx.ext.web.RoutingContext;
import jakarta.ws.rs.core.HttpHeaders;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GatewayResourceUnitTest {

    @Test
    void testClientIpReturnsUnknownWhenNoForwardedAndNoRoutingContext() throws Exception {
        HttpHeaders headers = mock(HttpHeaders.class);
        when(headers.getHeaderString("X-Forwarded-For")).thenReturn(null);

        Method m = GatewayResource.class
                .getDeclaredMethod("clientIp", HttpHeaders.class, RoutingContext.class);
        m.setAccessible(true);

        String ip = (String) m.invoke(null, headers, null);
        assertEquals("unknown", ip);
    }
}
