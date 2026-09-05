package vn.thanhdo.integration.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import vn.thanhdo.integration.client.ApiClient;
import vn.thanhdo.integration.client.RateLimiter;

/**
 * Hai ApiClient doc lap, MOI BEN MOT BO DIEU TIET RIENG.
 *
 * <p>Gioi han 100 request/phut duoc dem rieng cho tung he thong, nen dung chung
 * mot bo dieu tiet se lang phi mot nua ngan sach.
 */
@Configuration
public class ClientConfig {

    @Bean
    public ApiClient sisClient(IntegrationProperties props) {
        return new ApiClient(
                "SIS",
                props.getSis().getBaseUrl(),
                props.getSis().getClientId(),
                props.getSis().getClientSecret(),
                props.getTenantId(),
                new RateLimiter(props.getRateLimit().getPermitsPerMinute()));
    }

    @Bean
    public ApiClient lmsClient(IntegrationProperties props) {
        return new ApiClient(
                "LMS",
                props.getLms().getBaseUrl(),
                props.getLms().getClientId(),
                props.getLms().getClientSecret(),
                props.getTenantId(),
                new RateLimiter(props.getRateLimit().getPermitsPerMinute()));
    }
}
