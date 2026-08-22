# Backend ↔ Chatbot 연결 검증 보고서

## 상태

- 작성일: 2026-08-09 (Asia/Seoul)
- Chatbot 로컬 계약 검증: 완료
- 실제 Backend 및 배포 네트워크 연결: 외부 환경 대기
- Backend 대화·응답 근거 저장 순서 검증: Backend 연결 대기

현재 저장소에는 Chatbot 서버만 있으며 실제 Backend 저장소, 배포 내부 DNS와 로그 조회 권한이
없다. 따라서 실제 연결 완료로 간주하지 않고, 로컬에서 검증 가능한 계약을 자동화한 뒤 운영 검증
절차를 남긴다.

## 로컬 검증 결과

`InternalChatQueryIntegrationTest`에서 `POST /internal/chat/query`의 다음 경로를 Spring 전체
Application Context와 HTTP 계층을 통해 검증한다.

| 시나리오 | 기대 결과 | 결과 |
|---|---|---|
| 정상 검색·LLM 응답 | `200 COMPLETED`, 근거와 traceId 반환 | 통과 |
| 검색 결과 없음 | `200 NO_RESULT`, 빈 sources, LLM 미호출 | 통과 |
| LLM timeout | `504 CHAT_QUERY_TIMEOUT`, 동일 traceId | 통과 |
| Elasticsearch 장애 | 안전한 `500 INTERNAL_ERROR`, 동일 traceId | 통과 |
| Elasticsearch 오류의 자격 증명 포함 | 오류 본문에 내부 키·토큰·비밀번호 미노출 | 통과 |

별도 인증 및 Controller 계약 테스트에서 다음 항목을 검증한다.

- `X-Internal-Api-Key` 정상·누락·오류 및 서버 설정 누락 fail-closed
- Backend가 전달한 `X-Trace-Id`의 응답 헤더·본문 전파
- traceId가 없을 때 서버 생성
- 요청 timeout 유효성 및 `504` 공통 오류 변환
- 동일 requestId 처리 중 `409`와 `Retry-After`
- 입력 검증 오류와 처리 불가 오류의 안전한 공통 응답

## 배포 환경 실행 전제

- Backend와 Chatbot에 동일한 `INTERNAL_API_KEY`를 Secret Manager에서 주입한다.
- Chatbot `/internal/**`는 외부 Load Balancer에 공개하지 않고 Backend 보안 그룹 또는 내부
  네트워크에서만 접근시킨다.
- Backend의 Chatbot base URL은 내부 DNS와 실제 컨테이너 포트를 사용한다.
- 실제 비밀값은 명령행 인수, 소스, 배포 manifest 평문 또는 검증 보고서에 기록하지 않는다.

## 실제 연결 검증 절차

1. Backend 실행 환경에서 Chatbot 내부 health endpoint와 DNS 해석·TCP 연결을 확인한다.
2. 새 UUID `requestId`와 Backend가 생성한 `X-Trace-Id`로 정상 질문을 호출한다.
3. 응답 헤더와 본문의 traceId가 요청값과 같은지 확인한다.
4. 잘못된 키와 키 누락 요청이 `401 UNAUTHORIZED`이며 비밀값을 반환하지 않는지 확인한다.
5. 검색 결과 없음, LLM timeout, Elasticsearch 연결 차단 시나리오를 차례로 실행한다.
6. Backend가 사용자 메시지를 먼저 저장하고, 성공 응답 후 답변과 sources를 저장하는지 확인한다.
7. 실패 응답에서는 완료 답변이 저장되지 않고 재시도 가능한 상태가 유지되는지 확인한다.
8. Backend와 Chatbot의 구조화 로그를 traceId로 함께 조회하고 키·토큰·연결 문자열이 없는지 확인한다.

## 판정 기준

다음 조건을 모두 만족해야 MVP 5번을 완료로 전환한다.

- Backend 실행 환경에서 Chatbot 내부 주소 호출 성공
- 정상·NO_RESULT·LLM timeout·Elasticsearch 장애 계약 일치
- requestId와 traceId로 양쪽 로그 상관관계 확인
- Backend 대화 및 응답 근거 저장 순서 확인
- Backend·Chatbot 로그와 오류 응답에서 운영 비밀값 미검출

외부 검증 전까지 MVP 5번은 `외부 환경 대기` 상태다.
