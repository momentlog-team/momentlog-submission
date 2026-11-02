package team.momentlog.server.document.web;

import team.momentlog.core.Snapshot;
import team.momentlog.core.TurnSignal;
import team.momentlog.server.document.application.ReportService;
import team.momentlog.server.document.report.ReportFacts;
import team.momentlog.server.document.report.Statement;

import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

import static org.springframework.web.util.HtmlUtils.htmlEscape;

/**
 * QR로 여는 모바일 페이지(디자인 08·09). 앱 설치 없이 휴대폰 기본 브라우저에서 열린다.
 * 차량 화면과 같은 데이터 색을 쓰고, 기준 폭 390px·본문 15px.
 */
final class DocumentPages {

    private static final DateTimeFormatter SEALED = DateTimeFormatter.ofPattern("yyyy.MM.dd HH:mm:ss");
    private static final DateTimeFormatter RECORDED = DateTimeFormatter.ofPattern("yyyy.MM.dd (E) HH:mm:ss", Locale.KOREAN);

    private DocumentPages() {
    }

    /** 08 다운로드 페이지. */
    static String share(String token, ReportService.SharedDocument doc) {
        ReportFacts f = doc.facts();
        Statement st = doc.statement();
        StringBuilder steps = new StringBuilder();
        for (ReportFacts.Step s : f.steps()) {
            steps.append("<li style=\"--dot:var(--").append(s.tone()).append(")\"><i></i><div><b>").append(f.stepText(s))
                    .append("</b><span>").append(e(s.title())).append(s.detail() == null ? "" : " · " + e(s.detail())).append("</span></div></li>");
        }
        String said = st == null || st.text() == null
                ? "<div class=\"said none\">진술 없음</div>"
                : "<div class=\"said\">“" + e(st.text()) + "”</div>";
        String plate = st == null || st.plate() == null ? ""
                : "<span class=\"plate\">" + e(st.plate()) + "</span><span class=\"plate-k\">상대 차량 번호 (진술)</span>";
        String place = doc.share().getPlaceText() != null ? e(doc.share().getPlaceText())
                : f.coordText() != null ? f.coordText() : "장소 기록 없음";
        String verify = doc.report() == null
                ? "<div class=\"card verify\"><span class=\"sh\">✓</span><div><b>원본 지문이 서버에 저장돼 있어요</b><span>저장 시각 "
                  + SEALED.format(doc.seal().getSealedAt().atZone(ReportFacts.ZONE)) + " · PDF를 받으면 원본 확인도 할 수 있어요</span></div></div>"
                : "<a class=\"card verify\" href=\"/v/" + e(doc.report().getDocumentNo()) + "\"><span class=\"sh\">✓</span><div><b>원본과 같은지 확인할 수 있어요</b><span>저장 시각 "
                  + SEALED.format(doc.seal().getSealedAt().atZone(ReportFacts.ZONE)) + " · 서버 기록</span></div><span class=\"go\">›</span></a>";

        return page("사고 정리", """
                <section class="hero">
                  <span class="pill">%s</span>
                  <div class="lead">%s</div>
                  <div class="when"><span class="clock">%s</span><span>%s</span></div>
                  <div class="place">%s</div>
                </section>
                <div class="stack">
                  <div class="card"><div class="k">주요 시점</div><ul class="steps">%s</ul></div>
                  <div class="card chart"><div class="k">시간축 그래프 · 저장 구간 %d초</div>%s
                    <div class="legend"><span><i style="background:var(--speed)"></i>속도</span><span><i style="background:var(--brake)"></i>브레이크</span><span><i style="background:var(--turn)"></i>방향지시등</span><span><i style="background:var(--steer)"></i>조향각</span></div></div>
                  <div class="card facts">
                    <div><div class="k">속도</div><div class="v">%s</div></div>
                    <div><div class="k">조향각</div><div class="v">%s</div></div>
                    <div><div class="k">전조등</div><div class="v">%s</div></div>
                    <div><div class="k">와이퍼</div><div class="v">%s</div></div></div>
                  <div class="card"><div class="k">진술 · 운전자 (센서 기록 아님)</div>%s%s</div>
                  %s
                </div>
                <p class="foot-note">이 문서는 운전자가 직접 내는 참고 자료예요. 블랙박스·빌트인 캠 영상과 함께 내면 바깥 상황과 내 차 조작을 같이 보여 줄 수 있어요.</p>
                <div class="sticky"><a class="btn primary" href="/r/%s/report.pdf">PDF 받기</a><button type="button" class="btn icon" aria-label="링크 공유" onclick="share()">⤴</button></div>
                <div class="toast" id="toast" role="status" hidden></div>
                <script>
                function toast(t){const el=document.getElementById('toast');el.textContent=t;el.hidden=false;setTimeout(()=>el.hidden=true,2200);}
                async function share(){const url=location.href;
                  if(navigator.share){try{await navigator.share({title:'사고 정리 문서',url});return;}catch(e){}}
                  try{await navigator.clipboard.writeText(url);toast('링크를 복사했어요');}catch(e){prompt('이 링크를 복사하세요',url);}}
                </script>
                """.formatted(e(f.pill()), e(f.lead()), f.time(), f.date(), place, steps, f.windowSeconds(), chart(f),
                e(f.speedText()), e(f.steeringText()), e(orNone(f.lights())), e(orNone(f.wiper())), said, plate, verify, e(token)));
    }

