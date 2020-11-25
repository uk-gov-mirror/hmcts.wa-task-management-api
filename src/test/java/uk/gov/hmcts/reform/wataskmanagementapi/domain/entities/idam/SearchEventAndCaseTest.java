package uk.gov.hmcts.reform.wataskmanagementapi.domain.entities.idam;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

class SearchEventAndCaseTest {

    @Test
    void should_create_full_object_and_get_values() {

        SearchEventAndCase searchEventAndCase = new SearchEventAndCase(
            "some-caseId",
            "some-eventId"
        );

        Assertions.assertThat(searchEventAndCase.getCaseId()).isEqualTo("some-caseId");
        Assertions.assertThat(searchEventAndCase.getEventId()).isEqualTo("some-eventId");

    }
}