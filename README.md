# ochestration

`ochestration`은 다중 PG(Provider) 결제 승인을 단일 진입점에서 오케스트레이션하는 백엔드 서비스입니다.  
핵심 목표는 **가용한 Provider를 동적으로 선택**하고, **실패 유형에 따라 fallback 전략을 다르게 적용**하는 것입니다.

## 1. 프로젝트 목적

단일 PG 직접 연동 구조에서는 아래 문제가 자주 발생합니다.

- 특정 PG 장애 시 결제 승인 성공률 급락
- 실패 코드별 재시도/중단 정책이 서비스마다 분산
- 운영자가 Provider 연결 상태/헬스 상태를 즉시 제어하기 어려움

이 프로젝트는 위 문제를 해결하기 위해 다음을 제공합니다.

- Provider 연결 상태 + 헬스 상태 + 기능(capability) 기반 필터링
- `RETRYABLE_TECHNICAL` 실패 시 자동 fallback
- `NON_RETRYABLE_BUSINESS` 실패 시 즉시 중단
- 승인 시도 이력(`attempts`)과 선택 근거(`selectionSummary`)를 함께 저장/조회

## 2. 기술 스택

- Kotlin
- Spring Boot 4.0.3
- Java 21
- Spring MVC + WebFlux(WebClient)
- Coroutines
- In-memory Repository (데모/리허설 용도)

## 3. 아키텍처

패키지 구조는 역할 중심으로 분리되어 있습니다.

- `application/orchestration`
  - `PgOrchestrator`: 승인 흐름 실행
  - `ProviderSelectionPolicy`: 후보 필터링/우선순위 결정
- `application/service`
  - `UnifiedPaymentService`: approve/get/cancel 유스케이스
  - `ProviderManagementService`: connect/disconnect/health 관리
- `application/port/out`
  - `PaymentProviderGateway` 인터페이스 및 Gateway DTO
- `domain/model`
  - `Payment`, `PaymentAttempt`, `SelectionSummary`, `FailureCategory` 등
- `domain/service`
  - `FailureClassifier`, `ProviderCapabilityRegistry`
- `infrastructure/gateway/pg`
  - `TossPgAdapter`(실연동), `KakaoPayPgAdapter`/`InicisPgAdapter`(stub)
- `infrastructure/persistence/memory`
  - Payment/Provider 상태 in-memory 저장소
- `presentation/web/controller`
  - 결제/Provider 관리 REST API

## 4. 오케스트레이션 규칙

## 4.1 Provider 선정

`ProviderSelectionPolicy`가 아래 순서로 후보를 추립니다.

1. 우선순위 구성
   - 기본: `TOSS -> KAKAOPAY -> INICIS`
   - 요청에 `preferredPrimaryProvider`가 있으면 최우선 배치
2. 연결 상태 확인
   - `DISCONNECTED`는 제외
3. 헬스 상태 확인
   - `UNHEALTHY`는 제외
4. 승인 capability 확인
   - approve 미지원 Provider는 제외

최종적으로 `selectedPrimaryReason`과 `filteredOutProviders`를 남겨 추적 가능하게 만듭니다.

## 4.2 실패 분류 기반 fallback

`FailureClassifier` 기준:

- `NON_RETRYABLE_BUSINESS`
  - 예: `CARD_LIMIT_EXCEEDED`, `INVALID_REQUEST`
  - fallback 없이 즉시 실패 처리
- `RETRYABLE_TECHNICAL`
  - 예: `PG_TIMEOUT`, `NETWORK_ERROR`, `INTERNAL_SERVER_ERROR`
  - 다음 후보 Provider로 fallback
- `UNKNOWN`
  - 미분류 코드

## 5. API

## 5.1 Payment

- `POST /api/payments/approve`
- `GET /api/payments/{paymentId}`
- `POST /api/payments/{paymentId}/cancel`

### approve 요청 예시

```json
{
  "orderId": "order-001",
  "amount": 15000,
  "currency": "KRW",
  "preferredPrimaryProvider": "TOSS",
  "metadata": {
    "paymentKey": "tgen_..."
  }
}
```

`TossPgAdapter`는 `metadata.paymentKey`를 사용해 토스 confirm API를 호출합니다.

## 5.2 Provider 운영

- `POST /api/providers/connect`
- `DELETE /api/providers/{provider}`
- `GET /api/providers`
- `GET /api/providers/capabilities`
- `GET /api/providers/health`
- `POST /api/providers/health`

## 6. 시드 데이터

애플리케이션 기동 시(`DataSeeder`) 기본 상태:

- merchant: `merchant-001`
- 연결: `TOSS`, `KAKAOPAY`
- 미연결: `INICIS`
- 헬스: 전 Provider `HEALTHY`

## 7. 실행 방법

## 7.1 일반 실행

```bash
./gradlew bootRun
```

기본 포트: `8080`

## 7.2 테스트

```bash
./gradlew test
```

## 7.3 데모 리허설 실행

프로젝트에 데모 스크립트가 포함되어 있습니다.

```bash
./scripts/start_and_demo.sh
```

수행 내용:

- 백엔드 기동
- Provider 연결/해제 API 호출
- 승인 시나리오 9종 실행
- 조회 + 취소 시연

## 8. 환경변수

`application.yaml` 기준 핵심 값:

- `server.port` (기본 `8080`)
- `gateway.toss-test.base-url` (기본 `https://api.tosspayments.com`)
- `gateway.toss-test.secret-key`
- `gateway.toss-test.connect-timeout-ms` (기본 `3000`)
- `gateway.toss-test.read-timeout-ms` (기본 `5000`)

## 9. 현재 범위와 한계

- 영속 저장소 대신 in-memory repository 사용
- merchant는 샘플 값(`merchant-001`) 고정
- KakaoPay/Inicis는 stub adapter
- 승인 멱등성은 데모 수준(요청 idempotency key 저장/검증 고도화 전)

## 10. 면접 관점에서 강조할 점

- 실패 유형 기반 fallback 정책을 도메인 규칙으로 명시한 점
- Provider 상태(연결/헬스/capability)를 런타임에 조합해 선택하는 점
- 결과뿐 아니라 “왜 그 Provider가 선택/제외되었는지”를 `selectionSummary`로 남긴 점
- 실제 연동(Toss) + stub 연동(Kakao/Inicis) 병행으로 확장 경로를 열어둔 점
