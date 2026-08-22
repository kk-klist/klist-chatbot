# Circuit Breaker 도입 결정

## 결정

현재 단계에서는 LLM, Elasticsearch, TourAPI 호출에 Circuit Breaker를 추가하지 않는다.
기존 timeout, 제한된 재시도, 지수 Backoff와 재시도 소진 지표로 운영 기준선을 먼저 확보한 뒤
아래 재검토 조건이 충족되면 도입한다.

## 판단 근거

### LLM

- Chat 요청의 전체 timeout 예산 안에서만 재시도한다.
- timeout, connection, 일부 5xx만 최대 3회 재시도한다.
- 인증 오류, 잘못된 요청, 유효하지 않은 응답은 재시도하지 않는다.
- 재시도와 소진 횟수 및 LLM 오류율을 Micrometer 지표로 수집한다.

LLM 장애가 길어지면 Circuit Breaker의 빠른 실패가 처리량 보호에 도움이 될 수 있다. 그러나 현재는
실제 요청량, 오류율, 복구 시간 분포가 없어 failure-rate window와 open duration을 안전하게 정할 수
없다. 임의의 임계값은 일시적인 오류로 회로를 열거나 정상 복구 후 요청을 불필요하게 차단할 수 있다.

### Elasticsearch

- 검색 요청은 connection, timeout 등 일시 장애만 제한적으로 재시도한다.
- 재시도 간격은 상한이 있는 지수 Backoff를 사용한다.
- 검색은 Chat 답변 근거를 만드는 필수 단계라 회로가 열린 동안 대체 데이터 경로가 없다.

대체 검색 경로 없이 Circuit Breaker만 추가하면 장애 중 부하는 줄지만 서비스 결과는 동일하게
실패한다. 먼저 실제 재시도 소진 빈도와 Elasticsearch 복구 시간을 확인한다.

### TourAPI

- TourAPI 호출은 사용자 Chat 요청 경로가 아닌 수집 작업에서 수행한다.
- 개별 관광지 실패가 전체 수집 작업을 중단시키지 않도록 격리한다.
- 네트워크 timeout이 설정되어 있고 Scheduler 중복 실행을 방지한다.

백그라운드 수집은 호출 간격, Retry 정책과 다중 인스턴스 실행 제어가 우선이다. 이 정책을 적용한
뒤에도 장시간 장애 시 불필요한 호출이 누적되는지 확인하고 Circuit Breaker를 검토한다.

## 재검토 조건

다음 중 하나가 운영 지표나 장애 기록에서 반복적으로 확인되면 Circuit Breaker를 도입한다.

- LLM 또는 Elasticsearch의 재시도 소진율이 5분 구간에서 5% 이상 발생
- 동일 외부 의존성의 연속 실패가 10회 이상 발생
- 외부 장애 중 재시도로 인해 Chat 처리시간 또는 실행 스레드 사용량이 허용 범위를 초과
- TourAPI 장애 중 Scheduler 실행이 겹치거나 실패 호출이 지속적으로 누적
- 서비스 인스턴스 증가로 각 인스턴스의 독립 재시도가 외부 장애를 증폭

임계값은 초기 경보 기준이며 실제 트래픽 기준선을 확보한 뒤 조정한다.

## 도입 시 요구사항

- LLM, Elasticsearch, TourAPI 회로를 각각 독립적으로 관리한다.
- 공통 오류 타입의 `retryable` 분류와 일치하는 실패만 회로 실패로 집계한다.
- open, half-open, closed 전환과 차단 요청 수를 Micrometer로 노출한다.
- 기존 요청 timeout 예산을 Circuit Breaker보다 우선 적용한다.
- LLM과 Elasticsearch는 안전한 fallback이 없으므로 기존 Backend 오류 계약으로 빠르게 실패한다.
- 설정값은 환경변수로 외부화하고 장애·복구 시나리오 테스트를 추가한다.

## 현재 운영 순서

1. 재시도·소진·오류율 지표의 대시보드와 경보를 구성한다.
2. 실제 트래픽에서 오류율과 복구 시간 기준선을 수집한다.
3. 재검토 조건 충족 여부를 확인한다.
4. 필요한 의존성에만 Circuit Breaker를 선택적으로 적용한다.
