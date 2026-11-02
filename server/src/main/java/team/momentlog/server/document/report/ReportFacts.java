package team.momentlog.server.document.report;

import team.momentlog.core.IncidentNarrator;
import team.momentlog.core.IncidentRecord;
import team.momentlog.core.Snapshot;
import team.momentlog.core.Trigger;
import team.momentlog.core.TurnSignal;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

/**
 * 사고 정리 문서(PDF·모바일 페이지)에 들어갈 값. 봉인된 원본 기록에서만 뽑는다.
 * 경위 문장은 차량 화면과 같도록 core [IncidentNarrator]를 그대로 쓰고, 여기서는 배치용 숫자만 계산한다.
 */
public record ReportFacts(
        IncidentRecord record,
        ZonedDateTime triggerAt,
        String pill,
        String lead,
        List<String> details,
        double windowStartSec,
        double windowEndSec,
        List<Step> steps,
        Float maxSpeedBefore,
        Float speedAtTrigger,
        Float speedAfter,
        Float steeringAtTrigger,
        String lights,
        String wiper,
        Double latitude,
        Double longitude
) {

    public static final ZoneId ZONE = ZoneId.of("Asia/Seoul");
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyy.MM.dd (E)", Locale.KOREAN);
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm:ss");

    /** 시간축 위 한 시점. [tone]은 그래프·점 색 이름(turn, brake, impact). */
    public record Step(double atSec, String title, String detail, String tone) {
    }

    public static ReportFacts of(IncidentRecord record) {
        long t0 = record.getTriggerAtMillis();
        List<Snapshot> snaps = record.getSnapshots();
        List<Snapshot> before = snaps.stream().filter(s -> s.getAtMillis() <= t0).toList();
        Snapshot at = before.isEmpty() ? null : before.get(before.size() - 1);
        String label = record.getTrigger().getLabel();

        List<String> lines = new ArrayList<>(new IncidentNarrator(true, 5f).narrate(record));
        String headline = lines.isEmpty() ? label + " 기록" : lines.remove(0);
        String lead = pickLead(lines, headline);
        lines.remove(lead);

        List<Step> steps = new ArrayList<>();
        Snapshot turnStart = runStart(before, s -> s.getTurn() != null && s.getTurn() != TurnSignal.OFF, t0);
        if (turnStart != null) {
            String side = turnStart.getTurn() == TurnSignal.LEFT ? "좌측" : "우측";
            steps.add(new Step(rel(turnStart, t0), side + " 방향지시등 켬", null, "turn"));
        }
        Snapshot brakeStart = runStart(before, s -> Boolean.TRUE.equals(s.getBrake()), t0);
        if (brakeStart != null) {
            steps.add(new Step(rel(brakeStart, t0), "브레이크 시작", kph(brakeStart.getSpeedKph()), "brake"));
        }
        steps.add(new Step(0, label.endsWith("감지") ? label : label + " 감지", at == null ? null : kph(at.getSpeedKph()), "impact"));
        steps.sort((a, b) -> Double.compare(a.atSec(), b.atSec()));

        Float maxBefore = before.stream().map(Snapshot::getSpeedKph).filter(v -> v != null).max(Float::compare).orElse(null);
        Snapshot last = snaps.isEmpty() ? null : snaps.get(snaps.size() - 1);
        Snapshot located = before.stream().filter(s -> s.getLatitude() != null && s.getLongitude() != null)
                .reduce((a, b) -> b).orElse(null);

        return new ReportFacts(
                record,
                Instant.ofEpochMilli(t0).atZone(ZONE),
                pill(record.getTrigger()),
                lead,
                lines,
                snaps.isEmpty() ? 0 : rel(snaps.get(0), t0),
                last == null ? 0 : rel(last, t0),
                steps,
                maxBefore,
                at == null ? null : at.getSpeedKph(),
                last == null || last == at ? null : last.getSpeedKph(),
                at == null ? null : at.getSteeringDeg(),
                at == null ? null : lights(at),
                at == null || at.getWiperOn() == null ? null : (at.getWiperOn() ? "작동 중" : "꺼짐"),
                located == null ? null : located.getLatitude(),
                located == null ? null : located.getLongitude());
    }

    public String date() {
        return DATE.format(triggerAt);
    }

    public String time() {
        return TIME.format(triggerAt);
    }

    /** 문서 번호와 파일 이름에 쓰는 기준 시각. 같은 기록이면 항상 같다. */
    public String stamp() {
        return DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss").format(triggerAt);
    }

    public String windowText() {
        return String.format(Locale.ROOT, "−%d초 ~ +%d초", Math.round(-windowStartSec), Math.round(windowEndSec));
    }

    public long windowSeconds() {
        return Math.round(windowEndSec - windowStartSec);
    }

    public String steeringText() {
        if (steeringAtTrigger == null) return "기록 없음";
        int deg = Math.round(steeringAtTrigger);
        if (Math.abs(steeringAtTrigger) < 5f) return deg + "° (정면)";
        return (steeringAtTrigger < 0 ? "왼쪽 " : "오른쪽 ") + Math.abs(deg) + "°";
    }

    public String speedText() {
        if (speedAtTrigger == null) return "기록 없음";
        String s = (maxSpeedBefore == null ? "" : "최고 " + Math.round(maxSpeedBefore) + " → ") + Math.round(speedAtTrigger) + "km/h";
        return speedAfter == null ? s : s + " (직후 " + Math.round(speedAfter) + "km/h)";
    }

    public String stepText(Step step) {
        if (step.atSec() == 0) return "0초";
        return String.format(Locale.ROOT, "%s%.1f초", step.atSec() < 0 ? "−" : "+", Math.abs(step.atSec()));
    }

    public String coordText() {
        if (latitude == null || longitude == null) return null;
        return String.format(Locale.ROOT, "%.5f, %.5f", latitude, longitude);
    }

    private static String pill(Trigger trigger) {
        return switch (trigger) {
            case HARD_STOP -> "급정지 감지 · 자동 저장";
            case IMPACT -> "충격 감지 · 자동 저장";
            case VOICE -> "운전자 요청 · 음성 저장";
            case AIRBAG -> "에어백 전개 · 자동 저장";
        };
    }

    /** 경위 한 줄: 방향지시등 → 브레이크 → 속도 순으로 사실이 있는 첫 문장. 없으면 저장 안내 문장. */
    private static String pickLead(List<String> lines, String headline) {
        for (String key : List.of("방향지시등이 켜져", "브레이크를 밟고", "최고 속도")) {
            for (String line : lines) {
                if (line.contains(key)) return line;
            }
        }
        return headline;
    }

    /** 기준 시점 직전 3초 안에 [on]이던 마지막 시점에서 거슬러 올라가, 계속 [on]이던 구간의 시작. 없으면 null. */
    private static Snapshot runStart(List<Snapshot> before, Predicate<Snapshot> on, long t0) {
        int last = -1;
        for (int i = before.size() - 1; i >= 0 && t0 - before.get(i).getAtMillis() <= 3_000; i--) {
            if (on.test(before.get(i))) {
                last = i;
                break;
            }
        }
        if (last < 0) return null;
        int start = last;
        while (start > 0 && on.test(before.get(start - 1))) start--;
        return before.get(start);
    }

    private static String lights(Snapshot s) {
        if (Boolean.TRUE.equals(s.getHighBeam())) return "상향등 켜짐";
        if (Boolean.TRUE.equals(s.getLowBeam())) return "전조등 켜짐";
        if (s.getLowBeam() == null && s.getHighBeam() == null) return null;
        return "전조등 꺼짐";
    }

    private static double rel(Snapshot s, long t0) {
        return (s.getAtMillis() - t0) / 1000.0;
    }

    private static String kph(Float v) {
        return v == null ? null : Math.round(v) + "km/h";
    }
}
