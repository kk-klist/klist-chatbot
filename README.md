# klist-chatbot

관광 데이터를 PostgreSQL에 저장하고 Elasticsearch에서 검색한 결과를 기반으로 답변을 생성하는
관광 챗봇 서버다.

## Cloud Runtime

운영 애플리케이션은 Docker 또는 Testcontainers에 의존하지 않는다. `cloud` 프로필에서 클라우드가
제공하는 PostgreSQL과 Elasticsearch에 직접 연결한다.

필수 환경변수:

```text
SPRING_PROFILES_ACTIVE=cloud

DB_URL=jdbc:postgresql://<host>:5432/<database>
DB_USERNAME=<database-user>
DB_PASSWORD=<database-password>

ELASTICSEARCH_URIS=https://<elasticsearch-endpoint>
ELASTICSEARCH_USERNAME=<elasticsearch-user>
ELASTICSEARCH_PASSWORD=<elasticsearch-password>

INTERNAL_API_KEY=<256-bit-random-secret>

STT_OPENAI_ENABLED=true
STT_OPENAI_API_KEY=<openai-api-key>
```

Backend는 모든 `/internal/**` 요청에 `X-Internal-Api-Key` 헤더로 같은 값을 전달해야 한다.
키는 소스나 이미지에 포함하지 않고 배포 환경의 Secret Manager에서 환경변수로 주입한다.

음성 질문은 `POST /internal/chat/query/audio`의 multipart `request` JSON과 `audio` 파일로 전달한다.
지원 형식은 mp3, mp4, mpeg, mpga, m4a, wav, webm이며 최대 크기는 25MB다. 음성 파일은
DB나 파일 시스템에 저장하지 않고 STT 결과 텍스트만 기존 Chat 처리 흐름에 전달한다.

내부 Chat 요청은 UUID `requestId`를 필수로 사용한다. 완료 응답과 처리 잠금은 Redis에 5분간
보관하며 Redis 장애 시 중복 LLM 호출을 막기 위해 `503`으로 fail-closed 처리한다. Backend는 최대
10개의 임시 대화 문맥을 전달하고, 답변 완료 후 자체 Redis 세션 TTL을 3분으로 갱신한다.

TourAPI 수집 또는 Scheduler를 사용할 때 추가하는 환경변수:

```text
TOUR_API_SERVICE_KEY=<service-key>
TOUR_API_INGESTION_SCHEDULE_ENABLED=true
TOUR_API_INGESTION_SCHEDULE_CRON=0 0 3 * * *
TOUR_API_INGESTION_SCHEDULE_ZONE=Asia/Seoul
```

Elasticsearch에는 `analysis-nori` 지원이 필요하다. 신규 환경에서는 애플리케이션 트래픽을 받기 전에
버전 인덱스와 Alias를 만들고 PostgreSQL 원본 데이터를 전체 재색인해야 한다.

배포 시 인덱스 시작 모드는 다음 환경변수로 선택한다.

```text
# 기본값. 인덱스를 변경하지 않는다.
TOURIST_SPOT_INDEX_BOOTSTRAP_MODE=none

# 현재 버전 인덱스와 Mapping 및 Alias를 멱등 생성한다.
TOURIST_SPOT_INDEX_BOOTSTRAP_MODE=initialize

# 신규 버전 인덱스에 PostgreSQL 전체 데이터를 색인한 뒤 Alias를 전환한다.
TOURIST_SPOT_INDEX_BOOTSTRAP_MODE=reindex
TOURIST_SPOT_INDEX_VERSION=v2
```

색인 실패 재처리 Scheduler는 기본적으로 비활성화되어 있다. 운영 환경에서 다음 설정으로 활성화한다.

```text
TOURIST_SPOT_INDEX_FAILURE_RETRY_ENABLED=true
TOURIST_SPOT_INDEX_FAILURE_RETRY_INTERVAL=30s
TOURIST_SPOT_INDEX_FAILURE_RETRY_BATCH_SIZE=20
TOURIST_SPOT_INDEX_FAILURE_RETRY_MAX_ATTEMPTS=5
TOURIST_SPOT_INDEX_FAILURE_RETRY_INITIAL_BACKOFF=30s
TOURIST_SPOT_INDEX_FAILURE_RETRY_MAX_BACKOFF=30m
```

단건·전체 색인 실패는 `tourist_spot_index_failure`에 기록된다. Elasticsearch 일시 장애만 재처리하며,
매핑 오류와 원본 삭제, 최대 재시도 소진 건은 `EXHAUSTED` 상태로 격리한다.

`reindex`에는 아직 존재하지 않는 새 버전을 지정해야 한다. 문서 변환이나 색인이 하나라도 실패하면
Alias를 전환하지 않고 애플리케이션 시작을 실패시킨다. 초기 배포는 `initialize`, 데이터가 존재하는
환경의 버전 교체 배포는 새 버전과 `reindex` 조합을 사용한다.

실행 예시:

```text
java -jar build/libs/klist-chatbot-0.0.1-SNAPSHOT.jar
```

Testcontainers는 `postgresqlTest`, `elasticsearchTest` 같은 통합 테스트에서만 사용한다.
