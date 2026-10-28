package team.momentlog.server.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import team.momentlog.server.device.infrastructure.DeviceJpaRepository;
import team.momentlog.server.document.infrastructure.VerifyLogJpaRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:documenttest;MODE=MySQL")
@AutoConfigureMockMvc
class DocumentApiTest {

    private static final String BODY = "v=2\nid=d-1\ntrigger=HARD_STOP\ntriggerAt=40000\n1000|50.0|false|OFF|0.0||||\n";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DeviceJpaRepository deviceJpaRepository;

    @Autowired
    VerifyLogJpaRepository verifyLogJpaRepository;

    private void seal(String recordId, String body) throws Exception {
        String json = "{\"sha256\":\"" + sha256(body) + "\",\"formatVersion\":2,\"deviceId\":\"car-1\"}";
        mockMvc.perform(post("/api/v1/records/" + recordId + "/seal").contentType(MediaType.APPLICATION_JSON).content(json))
                .andExpect(status().isOk());
    }

    private String shareBody(String body) {
        return "{\"body\":\"" + body.replace("\n", "\\n") + "\",\"statementJson\":\"{\\\"text\\\":\\\"앞차가 급정거\\\"}\",\"placeText\":\"교차로\"}";
    }

    @Test
    void 봉인한_원본으로만_공유를_만들고_기기를_등록한다() throws Exception {
        seal("d-1", BODY);
        assertThat(deviceJpaRepository.findById("car-1")).isPresent();

        mockMvc.perform(post("/api/v1/records/d-1/documents").contentType(MediaType.APPLICATION_JSON).content(shareBody(BODY)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.token").isNotEmpty())
                .andExpect(jsonPath("$.path").value(org.hamcrest.Matchers.startsWith("/r/")))
                .andExpect(jsonPath("$.expiresAt").exists());

        mockMvc.perform(post("/api/v1/records/d-1/documents").contentType(MediaType.APPLICATION_JSON).content(shareBody(BODY.replace("50.0", "30.0"))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("BODY_MISMATCH"));
    }

    @Test
    void 봉인이_없으면_공유를_만들지_않는다() throws Exception {
        mockMvc.perform(post("/api/v1/records/none/documents").contentType(MediaType.APPLICATION_JSON).content(shareBody(BODY)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.errorCode").value("SEAL_NOT_FOUND"));
    }

    @Test
    void 문서_대조는_결과와_무관하게_이력을_남긴다() throws Exception {
        long before = verifyLogJpaRepository.count();
        mockMvc.perform(post("/api/v1/documents/SG-20261028-101500/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sha256\":\"" + "c".repeat(64) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_FOUND"));
        assertThat(verifyLogJpaRepository.count()).isEqualTo(before + 1);
    }

    private static String sha256(String text) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(text.getBytes(StandardCharsets.UTF_8)));
    }
}
