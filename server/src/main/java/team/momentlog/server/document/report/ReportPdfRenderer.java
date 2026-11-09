package team.momentlog.server.document.report;

import com.google.zxing.BarcodeFormat;
import com.google.zxing.EncodeHintType;
import com.google.zxing.WriterException;
import com.google.zxing.common.BitMatrix;
import com.google.zxing.qrcode.QRCodeWriter;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDFont;
import org.apache.pdfbox.pdmodel.font.PDType0Font;
import org.springframework.stereotype.Component;
import team.momentlog.core.Snapshot;
import team.momentlog.core.TurnSignal;

import java.awt.Color;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.List;
import java.util.Map;
import java.util.TimeZone;

/**
 * 사고 정리 문서 PDF. 디자인 10번(A4 세로 1장)의 순서를 따른다:
 * 경위 한 줄 → 일시·위치·저장 구간·차량 상태 → 주요 시점과 시간축 그래프 → 기록된 신호 → 진술 → 원본 확인 QR.
 * 좌표는 위에서부터 잰 값(top)으로 다루고 그릴 때 PDF 좌표로 바꾼다. 단위는 pt(1px = 0.75pt).
 */
@Component
public class ReportPdfRenderer {

    private static final float W = PDRectangle.A4.getWidth();
    private static final float H = PDRectangle.A4.getHeight();
    private static final float M = 36.85f; // 13mm
    private static final float CW = W - M * 2;

    private static final Color INK = new Color(0x10151B);
    private static final Color SECONDARY = new Color(0x4F5966);
    private static final Color TERTIARY = new Color(0x7C8591);
    private static final Color LINE = new Color(0xD5DBE2);
    private static final Color SURFACE = new Color(0xF3F5F8);
    private static final Color SUNK = new Color(0xE9EDF1);
    private static final Color IMPACT = new Color(0xD02B35);
    private static final Color PILL_BG = new Color(0xFDE7E8);
    private static final Color PILL_FG = new Color(0xB4232C);
    private static final Map<String, Color> TONE = Map.of(
            "speed", INK, "brake", new Color(0xF0505A), "turn", new Color(0xF5A524),
            "steer", new Color(0x36C5D6), "impact", IMPACT);

    private static final DateTimeFormatter ISSUED = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");

    /** PDF에 넣을 값 묶음. */
    public record Input(ReportFacts facts, Statement statement, String placeText, String documentNo, Instant issuedAt,
                        Instant sealedAt, String sha256, String verifyUrl) {
    }

    public byte[] render(Input in) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            Fonts fonts = new Fonts(font(doc, "Regular"), font(doc, "Bold"), font(doc, "ExtraBold"));
            PDPage page = new PDPage(PDRectangle.A4);
            doc.addPage(page);
            info(doc, in);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                new Page(cs, fonts, in).draw();
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static PDFont font(PDDocument doc, String weight) throws IOException {
        try (InputStream is = ReportPdfRenderer.class.getResourceAsStream("/fonts/NanumGothic-" + weight + ".ttf")) {
            if (is == null) throw new IOException("폰트 없음: " + weight);
            return PDType0Font.load(doc, is);
        }
    }

    private static void info(PDDocument doc, Input in) {
        PDDocumentInformation info = doc.getDocumentInformation();
        info.setTitle("사고 정리 문서 " + in.documentNo());
        info.setCreator("순간기록");
        Calendar c = new GregorianCalendar(TimeZone.getTimeZone("UTC"));
        c.setTimeInMillis(in.issuedAt().toEpochMilli());
        info.setCreationDate(c);
    }

    private record Fonts(PDFont regular, PDFont bold, PDFont extra) {
    }

    /** 한 장을 그리는 동안의 상태. */
    private static final class Page {
        private final PDPageContentStream cs;
        private final Fonts f;
        private final Input in;
        private final ReportFacts facts;

        Page(PDPageContentStream cs, Fonts f, Input in) {
            this.cs = cs;
            this.f = f;
            this.in = in;
            this.facts = in.facts();
        }

        void draw() throws IOException {
            float y = header(M);
            y = lead(y + 10);
            y = factsRow(y + 10);
            y = middle(y + 10);
            y = signals(y + 10);
            statement(y + 10);
            footer();
        }

