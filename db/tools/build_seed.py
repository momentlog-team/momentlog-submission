"""샘플 기록 A·B·C(replay 데이터)와 예시 D로 db/app/seed.sql, db/server/seed.sql을 만든다.

    python -I db/tools/build_seed.py [replay 폴더] [10-report-sample-A.pdf]

기본 경로는 디자인 프로젝트가 저장소 바로 위 폴더에 있다고 본다(../replay, ../designs/export/...).
원본 본문은 core RecordHasher v2와 같은 형식(100ms 스냅샷 줄)으로 만들고, SHA-256은 그 바이트로 계산한다.
"""
import hashlib
import json
import sys
import uuid
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KST = timezone(timedelta(hours=9))
NS = uuid.UUID("6f0b1d2a-0000-4000-8000-6d6f6d656e74")  # 시드 id 고정용
FORMAT_VERSION = 2
DAY = 86_400_000


def rid(name):
    return str(uuid.uuid5(NS, name))


def ms(dt):
    return int(dt.timestamp() * 1000)


def kst(s):
    return datetime.strptime(s, "%Y-%m-%d %H:%M:%S").replace(tzinfo=KST)


def utc_sql(epoch_ms):
    return datetime.fromtimestamp(epoch_ms / 1000, timezone.utc).strftime("%Y-%m-%d %H:%M:%S.%f")[:-3]


def q(v):
    if v is None:
        return "NULL"
    if isinstance(v, bool):
        return "1" if v else "0"
    if isinstance(v, (int, float)):
        return repr(v)
    if isinstance(v, bytes):
        return "X'" + v.hex() + "'"
    return "'" + str(v).replace("'", "''") + "'"


def insert(table, rows):
    if not rows:
        return ""
    cols = list(rows[0])
    values = ",\n".join("  (" + ", ".join(q(r[c]) for c in cols) + ")" for r in rows)
    return f"INSERT INTO {table} ({', '.join(cols)}) VALUES\n{values};\n\n"


# ---- 신호 트랙 → 100ms 스냅샷 → RecordHasher v2 본문 ----

def hold(track, t):
    """계단식: t 이하의 마지막 값."""
    cur = None
    for item in track:
        if item[0] > t + 1e-9:
            break
        cur = item[1:]
    return cur


def snapshots(tracks, start_ms, t_from, t_to):
    out = []
    n = round((t_to - t_from) * 10)
    for i in range(n + 1):
        t = round(t_from + i / 10, 3)
        speed = hold(tracks["speedKmh"], t)
        brake = hold(tracks["brakePct"], t)
        turn = hold(tracks["turnSignal"], t)
        steer = hold(tracks["steeringDeg"], t)
        light = hold(tracks["headlight"], t)
        wiper = hold(tracks["wiper"], t)
        loc = hold(tracks["location"], t)
        out.append({
            "at": start_ms + round(t * 1000),
            "speed": speed[0] if speed else None,
            "brake": (brake[0] > 0) if brake else None,
            "turn": turn[0].upper() if turn else None,
            "steer": steer[0] if steer else None,
            "lowBeam": (light[0] in ("low", "high")) if light else None,
            "highBeam": (light[0] == "high") if light else None,
            "wiper": (wiper[0] != "off") if wiper else None,
            "lat": loc[0] if loc and loc[0] is not None else None,
            "lng": loc[1] if loc and loc[0] is not None else None,
        })
    return out


def canonical(record_id, trigger, trigger_at, events, merged, snaps):
    num = lambda v: "" if v is None else f"{v:.2f}"
    coord = lambda v: "" if v is None else f"{v:.6f}"
    b = lambda v: "" if v is None else ("true" if v else "false")
    lines = [f"v={FORMAT_VERSION}", f"id={record_id}", f"trigger={trigger}", f"triggerAt={trigger_at}"]
    lines += [f"event={k}@{at}" for k, at in events]
    lines += [f"merged={m}" for m in merged]
    for s in snaps:
        lines.append("|".join([str(s["at"]), num(s["speed"]), b(s["brake"]), s["turn"] or "", num(s["steer"]),
                               b(s["lowBeam"]), b(s["highBeam"]), b(s["wiper"]), coord(s["lat"]), coord(s["lng"])]))
    return ("\n".join(lines) + "\n").encode("utf-8")


# core Trigger 이름. IMPACT는 기획서 v2의 충격 감지를 위한 제안 값(지금 core에는 없음, README 참고).
CORE_TRIGGER = {"impact": "IMPACT", "hardBrake": "HARD_STOP", "airbag": "AIRBAG", "command": "VOICE"}


