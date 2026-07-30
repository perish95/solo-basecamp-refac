package com.basecamp.backend.domain.camp.dto.response;

import lombok.Builder;
import lombok.Getter;

// GET /api/v1/camps/fetch/status 응답. 비동기로 도는 고캠핑 전체 동기화의 현재 진행 상태를 알려준다.
@Getter
@Builder
public class GocampingSyncStatusResponseDto {

  public enum Status {
    IDLE,
    RUNNING,
    DONE,
    CANCELLED,
    FAILED
  }

  private Status status;
  private int totalReceived;
  private int totalSaved;
  private String message;

  public static GocampingSyncStatusResponseDto idle() {
    return GocampingSyncStatusResponseDto.builder()
        .status(Status.IDLE)
        .message("아직 실행된 적이 없습니다")
        .build();
  }

  public static GocampingSyncStatusResponseDto running() {
    return GocampingSyncStatusResponseDto.builder()
        .status(Status.RUNNING)
        .message("동기화가 진행 중입니다")
        .build();
  }

  public static GocampingSyncStatusResponseDto done(int totalReceived, int totalSaved) {
    return GocampingSyncStatusResponseDto.builder()
        .status(Status.DONE)
        .totalReceived(totalReceived)
        .totalSaved(totalSaved)
        .message(String.format("총 %d건 수신, %d건 신규 저장 완료", totalReceived, totalSaved))
        .build();
  }

  public static GocampingSyncStatusResponseDto cancelled(int totalReceived, int totalSaved) {
    return GocampingSyncStatusResponseDto.builder()
        .status(Status.CANCELLED)
        .totalReceived(totalReceived)
        .totalSaved(totalSaved)
        .message(String.format("취소되었습니다 (수신 %d건, 저장 %d건까지 진행)", totalReceived, totalSaved))
        .build();
  }

  public static GocampingSyncStatusResponseDto failed(String message) {
    return GocampingSyncStatusResponseDto.builder().status(Status.FAILED).message(message).build();
  }
}