    /** 09 원본 확인. PDF는 휴대폰 안에서 지문만 계산하고 파일은 보내지 않는다. */
    static String verify(String documentNo, ReportService.VerifyTarget target) {
        String rec = target == null
                ? "<div class=\"rec\">문서 번호 " + e(documentNo) + "<small>서버에 이 번호로 발급된 문서가 없어요</small></div>"
                : "<div class=\"rec\">" + e(target.report().getRecordLead() == null ? "문서 번호 " + documentNo : target.report().getRecordLead())
                  + "<small>" + (target.report().getRecordAt() == null ? "" : RECORDED.format(target.report().getRecordAt().atZone(ReportFacts.ZONE)))
                  + (target.report().getPlaceText() == null ? "" : " · " + e(target.report().getPlaceText())) + "</small></div>";
        String sealed = target == null ? "-" : SEALED.format(target.seal().getSealedAt().atZone(ReportFacts.ZONE)) + " (서버 시각)";
        String original = target == null ? "" : target.report().getPdfSha256();
        return page("원본 확인", """
                <div id="before">
                  <div class="intro"><div class="k">확인할 기록</div>%s
                    <h2>받은 PDF가 원본과 같은지 확인해요</h2>
                    <p>저장 순간에 남긴 원본 지문과 비교해요. 숫자 하나만 바뀌어도 알 수 있어요.</p></div>
                  <label class="drop"><input type="file" id="file" accept="application/pdf,.pdf" hidden><b>PDF 파일 선택</b><span>%s 같은 파일</span></label>
                  <div class="privacy">🔒 <span>파일은 이 휴대폰 안에서만 확인하고 서버로 보내지 않아요. 지문(SHA-256)만 보내요.</span></div>
                </div>
                <div id="after" hidden>
                  <div class="verdict" id="verdict"><div class="mark" id="mark"></div><h2 id="title"></h2><p id="desc"></p></div>
                  <div class="rows"><div class="cmp">
                    <div class="r"><span>기록</span>%s</div>
                    <div class="r"><span>원본 저장</span><b>%s</b></div>
                    <div class="r"><span>원본 지문</span><b class="hash">%s</b></div>
                    <div class="r"><span>올린 파일</span><b class="hash" id="mine"></b></div></div></div>
                  <div class="acts"><button type="button" class="btn" onclick="location.reload()">다른 파일 확인</button></div>
                </div>
                <script>
                %s
                const ORIGINAL='%s';
                const fmt=h=>h.slice(0,4)+' '+h.slice(4,8)+' … '+h.slice(56,60)+' '+h.slice(60,64);
                function mark(mine){if(!ORIGINAL)return fmt(mine);const a=fmt(mine),b=fmt(ORIGINAL);
                  return a===b?a:a.split(' ').map((p,i)=>p===b.split(' ')[i]?p:'<mark>'+p+'</mark>').join(' ');}
                document.getElementById('file').addEventListener('change',async ev=>{
                  const file=ev.target.files[0];if(!file)return;
                  const sha=sha256(new Uint8Array(await file.arrayBuffer()));
                  const res=await fetch('/api/v1/documents/%s/verify',{method:'POST',headers:{'Content-Type':'application/json'},body:JSON.stringify({sha256:sha})});
                  const {status}=await res.json();
                  const ok=status==='MATCH';
                  const v=document.getElementById('verdict');v.className='verdict '+(ok?'ok':'bad');
                  document.getElementById('mark').textContent=ok?'✓':'✕';
                  document.getElementById('title').textContent=ok?'원본과 일치해요':status==='NOT_FOUND'?'확인할 원본이 없어요':'원본과 달라요';
                  document.getElementById('desc').innerHTML=ok?'이 PDF는 저장된 원본과 내용이 같아요.<br>저장 후 바뀐 곳이 없어요.'
                    :status==='NOT_FOUND'?'이 문서 번호로 발급된 원본이 없어요.':'이 PDF는 저장된 원본과 내용이 달라요.<br>저장 후 누군가 고쳤을 수 있어요.';
                  document.getElementById('mine').innerHTML=mark(sha);
                  document.getElementById('before').hidden=true;document.getElementById('after').hidden=false;window.scrollTo(0,0);
                });
                document.querySelector('.drop').addEventListener('click',()=>document.getElementById('file').click());
                </script>
                """.formatted(rec, target == null ? "순간기록_….pdf" : e(target.report().getFileName()),
                rec, sealed, target == null ? "-" : fmtHash(original), SHA256_JS, original, e(documentNo)));
    }

