# TODO

이 프로젝트는 Backend와 분리된 Chatbot 서버다.
Backend는 사용자 인증, 대화 세션 소유권, 대화 원본 저장과 Client 통신을 담당한다.
Chatbot 서버는 관광 데이터 검색, 대화 조율, LLM 호출과 근거 기반 답변 생성을 담당한다.

## 완료

- [x] 관광지, 카테고리, 지역, 대화 이력 Entity
- [x] PostgreSQL 연결
- [x] 관광 데이터 Repository 및 Upsert
- [x] TourAPI DTO 및 응답 모델
- [x] Spring RestClient 기반 실제 TourAPI HTTP Client
- [x] timeout, connection, HTTP, JSON, TourAPI 오류 처리
- [x] TourAPI Collector
- [x] TourAPI 관광 데이터 Normalizer
- [x] 단건 Import Orchestration
- [x] `areaBasedList2` 페이지 Ingestion
- [x] 페이지 및 관광지 처리 제한
- [x] Ingestion 실행 결과 Summary
- [x] 개별 관광지 실패 격리
- [x] 개발용 일회성 Ingestion Runner
- [x] 단일 인스턴스 Scheduler 및 중복 실행 방지
- [x] 실제 TourAPI → PostgreSQL 저장 검증
- [x] Flyway V1 초기 마이그레이션
- [x] PK, FK, Unique, Index 정의
- [x] Hibernate `ddl-auto=validate` 정책
- [x] H2 및 Testcontainers PostgreSQL 테스트
- [x] TourAPI 수집 파이프라인 테스트

## 앞으로 구현할 작업

### P0. Backend ↔ Chatbot 내부 API 계약

- [x] 요청 DTO 정의
  - 필수 UUID `requestId`
  - `sessionId`
  - 사용자 식별자
  - 현재 질문
  - 최대 10개의 임시 대화 문맥
  - 처리 timeout
- [x] 응답 DTO 정의
  - 자연어 답변
  - 추천 관광지 ID와 추천 이유
  - 검색 근거
  - 처리 상태와 안전한 오류 코드
- [x] Backend 원본 저장·문맥 조립 및 Chatbot 무상태 처리 책임 확정
- [x] 서비스 간 인증 방식을 `X-Internal-Api-Key` 공유 키로 확정
- [x] 일반 JSON 응답 계약
- [x] 공통 에러 응답 계약
- [x] timeout, traceId, source 계약
- [x] Backend ↔ Chatbot OpenAPI 문서
- [x] Mock 기반 HTTP 계약 테스트
- [ ] SSE 스트리밍 계약

### P1. Elasticsearch 관광 데이터 색인

- [x] 관광지 Search Document 설계
- [x] TouristSpot Entity → Search Document Mapper
- [x] Search Document 필드 타입 및 Mapper 단위 테스트
- [x] 관광지 Index Mapping 작성
- [x] Nori 기반 한국어 검색 Analyzer 정책 결정
- [x] PostgreSQL → Elasticsearch 전체 재색인
- [x] 버전 인덱스 생성 및 전체 성공 후 Alias 전환
- [x] 관광지 생성·수정 트랜잭션 커밋 후 단건 색인 및 갱신
- [ ] 삭제 또는 비활성 관광지 색인 제거
- [x] 전체 재색인 결과 Summary
- [x] Elasticsearch 통합 테스트가 모두 Skip되면 빌드 실패
- [x] Testcontainers PostgreSQL·Elasticsearch 실제 통합 테스트
- [x] Cloud 배포용 인덱스 초기화 및 전체 재색인 실행 모드
- [x] 색인 실패 기록 및 재처리
- [x] PostgreSQL 원본 수정 시각과 색인 버전 비교

### P2. Elasticsearch 관광지 검색

- [x] 키워드 검색
- [x] 관광지명, 주소, 설명, 운영정보 필드별 가중치
- [x] 지역 ID 및 지역 코드 필터
- [x] 콘텐츠 유형 및 카테고리 필터
- [x] 위도·경도 기반 거리 검색
- [x] 검색 결과 개수 및 최소 점수 제한
- [x] 검색 결과를 Chatbot 근거 DTO로 변환
- [x] 대표 자연어 질문 기반 검색 Top-1 품질 테스트
- [x] TourAPI 표본 기반 품질 지표: 14개 질문, Top-1 92.86%, Top-3 100%, 지역·콘텐츠 유형별 집계
- [x] 검색 결과 없음 처리
- [ ] 지역명·카테고리명 데이터 확보 후 자연어 검색 필드와 가중치 추가

