package team.momentlog.server.document;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultMatcher;
import team.momentlog.server.document.infrastructure.RecordShareJpaRepository;
import team.momentlog.server.document.infrastructure.ReportJpaRepository;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:shareversiontest;MODE=MySQL",
        "momentlog.report.dir=build/test-reports-version"})
@AutoConfigureMockMvc
class ShareVersionTest {

    /** 2026-11-07 09:15:33 KST. 시연 C(주차장 접촉, 진술을 나중에 추가). */
    private static final long T0 = 1_794_010_533_000L;
    private static final Instant START = Instant.ofEpochMilli(T0).plusSeconds(60);
    private static final String FIRST_NO = "SG-20261107-091533";

    @MockBean
    Clock clock;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    RecordShareJpaRepository recordShareJpaRepository;

    @Autowired
    ReportJpaRepository reportJpaRepository;

    private final ObjectMapper json = new ObjectMapper();

    @BeforeEach
    void setUp() {
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
        at(START);
    }

    @Test
    void 같은_내용은_공유를_재사용하고_진술이_바뀌면_새_판본을_발급한다() throws Exception {
        String body = canonical();
        mockMvc.perform(post("/api/v1/records/ver-1/seal").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("sha256", sha256(body.getBytes(StandardCharsets.UTF_8)), "formatVersion", 2))))
                .andExpect(status().isOk());

        // QR 화면을 두 번 열어도 링크는 하나
        JsonNode first = share(body, "{\"text\":\"기둥에 살짝 닿았어요\",\"plate\":null}", status().isCreated());
        at(START.plusSeconds(30));
        JsonNode again = share(body, "{\"text\":\"기둥에 살짝 닿았어요\",\"plate\":null}", status().isOk());
        assertThat(again.get("shareId").asText()).isEqualTo(first.get("shareId").asText());
        assertThat(again.get("token").asText()).isEqualTo(first.get("token").asText());
        assertThat(recordShareJpaRepository.findAll()).hasSize(1);

        byte[] firstPdf = pdf(first.get("token").asText());
        assertThat(reportJpaRepository.findByDocumentNo(FIRST_NO)).isPresent();

        // 진술을 고치면 새 공유와 새 문서 번호
        at(START.plusSeconds(600));
        JsonNode edited = share(body, "{\"text\":\"후진하다가 왼쪽 앞이 기둥에 닿았어요\",\"plate\":null}", status().isCreated());
        assertThat(edited.get("token").asText()).isNotEqualTo(first.get("token").asText());
        assertThat(recordShareJpaRepository.findAll()).hasSize(2);

        at(START.plusSeconds(660));
        byte[] editedPdf = pdf(edited.get("token").asText());
        assertThat(editedPdf).isNotEqualTo(firstPdf);
        assertThat(reportJpaRepository.findByDocumentNo(FIRST_NO + "-2")).isPresent();
        try (PDDocument doc = Loader.loadPDF(editedPdf)) {
            assertThat(new PDFTextStripper().getText(doc)).contains("후진하다가", FIRST_NO + "-2");
        }
        assertThat(pdf(first.get("token").asText())).as("앞 판본 링크는 앞 판본 PDF를 그대로 준다").isEqualTo(firstPdf);

        // 앞 판본도 원본과 일치하지만, 확인 화면에서 새 판본이 있다고 알린다
        verify(FIRST_NO, firstPdf, "MATCH");
        verify(FIRST_NO + "-2", editedPdf, "MATCH");
        mockMvc.perform(get("/v/" + FIRST_NO))
                .andExpect(content().string(containsString("새 판본(" + FIRST_NO + "-2)")));
        mockMvc.perform(get("/v/" + FIRST_NO + "-2"))
                .andExpect(content().string(not(containsString("새 판본"))));
    }

    private JsonNode share(String body, String statementJson, ResultMatcher expected) throws Exception {
        String response = mockMvc.perform(post("/api/v1/records/ver-1/documents").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", body, "statementJson", statementJson))))
                .andExpect(expected)
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        return json.readTree(response);
    }

    private byte[] pdf(String token) throws Exception {
        return mockMvc.perform(get("/r/" + token + "/report.pdf")).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray();
    }

    private void verify(String documentNo, byte[] pdf, String expected) throws Exception {
        mockMvc.perform(post("/api/v1/documents/" + documentNo + "/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sha256\":\"" + sha256(pdf) + "\"}"))
                .andExpect(jsonPath("$.status").value(expected));
    }

    private void at(Instant now) {
        when(clock.instant()).thenReturn(now);
        when(clock.millis()).thenReturn(now.toEpochMilli());
    }

    /** 5km/h 저속 후진, 조향 왼쪽 끝. 위치 없음(실내 주차장). */
    private static String canonical() {
        StringBuilder b = new StringBuilder("v=2\nid=ver-1\ntrigger=HARD_STOP\ntriggerAt=" + T0 + "\nevent=HARD_STOP@" + T0 + "\n");
        for (long t = -30_000; t <= 10_000; t += 500) {
            double speed = t < 0 ? 5 : 0;
            b.append(T0 + t).append('|').append(String.format(java.util.Locale.ROOT, "%.2f", speed)).append('|')
                    .append(t >= -1_000).append('|').append("OFF").append('|').append("-480.00").append('|')
                    .append("false|false|false||\n");
        }
        return b.toString();
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
