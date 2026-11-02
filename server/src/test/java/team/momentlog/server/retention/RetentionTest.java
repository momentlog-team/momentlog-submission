package team.momentlog.server.retention;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import team.momentlog.server.document.infrastructure.RecordShareJpaRepository;
import team.momentlog.server.document.infrastructure.ReportJpaRepository;
import team.momentlog.server.document.infrastructure.VerifyLogJpaRepository;
import team.momentlog.server.record.infrastructure.RecordSealJpaRepository;
import team.momentlog.server.retention.application.RetentionService;

import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:retentiontest;MODE=MySQL",
        "momentlog.report.dir=build/test-reports-retention"})
@AutoConfigureMockMvc
class RetentionTest {

    /** 2026-11-03 18:42:17 KST. */
    private static final long T0 = 1_793_698_937_000L;
    private static final Instant START = Instant.ofEpochMilli(T0).plusSeconds(20);
    private static final String DOCUMENT_NO = "SG-20261103-184217";

    @MockBean
    Clock clock;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RetentionService retentionService;

    @Autowired
    RecordShareJpaRepository recordShareJpaRepository;

    @Autowired
    ReportJpaRepository reportJpaRepository;

    @Autowired
    VerifyLogJpaRepository verifyLogJpaRepository;

    @Autowired
    RecordSealJpaRepository recordSealJpaRepository;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        at(START);
    }

    @Test
    void 기간별로_공유_본문_확인_이력_문서_지문을_지운다() throws Exception {
        String body = canonical();
        mockMvc.perform(post("/api/v1/records/ret-1/seal").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("sha256", sha256(body.getBytes(StandardCharsets.UTF_8)), "formatVersion", 2))))
                .andExpect(status().isOk());
        String created = mockMvc.perform(post("/api/v1/records/ret-1/documents").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", body,
                                "statementJson", "{\"text\":\"뒤에서 받았어요\",\"plate\":\"12가 3456\"}",
                                "placeText", "서울 강남구 테헤란로 교차로 부근"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = json.readTree(created).get("token").asText();
        byte[] pdf = mockMvc.perform(get("/r/" + token + "/report.pdf")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
        Path pdfFile = Path.of("build/test-reports-retention",
                reportJpaRepository.findByDocumentNo(DOCUMENT_NO).orElseThrow().getStorageKey());
        assertThat(pdfFile).exists();
        verify(pdf, "MATCH");

        // 29일 뒤: 아직 만료 전이라 아무것도 지우지 않는다
        at(START.plus(Duration.ofDays(29)));
        assertThat(retentionService.purge()).isEqualTo(new RetentionService.Result(0, 0, 0, 0, 0));

        // 30일 뒤: 공유 본문·진술·장소와 PDF 파일을 지운다. 원본 확인은 계속 된다
        at(START.plus(Duration.ofDays(30)));
        assertThat(retentionService.purge().purgedShares()).isEqualTo(1);
        var share = recordShareJpaRepository.findAll().get(0);
        assertThat(share.getBody()).isNull();
        assertThat(share.getStatementJson()).isNull();
        assertThat(share.getPlaceText()).isNull();
        assertThat(pdfFile).doesNotExist();
        mockMvc.perform(get("/r/" + token)).andExpect(status().isGone());
        mockMvc.perform(get("/v/" + DOCUMENT_NO))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("2026.11.03 (화) 18:42:17")))
                .andExpect(content().string(containsString("서울 강남구 테헤란로 교차로 부근")));
        verify(pdf, "MATCH");
        assertThat(retentionService.purge().purgedShares()).isZero();

        // 121일 뒤: 90일 지난 확인 이력(0일, 30일째)만 지운다
        at(START.plus(Duration.ofDays(121)));
        assertThat(retentionService.purge().verifyLogs()).isEqualTo(2);
        assertThat(verifyLogJpaRepository.count()).isZero();
        assertThat(reportJpaRepository.count()).isEqualTo(1);

        // 3년 뒤: 문서 지문, 공유 행, 봉인까지 지운다
        at(START.plus(Duration.ofDays(1095)).plusSeconds(60));
        assertThat(retentionService.purge()).isEqualTo(new RetentionService.Result(0, 0, 1, 1, 1));
        assertThat(recordSealJpaRepository.count()).isZero();
        mockMvc.perform(get("/v/" + DOCUMENT_NO))
                .andExpect(content().string(containsString("발급된 문서가 없어요")));
        verify(pdf, "NOT_FOUND");
    }

    private void at(Instant now) {
        when(clock.instant()).thenReturn(now);
        when(clock.millis()).thenReturn(now.toEpochMilli());
    }

    private void verify(byte[] pdf, String expected) throws Exception {
        mockMvc.perform(post("/api/v1/documents/" + DOCUMENT_NO + "/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sha256\":\"" + sha256(pdf) + "\"}"))
                .andExpect(jsonPath("$.status").value(expected));
    }

    private static String canonical() {
        StringBuilder b = new StringBuilder("v=2\nid=ret-1\ntrigger=HARD_STOP\ntriggerAt=" + T0 + "\nevent=HARD_STOP@" + T0 + "\n");
        for (long t = -30_000; t <= 10_000; t += 500) {
            double speed = t < -4_100 ? 32 : t <= 0 ? 32 - (t + 4_100) / 4_100.0 * 20 : Math.max(0, 12 - t / 400.0);
            boolean brake = t >= -4_100 && t <= 3_000;
            String turn = t >= -6_200 && t <= 1_000 ? "RIGHT" : "OFF";
            b.append(T0 + t).append('|').append(String.format(java.util.Locale.ROOT, "%.2f", speed)).append('|')
                    .append(brake).append('|').append(turn).append('|').append("0.00").append('|')
                    .append("true|false|false|37.504500|127.048800\n");
        }
        return b.toString();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
