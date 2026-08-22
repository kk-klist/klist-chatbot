# 대화 저장 및 문맥 관리 책임

## 결정

Backend가 3분 TTL의 임시 대화 세션과 최근 문맥 조립을 전담한다.
Chatbot은 대화를 영속화하거나 세션별 최근 문맥을 자체 캐시하지 않는 무상태 처리자로 운영한다.

현재 `POST /internal/chat/query` 계약은 필수 UUID `requestId`, `sessionId`, `userId`, 현재
`message`, 최대 10개의 `context`, `timeoutMs`를 전달한다. `messageId`와 영구 채팅 내역은 사용하지
않는다.

## Backend 책임

- 사용자 인증 및 대화 세션 소유권 검증
- Redis 기반 임시 세션과 최근 문맥 관리
- Chatbot 답변 완료 후 세션 TTL 3분 설정
- 사용자 종료, 화면 이탈 또는 TTL 만료 시 세션과 문맥 삭제
- 최근 대화 최대 10개 선택 및 순서 유지
- HTTP 재시도에서 동일한 `requestId` 유지

Frontend와 Gateway는 Chatbot에 직접 접근하지 않고 Backend를 통해 임시 대화 상태를 조회한다.

## Chatbot 책임

- 전달받은 현재 질문과 최대 10개 임시 문맥의 유효성 검증
- 관광지 검색, 근거 구성, Prompt 생성 및 LLM 호출
- 검색 근거에 한정된 답변과 추천 관광지 반환
- 요청 처리 중 필요한 데이터만 메모리에 유지
- Redis 기반 `requestId` 중복 실행 차단과 완료 응답 5분 캐시
- traceId, 처리시간, 오류 유형과 운영 지표 기록

Chatbot은 `sessionId`와 `userId`를 대화 조회 키로 사용하지 않는다. 이 값은 내부 요청의 식별과
향후 추적 확장을 위한 메타데이터이며 세션 소유권을 신뢰하거나 검증하는 근거가 아니다.

## 저장 순서

1. Backend가 인증과 세션 소유권을 검증한다.
2. Backend가 UUID `requestId`를 생성하고 임시 문맥을 조립한다.
3. Backend가 현재 질문과 최대 10개의 최근 문맥을 Chatbot에 전달한다.
4. Chatbot이 검색과 LLM 처리를 수행하고 근거 포함 응답을 반환한다.
5. Backend가 응답을 임시 문맥에 추가하고 세션 TTL을 3분으로 갱신한다.
6. Backend가 Client에 최종 응답을 전달한다.

## 최근 문맥 Redis 캐시 결정

Chatbot에는 최근 대화 문맥 캐시를 두지 않는다.

근거는 다음과 같다.

- Backend 원본과 Chatbot 캐시 사이에 메시지 순서 및 갱신 불일치가 발생할 수 있다.
- 동일 사용자나 세션의 문맥이 여러 서비스에 중복 저장된다.
- 개인정보 보존과 삭제 정책을 두 서비스에서 함께 집행해야 한다.
- Chatbot 재시작이나 Redis 장애에 따라 답변 문맥이 달라질 수 있다.
- 현재 API는 최근 문맥을 전달하지 않아 캐시를 안전하게 채울 기준이 없다.

Backend는 임시 문맥을 Redis에 3분 동안 보관한다. Chatbot은 매 요청에 전달된 문맥만 사용한다.

## 멱등성 정책

- 완료 응답과 처리 잠금의 TTL은 5분이다.
- 동일 요청 처리 중 재호출은 `409 REQUEST_IN_PROGRESS`와 `Retry-After: 1`을 반환한다.
- 같은 `requestId`를 다른 요청 내용에 사용하면 `409 REQUEST_ID_CONFLICT`를 반환한다.
- Redis 장애 시 `503 CHAT_PROCESSING_UNAVAILABLE`로 fail-closed 처리한다.