    static String error(String title, String message) {
        return page(title, "<div class=\"intro\"><h2>" + e(title) + "</h2><p>" + e(message) + "</p></div>");
    }

    /** 속도 선, 브레이크·방향지시등 띠, 조향각 선을 한 SVG에. 0초에 빨간 점선. */
    private static String chart(ReportFacts f) {
        List<Snapshot> snaps = f.record().getSnapshots();
        long t0 = f.record().getTriggerAtMillis();
        double from = f.windowStartSec();
        double to = Math.max(f.windowEndSec(), from + 1);
        int w = 350;
        int speedH = 120;
        int bandTop = 128;
        int band = 12;
        int steerTop = bandTop + band * 2 + 10;
        int steerH = 60;
        int h = steerTop + steerH + 18;
        double maxSpeed = 10;
        double maxSteer = 90;
        for (Snapshot s : snaps) {
            if (s.getSpeedKph() != null) maxSpeed = Math.max(maxSpeed, s.getSpeedKph());
            if (s.getSteeringDeg() != null) maxSteer = Math.max(maxSteer, Math.abs(s.getSteeringDeg()));
        }
        maxSpeed = Math.ceil(maxSpeed * 1.1 / 10) * 10;

        StringBuilder speed = new StringBuilder();
        StringBuilder steer = new StringBuilder();
        StringBuilder bands = new StringBuilder();
        for (int i = 0; i < snaps.size(); i++) {
            Snapshot s = snaps.get(i);
            double x = x(s.getAtMillis(), t0, from, to, w);
            double nx = i + 1 < snaps.size() ? x(snaps.get(i + 1).getAtMillis(), t0, from, to, w) : x + 0.5;
            if (s.getSpeedKph() != null) speed.append(speed.isEmpty() ? "M" : "L").append(n(x)).append(' ').append(n(speedH - s.getSpeedKph() / maxSpeed * speedH));
            if (s.getSteeringDeg() != null) steer.append(steer.isEmpty() ? "M" : "L").append(n(x)).append(' ').append(n(steerTop + steerH / 2.0 + s.getSteeringDeg() / maxSteer * (steerH / 2.0 - 1)));
            double bw = Math.max(nx - x, 0.5);
            if (Boolean.TRUE.equals(s.getBrake())) bands.append(rect(x, bandTop, bw, band, "var(--brake)"));
            if (s.getTurn() != null && s.getTurn() != TurnSignal.OFF) bands.append(rect(x, bandTop + band + 2, bw, band, "var(--turn)"));
        }
        double zero = (0 - from) / (to - from) * w;
        StringBuilder ticks = new StringBuilder();
        double step = (to - from) > 60 ? 30 : (to - from) > 25 ? 10 : 5;
        for (double t = Math.ceil(from / step) * step; t <= to; t += step) {
            double tx = (t - from) / (to - from) * w;
            String label = t == 0 ? "0초" : (t > 0 ? "+" : "−") + Math.round(Math.abs(t));
            ticks.append("<text x=\"").append(n(tx)).append("\" y=\"").append(h - 4).append("\" text-anchor=\"middle\" class=\"")
                    .append(t == 0 ? "t0" : "t").append("\">").append(label).append("</text>");
        }
        return """
                <svg class="chart" viewBox="0 0 %d %d" role="img" aria-label="시간축 그래프">
                  <rect x="0" y="%d" width="%d" height="%d" fill="var(--sunk)"/><rect x="0" y="%d" width="%d" height="%d" fill="var(--sunk)"/>
                  %s
                  <line x1="0" x2="%d" y1="%d" y2="%d" stroke="var(--line)"/><line x1="0" x2="%d" y1="%s" y2="%s" stroke="var(--line)"/>
                  <path d="%s" fill="none" stroke="var(--steer)" stroke-width="1.6"/>
                  <path d="%s" fill="none" stroke="var(--speed)" stroke-width="2.2" stroke-linejoin="round"/>
                  <line x1="%s" x2="%s" y1="0" y2="%d" stroke="var(--impact)" stroke-width="1.4" stroke-dasharray="4 3"/>
                  <text x="2" y="10" class="t">%d km/h</text><text x="2" y="%d" class="t">브레이크</text><text x="2" y="%d" class="t">방향지시등</text>
                  <text x="2" y="%d" class="t">왼쪽</text><text x="2" y="%d" class="t">오른쪽</text>
                  %s
                </svg>""".formatted(w, h, bandTop, w, band, bandTop + band + 2, w, band, bands,
                w, speedH, speedH, w, n(steerTop + steerH / 2.0), n(steerTop + steerH / 2.0), steer, speed,
                n(zero), n(zero), steerTop + steerH, Math.round(maxSpeed), bandTop + 9, bandTop + band + 11,
                steerTop + 9, steerTop + steerH - 2, ticks);
    }

