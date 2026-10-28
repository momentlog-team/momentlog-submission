package team.momentlog.server.document.report;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * 운전자 진술. 차량 앱이 공유를 만들 때 `statementJson`으로 보낸다: `{"text": "...", "plate": "12가 3456"}`.
 * 센서 기록이 아니므로 문서에서 따로 구분해 보여 준다.
 */
public record Statement(String text, String plate) {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    public static Statement parse(String json) {
        if (json == null || json.isBlank()) return null;
        try {
            JsonNode node = MAPPER.readTree(json);
            String text = textOf(node, "text");
            String plate = textOf(node, "plate");
            return text == null && plate == null ? null : new Statement(text, plate);
        } catch (Exception e) {
            // 형식이 다르면 받은 문자열을 그대로 진술로 보여 준다
            return new Statement(json, null);
        }
    }

    private static String textOf(JsonNode node, String field) {
        JsonNode v = node.get(field);
        return v == null || v.isNull() || v.asText().isBlank() ? null : v.asText();
    }
}
