package com.uberlite.discountsanalytics;

import com.uberlite.common.dto.RiderTripCountDto;
import com.uberlite.discountsanalytics.domain.PromoFlagger;
import com.uberlite.discountsanalytics.domain.RiderTripCountSource;
import com.uberlite.discountsanalytics.repository.entity.PromoCandidateEntity;
import com.uberlite.discountsanalytics.repository.PromoCandidateRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

/**
 * The batch against a real Postgres, with only the Trip Service call mocked out.
 *
 * <p>Covers what the unit test cannot: that the upsert and the stale sweep are valid SQL, and that
 * re-running the batch is genuinely idempotent rather than merely intended to be.
 */
@Testcontainers(disabledWithoutDocker = true)
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.cloud.discovery.enabled=false",
        // Never fires during the test; the batch is invoked directly instead.
        "discounts-analytics.cron=0 0 2 29 2 ?"
})
class DiscountsAnalyticsIntegrationTest {

    @Container
    static final PostgreSQLContainer<?> postgres = new PostgreSQLContainer<>("postgres:15")
            .withDatabaseName("discountsanalyticsdb")
            .withUsername("uberlite")
            .withPassword("changeme");

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        Flyway.configure()
                .dataSource(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())
                .locations("classpath:db/migration")
                .load()
                .migrate();
        registry.add("spring.datasource.url", postgres::getJdbcUrl);
        registry.add("spring.datasource.username", postgres::getUsername);
        registry.add("spring.datasource.password", postgres::getPassword);
    }

    @MockitoBean
    private RiderTripCountSource tripCounts;

    @Autowired
    private PromoFlagger flagger;

    @Autowired
    private PromoCandidateRepository candidates;

    @Test
    void writesCandidatesAndRevokesThemOnceTheRiderCrossesTheThreshold() {
        when(tripCounts.completedTripCounts()).thenReturn(List.of(
                new RiderTripCountDto("rider-new", 1),
                new RiderTripCountDto("rider-regular", 9)));

        flagger.flagCandidates();

        assertThat(candidates.findAllByOrderByRiderIdAsc())
                .extracting(PromoCandidateEntity::getRiderId)
                .containsExactly("rider-new");

        // The next night, that rider has taken their third trip.
        when(tripCounts.completedTripCounts()).thenReturn(List.of(
                new RiderTripCountDto("rider-new", 3),
                new RiderTripCountDto("rider-regular", 9)));

        flagger.flagCandidates();

        assertThat(candidates.findAllByOrderByRiderIdAsc()).isEmpty();
    }

    @Test
    void reRunningTheBatchIsIdempotentRatherThanAccumulating() {
        when(tripCounts.completedTripCounts())
                .thenReturn(List.of(new RiderTripCountDto("rider-steady", 2)));

        flagger.flagCandidates();
        flagger.flagCandidates();

        // A plain INSERT would have blown up on the primary key the second time round.
        assertThat(candidates.findAllByOrderByRiderIdAsc())
                .extracting(PromoCandidateEntity::getRiderId)
                .containsExactly("rider-steady");
    }
}