    private static double x(long at, long t0, double from, double to, int w) {
        return ((at - t0) / 1000.0 - from) / (to - from) * w;
    }

    private static String rect(double x, double y, double w, double h, String fill) {
        return "<rect x=\"" + n(x) + "\" y=\"" + n(y) + "\" width=\"" + n(w) + "\" height=\"" + n(h) + "\" fill=\"" + fill + "\"/>";
    }

    private static String n(double v) {
        return String.format(Locale.ROOT, "%.1f", v);
    }

    private static String fmtHash(String h) {
        return h.substring(0, 4) + " " + h.substring(4, 8) + " … " + h.substring(56, 60) + " " + h.substring(60, 64);
    }

    private static String orNone(String s) {
        return s == null ? "기록 없음" : s;
    }

    private static String e(String s) {
        return htmlEscape(s, "UTF-8");
    }

    private static String page(String sub, String body) {
        return """
                <!doctype html><html lang="ko"><head><meta charset="utf-8">
                <meta name="viewport" content="width=device-width, initial-scale=1">
                <title>순간기록 · %s</title><style>%s</style></head>
                <body><main><div class="head"><span class="brand">순간기록<small>%s</small></span></div>%s</main></body></html>
                """.formatted(sub, CSS, sub, body);
    }

    private static final String CSS = """
            :root{--bg:#E9EDF1;--surface:#fff;--sunk:#DDE2E8;--line:#C9D0D8;--ink:#10151B;--text2:#56606C;--text3:#8A939E;
              --accent:#2F6FEB;--ok:#0E8A61;--bad:#C8232D;--impact:#E0313C;--speed:#10151B;--brake:#F0505A;--turn:#F5A524;--steer:#36C5D6}
            *{box-sizing:border-box;margin:0;padding:0}
            body{background:var(--bg);color:var(--ink);font:15px/1.6 -apple-system,"Apple SD Gothic Neo","Malgun Gothic","Noto Sans KR",sans-serif}
            main{max-width:430px;margin:0 auto;min-height:100vh;padding-bottom:24px}
            .head{padding:16px 20px 12px}.brand{font-size:16px;font-weight:800;letter-spacing:-.02em}
            .brand small{font-size:12px;font-weight:600;color:var(--text3);margin-left:6px}
            .k{font-size:12px;font-weight:700;color:var(--text3);letter-spacing:.02em;margin-bottom:6px}
            .hero{padding:8px 20px 20px}.pill{display:inline-flex;align-items:center;height:28px;padding:0 12px;border-radius:999px;font-size:13px;font-weight:700;background:#FDE7E8;color:#B4232C}
            .lead{font-size:24px;line-height:32px;font-weight:800;letter-spacing:-.03em;margin-top:12px}
            .when{display:flex;align-items:baseline;gap:10px;margin-top:16px}.when span:not(.clock){font-size:14px;color:var(--text2)}
            .clock{font-size:28px;line-height:34px;font-weight:800;font-variant-numeric:tabular-nums}
            .place{font-size:14px;color:var(--text2);margin-top:2px}
            .stack{display:flex;flex-direction:column;gap:12px;padding:0 20px}
            .card{background:var(--surface);border-radius:20px;padding:20px;display:block;color:inherit;text-decoration:none}
            .steps{list-style:none;display:flex;flex-direction:column;gap:12px}
            .steps li{display:grid;grid-template-columns:12px 1fr;gap:10px;align-items:baseline}
            .steps li i{width:12px;height:12px;border-radius:50%;background:var(--dot)}
            .steps b{font-size:17px;font-variant-numeric:tabular-nums}.steps span{display:block;font-size:14px;color:var(--text2)}
            svg.chart{width:100%;height:auto;display:block}svg .t{font-size:9px;fill:var(--text3)}svg .t0{font-size:9px;font-weight:700;fill:var(--impact)}
            .legend{display:flex;flex-wrap:wrap;gap:6px 14px;font-size:12px;color:var(--text2);margin-top:10px}
            .legend i{display:inline-block;width:14px;height:4px;border-radius:2px;margin-right:5px;vertical-align:3px}
            .facts{display:grid;grid-template-columns:1fr 1fr;gap:14px 12px}.facts .v{font-size:15px;font-weight:600}
            .said{font-size:16px;line-height:1.6}.said.none{color:var(--text3)}
            .plate{display:inline-flex;align-items:center;height:36px;padding:0 12px;border-radius:8px;background:#fff;border:2px solid var(--ink);font-weight:800;font-size:19px;margin-top:10px}
            .plate-k{font-size:12px;color:var(--text3);margin-left:8px}
            .verify{display:flex;gap:12px;align-items:center}.verify .sh{width:44px;height:44px;border-radius:14px;background:rgba(18,160,113,.14);color:#12A071;display:grid;place-items:center;font-weight:800;flex:none}
            .verify b{font-size:15px;display:block}.verify span{font-size:13px;color:var(--text2)}.verify .go{margin-left:auto;font-size:22px;color:var(--text3)}
            .foot-note{font-size:13px;color:var(--text3);padding:12px 24px 8px}
            .sticky{position:sticky;bottom:0;padding:12px 20px 20px;background:linear-gradient(to top,var(--bg) 70%,transparent);display:grid;grid-template-columns:1fr 56px;gap:10px}
            .btn{display:flex;align-items:center;justify-content:center;height:56px;border-radius:16px;border:0;width:100%;font:700 16px/1 inherit;background:var(--surface);color:var(--ink);text-decoration:none;cursor:pointer;box-shadow:0 1px 3px rgba(16,21,27,.08)}
            .btn.primary{background:var(--accent);color:#fff}.btn.icon{font-size:20px}
            .toast{position:fixed;left:20px;right:20px;bottom:100px;max-width:390px;margin:0 auto;background:var(--ink);color:#fff;border-radius:16px;padding:14px 16px;font-weight:700}
            .intro{padding:12px 24px 0}.intro h2{font-size:26px;line-height:34px;font-weight:800;letter-spacing:-.03em;margin-top:20px}.intro p{color:var(--text2);margin-top:10px}
            .rec{font-size:15px;line-height:1.5}.rec small{display:block;font-size:13px;color:var(--text2);margin-top:2px}
            .drop{display:block;margin:24px 20px 0;border:2px dashed var(--line);border-radius:20px;padding:28px 20px;text-align:center;cursor:pointer;background:var(--surface)}
            .drop b{display:block;font-size:16px;color:var(--accent)}.drop span{font-size:13px;color:var(--text3)}
            .privacy{display:flex;gap:8px;font-size:13px;color:var(--text2);padding:16px 24px 0}
            .verdict{color:#fff;padding:24px 24px 32px;text-align:center}.verdict.ok{background:var(--ok)}.verdict.bad{background:var(--bad);padding-bottom:40px}
            .verdict .mark{width:120px;height:120px;margin:12px auto 20px;border-radius:50%;background:rgba(255,255,255,.18);display:grid;place-items:center;font-size:64px;font-weight:800}
            .verdict h2{font-size:34px;line-height:42px;font-weight:800;letter-spacing:-.03em}.verdict.bad h2{font-size:40px;line-height:48px}
            .verdict p{font-size:15px;margin-top:10px;opacity:.92}
            .rows{padding:20px}.cmp{background:var(--surface);border-radius:20px;padding:6px 18px}
            .cmp .r{display:grid;grid-template-columns:96px 1fr;gap:10px;padding:12px 0;border-bottom:1px solid var(--line);align-items:baseline}
            .cmp .r:last-child{border-bottom:0}.cmp .r>span{font-size:13px;color:var(--text3);font-weight:700}
            .cmp .r b{font-size:15px;font-weight:600;word-break:break-all}
            .hash mark{background:rgba(224,49,60,.18);color:var(--impact);border-radius:4px;padding:0 2px;font-weight:800}
            .acts{padding:12px 20px 28px}
            """;

