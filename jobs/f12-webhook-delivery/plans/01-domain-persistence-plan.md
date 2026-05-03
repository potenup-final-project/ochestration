# F12 Domain Persistence Plan

> 작성일: 2026-05-04
> 대상 브랜치: `feat/f12-webhook-delivery` (`develop` 기준)
> 기준 문서: `jobs/f12-webhook-delivery/f12-webhook-delivery-plan.md`

## 1. Executive Summary

웹훅 엔드포인트와 delivery를 표현하는 도메인 모델, 포트, JPA 엔티티/어댑터, DB 스키마를 먼저 추가한다. 이 단계는 HTTP 발송이나 결제 훅을 건드리지 않는다.

## 2. Problem Statement

현재 `src/main/kotlin/com/pg/ochestration`에는 웹훅 도메인과 테이블이 없다. 이후 워커와 API가 의존할 안정적인 저장 계약이 먼저 필요하다.

## 3. Proposed Solution

기존 `Payment`, `MerchantApiKey`, `IdempotencyRecord` 패턴에 맞춰 도메인 모델과 JPA 엔티티를 분리한다. 포트는 `application/port/out`에 두고, JPA 구현은 `infrastructure/persistence/jpa`에 둔다.

## 4. Feature Specification

- `WebhookEndpoint`: `endpointId`, `merchantId`, `url`, `signingSecret`, `status`, `description`, `createdAt`, `updatedAt`
- `WebhookDelivery`: `deliveryId`, `endpointId`, `merchantId`, `eventType`, `eventId`, `paymentId`, `payload`, `status`, `attemptCount`, `maxAttempts`, `nextRetryAt`, `lastAttemptedAt`, `lastResponseCode`, `lastError`, `createdAt`
- `WebhookEventType`: `PAYMENT_APPROVED`, `PAYMENT_FAILED`, `PAYMENT_CANCELED`
- DB 저장 값은 원본 요구사항의 외부 문자열과 맞추기 위해 이벤트 payload에서는 `PAYMENT.APPROVED` 형식을 사용한다.

## 5. Technical Plan

- `domain/model/WebhookEndpoint.kt` 추가
- `domain/model/WebhookDelivery.kt` 추가
- `domain/exception/WebhookException.kt` 추가
- `application/port/out/WebhookEndpointRepository.kt` 추가
- `application/port/out/WebhookDeliveryRepository.kt` 추가
- `schema.sql`에 `webhook_endpoints`, `webhook_deliveries` 추가
- `infrastructure/persistence/jpa/entity/WebhookEndpointJpaEntity.kt` 추가
- `infrastructure/persistence/jpa/entity/WebhookDeliveryJpaEntity.kt` 추가
- Spring Data repository와 adapter 추가

## 6. Success Metrics & Verification

- `WebhookDelivery.markSent()`는 `SENT`, attempt 증가, retry 제거를 수행한다.
- `WebhookDelivery.markFailed()`는 maxAttempts 전에는 `FAILED`, maxAttempts 도달 시 `DEAD`를 반환한다.
- `findDueForDispatch(now, limit)`는 `PENDING`과 재시도 시간이 지난 `FAILED`만 반환한다.
- JPA adapter 저장/조회 테스트가 통과한다.

## 7. Risks & Mitigations

- `signingSecret` 평문 저장은 운영 보안상 약하다. F12 MVP에서는 기획 범위대로 저장하고, 암호화는 별도 작업으로 둔다.
- `eventType` 내부 enum 이름과 외부 payload 문자열이 다르면 혼동될 수 있다. enum에 외부 표시값 프로퍼티를 둔다.
- `nextRetryAt`이 null인 `FAILED` row가 생기면 재시도 대상에서 누락된다. 도메인 전이 메서드에서만 실패 상태를 만들도록 한다.

## 8. Out Of Scope

- HTTP 발송
- 스케줄러
- 웹 API
- 결제 승인/취소 서비스 변경

## 9. Commit Plan Draft

`feat(webhook): 웹훅 도메인과 저장소 추가`

## 10. Deep Review

이 단계는 이후 기능의 바닥이므로 과한 추상화보다 명확한 저장 계약이 중요하다. 단일 테이블 delivery 방식은 엔드포인트별 발송 상태를 단순하게 만들지만 같은 결제 이벤트에 delivery row가 여러 개 생긴다. 따라서 `eventId`를 delivery와 별도로 저장해 같은 이벤트의 여러 발송을 추적할 수 있게 한다.