### P3. Chat Orchestrator

- [x] Chat 요청 필수값·길이·timeout 경계 검증
- [x] 규칙 기반 질문 분석과 Elasticsearch 검색 조건 생성
- [x] TouristSpot Retriever 연결
- [x] 검색 근거 정리
- [x] Prompt 생성
- [x] LLM 호출
- [x] 응답 검증
- [x] 추천 관광지 ID가 검색 결과에 포함되는지 검증
- [x] 검색·LLM·전체 처리 시간과 결과 메타데이터 집계

### P4. LLM Client와 Prompt

- [x] LLM 설정 외부화
- [x] LLM Client 인터페이스와 요청·결과 모델
- [x] Responses API 기반 LLM HTTP Client
- [x] timeout, connection, HTTP, 모델 오류 구분
- [x] 요청 잔여 timeout을 LLM 네트워크 연결·응답 timeout에 적용
- [x] 구조화 응답 파싱
- [x] LLM 입력·출력 토큰 사용량 수집
- [x] 근거 기반 시스템 Prompt
- [x] 검색 근거에 없는 사실 생성 방지
- [x] URL, 운영시간, 가격 추측 방지
- [x] 검색 결과 부족 시 분석 조건 기반 변경 요청
- [x] LLM 응답 검증 실패 처리

### P5. Backend 전용 Chat API

- [x] Backend 전용 JSON Chat API를 실제 Completion 처리 흐름에 연결
- [x] Backend 전용 `X-Internal-Api-Key` 서비스 인증
- [ ] 외부 Client 직접 접근 차단
- [x] 요청 traceId MDC 로깅
- [x] API timeout 정책
- [x] Chatbot 오류를 Backend 오류 계약으로 변환
- [x] 인증 포함 Backend JSON Chat 연동 통합 테스트

### P6. Redis

- [x] `requestId` 기반 중복 요청 방지
- [x] 처리 중 요청 상태
- [x] 완료 응답 5분 캐시
- [x] 최근 대화 문맥은 Backend 관리, Chatbot Redis 캐시 미도입 결정
- [x] 정규화된 관광지 검색 결과 Redis 캐시
- [x] 검색 결과 캐시 TTL 정책
- [x] Redis 장애 시 Elasticsearch 직접 검색 fallback

### P7. SSE 스트리밍

- [ ] Chatbot SSE 이벤트 계약
- [ ] `started`, `content`, `recommendation`, `completed`, `error` 이벤트
- [ ] Backend와 Gateway의 SSE 전달 검증
- [ ] 연결 종료와 취소 처리
- [ ] 중복 스트림 방지
- [ ] 스트리밍 timeout

### P8. 오류, Retry 및 안정성

- [x] Docker 비의존 Cloud Runtime 프로필과 환경변수 계약
- [x] Chat·LLM·검색·색인 공통 오류 타입과 재시도 가능 여부 분류
- [x] Elasticsearch 일시 장애 Retry와 지수 Backoff
- [x] LLM timeout, connection, 일부 5xx Retry
- [x] Retry 비대상 오류 구분
- [x] LLM 지수 Backoff와 요청 timeout 예산 제한
- [x] Circuit Breaker 필요성 검토 및 운영 지표 확보 전 도입 보류 결정
- [x] TourAPI 호출 간격 및 Retry 정책
- [x] Redis 기반 다중 인스턴스 Scheduler 분산 잠금

### P9. 실행 이력과 모니터링

- [ ] Ingestion 실행 이력 PostgreSQL 저장
- [ ] 마지막 성공 수집 시각
- [ ] Elasticsearch 색인 실행 이력
- [x] 검색 응답 시간
- [x] LLM 응답 시간
- [x] 검색 결과 없음 비율
- [x] LLM 오류율
- [x] 검색·LLM·전체 처리 시간 및 토큰 사용량 Micrometer 지표
- [x] Actuator 및 Micrometer 기반 외부 연동 재시도·소진 지표
- [x] Cloud JSON 구조화 로그와 Chat 요청 완료 추적