        private float header(float top) throws IOException {
            text("사고 정리 문서", f.extra(), 16.5f, INK, M, top + 16);
            float tw = width("사고 정리 문서", f.extra(), 16.5f);
            text("순간기록 · 차량 신호 기록", f.bold(), 9, TERTIARY, M + tw + 6, top + 16);
            rightText("문서 번호 " + in.documentNo(), f.regular(), 8.25f, SECONDARY, W - M, top + 7);
            rightText("발급 " + ISSUED.format(in.issuedAt().atZone(ReportFacts.ZONE)) + " · 1/1쪽", f.regular(), 8.25f, SECONDARY, W - M, top + 17.5f);
            fillRect(M, top + 22, CW, 1.9f, INK);
            return top + 24;
        }

        private float lead(float top) throws IOException {
            float pw = width(facts.pill(), f.bold(), 8.25f) + 16;
            roundRect(M, top, pw, 16.5f, 8.25f, PILL_BG);
            text(facts.pill(), f.bold(), 8.25f, PILL_FG, M + 8, top + 11.3f);
            float y = top + 21;
            for (String line : wrap(facts.lead(), f.extra(), 17, CW, 2)) {
                y += 21;
                text(line, f.extra(), 17, INK, M, y);
            }
            for (String detail : facts.details()) {
                for (String line : wrap(detail, f.regular(), 9.5f, CW, 2)) {
                    y += 13.5f;
                    text(line, f.regular(), 9.5f, SECONDARY, M, y);
                }
            }
            return y + 4;
        }

        private float factsRow(float top) throws IOException {
            float h = 46;
            strokeRoundRect(M, top, CW, h, 7.5f, LINE);
            float[] ratios = {1.15f, 1.25f, 0.9f, 1.1f};
            float sum = 4.4f;
            String[][] cells = {
                    {"일시 (" + facts.record().getTrigger().getLabel() + " 시각)", facts.time(), facts.date()},
                    {"위치", placeText(), facts.coordText() == null ? "좌표 기록 없음" : facts.coordText()},
                    {"저장 구간", facts.windowText(), facts.windowSeconds() + "초"},
                    {"차량 상태", orNone(facts.lights()), "와이퍼 " + orNone(facts.wiper())},
            };
            float x = M;
            for (int i = 0; i < cells.length; i++) {
                float cw = CW * ratios[i] / sum;
                if (i > 0) fillRect(x, top, 0.75f, h, LINE);
                text(cells[i][0], f.bold(), 7.5f, TERTIARY, x + 9, top + 12);
                boolean big = i == 0;
                text(fit(cells[i][1], big ? f.extra() : f.bold(), big ? 15 : 9.5f, cw - 18), big ? f.extra() : f.bold(), big ? 15 : 9.5f, INK, x + 9, top + (big ? 29 : 26));
                text(fit(cells[i][2], f.regular(), 8.25f, cw - 18), f.regular(), 8.25f, SECONDARY, x + 9, top + 40);
                x += cw;
            }
            return top + h;
        }

        private String placeText() {
            return in.placeText() == null ? "장소 기록 없음" : in.placeText();
        }

        private float middle(float top) throws IOException {
            float leftW = 140;
            float h = 215;
            roundRect(M, top, leftW, h, 7.5f, SURFACE);
            text("주요 시점", f.bold(), 7.5f, TERTIARY, M + 9, top + 14);
            float y = top + 32;
            for (ReportFacts.Step step : facts.steps()) {
                Color c = TONE.getOrDefault(step.tone(), INK);
                circle(M + 13, y - 3.2f, 3.4f, c);
                text(facts.stepText(step), f.extra(), 10, INK, M + 22, y);
                text(fit(step.title(), f.regular(), 8.25f, leftW - 30), f.regular(), 8.25f, SECONDARY, M + 22, y + 12);
                if (step.detail() != null) text(step.detail(), f.regular(), 8.25f, SECONDARY, M + 22, y + 23);
                y += step.detail() != null ? 38 : 28;
            }

            float gx = M + leftW + 10.5f;
            float gw = CW - leftW - 10.5f;
            strokeRoundRect(gx, top, gw, h, 7.5f, LINE);
            text("시간축 그래프 · 0초 = " + facts.record().getTrigger().getLabel() + " (" + facts.time() + ")", f.bold(), 7.5f, TERTIARY, gx + 8, top + 14);
            chart(gx + 8, top + 22, gw - 16, h - 44);
            float lx = gx + 8;
            for (String[] l : new String[][]{{"speed", "속도 km/h"}, {"brake", "브레이크"}, {"turn", "방향지시등"}, {"steer", "조향각 (가운데 = 정면)"}}) {
                fillRect(lx, top + h - 11, 10.5f, 2.25f, TONE.get(l[0]));
                text(l[1], f.regular(), 7.5f, SECONDARY, lx + 14, top + h - 8.5f);
                lx += 14 + width(l[1], f.regular(), 7.5f) + 10;
            }
            return top + h;
        }

