package uk.gov.hmcts.reform.wataskmanagementapi.config;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import uk.gov.hmcts.reform.wataskmanagementapi.SpringBootIntegrationBaseTest;

import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Paths;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;


class SwaggerPublisherTest extends SpringBootIntegrationBaseTest {

    @Autowired
    private MockMvc mvc;

    @DisplayName("Generate swagger documentation")
    @Test
    void generateDocs() throws Exception {
        byte[] specs = mvc.perform(get("/v2/api-docs"))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

        try (OutputStream outputStream = Files.newOutputStream(Paths.get("/tmp/swagger-specs.json"))) {
            outputStream.write(specs);
        }

    }
}