def stopped_after(tracks, t0, t1):
    for t, v in tracks["speedKmh"]:
        if t0 < t <= t1 and v < 1.0:
            return t
    return None


def peak_g(tracks, t):
    vals = [((x * x + y * y) ** 0.5) / 9.81 for tt, x, y, z in tracks["accel"] if abs(tt - t) <= 0.5]
    return round(max(vals), 2)


def build(replay_dir, pdf_path):
    load = lambda name: json.loads((replay_dir / f"{name}.json").read_text(encoding="utf-8"))
    A, B, C = load("A_intersection_rear_end"), load("B_road_rage"), load("C_parking_pillar")
    now = kst("2026-11-07 09:30:00")  # 시안 06번 기준 시각 (D가 "6일 뒤 삭제")

    app = {k: [] for k in ["vehicle_signal_support", "record", "record_trigger", "record_merge", "record_payload",
                           "statement", "statement_event", "record_seal", "share", "signal_health_daily", "notice_log"]}
    srv = {k: [] for k in ["t_device", "t_record_seal", "t_record_share", "t_report", "t_verify_log"]}
    device_id = rid("device-demo")
    srv["t_device"].append({"device_id": device_id, "token_sha256": hashlib.sha256(b"demo-device-token").hexdigest(),
                            "plan": "free", "created_at": utc_sql(ms(kst("2026-11-01 08:00:00"))),
                            "last_seen_at": utc_sql(ms(now))})

    def add_record(key, data, *, anchor_t, events, window, focus_t, headline, detail, statement_state,
                   merged=(), place=None, triggers_extra=None, sealed=True):
        start = ms(datetime.fromisoformat(data["startTime"]))
        at = lambda t: start + round(t * 1000)
        tr = data["tracks"]
        record_id = rid(key)
        anchor_type = next(k for t, k in events if t == anchor_t)
        snaps = snapshots(tr, start, *window)
        body = canonical(record_id, CORE_TRIGGER[anchor_type], at(anchor_t),
                         [(CORE_TRIGGER[k], at(t)) for t, k in events], list(merged), snaps)
        sha = hashlib.sha256(body).hexdigest()
        stop = stopped_after(tr, anchor_t, window[1])
        loc = hold(tr["location"], anchor_t)
        has_loc = loc is not None and loc[0] is not None
        saved_at = at(window[1])
        app["record"].append({
            "id": record_id, "state": "saved", "is_demo": 1, "tz_offset_min": 540,
            "window_start_at": at(window[0]), "window_end_at": at(window[1]),
            "stopped_at": at(stop) if stop is not None else None, "focus_at": at(focus_t),
            "place_text": place if place is not None else (data["place"] if has_loc else None),
            "lat": loc[0] if has_loc else None, "lng": loc[1] if has_loc else None,
            "heading_deg": loc[2] if has_loc else None,
            "headline": headline, "detail": detail, "statement_state": statement_state, "user_kept_at": None,
            "created_at": at(min(t for t, _ in events)), "saved_at": saved_at,
        })
        for t, k in events:
            row = {"record_id": record_id, "type": k, "at": at(t), "is_anchor": int(t == anchor_t),
                   "impact_source": None, "impact_direction": None, "peak_g": None,
                   "speed_from_kmh": None, "speed_to_kmh": None, "command_input": None, "while_parked": None}
            row.update((triggers_extra or {}).get(t, {}))
            app["record_trigger"].append(row)
        for m in merged:
            app["record_merge"].append({"record_id": record_id, "merged_record_id": m})
        app["record_payload"].append({"record_id": record_id, "format_version": FORMAT_VERSION, "body": body,
                                      "sha256": sha, "created_at": saved_at})
        sealed_at = saved_at + 1_500 if sealed else None
        app["record_seal"].append({"record_id": record_id, "sha256": sha, "format_version": FORMAT_VERSION,
                                   "status": "sealed" if sealed else "queued", "attempts": 1 if sealed else 2,
                                   "last_attempt_at": sealed_at if sealed else saved_at + 60_000,
                                   "last_error": None if sealed else "network unavailable",
                                   "server_sealed_at": sealed_at})
        if sealed:
            srv["t_record_seal"].append({"record_id": record_id, "device_id": device_id, "sha256": sha,
                                         "format_version": FORMAT_VERSION, "device_created_at": utc_sql(saved_at),
                                         "sealed_at": utc_sql(sealed_at)})
        return record_id, body, at

    def add_statements(record_id, at, voice):
        ids = {}
        for t, kind, text in voice:
            if kind in ("answer", "plate"):
                app["statement"].append({"record_id": record_id, "kind": kind, "text": text, "said_at": at(t), "edited": 0})
                ids[kind] = len(app["statement"])
        return ids

    # A. 교차로 후방 추돌 — 충격 감지(가속도), 진술 완료
    a_id, a_body, a_at = add_record(
        "A", A, anchor_t=60.0, events=[(60.0, "impact")], window=(30.0, 73.0), focus_t=60.0,
        headline="충돌 6.2초 전부터 우측 방향지시등이 켜져 있었어요",
        detail="4.1초 전부터 브레이크 · 충돌 때 12km/h · 핸들 정면(0°)", statement_state="DONE",
        triggers_extra={60.0: {"impact_source": "accel", "impact_direction": "rear", "peak_g": peak_g(A["tracks"], 60.0)}})
    a_stmt = add_statements(a_id, a_at, A["voice"])

    # B. 보복운전 — 급정지 자동 기록(t=108, 구간 78~128)을 "기록해"(t=200)가 합침 → 새 id, 앞 기록은 삭제
    b_tr = B["tracks"]
    b_start = ms(datetime.fromisoformat(B["startTime"]))
    b_old = rid("B-auto")
    old_body = canonical(b_old, "HARD_STOP", b_start + 108_000, [("HARD_STOP", b_start + 108_000)], [],
                         snapshots(b_tr, b_start, 78.0, 128.0))
    srv["t_record_seal"].append({"record_id": b_old, "device_id": device_id,
                                 "sha256": hashlib.sha256(old_body).hexdigest(), "format_version": FORMAT_VERSION,
                                 "device_created_at": utc_sql(b_start + 128_000),
                                 "sealed_at": utc_sql(b_start + 129_500)})
    v_from = max(v for t, v in b_tr["speedKmh"] if 106.5 <= t <= 108.0)
    v_to = min(v for t, v in b_tr["speedKmh"] if 108.0 <= t <= 112.0)
    b_id, b_body, b_at = add_record(
        "B", B, anchor_t=200.0, events=[(108.0, "hardBrake"), (200.0, "command")], window=(20.0, 230.0), focus_t=108.0,
        headline="기록 요청 1분 32초 전, 95→41km/h로 급제동했어요",
        detail="−2:10 뒤차 근접·상향등(진술) · −1:40 끼어듦(진술)", statement_state="DONE", merged=[b_old],
        triggers_extra={108.0: {"speed_from_kmh": round(v_from), "speed_to_kmh": round(v_to)},
                        200.0: {"command_input": "voice", "while_parked": 0}})
    b_stmt = add_statements(b_id, b_at, B["voice"])
    for offset, label in [(-130.0, "뒤차 근접·상향등"), (-100.0, "앞으로 끼어듦")]:
        app["statement_event"].append({"record_id": b_id, "statement_id": b_stmt["answer"],
                                       "at": b_at(200.0 + offset), "label": label})

    # C. 주차장 기둥 — 충격 감지(옆 방향 1.7g), 실내라 위치 없음, "나중에", 통신 없어 봉인 대기
    add_record(
        "C", C, anchor_t=40.0, events=[(40.0, "impact")], window=(10.0, 50.4), focus_t=40.0,
        headline="충돌 때 5km/h, 핸들 왼쪽 끝", detail="실내 주차장 · 위치 확인 불가", statement_state="LATER",
        triggers_extra={40.0: {"impact_source": "accel", "impact_direction": "frontLeft", "peak_g": peak_g(C["tracks"], 40.0)}},
        sealed=False)

    # D. 급정지만 (시안 06번 예시, replay 없음) — 안 멈춰서 감지 +20초, 질문 전(NONE), 7일 뒤 삭제
    d_start = kst("2026-11-06 07:51:48")
    d_tracks = {
        "speedKmh": [[i / 10, 62.0 if i < 299 else max(38.0, 62.0 - (i - 299) * 2.4)] for i in range(0, 501)],
        "brakePct": [[0.0, 0], [29.8, 60], [31.5, 0]], "turnSignal": [[0.0, "off"]], "steeringDeg": [[0.0, 0]],
        "headlight": [[0.0, "off"]], "wiper": [[0.0, "off"]], "location": [[0.0, None]],
    }
    d_data = {"startTime": d_start.isoformat(), "tracks": d_tracks, "place": "서울 서초구 반포대로 부근 (예시)"}
    add_record("D", d_data, anchor_t=30.0, events=[(30.0, "hardBrake")], window=(0.0, 50.0), focus_t=30.0,
               headline="급정지만 감지됐어요", detail="62→38km/h · 차가 멈추지 않아 감지 뒤 20초까지",
               statement_state="NONE", place="서울 서초구 반포대로 부근 (예시)",
               triggers_extra={30.0: {"speed_from_kmh": 62, "speed_to_kmh": 38}})

    # 차종 지원 · 월간 점검 · 안내
    checked = ms(kst("2026-11-01 08:00:00"))
    for sig in ["speed", "brake", "turnSignal", "steering", "gear", "headlight", "wiper", "airbag", "accel", "location"]:
        app["vehicle_signal_support"].append({"signal": sig, "supported": 1, "checked_at": checked})
    app["vehicle_signal_support"].append({"signal": "impactSignal", "supported": 0, "checked_at": checked})
    for day in range(1, 8):
        for sig in ["speed", "brake", "turnSignal", "steering"]:
            app["signal_health_daily"].append({"day": f"2026-11-{day:02d}", "signal": sig, "received": 1})
    app["notice_log"].append({"kind": "monthly", "period": "2026-11", "shown_at": ms(kst("2026-11-01 08:12:40"))})

    # A를 휴대폰으로 받음: 공유 → PDF 발급 → 원본 확인 2번(원본 / 숫자 하나 고친 가짜)
    share_id = rid("share-A")
    token = "demo-token-A"
    created = ms(kst("2026-11-03 18:45:40"))
    app["share"].append({"id": share_id, "record_id": a_id, "url": f"https://doc.momentlog.invalid/d/{token}",
                         "document_no": "SG-20261103-184217", "created_at": created,
                         "expires_at": created + 30 * DAY, "opened_at": ms(kst("2026-11-03 18:46:02"))})
    statement_json = json.dumps({"answer": A["voice"][0][2], "plate": A["voice"][1][2],
                                 "saidAt": "2026-11-03T18:42:29+09:00", "events": []}, ensure_ascii=False)
    srv["t_record_share"].append({"share_id": share_id, "token_sha256": hashlib.sha256(token.encode()).hexdigest(),
                                  "record_id": a_id, "body": a_body, "statement_json": statement_json,
                                  "place_text": A["place"], "created_at": utc_sql(created),
                                  "opened_at": utc_sql(ms(kst("2026-11-03 18:46:02"))),
                                  "expires_at": utc_sql(created + 30 * DAY)})
    pdf = pdf_path.read_bytes()
    pdf_sha = hashlib.sha256(pdf).hexdigest()
    report_id = rid("report-A")
    srv["t_report"].append({"report_id": report_id, "document_no": "SG-20261103-184217", "share_id": share_id,
                            "record_id": a_id, "pdf_sha256": pdf_sha, "file_name": "순간기록_20261103_184217.pdf",
                            "byte_size": len(pdf), "page_count": 1, "storage_key": f"reports/2026/11/{report_id}.pdf",
                            "issued_at": utc_sql(ms(kst("2026-11-03 19:02:40")))})
    srv["t_verify_log"] += [
        {"document_no": "SG-20261103-184217", "submitted_sha256": pdf_sha, "result": "MATCH",
         "verified_at": utc_sql(ms(kst("2026-11-04 10:20:05")))},
        {"document_no": "SG-20261103-184217", "submitted_sha256": hashlib.sha256(tamper(pdf)).hexdigest(),
         "result": "MISMATCH", "verified_at": utc_sql(ms(kst("2026-11-04 10:21:44")))},
    ]
    return app, srv


def tamper(pdf: bytes) -> bytes:
    """시연용 가짜: 파일 가운데 한 바이트를 바꾼다(실제 시연은 12 → 21km/h를 고친 PDF)."""
    i = len(pdf) // 2
    return pdf[:i] + bytes([pdf[i] ^ 0x01]) + pdf[i + 1:]


def main():
    replay_dir = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT.parent / "replay"
    pdf_path = Path(sys.argv[2]) if len(sys.argv) > 2 else ROOT.parent / "designs/export/10-report-sample-A.pdf"
    app, srv = build(replay_dir, pdf_path)
    head = "-- build_seed.py가 만든 파일. 직접 고치지 말고 스크립트를 고쳐 다시 만든다.\n-- 샘플 A·B·C(replay)와 예시 D. 실명·실제 번호판 아님.\n\n"
    (ROOT / "db/app/seed.sql").write_text(head + "".join(insert(t, rows) for t, rows in app.items()), encoding="utf-8")
    (ROOT / "db/server/seed.sql").write_text(head + "".join(insert(t, rows) for t, rows in srv.items()), encoding="utf-8")
    print("app records:", len(app["record"]), "server seals:", len(srv["t_record_seal"]))


if __name__ == "__main__":
    main()
