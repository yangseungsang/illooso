package ai.illooso.app;

import java.util.List;
import java.util.Objects;

/** Immutable arrival experience. A review never establishes a right to stop or park. */
public final class Review {
  public enum Tag {
    EASY_PICKUP("탑승 지점을 찾기 쉬워요"),
    SHORT_WALK("목적지까지 걷기 가까워요"),
    CLEAR_ACCESS("진입 방향을 찾기 쉬워요"),
    COMFORTABLE_WAIT("주차 구획에서 기다리기 편해요"),
    TRAFFIC("차량이 몰리는 시간엔 혼잡해요"),
    PEDESTRIANS("보행자 통행을 살펴야 해요"),
    NARROW_ACCESS("진입로가 좁다는 의견이 있어요"),
    LIMITED_VISIBILITY("어두울 때 시야를 확인해요"),
    SIGNAGE("현장 정차 표지를 확인해요"),
    ROAD_CHANGE("현장 도로 상태 확인이 필요해요"),
    PAID_PARKING("주차 요금을 미리 확인해요");

    public final String label;

    Tag(String label) {
      this.label = label;
    }
  }

  public final String id, placeId, author, text, mode;
  public final long visitedAt, createdAt;
  public final int helpful;
  public final boolean sample;
  public final List<Tag> positiveTags, cautionTags;

  public Review(
      String id,
      String placeId,
      String author,
      String text,
      long visitedAt,
      long createdAt,
      String mode,
      int helpful,
      boolean sample,
      List<Tag> positiveTags,
      List<Tag> cautionTags) {
    this.id = Objects.requireNonNull(id, "id");
    this.placeId = Objects.requireNonNull(placeId, "placeId");
    this.author = Objects.requireNonNull(author, "author");
    this.text = Objects.requireNonNull(text, "text");
    this.visitedAt = visitedAt;
    this.createdAt = createdAt;
    this.mode = mode == null ? "" : mode;
    this.helpful = Math.max(0, helpful);
    this.sample = sample;
    this.positiveTags = positiveTags == null ? List.of() : List.copyOf(positiveTags);
    this.cautionTags = cautionTags == null ? List.of() : List.copyOf(cautionTags);
  }

  /** Returns the same experience with a display/ranking count, without changing its evidence. */
  public Review withHelpful(int count) {
    return new Review(
        id,
        placeId,
        author,
        text,
        visitedAt,
        createdAt,
        mode,
        count,
        sample,
        positiveTags,
        cautionTags);
  }
}
