package team.momentlog.server.record;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(properties = "spring.datasource.url=jdbc:h2:mem:sealtest;MODE=MySQL")
@AutoConfigureMockMvc
class RecordSealApiTest {

    private static final String ORIGINAL = "a".repeat(64);
    private static final String TAMPERED = "b".repeat(64);

    @Autowired
    MockMvc mockMvc;

    private String sealBody(String sha) {
        return "{\"sha256\":\"" + sha + "\",\"formatVersion\":1,\"deviceCreatedAt\":\"2026-10-23T09:00:00Z\"}";
    }

    private String verifyBody(String sha) {
        return "{\"sha256\":\"" + sha + "\"}";
    }

    @Test
    void 봉인한_원본은_일치하고_고친_문서는_불일치() throws Exception {
        mockMvc.perform(post("/api/v1/records/r-1/seal").contentType(MediaType.APPLICATION_JSON).content(sealBody(ORIGINAL)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sealedAt").exists());

        mockMvc.perform(post("/api/v1/records/r-1/verify").contentType(MediaType.APPLICATION_JSON).content(verifyBody(ORIGINAL)))
                .andExpect(jsonPath("$.status").value("MATCH"));

        mockMvc.perform(post("/api/v1/records/r-1/verify").contentType(MediaType.APPLICATION_JSON).content(verifyBody(TAMPERED)))
                .andExpect(jsonPath("$.status").value("MISMATCH"));
    }

    @Test
    void 같은_해시를_다시_보내면_처음_봉인을_돌려주고_다른_해시는_거부() throws Exception {
        mockMvc.perform(post("/api/v1/records/r-2/seal").contentType(MediaType.APPLICATION_JSON).content(sealBody(ORIGINAL)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/records/r-2/seal").contentType(MediaType.APPLICATION_JSON).content(sealBody(ORIGINAL)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/v1/records/r-2/seal").contentType(MediaType.APPLICATION_JSON).content(sealBody(TAMPERED)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.errorCode").value("SEAL_CONFLICT"));
    }

    @Test
    void 봉인된_적_없는_기록은_NOT_FOUND() throws Exception {
        mockMvc.perform(post("/api/v1/records/none/verify").contentType(MediaType.APPLICATION_JSON).content(verifyBody(ORIGINAL)))
                .andExpect(jsonPath("$.status").value("NOT_FOUND"));
    }

    @Test
    void 해시_형식이_틀리면_400() throws Exception {
        mockMvc.perform(post("/api/v1/records/r-3/seal").contentType(MediaType.APPLICATION_JSON).content(sealBody("xyz")))
                .andExpect(status().isBadRequest());
    }
}
