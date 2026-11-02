"""스키마 + 시드를 SQLite에 올려 화면(06·04·05·09번)과 보관 규칙 기준으로 확인한다.

    python -I db/tools/check_db.py [10-report-sample-A.pdf]

서버 스키마는 MySQL 문법이라 AUTO_INCREMENT만 SQLite 형식으로 바꿔 올린다.
"""
import hashlib
import sqlite3
import sys
from datetime import datetime, timedelta, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
KST = timezone(timedelta(hours=9))
failures = []


def check(name, cond, detail=""):
    print(("  ok   " if cond else "  FAIL ") + name + (f"  ({detail})" if detail and not cond else ""))
    if not cond:
        failures.append(name)


def load(schema, seed, sqlite_fix=lambda s: s):
    db = sqlite3.connect(":memory:")
    db.execute("PRAGMA foreign_keys = ON")
    db.executescript(sqlite_fix((ROOT / schema).read_text(encoding="utf-8")))
    db.executescript((ROOT / seed).read_text(encoding="utf-8"))
    return db


def clock(epoch_ms, tz_min=540):
    return datetime.fromtimestamp(epoch_ms / 1000, timezone(timedelta(minutes=tz_min)))


def ms(s):
    return int(datetime.strptime(s, "%Y-%m-%d %H:%M:%S").replace(tzinfo=KST).timestamp() * 1000)


def tamper(pdf):
    i = len(pdf) // 2
    return pdf[:i] + bytes([pdf[i] ^ 0x01]) + pdf[i + 1:]


