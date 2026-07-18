package com.meetingmind.auth;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;

@SpringBootTest
@ActiveProfiles("test")
class AuthApplicationContextTest {

    @MockitoBean
    private JdbcTemplate jdbcTemplate;

    @MockitoBean
    private PlatformTransactionManager transactionManager;

    @Test
    void contextLoadsWithFailClosedRuntimeDependencies() {
    }
}
