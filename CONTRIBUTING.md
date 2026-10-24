# CONTRIBUTING

## 커밋 메시지

```
[타입] 작업 내용 요약 (by 역할명, YYMMDD)
```

타입: `[feat]` 기능 · `[fix]` 버그 · `[refactor]` 리팩터링 · `[style]` 포맷 · `[docs]` 문서 · `[test]` 테스트 · `[chore]` 빌드·설정 · `[revert]` 되돌리기

예: `[feat] 급정지 감지 기준을 감속도로 변경 (by 앱, 261023)`

- 역할명은 `앱`, `백엔드A`, `백엔드B` 중 하나. 실명은 쓰지 않는다.
- 날짜는 `YYMMDD` 6자리
- 커밋은 작업 단위로 나누고, 파일은 경로를 지정해서 add 한다(`git add .` 금지)

> 블라인드 심사 규정 때문에 커밋 메시지·작성자에 실명·개인 계정·이메일을 넣지 않는다. clone한 뒤 저장소 로컬 설정으로 작성자를 역할명으로 맞춘다.
>
> ```
> git config user.name "앱"                      # 또는 백엔드A, 백엔드B
> git config user.email "app@momentlog.invalid"   # backend-a@…, backend-b@…
> ```

## 참고 자료 규칙 (반드시 지킨다)

- 참고는 **공식 자료만**: Pleos 공식 문서, Pleos 공식 샘플(`Pleos-Playground/*`), 공개 SDK 소스, Android 공식 문서.
- **공모전 다른 참가작의 코드·README·문서는 보지도, 가져오지도 않는다.** 검색하다 걸리더라도 열지 않는다(표절 시 수상 취소·상금 환수).
- 외부 오픈소스 라이브러리를 쓰면 라이선스를 확인하고 README에 출처를 적는다.
- AI 도구가 만든 코드도 위 기준을 지켰는지 리뷰에서 확인한다.

## 브랜치

```
{타입}/{작업명}
```

타입: `feature` · `fix` · `hotfix` · `refactor` · `chore`

예: `feature/voice-statement`, `fix/speed-callback`

## 작업 흐름

1. `main`에서 규칙에 맞는 브랜치를 만든다.
2. 커밋 규칙에 맞춰 커밋한다.
3. PR을 올린다. 제목은 커밋과 같은 형식.
4. 리뷰 후 머지한다.

## 모듈별 담당 (초안)

| 모듈 | 담당 |
|---|---|
| `app/` | 앱 |
| `core/` | 앱 + 백엔드 A (경위 문장 규칙) |
| `server/` | 백엔드 A, B |
