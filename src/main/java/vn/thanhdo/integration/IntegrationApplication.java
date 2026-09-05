package vn.thanhdo.integration;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;
import vn.thanhdo.integration.config.IntegrationProperties;

/**
 * Integration Service ket noi UniSIS <-> UniLearn LMS.
 *
 * <p>Day KHONG phai mot API nghiep vu: dich vu nay chu yeu la HTTP client cua hai
 * he thong nguon. Phan phuc vu vao chi gom bo nhan webhook, /health va vai
 * endpoint quan tri.
 */
@SpringBootApplication
@EnableConfigurationProperties(IntegrationProperties.class)
@EnableScheduling
public class IntegrationApplication {

    public static void main(String[] args) {
        SpringApplication.run(IntegrationApplication.class, args);
    }
}
