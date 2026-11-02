package team.momentlog.server.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import team.momentlog.server.device.infrastructure.DeviceJpaRepository;
import team.momentlog.server.document.infrastructure.RecordShareJpaRepository;
import team.momentlog.server.document.infrastructure.ReportJpaRepository;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:revoketest;MODE=MySQL",
        "momentlog.report.dir=build/test-reports-revoke"})
@AutoConfigureMockMvc
class ShareRevokeTest {

    /** 2026-11-03 18:42:17 KST. */
    private static final long T0 = 1_793_698_937_000L;
    private static final String TOKEN = "token-of-car-a";

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DeviceJpaRepository deviceJpaRepository;

    @Autowired
    RecordShareJpaRepository recordShareJpaRepository;

    @Autowired
    ReportJpaRepository reportJpaRepository;

    private final ObjectMapper json = new ObjectMapper();

    @Test
    void 기록을_봉인한_기기만_공유를_철회하고_원본_확인은_남는다() throws Exception {
        String body = canonical("rv-1");
        seal("rv-1", body, "car-a", TOKEN).andExpect(status().isOk());
        String token = share("rv-1", body);
        byte[] pdf = mockMvc.perform(get("/r/" + token + "/report.pdf")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        var report = reportJpaRepository.findByShareId(recordShareJpaRepository.findAll().get(0).getShareId()).get(0);
        Path pdfFile = Path.of("build/test-reports-revoke", report.getStorageKey());
        assertThat(pdfFile).exists();

        // 다른 기기 B가 자기 토큰으로 등록돼 있어도 A의 기록은 못 지운다
        seal("rv-other", canonical("rv-other"), "car-b", "token-of-car-b").andExpect(status().isOk());
        mockMvc.perform(revoke("rv-1", "car-b", "token-of-car-b"))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("NOT_OWNER"));
        mockMvc.perform(revoke("rv-1", "car-a", "wrong"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("DEVICE_AUTH"));
        mockMvc.perform(delete("/api/v1/records/rv-1/documents")).andExpect(status().isUnauthorized());
        mockMvc.perform(revoke("none", "car-a", TOKEN)).andExpect(status().isNotFound());
        mockMvc.perform(get("/r/" + token)).andExpect(status().isOk());

        mockMvc.perform(revoke("rv-1", "car-a", TOKEN)).andExpect(status().isNoContent());
        mockMvc.perform(get("/r/" + token)).andExpect(status().isGone());
        mockMvc.perform(get("/r/" + token + "/report.pdf")).andExpect(status().isGone());
        var revoked = recordShareJpaRepository.findAll().get(0);
        assertThat(revoked.getBody()).isNull();
        assertThat(revoked.getStatementJson()).isNull();
        assertThat(revoked.getPurgedAt()).isNotNull();
        assertThat(pdfFile).doesNotExist();

        // 이미 건넨 PDF는 계속 원본과 대조할 수 있다
        mockMvc.perform(post("/api/v1/documents/" + report.getDocumentNo() + "/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sha256\":\"" + sha256(pdf) + "\"}"))
                .andExpect(jsonPath("$.status").value("MATCH"));

        mockMvc.perform(revoke("rv-1", "car-a", TOKEN)).andExpect(status().isNoContent());
    }

    @Test
    void 토큰이_묶인_기기_ID는_토큰_없이_쓸_수_없다() throws Exception {
        seal("au-1", canonical("au-1"), "car-c", "token-of-car-c").andExpect(status().isOk());
        assertThat(deviceJpaRepository.findById("car-c").orElseThrow().hasToken()).isTrue();

        seal("au-2", canonical("au-2"), "car-c", null)
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("DEVICE_AUTH"));
        seal("au-3", canonical("au-3"), "car-c", "someone-else").andExpect(status().isUnauthorized());
        seal("au-4", canonical("au-4"), "car-c", "token-of-car-c").andExpect(status().isOk());
    }

    @Test
    void 토큰_없이_등록된_예전_기기는_처음_보낸_토큰으로_묶인다() throws Exception {
        seal("old-1", canonical("old-1"), "car-d", null).andExpect(status().isOk());
        assertThat(deviceJpaRepository.findById("car-d").orElseThrow().hasToken()).isFalse();

        mockMvc.perform(revoke("old-1", "car-d", "token-of-car-d")).andExpect(status().isNoContent());
        assertThat(deviceJpaRepository.findById("car-d").orElseThrow().hasToken()).isTrue();
        mockMvc.perform(revoke("old-1", "car-d", "another")).andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions seal(String recordId, String body, String deviceId, String deviceToken) throws Exception {
        MockHttpServletRequestBuilder request = post("/api/v1/records/" + recordId + "/seal").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("sha256", sha256(body.getBytes(StandardCharsets.UTF_8)), "formatVersion", 2, "deviceId", deviceId)));
        if (deviceToken != null) {
            request.header("X-Device-Token", deviceToken);
        }
        return mockMvc.perform(request);
    }

    private String share(String recordId, String body) throws Exception {
        String response = mockMvc.perform(post("/api/v1/records/" + recordId + "/documents").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", body, "statementJson", "{\"text\":\"뒤에서 받았어요\",\"plate\":\"12가 3456\"}"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(response).get("token").asText();
    }

    private static MockHttpServletRequestBuilder revoke(String recordId, String deviceId, String deviceToken) {
        return delete("/api/v1/records/" + recordId + "/documents")
                .header("X-Device-Id", deviceId)
                .header("X-Device-Token", deviceToken);
    }

    private static String canonical(String id) {
        StringBuilder b = new StringBuilder("v=2\nid=" + id + "\ntrigger=HARD_STOP\ntriggerAt=" + T0 + "\nevent=HARD_STOP@" + T0 + "\n");
        for (long t = -30_000; t <= 10_000; t += 1_000) {
            b.append(T0 + t).append('|').append(t < 0 ? "30.00" : "0.00").append("|false|OFF|0.00|false|false|false||\n");
        }
        return b.toString();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
