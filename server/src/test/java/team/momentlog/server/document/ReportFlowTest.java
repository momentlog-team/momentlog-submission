package team.momentlog.server.document;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import team.momentlog.server.document.infrastructure.ReportJpaRepository;

import javax.imageio.ImageIO;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = {
        "spring.datasource.url=jdbc:h2:mem:reporttest;MODE=MySQL",
        "momentlog.report.dir=build/test-reports"})
@AutoConfigureMockMvc
class ReportFlowTest {

    /** 2026-11-03 18:42:17 KST에 급정지. 6.2초 전 우측 방향지시등, 4.1초 전 브레이크, 32 → 12km/h. */
    private static final long T0 = 1_793_698_937_000L;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    ReportJpaRepository reportJpaRepository;

    private final ObjectMapper json = new ObjectMapper();

    private static String canonical() {
        StringBuilder b = new StringBuilder("v=2\nid=rep-1\ntrigger=HARD_STOP\ntriggerAt=" + T0 + "\nevent=HARD_STOP@" + T0 + "\n");
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

    @Test
    void 공유에서_PDF를_한번만_발급하고_원본_대조가_일치한다() throws Exception {
        String body = canonical();
        mockMvc.perform(post("/api/v1/records/rep-1/seal").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("sha256", sha256(body.getBytes(StandardCharsets.UTF_8)), "formatVersion", 2))))
                .andExpect(status().isOk());

        String created = mockMvc.perform(post("/api/v1/records/rep-1/documents").contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(Map.of("body", body,
                                "statementJson", "{\"text\":\"우회전하려고 깜빡이 켜고 천천히 서는데 뒤에서 받았어요\",\"plate\":\"12가 3456\"}",
                                "placeText", "서울 강남구 테헤란로 교차로 부근"))))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
        String token = json.readTree(created).get("token").asText();

        mockMvc.perform(get("/r/" + token))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString("우측 방향지시등")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("12가 3456")));

        byte[] first = mockMvc.perform(get("/r/" + token + "/report.pdf"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_PDF))
                .andReturn().getResponse().getContentAsByteArray();
        byte[] second = mockMvc.perform(get("/r/" + token + "/report.pdf")).andReturn().getResponse().getContentAsByteArray();
        assertThat(second).isEqualTo(first);

        var report = reportJpaRepository.findFirstByRecordId("rep-1").orElseThrow();
        assertThat(report.getDocumentNo()).isEqualTo("SG-20261103-184217");
        assertThat(report.getPdfSha256()).isEqualTo(sha256(first));

        try (PDDocument pdf = Loader.loadPDF(first)) {
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);
            String text = new PDFTextStripper().getText(pdf);
            assertThat(text).contains("사고 정리 문서", "SG-20261103-184217", "우측 방향지시등", "12가 3456");
            Files.createDirectories(Path.of("build"));
            Files.write(Path.of("build/report-sample.pdf"), first);
            ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0, 110), "png", Path.of("build/report-sample.png").toFile());
        }

        mockMvc.perform(post("/api/v1/documents/SG-20261103-184217/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sha256\":\"" + sha256(first) + "\"}"))
                .andExpect(jsonPath("$.status").value("MATCH"));
        byte[] tampered = first.clone();
        tampered[tampered.length / 2] ^= 1;
        mockMvc.perform(post("/api/v1/documents/SG-20261103-184217/verify").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"sha256\":\"" + sha256(tampered) + "\"}"))
                .andExpect(jsonPath("$.status").value("MISMATCH"));

        mockMvc.perform(get("/v/SG-20261103-184217"))
                .andExpect(status().isOk())
                .andExpect(content().string(org.hamcrest.Matchers.containsString(report.getPdfSha256())));
    }

    @Test
    void 없는_토큰은_404_페이지() throws Exception {
        mockMvc.perform(get("/r/nope")).andExpect(status().isNotFound());
    }

    private static String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