        /** 위: 속도 선, 가운데: 브레이크·방향지시등 띠, 아래: 조향각 선. 0초에 세로 점선. */
        private void chart(float x, float top, float w, float h) throws IOException {
            List<Snapshot> snaps = facts.record().getSnapshots();
            double from = facts.windowStartSec();
            double to = Math.max(facts.windowEndSec(), from + 1);
            long t0 = facts.record().getTriggerAtMillis();
            float axisH = 11;
            float speedH = (h - axisH) * 0.52f;
            float laneH = 11;
            float steerTop = top + speedH + laneH * 2 + 12;
            float steerH = h - axisH - (steerTop - top);

            float maxSpeed = 10;
            for (Snapshot s : snaps) if (s.getSpeedKph() != null) maxSpeed = Math.max(maxSpeed, s.getSpeedKph());
            maxSpeed = (float) Math.ceil(maxSpeed * 1.1 / 10) * 10;

            fillRect(x, top + speedH, w, 0.5f, LINE);
            text(Math.round(maxSpeed) + "", f.regular(), 6.5f, TERTIARY, x, top + 6);

            // 띠: 브레이크, 방향지시등
            float brakeTop = top + speedH + 4;
            float turnTop = brakeTop + laneH + 2;
            fillRect(x, brakeTop, w, laneH, SUNK);
            fillRect(x, turnTop, w, laneH, SUNK);
            for (int i = 0; i < snaps.size(); i++) {
                Snapshot s = snaps.get(i);
                double a = (s.getAtMillis() - t0) / 1000.0;
                double b = i + 1 < snaps.size() ? (snaps.get(i + 1).getAtMillis() - t0) / 1000.0 : a + 0.1;
                float sx = sx(x, w, from, to, a);
                float ex = sx(x, w, from, to, b);
                if (Boolean.TRUE.equals(s.getBrake())) fillRect(sx, brakeTop, Math.max(ex - sx, 0.4f), laneH, TONE.get("brake"));
                if (s.getTurn() != null && s.getTurn() != TurnSignal.OFF) fillRect(sx, turnTop, Math.max(ex - sx, 0.4f), laneH, TONE.get("turn"));
            }
            text("브레이크", f.regular(), 6.5f, SECONDARY, x + 2, brakeTop + 8);
            text("방향지시등", f.regular(), 6.5f, SECONDARY, x + 2, turnTop + 8);

            // 조향각: 가운데가 정면, 위가 왼쪽
            float mid = steerTop + steerH / 2;
            fillRect(x, mid, w, 0.5f, LINE);
            float maxSteer = 90;
            for (Snapshot s : snaps) if (s.getSteeringDeg() != null) maxSteer = Math.max(maxSteer, Math.abs(s.getSteeringDeg()));
            float finalMaxSteer = maxSteer;
            polyline(snaps, s -> s.getSteeringDeg() == null ? null : mid + s.getSteeringDeg() / finalMaxSteer * (steerH / 2 - 1),
                    x, w, from, to, t0, TONE.get("steer"), 1.1f);
            text("왼쪽", f.regular(), 6.5f, TERTIARY, x + 2, steerTop + 6);
            text("오른쪽", f.regular(), 6.5f, TERTIARY, x + 2, steerTop + steerH);

            // 속도
            float finalMaxSpeed = maxSpeed;
            polyline(snaps, s -> s.getSpeedKph() == null ? null : top + speedH - s.getSpeedKph() / finalMaxSpeed * speedH,
                    x, w, from, to, t0, TONE.get("speed"), 1.5f);

            // 0초
            float zx = sx(x, w, from, to, 0);
            dashedV(zx, top, steerTop + steerH, IMPACT);
            float axisY = top + h - 1;
            double step = (to - from) > 60 ? 30 : (to - from) > 25 ? 10 : 5;
            for (double t = Math.ceil(from / step) * step; t <= to; t += step) {
                String label = t == 0 ? "0초" : (t > 0 ? "+" : "−") + Math.round(Math.abs(t));
                float tx = sx(x, w, from, to, t);
                float lw = width(label, f.regular(), 6.5f);
                text(label, t == 0 ? f.bold() : f.regular(), 6.5f, t == 0 ? IMPACT : TERTIARY, Math.min(Math.max(tx - lw / 2, x), x + w - lw), axisY);
            }
        }

