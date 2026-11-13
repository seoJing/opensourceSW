
package com.suwontrip.app;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// 실제 서버 포트 없이 Spring MVC 요청을 검증하는 통합 테스트
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test") // 테스트 전용 H2 데이터베이스 설정 적용
class HealthEndpointTests {

    @Autowired // Spring이 구성한 MockMvc Bean 주입 (Dependency Injection)
    private MockMvc mockMvc;

    @Test
    void healthEndpointReportsDatabaseIsUp() throws Exception {
        mockMvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                // 테스트용 H2 DataSource의 연결 상태를 확인한다.
                .andExpect(jsonPath("$.components.db.status").value("UP"));
    }
}