### P10. 단계적 운영 검증

- [x] 실제 TourAPI 50건 수집
- [ ] 실제 TourAPI 100건 수집
- [ ] 실제 TourAPI 500건 수집
- [x] 콘텐츠 유형 및 지역별 데이터 품질 확인 (50건 표본)
- [ ] 전체 TourAPI 데이터 수집
- [ ] Elasticsearch 전체 색인
- [x] 대표 관광 질문 기반 Chatbot 품질 평가 fixture
- [x] 추천 ID 및 URL·운영시간·요금·전화번호 근거 정확성·환각 평가
- [ ] 부하 및 장애 테스트

### P11. Speech-to-Text 음성 질문

- [x] 교체 가능한 STT Client 인터페이스와 Service 계층
- [x] OpenAI Audio Transcriptions Provider 구현
- [x] mp3, mp4, mpeg, mpga, m4a, wav, webm 형식 검증
- [x] 최대 25MB 파일 크기 검증
- [x] timeout, Provider 실패와 빈 변환 결과 예외 처리
- [x] STT 결과 텍스트를 기존 Chat 질문 흐름에 전달
- [x] 음성 파일 비영속 처리
- [x] 단위 및 HTTP 계약 테스트

## MVP 출시 우선순위

다음 6개 항목을 MVP 출시 게이트로 지정한다. 번호 순서대로 구현하되, 서로 독립적인 테스트와 문서
작업은 병행할 수 있다. 6개 항목이 완료되기 전에는 아래의 출시 후 보완 항목으로 범위를 확장하지
않는다.

### 1. Backend 전용 서비스 인증 및 외부 접근 차단

- [x] Backend와 Chatbot 사이의 MVP 인증 방식을 공유 키로 확정한다.
- [x] `/internal/**` 요청에서 Backend 자격 증명을 검증한다.
- [x] 인증 누락·오류 요청을 안전한 공통 오류 응답으로 변환한다.
- 외부 Client와 신뢰되지 않은 네트워크의 직접 접근을 차단한다.
- [x] 정상·누락·오류 자격 증명과 설정 누락 fail-closed를 테스트한다.
- [ ] Secret Manager 연동과 무중단 키 회전은 배포 환경 연결 단계에서 검증한다.

이 단계는 보안 정책 변경이므로 구현 전에 `99_Autonomous_Development_Policy.md`에 따른 사용자
확인을 받는다.

### 2. `requestId` 기반 멱등성과 완료 응답 캐시

- [x] Chat 요청에 필수 UUID `requestId`를 포함한다.
- [x] Redis `SET NX + TTL`로 처리 중 요청의 중복 실행을 차단한다.
- [x] 완료 응답을 5분 TTL로 캐시해 동일 요청에 재사용한다.
- [x] 처리 중 동일 요청은 `409 REQUEST_IN_PROGRESS`로 반환한다.
- [x] 같은 `requestId`에 다른 내용을 사용하면 `409 REQUEST_ID_CONFLICT`로 반환한다.
- [x] Redis 장애 시 `503` fail-closed로 중복 LLM 호출을 방지한다.
- [x] 최대 10개의 임시 문맥을 검증하고 Prompt에 데이터로 전달한다.

이 단계는 API 계약 변경이므로 구현 전에 사용자 확인을 받는다. Backend 저장 메시지 ID와
`requestId`의 관계도 함께 확정한다.

### 3. Elasticsearch 색인 실패 기록 및 재처리

- [x] 단건·전체 색인 실패의 관광지 ID, 작업 유형, 원인 분류와 발생 시각을 기록한다.
- [x] 재시도 횟수, 다음 실행 시각과 최종 처리 상태를 관리한다.
- [x] 일시 장애만 지수 Backoff 후 재처리하고 영구 오류는 격리한다.
- [x] 유일 제약, 비관적 잠금과 Entity 버전으로 중복 기록·동시 재처리를 방지한다.
- [x] 재처리 성공 후 PostgreSQL 수정 시각과 Elasticsearch 문서 시각을 다시 비교한다.
- [x] 전체 재색인 실패가 모두 복구된 경우에만 대상 인덱스로 Alias를 전환한다.