        private float signals(float top) throws IOException {
            float h = 104;
            strokeRoundRect(M, top, CW, h, 7.5f, LINE);
            text("기록된 신호 (0초 기준)", f.bold(), 7.5f, TERTIARY, M + 9, top + 13);
            String[][] rows = {
                    {"speed", "속도", facts.speedText()},
                    {"brake", "브레이크", stepLabel("brake", "밟지 않음")},
                    {"turn", "방향지시등", stepLabel("turn", "켜지지 않음")},
                    {"steer", "조향각", facts.steeringText()},
                    {null, "전조등 · 와이퍼", orNone(facts.lights()) + " · " + orNone(facts.wiper())},
            };
            float y = top + 28;
            for (int i = 0; i < rows.length; i++) {
                if (rows[i][0] != null) circle(M + 12, y - 3, 3, TONE.get(rows[i][0]));
                text(rows[i][1], f.regular(), 8.6f, SECONDARY, M + 20, y);
                rightText(rows[i][2], f.bold(), 8.6f, INK, W - M - 9, y);
                if (i < rows.length - 1) fillRect(M + 9, y + 5, CW - 18, 0.5f, SUNK);
                y += 15.5f;
            }
            return top + h;
        }

        private String stepLabel(String tone, String none) {
            return facts.steps().stream().filter(s -> s.tone().equals(tone)).findFirst()
                    .map(s -> facts.stepText(s) + "부터 " + (tone.equals("brake") ? "밟음" : s.title().replace(" 켬", "")))
                    .orElse(none);
        }

        private void statement(float top) throws IOException {
            Statement st = in.statement();
            List<String> lines = st == null || st.text() == null ? List.of("진술 없음") : wrap("“" + st.text() + "”", f.bold(), 11, CW - 130, 3);
            float h = 26 + lines.size() * 15;
            roundRect(M, top, CW, h, 7.5f, SURFACE);
            text("진술 · 운전자 음성 (센서 기록 아님)", f.bold(), 7.5f, TERTIARY, M + 10.5f, top + 13);
            float y = top + 15;
            for (String line : lines) {
                y += 15;
                text(line, f.bold(), 11, st == null || st.text() == null ? TERTIARY : INK, M + 10.5f, y);
            }
            if (st != null && st.plate() != null) {
                float pw = width(st.plate(), f.extra(), 12) + 15;
                float px = W - M - 10.5f - pw;
                float py = top + h / 2 - 15;
                roundRect(px, py, pw, 22.5f, 4.5f, Color.WHITE);
                strokeRoundRect(px, py, pw, 22.5f, 4.5f, INK, 1.5f);
                text(st.plate(), f.extra(), 12, INK, px + 7.5f, py + 15.5f);
                String k = "상대 차량 번호 (진술)";
                text(k, f.regular(), 7.5f, TERTIARY, px + pw / 2 - width(k, f.regular(), 7.5f) / 2, py + 32);
            }
        }

