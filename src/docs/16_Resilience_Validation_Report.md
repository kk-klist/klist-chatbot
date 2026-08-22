# Redis·Elasticsearch·LLM 장애 안전성 검증 보고서

## 검증 개요

- 검증일: 2026-08-09 (Asia/Seoul)
- 범위: MVP 출시 게이트 6
- 방식: 단위 테스트, Spring HTTP 통합 테스트, Micrometer·로그 캡처 테스트
- 결과: 로컬 자동 검증 완료

## Redis

| 시나리오 | 기대 동작 | 결과 |
|---|---|---|
| 검색 캐시 읽기 장애 | Elasticsearch 직접 검색 | 통과 |
| 검색 캐시 쓰기 장애 | 검색 결과 정상 반환 | 통과 |
| 캐시 JSON 손상 | Elasticsearch 직접 검색 | 통과 |
| 캐시 장애 복구 | 다음 요청부터 Redis 재사용 | 통과 |
| Scheduler 잠금 획득 장애 | 실행하지 않는 fail-closed | 통과 |
| Scheduler 잠금 복구 | 다음 실행에서 정상 잠금 획득 | 통과 |

검색 캐시는 가용성을 우선해 Redis 장애 시 Elasticsearch로 전환한다. Scheduler 분산 잠금은
중복 수집 방지를 우선해 Redis 장애 시 실행하지 않는다.

## Elasticsearch

| 시나리오 | 기대 동작 | 결과 |
|---|---|---|
| 일시적 DataAccess 오류 | 제한된 지수 Backoff 재시도 | 통과 |
| 중첩 connection 오류 | 재시도 가능 오류 분류 | 통과 |
| 중첩 socket timeout | 재시도 가능 오류 분류 | 통과 |
| 영구 오류 | 즉시 실패, 재시도 없음 | 통과 |
| 재시도 소진 | 안전한 Backend 오류 및 소진 지표 | 통과 |
| 장애 후 복구 | 후속 시도 성공 결과 반환 | 통과 |
| timeout 예산 부족 | 추가 Backoff와 호출 없이 실패 | 통과 |

Chat 요청 timeout을 검색 Gateway까지 전달한다. 다음 Backoff가 남은 timeout 이상이면 재시도를
시작하지 않고 `timeout_budget` 소진 이벤트를 기록한다. 개별 Elasticsearch 네트워크 호출은
배포 설정의 connection/socket timeout으로 제한하고, 호출 사이의 Retry·Backoff는 요청 예산을
초과하지 않는다.

## LLM

| 시나리오 | 기대 동작 | 결과 |
|---|---|---|
| timeout | 재시도 가능 timeout으로 분류 | 통과 |
| connection 오류 | 재시도 가능 오류로 분류 | 통과 |
| HTTP 429 | 재시도 가능 rate limit으로 분류 | 통과 |
| HTTP 5xx | 재시도 가능 HTTP 오류로 분류 | 통과 |
| 인증·모델·일부 4xx | 영구 오류, 재시도 없음 | 통과 |
| 빈 출력·잘못된 응답 | invalid response로 분류 | 통과 |
| Backoff 예산 부족 | timeout으로 즉시 종료 | 통과 |
| 장애 후 복구 | 감소된 남은 timeout으로 성공 | 통과 |

매 LLM 재시도는 경과 시간을 제외한 timeout을 외부 HTTP Client에 다시 전달한다.

## 관측성

- `chatbot.retry.attempts`: component와 제한된 reason 태그로 재시도 횟수 기록
- `chatbot.retry.exhausted`: component와 reason 태그로 최종 소진 기록
- 재시도 로그: component, reason, 다음 시도 번호, Backoff 밀리초
- 소진 로그: component, reason, 총 시도 횟수
- 내부 API 오류 응답: traceId 유지, 내부 키·토큰·비밀번호 미노출

Elasticsearch와 LLM의 복구·소진 테스트에서 이벤트 발생 횟수와 필드를 함께 검증한다.

## 운영 환경 후속 확인

MVP 로컬 검증은 완료했다. 배포 후에는 실제 Redis·Elasticsearch·LLM 연결을 단계적으로 차단해
대시보드 카운터, JSON 로그, Backend 오류 응답과 복구 시간을 동일 traceId로 확인한다. 이는
MVP 5번의 실제 Backend·배포 환경 연결 검증 절차와 함께 수행한다.