영속 실패 기록은 DB 스키마 변경이 필요하므로 Entity와 Migration 설계 전에 사용자 확인을 받는다.

### 4. 실제 TourAPI 50건 수집 및 데이터 품질 검증

- [x] 실제 TourAPI 관광지 50건을 PostgreSQL에 적재한다.
- [x] 필수 필드 누락, 좌표 범위, URL 형식과 수정 시각을 검사한다.
- [x] 지역 및 콘텐츠 유형별 건수와 실패·보강 비율을 집계한다.
- [x] 동일 데이터 재수집 시 생성·수정·건너뛰기 결과를 검증한다.
- [x] 발견된 데이터 문제와 검색 문서 영향을 재현 가능한 보고서로 남긴다.

실제 API 자격 증명과 네트워크가 준비되면 질문 없이 검증을 진행한다. 외부 상태로 실행할 수 없는
경우에는 fixture 기반 검증을 먼저 보완하고 차단 사유를 보고한다.

### 5. 실제 Backend ↔ Chatbot 운영 환경 연결 검증

- [ ] 배포 환경의 Backend가 Chatbot 내부 DNS와 포트로 호출 가능한지 확인한다. (외부 환경 대기)
- [x] 인증, traceId, timeout과 공통 오류 변환의 Chatbot 로컬 계약을 검증한다.
- [x] 정상 응답, 검색 결과 없음, LLM timeout과 Elasticsearch 장애의 Chatbot 로컬 시나리오를 확인한다.
- [ ] Backend 대화 저장 순서와 Chatbot 응답 근거 저장 결과를 점검한다. (Backend 연결 대기)
- [x] 오류 응답에 내부 API 키와 외부 연동 비밀값이 노출되지 않는지 검증한다.
- [ ] 배포 로그에 운영 비밀값이 노출되지 않는지 확인한다. (외부 환경 대기)

외부 Backend 또는 배포 환경 접근 권한이 없으면 로컬 계약 테스트 결과와 필요한 실행 절차를
문서화하고 실제 연결 검증을 대기 상태로 남긴다.

### 6. Redis·Elasticsearch·LLM 장애 안전성 검증

- [x] Redis 장애 시 검색 캐시 fallback과 Scheduler 분산 잠금 fail-closed를 검증한다.
- [x] Elasticsearch timeout·connection·재시도 소진 시 안전한 Backend 오류를 검증한다.
- [x] LLM timeout·connection·429·5xx·유효하지 않은 응답을 구분해 검증한다.
- [x] 장애 중 Retry와 Backoff가 요청 timeout 예산을 초과하지 않는지 확인한다.
- [x] 복구 후 정상 요청이 추가 조치 없이 처리되는지 확인한다.
- [x] 구조화 로그와 Micrometer 오류·재시도·소진 지표가 함께 기록되는지 확인한다.

## MVP 완료 기준

- 위 6개 출시 게이트가 모두 완료 또는 외부 환경 대기 상태로 명확히 분류되어야 한다.
- 자동 테스트가 모두 성공하고 실제 환경 검증 결과가 문서화되어야 한다.
- API·보안·DB 변경은 승인된 계약과 Migration을 기준으로 구현되어야 한다.
- 알려진 제한과 운영 대응 절차가 Backend 운영 담당자에게 전달되어야 한다.

## 출시 후 보완 항목

다음 항목은 MVP 출시 게이트 완료 후 실제 사용량과 운영 지표를 근거로 진행한다.

- SSE 스트리밍 이벤트, 연결 취소와 중복 스트림 방지
- TourAPI 100건·500건·전체 데이터 단계별 수집
- Elasticsearch 전체 색인 운영 검증과 실행 이력 영속화
- Ingestion 실행 이력과 마지막 성공 시각 영속화
- 지역명·카테고리명 자연어 검색 필드 및 가중치 보강
- 부하 테스트와 장시간 내구성 테스트
- 운영 지표 기준 충족 시 선택적 Circuit Breaker 도입
- 삭제 또는 비활성 관광지 색인 제거
- 고급 문맥 요약, 개인화 추천과 Vector Search
