# F12 Delivery Worker Plan

> 작성일: 2026-05-04
> 대상 브랜치: `feat/f12-webhook-delivery` (`develop` 기준)
> 기준 문서: `jobs/f12-webhook-delivery/f12-webhook-delivery-plan.md`

## 1. Executive Summary

저장된 `WebhookDelivery`를 주기적으로 조회해 HTTP POST로 발송하고, 결과에 따라 `SENT`, `FAILED`, `DEAD` 상태로 갱신하는 워커를 만든다.

## 2. Problem Statement

웹훅은 결제 API 응답 경로와 분리되어야 한다. 따라서 결제 처리 중 외부 URL로 직접 요청하지 않고, DB에 쌓인 delivery를 별도 스케줄러가 처리해야 한다.

## 3. Proposed Solution

`@Scheduled` 기반 `WebhookDispatchService`를 추가한다. 워커는 한 번에 제한된 개수만 조회하고, 각 delivery별로 서명 생성, HTTP POST, 상태 저장을 수행한다.

## 4. Feature Specification

- 스케줄러 주기: 10초
- 기본 배치 크기: 50
- HTTP timeout: 10초
- 성공 기준: 2xx
- 실패 기준: 비 2xx, timeout, client exception
- 헤더:
  - `Content-Type: application/json`
  - `X-Webhook-Signature: sha256=<hex>`
  - `X-Webhook-Event: <event type>`
  - `X-Webhook-Event-Id: <event id>`
  - `X-Webhook-Delivery-Id: <delivery id>`

## 5. Technical Plan

- `application/port/out/WebhookSigner.kt`, `infrastructure/webhook/HmacWebhookSigner.kt` 추가
- `application/port/out/WebhookHttpClient.kt` 추가. HTTP port는 delivery뿐 아니라 endpoint URL/signingSecret 조회 결과를 함께 받아 발송한다.
- `infrastructure/webhook/DefaultWebhookHttpClient.kt` 추가
- `application/service/WebhookDispatchService.kt` 추가
- `application/port/out/WebhookUrlValidator.kt`, `infrastructure/webhook/DefaultWebhookUrlValidator.kt` 추가. 발송 직전 URL을 재검증해 DNS rebinding과 내부망 호출 위험을 줄인다.
- `OchestrationApplication` 또는 config에 `@EnableScheduling` 추가
- `application.yaml`에 webhook dispatch 설정 추가

## 6. Success Metrics & Verification

- 같은 secret, eventId, payload는 항상 같은 HMAC hex를 만든다.
- payload만 같은데 eventId가 다르면 서명이 달라진다.
- 2xx 응답이면 delivery가 `SENT`가 된다.
- 500 응답이나 timeout이면 attempt가 증가하고 재시도 시간이 설정된다.
- 5번째 실패는 `DEAD`가 된다.
- 발송 직전 URL 보안 검증 실패는 재시도하지 않고 `DEAD`가 된다.

## 7. Risks & Mitigations

- `@Scheduled` 메서드에서 suspend client를 직접 호출하면 실행 모델이 꼬일 수 있다. 워커와 HTTP client는 동기 메서드로 두고 외부 HTTP 호출을 트랜잭션 밖에서 수행한다.
- HTTP 발송과 상태 저장 사이에 장애가 나면 중복 발송될 수 있다. 이는 웹훅 시스템의 일반적인 at-least-once 특성으로 문서화하고 event id를 제공한다.
- 병렬 서버 인스턴스에서는 같은 row를 중복 조회할 수 있다. MVP에서는 단일 인스턴스를 가정하고, 다중 인스턴스 잠금은 별도 작업으로 둔다.
- 발송 직전 DNS 재검증은 DNS rebinding 위험을 줄이지만 완전한 네트워크 격리는 아니다. 운영 환경에서는 egress firewall 또는 metadata endpoint 차단 정책도 함께 필요하다.

## 8. Out Of Scope

- 분산락
- 큐 기반 worker
- 수동 재발송
- 발송 이력 조회 API

## 9. Commit Plan Draft

`feat(webhook): 웹훅 발송 워커 추가`

## 10. Deep Review

워커는 장애가 나도 결제 처리 자체를 망가뜨리면 안 된다. 반대로 실패를 삼켜서 delivery 상태가 그대로 남으면 무한 반복이 된다. 따라서 모든 발송 결과는 반드시 도메인 상태 전이 메서드를 통해 저장하고, 예외는 delivery 단위로 격리한다.