def main():
    pdf_path = Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT.parent / "designs/export/10-report-sample-A.pdf"

    print("차량 앱 DB")
    app = load("db/app/schema.sql", "db/app/seed.sql")
    app.row_factory = sqlite3.Row
    rows = app.execute("SELECT * FROM v_record_list ORDER BY anchor_at DESC").fetchall()
    by_time = {clock(r["anchor_at"]).strftime("%m.%d %H:%M:%S"): r for r in rows}

    check("06 목록 4건, 최신순 C·D·B·A", list(by_time) == ["11.07 09:15:33", "11.06 07:52:18", "11.05 14:10:52", "11.03 18:42:17"], list(by_time))
    A, B, C, D = (by_time[k] for k in ["11.03 18:42:17", "11.05 14:10:52", "11.07 09:15:33", "11.06 07:52:18"])
    check("0초 계기: A 충격 / B 기록해 / C 충격 / D 급정지",
          [A["anchor_type"], B["anchor_type"], C["anchor_type"], D["anchor_type"]] == ["impact", "command", "impact", "hardBrake"])
    check("구간 길이: A 43초 / B 3분 30초 / C 40초 / D 50초",
          [round(r["duration_ms"] / 1000, 1) for r in (A, B, C, D)] == [43.0, 210.0, 40.4, 50.0],
          [r["duration_ms"] for r in (A, B, C, D)])
    check("B 마커 2개, 합친 앞 기록 1건", B["marker_count"] == 2 and B["merged_count"] == 1)
    check("보관: A·B·C keepAuto, D expiring", [A["retention"], B["retention"], C["retention"], D["retention"]] == ["keepAuto"] * 3 + ["expiring"])
    now = ms("2026-11-07 09:30:00")
    days = -(-(D["delete_at"] - now) // 86_400_000)
    check("D '6일 뒤 삭제' (기준 11.07 09:30)", days == 6, days)
    check("'진술 미작성'은 C만", [r["statement_missing"] for r in (A, B, C, D)] == [0, 0, 1, 0])
    check("C 위치 확인 불가 (place NULL)", C["place_text"] is None)

    b_marks = app.execute("SELECT type, at, speed_from_kmh, speed_to_kmh FROM record_trigger WHERE record_id = ? ORDER BY at", (B["id"],)).fetchall()
    rel = [(m["at"] - B["anchor_at"]) // 1000 for m in b_marks]
    check("B 마커 위치: −1분 32초 급정지, 0초 기록해", rel == [-92, 0], rel)
    check("B 급제동 95→41km/h", (b_marks[0]["speed_from_kmh"], b_marks[0]["speed_to_kmh"]) == (95, 41),
          (b_marks[0]["speed_from_kmh"], b_marks[0]["speed_to_kmh"]))
    ev = app.execute("SELECT label, at FROM statement_event WHERE record_id = ? ORDER BY at", (B["id"],)).fetchall()
    check("B 진술 사건 2개(−2:10, −1:40)", [(e["at"] - B["anchor_at"]) // 1000 for e in ev] == [-130, -100])

    st = app.execute("SELECT kind, text, said_at FROM statement WHERE record_id = ? ORDER BY said_at", (A["id"],)).fetchall()
    check("A 진술 18:42:29 + 번호 12가 3456", clock(st[0]["said_at"]).strftime("%H:%M:%S") == "18:42:29" and st[1]["text"] == "12가 3456")

    bad = [r["record_id"] for r in app.execute("SELECT record_id, body, sha256 FROM record_payload")
           if hashlib.sha256(r["body"]).hexdigest() != r["sha256"]]
    check("원본 본문 SHA-256 = 저장된 지문 (4건)", not bad, bad)
    a_body = app.execute("SELECT body FROM record_payload WHERE record_id = ?", (A["id"],)).fetchone()["body"].decode()
    impact_line = next(l for l in a_body.splitlines() if l.startswith(str(A["anchor_at"]) + "|"))
    check("A 충격 순간 스냅샷: 12km/h, 브레이크, 우측, 핸들 0", impact_line.split("|")[1:5] == ["12.00", "true", "RIGHT", "0.00"], impact_line)
    check("C 봉인 대기(통신 없음)", app.execute("SELECT status FROM record_seal WHERE record_id = ?", (C["id"],)).fetchone()[0] == "queued")
    check("05 휴대폰에서 열림 18:46:02",
          clock(app.execute("SELECT opened_at FROM share").fetchone()[0]).strftime("%H:%M:%S") == "18:46:02")

    app.execute("DELETE FROM record WHERE id IN (SELECT id FROM v_record_list WHERE retention = 'expiring' AND delete_at <= ?)",
                (ms("2026-11-13 08:00:00"),))
    left = app.execute("SELECT COUNT(*) FROM record").fetchone()[0]
    orphans = app.execute("SELECT COUNT(*) FROM record_trigger WHERE record_id NOT IN (SELECT id FROM record)").fetchone()[0]
    check("11.13 삭제 작업: D만 지워지고 딸린 행도 함께", left == 3 and orphans == 0, (left, orphans))

    print("서버 DB")
    srv = load("db/server/schema.sql", "db/server/seed.sql",
               lambda s: s.replace("BIGINT       NOT NULL AUTO_INCREMENT PRIMARY KEY", "INTEGER PRIMARY KEY AUTOINCREMENT"))
    seals = dict(srv.execute("SELECT record_id, sha256 FROM t_record_seal").fetchall())
    check("봉인 4건 (A, B, 합쳐진 B 앞 기록, D), C는 아직 없음", len(seals) == 4 and C["id"] not in seals)
    body, rec = srv.execute("SELECT body, record_id FROM t_record_share").fetchone()
    check("공유 본문 SHA-256 = 봉인값 (업로드 검사 규칙)", hashlib.sha256(body).hexdigest() == seals[rec])

    def verify(document_no, data):
        row = srv.execute("SELECT pdf_sha256 FROM t_report WHERE document_no = ?", (document_no,)).fetchone()
        if row is None:
            return "NOT_FOUND"
        return "MATCH" if hashlib.sha256(data).hexdigest() == row[0] else "MISMATCH"

    pdf = pdf_path.read_bytes()
    check("09 원본 PDF → MATCH", verify("SG-20261103-184217", pdf) == "MATCH")
    check("09 한 바이트 고친 PDF → MISMATCH", verify("SG-20261103-184217", tamper(pdf)) == "MISMATCH")
    check("09 없는 문서 번호 → NOT_FOUND", verify("SG-20991231-000000", pdf) == "NOT_FOUND")
    bad_ts = srv.execute("SELECT COUNT(*) FROM t_record_seal WHERE sealed_at NOT GLOB '[0-9][0-9][0-9][0-9]-[0-9][0-9]-[0-9][0-9] *'").fetchone()[0]
    check("서버 시각 형식 YYYY-MM-DD HH:MM:SS.fff (UTC)", bad_ts == 0, bad_ts)
    a_sealed = srv.execute("SELECT sealed_at FROM t_record_seal WHERE record_id = ?", (A["id"],)).fetchone()[0]
    check("A 봉인 시각 = 기록 닫힘(18:42:30 KST) 뒤", a_sealed == "2026-11-03 09:42:31.500", a_sealed)
    check("확인 이력 MATCH·MISMATCH 1건씩",
          sorted(r[0] for r in srv.execute("SELECT result FROM t_verify_log")) == ["MATCH", "MISMATCH"])

    print(f"\n{'실패 ' + str(len(failures)) + '건: ' + ', '.join(failures) if failures else '모두 통과'}")
    sys.exit(1 if failures else 0)


if __name__ == "__main__":
    main()
