# F12 Endpoint API Plan

> 작성일: 2026-05-04
> 대상 브랜치: `feat/f12-webhook-delivery` (`develop` 기준)
> 기준 문서: `jobs/f12-webhook-delivery/f12-webhook-delivery-plan.md`

## 1. Executive Summary

가맹점이 웹훅 수신 URL을 등록, 조회, 수정, 비활성화/삭제할 수 있는 `/api/webhook-endpoints` API를 추가한다.

## 2. Problem Statement

웹훅 delivery를 만들려면 merchant별 활성 endpoint 목록이 필요하다. 현재는 이를 관리하는 API와 저장소가 없다.

## 3. Proposed Solution

기존 컨트롤러 스타일에 맞춰 `WebhookEndpointController`와 DTO를 추가한다. 인증된 `MerchantPrincipal`의 `merchantId`만 사용해 자기 엔드포인트만 관리하게 한다.

## 4. Feature Specification

- `POST /api/webhook-endpoints`: 엔드포인트 등록
- `GET /api/webhook-endpoints`: 내 엔드포인트 목록 조회
- `GET /api/webhook-endpoints/{endpointId}`: 단건 조회
- `PATCH /api/webhook-endpoints/{endpointId}`: URL, 상태, 설명 수정
- `DELETE /api/webhook-endpoints/{endpointId}`: 삭제 또는 비활성화
- 가맹점당 최대 5개
- 생성 시 signing secret 자동 발급

## 5. Technical Plan

- `application/service/WebhookEndpointService.kt` 추가
- `presentation/web/controller/WebhookEndpointController.kt` 추가
- `presentation/web/dto/WebhookEndpointDtos.kt` 추가
- `ApiExceptionHandler`에 `WebhookException` 매핑 추가
- 요청 URL은 우선 `http://` 또는 `https://` 형식만 허용한다.

## 6. Success Metrics & Verification

- endpoint 등록 시 secret이 생성되고 응답에는 필요한 경우 최초 1회만 노출한다.
- endpoint 목록/단건 조회/수정/삭제 응답에는 signing secret 원문을 절대 포함하지 않는다.
- 5개 초과 등록은 409 또는 400 계열 에러로 거절된다.
- 다른 merchant의 endpointId는 조회/수정/삭제할 수 없다.
- inactive endpoint는 조회에는 보이지만 신규 delivery 생성 대상에서는 제외된다.

## 7. Risks & Mitigations

- secret을 응답에 계속 노출하면 보안 위험이 크다. 생성 응답에만 최초 1회 노출하고, 일반 조회/수정/삭제 응답에서는 원문을 제외한다. 테스트에서 `signingSecret` 필드가 응답 DTO에 없는지 검증한다.
- DELETE를 물리 삭제로 구현하면 과거 delivery의 endpoint 참조가 깨질 수 있다. MVP에서는 비활성화로 처리하는 방향을 우선한다.
- URL 검증을 과하게 만들면 정상 사내 URL을 막을 수 있다. MVP는 스킴 검증으로 제한한다.

## 8. Out Of Scope

- secret rotation API
- endpoint test-send API
- endpoint별 발송 이력 조회

## 9. Commit Plan Draft

`feat(webhook): 웹훅 엔드포인트 관리 API 추가`

## 10. Deep Review

API는 제품 표면이므로 저장소 내부 구조를 그대로 노출하지 않는다. 특히 signing secret은 도메인에는 필요하지만 일반 조회 응답에는 필요하지 않다. 또한 삭제는 기획의 "삭제" 표현과 delivery 참조 무결성 사이의 충돌이 있으므로, 구현 전에 비활성화 방식으로 해석했음을 커밋과 PR 본문에 남긴다.