        private void footer() throws IOException {
            float qr = 69;
            float top = H - 30 - qr;
            fillRect(M, top - 8, CW, 0.75f, LINE);
            qr(in.verifyUrl(), M, top, qr);
            float x = M + qr + 10.5f;
            float tw = CW - qr - 10.5f;
            text("원본 확인", f.extra(), 9.75f, INK, x, top + 10);
            float y = top + 10;
            for (String line : wrap("휴대폰 카메라로 QR을 찍으면 이 문서가 저장된 원본과 같은지 확인할 수 있습니다. 숫자 하나만 바뀌어도 \"원본과 다름\"으로 나옵니다.", f.regular(), 8.25f, tw, 2)) {
                y += 11.5f;
                text(line, f.regular(), 8.25f, SECONDARY, x, y);
            }
            y += 12.5f;
            text("원본 지문 SHA-256 " + shortHash(in.sha256()) + " · 서버 저장 시각 " + ISSUED.format(in.sealedAt().atZone(ReportFacts.ZONE)),
                    f.bold(), 7.9f, INK, x, y);
            for (String line : wrap("이 문서는 차량 신호 기록으로 자동 작성한 사실 기록이며 과실 판단이 아닙니다. 블랙박스·빌트인 캠 영상과 함께 내면 바깥 상황과 내 차 조작을 같이 보여 줄 수 있습니다.", f.regular(), 7.1f, tw, 2)) {
                y += 10;
                text(line, f.regular(), 7.1f, TERTIARY, x, y);
            }
        }

        // ---- 그리기 도구 ----

        private float sx(float x, float w, double from, double to, double t) {
            return (float) (x + (t - from) / (to - from) * w);
        }

        private interface YOf {
            Float y(Snapshot s);
        }

        private void polyline(List<Snapshot> snaps, YOf yOf, float x, float w, double from, double to, long t0, Color c, float lw) throws IOException {
            cs.setStrokingColor(c);
            cs.setLineWidth(lw);
            boolean drawing = false;
            for (Snapshot s : snaps) {
                Float y = yOf.y(s);
                if (y == null) {
                    drawing = false;
                    continue;
                }
                float px = sx(x, w, from, to, (s.getAtMillis() - t0) / 1000.0);
                if (drawing) cs.lineTo(px, H - y);
                else cs.moveTo(px, H - y);
                drawing = true;
            }
            cs.stroke();
        }

        private void dashedV(float x, float top, float bottom, Color c) throws IOException {
            cs.setStrokingColor(c);
            cs.setLineWidth(0.9f);
            cs.setLineDashPattern(new float[]{2.5f, 2}, 0);
            cs.moveTo(x, H - top);
            cs.lineTo(x, H - bottom);
            cs.stroke();
            cs.setLineDashPattern(new float[]{}, 0);
        }

        private void qr(String content, float x, float top, float size) throws IOException {
            BitMatrix m;
            try {
                m = new QRCodeWriter().encode(content, BarcodeFormat.QR_CODE, 0, 0, Map.of(EncodeHintType.MARGIN, 0));
            } catch (WriterException e) {
                throw new IOException(e);
            }
            float cell = size / m.getWidth();
            cs.setNonStrokingColor(INK);
            for (int r = 0; r < m.getHeight(); r++) {
                for (int c = 0; c < m.getWidth(); c++) {
                    if (m.get(c, r)) cs.addRect(x + c * cell, H - top - (r + 1) * cell, cell + 0.05f, cell + 0.05f);
                }
            }
            cs.fill();
        }

        private void text(String s, PDFont font, float size, Color c, float x, float baselineTop) throws IOException {
            cs.beginText();
            cs.setFont(font, size);
            cs.setNonStrokingColor(c);
            cs.newLineAtOffset(x, H - baselineTop);
            cs.showText(safe(s, font));
            cs.endText();
        }

        private void rightText(String s, PDFont font, float size, Color c, float right, float baselineTop) throws IOException {
            text(s, font, size, c, right - width(s, font, size), baselineTop);
        }

        private float width(String s, PDFont font, float size) throws IOException {
            return font.getStringWidth(safe(s, font)) / 1000 * size;
        }

        private void fillRect(float x, float top, float w, float h, Color c) throws IOException {
            cs.setNonStrokingColor(c);
            cs.addRect(x, H - top - h, w, h);
            cs.fill();
        }

        private void circle(float cx, float cyTop, float r, Color c) throws IOException {
            roundRect(cx - r, cyTop - r, r * 2, r * 2, r, c);
        }

        private void roundRect(float x, float top, float w, float h, float r, Color fill) throws IOException {
            cs.setNonStrokingColor(fill);
            roundPath(x, top, w, h, r);
            cs.fill();
        }

