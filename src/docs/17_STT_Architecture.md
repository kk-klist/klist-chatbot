# Speech-to-Text 음성 질문 구조

## 선택한 방식

OpenAI Audio Transcriptions API와 `gpt-4o-mini-transcribe`를 기본 Provider로 사용한다. 기존
Spring `RestClient`만으로 multipart 요청을 보낼 수 있어 별도 SDK나 대규모 라이브러리가 필요하지
않다. 공식 가이드: https://developers.openai.com/api/docs/guides/speech-to-text

## API

`POST /internal/chat/query/audio`는 `multipart/form-data` 요청을 받는다.

- `request`: `requestId`, `sessionId`, `userId`, `context`, `timeoutMs` JSON
- `audio`: 음성 파일
- 인증: `X-Internal-Api-Key`
- 추적: `X-Trace-Id`

지원 확장자와 MIME 유형을 함께 검사한다. 지원 확장자는 mp3, mp4, mpeg, mpga, m4a, wav,
webm이고 애플리케이션 허용 크기는 최대 25MB다.

## 처리 흐름

```text
Backend multipart 요청
  → Internal API Key 인증
  → 음성 형식·크기 검증
  → SpeechTranscriptionService
  → SpeechToTextClient
  → OpenAI Audio Transcriptions API
  → 공백이 아닌 텍스트 검증
  → InternalChatQueryRequest.message
  → 기존 InternalChatQueryUseCase
  → 기존 검색·LLM·응답 흐름
```

음성 byte 배열은 HTTP 요청 처리 중에만 메모리에 존재한다. Entity, Repository, 파일 저장 로직을
추가하지 않았으며 STT 결과 텍스트만 기존 질문 DTO로 변환한다.

## 오류 계약

| 상황 | HTTP | code |
|---|---:|---|
| 빈 파일·지원하지 않는 형식 | 400 | `STT_INVALID_FILE` |
| 25MB 초과 | 413 | `STT_FILE_TOO_LARGE` |
| 변환 결과가 비어 있음 | 422 | `STT_EMPTY_RESULT` |
| STT timeout | 504 | `STT_TIMEOUT` |
| Provider 장애·비활성·설정 오류 | 503 | `STT_UNAVAILABLE` |

Provider 원문 오류와 API Key는 오류 응답에 포함하지 않는다.

## 환경변수

```text
STT_OPENAI_ENABLED=true
STT_OPENAI_API_KEY=<secret>
STT_OPENAI_BASE_URL=https://api.openai.com/v1
STT_OPENAI_MODEL=gpt-4o-mini-transcribe
STT_OPENAI_LANGUAGE=ko
STT_OPENAI_CONNECT_TIMEOUT=3s
STT_TIMEOUT=20s
STT_MAX_FILE_SIZE=26214400
```

API Key는 소스나 프로필 파일에 기록하지 않고 Secret Manager를 통해 환경변수로 주입한다.