    /** http(비보안 연결)에서도 동작하도록 crypto.subtle 대신 쓰는 SHA-256. 입력 Uint8Array, 출력 소문자 hex. */
    private static final String SHA256_JS = """
            function sha256(m){const K=new Uint32Array([0x428a2f98,0x71374491,0xb5c0fbcf,0xe9b5dba5,0x3956c25b,0x59f111f1,0x923f82a4,0xab1c5ed5,0xd807aa98,0x12835b01,0x243185be,0x550c7dc3,0x72be5d74,0x80deb1fe,0x9bdc06a7,0xc19bf174,0xe49b69c1,0xefbe4786,0x0fc19dc6,0x240ca1cc,0x2de92c6f,0x4a7484aa,0x5cb0a9dc,0x76f988da,0x983e5152,0xa831c66d,0xb00327c8,0xbf597fc7,0xc6e00bf3,0xd5a79147,0x06ca6351,0x14292967,0x27b70a85,0x2e1b2138,0x4d2c6dfc,0x53380d13,0x650a7354,0x766a0abb,0x81c2c92e,0x92722c85,0xa2bfe8a1,0xa81a664b,0xc24b8b70,0xc76c51a3,0xd192e819,0xd6990624,0xf40e3585,0x106aa070,0x19a4c116,0x1e376c08,0x2748774c,0x34b0bcb5,0x391c0cb3,0x4ed8aa4a,0x5b9cca4f,0x682e6ff3,0x748f82ee,0x78a5636f,0x84c87814,0x8cc70208,0x90befffa,0xa4506ceb,0xbef9a3f7,0xc67178f2]);
            const H=new Uint32Array([0x6a09e667,0xbb67ae85,0x3c6ef372,0xa54ff53a,0x510e527f,0x9b05688c,0x1f83d9ab,0x5be0cd19]);
            const l=m.length,n=((l+9+63)>>6)<<6,b=new Uint8Array(n);b.set(m);b[l]=0x80;const dv=new DataView(b.buffer);
            dv.setUint32(n-8,Math.floor(l/0x20000000));dv.setUint32(n-4,(l<<3)>>>0);const w=new Uint32Array(64);
            const r=(x,k)=>(x>>>k)|(x<<(32-k));
            for(let o=0;o<n;o+=64){for(let i=0;i<16;i++)w[i]=dv.getUint32(o+i*4);
            for(let i=16;i<64;i++){const a=w[i-15],c=w[i-2];w[i]=(w[i-16]+(r(a,7)^r(a,18)^(a>>>3))+w[i-7]+(r(c,17)^r(c,19)^(c>>>10)))>>>0;}
            let[a,bb,c,d,e,f,g,h]=H;
            for(let i=0;i<64;i++){const t1=(h+(r(e,6)^r(e,11)^r(e,25))+((e&f)^(~e&g))+K[i]+w[i])>>>0,t2=((r(a,2)^r(a,13)^r(a,22))+((a&bb)^(a&c)^(bb&c)))>>>0;
            h=g;g=f;f=e;e=(d+t1)>>>0;d=c;c=bb;bb=a;a=(t1+t2)>>>0;}
            H[0]+=a;H[1]+=bb;H[2]+=c;H[3]+=d;H[4]+=e;H[5]+=f;H[6]+=g;H[7]+=h;}
            return Array.from(H,x=>x.toString(16).padStart(8,'0')).join('');}
            """;
}