        private void strokeRoundRect(float x, float top, float w, float h, float r, Color c) throws IOException {
            strokeRoundRect(x, top, w, h, r, c, 0.75f);
        }

        private void strokeRoundRect(float x, float top, float w, float h, float r, Color c, float lw) throws IOException {
            cs.setStrokingColor(c);
            cs.setLineWidth(lw);
            roundPath(x, top, w, h, r);
            cs.stroke();
        }

        private void roundPath(float x, float top, float w, float h, float r) throws IOException {
            float k = 0.5523f * r;
            float b = H - top - h;
            float t = H - top;
            cs.moveTo(x + r, b);
            cs.lineTo(x + w - r, b);
            cs.curveTo(x + w - r + k, b, x + w, b + r - k, x + w, b + r);
            cs.lineTo(x + w, t - r);
            cs.curveTo(x + w, t - r + k, x + w - r + k, t, x + w - r, t);
            cs.lineTo(x + r, t);
            cs.curveTo(x + r - k, t, x, t - r + k, x, t - r);
            cs.lineTo(x, b + r);
            cs.curveTo(x, b + r - k, x + r - k, b, x + r, b);
            cs.closePath();
        }

        /** 단어 단위로 줄을 나누고, 한 단어가 넘치면 글자 단위로 자른다. [maxLines]를 넘으면 마지막 줄 끝을 "…"로. */
        private List<String> wrap(String s, PDFont font, float size, float maxW, int maxLines) throws IOException {
            List<String> lines = new ArrayList<>();
            StringBuilder cur = new StringBuilder();
            for (String word : s.split(" ")) {
                String tryLine = cur.isEmpty() ? word : cur + " " + word;
                if (width(tryLine, font, size) <= maxW) {
                    cur = new StringBuilder(tryLine);
                    continue;
                }
                if (!cur.isEmpty()) {
                    lines.add(cur.toString());
                    cur = new StringBuilder();
                }
                for (char ch : word.toCharArray()) {
                    if (width(cur.toString() + ch, font, size) > maxW) {
                        lines.add(cur.toString());
                        cur = new StringBuilder();
                    }
                    cur.append(ch);
                }
            }
            if (!cur.isEmpty()) lines.add(cur.toString());
            if (lines.size() > maxLines) {
                List<String> cut = new ArrayList<>(lines.subList(0, maxLines));
                cut.set(maxLines - 1, fit(cut.get(maxLines - 1) + " " + lines.get(maxLines), font, size, maxW));
                return cut;
            }
            return lines;
        }

        private String fit(String s, PDFont font, float size, float maxW) throws IOException {
            if (width(s, font, size) <= maxW) return s;
            String t = s;
            while (!t.isEmpty() && width(t + "…", font, size) > maxW) t = t.substring(0, t.length() - 1);
            return t + "…";
        }

        /** 폰트에 없는 글자는 비슷한 글자로 바꾸고, 그래도 없으면 뺀다(진술처럼 밖에서 온 글자도 있으므로). */
        private static String safe(String s, PDFont font) {
            StringBuilder out = new StringBuilder(s.length());
            s.codePoints().forEach(cp -> {
                String ch = new String(Character.toChars(cp));
                if (has(font, ch)) {
                    out.append(ch);
                    return;
                }
                String alt = switch (cp) {
                    case 0x2212, 0x2013, 0x2014 -> "-";
                    case 0x2026 -> "...";
                    case 0x201C, 0x201D -> "\"";
                    case 0x2018, 0x2019 -> "'";
                    case 0x00B7 -> "ㆍ";
                    default -> "";
                };
                if (!alt.isEmpty() && alt.codePoints().allMatch(a -> has(font, new String(Character.toChars(a))))) out.append(alt);
            });
            return out.toString();
        }

        private static boolean has(PDFont font, String ch) {
            try {
                font.encode(ch);
                return true;
            } catch (IllegalArgumentException | IOException e) {
                return false;
            }
        }

        private static String orNone(String s) {
            return s == null ? "기록 없음" : s;
        }

        private static String shortHash(String sha) {
            String h = sha.toLowerCase();
            return h.substring(0, 4) + " " + h.substring(4, 8) + " … " + h.substring(56, 60) + " " + h.substring(60, 64);
        }
    }
}
