package ai.illooso.app;

import static org.junit.Assert.*;

import java.time.Instant;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class ReviewInsightsTest {
  private static final Instant NOW = Instant.parse("2026-11-14T09:00:00Z");

  @Test
  public void midnightUsesClockDistanceAndStillRequiresMatchingDayType() {
    Review monday = review("night", "2026-11-09T23:50:00+09:00", "instant", 0);
    ReviewInsights.Context tuesday = context("2026-11-10T00:10:00+09:00", "instant");
    assertEquals(20, ReviewInsights.timeDistanceMinutes(monday, tuesday));
    assertTrue(ReviewInsights.isSimilar(monday, tuesday));
    Review friday = review("friday", "2026-11-13T23:50:00+09:00", "instant", 0);
    assertFalse(ReviewInsights.isSimilar(friday, context("2026-11-14T00:10:00+09:00", "instant")));
    assertTrue(ReviewInsights.isSimilar(monday, context("2026-11-10T01:50:00+09:00", "instant")));
    assertFalse(ReviewInsights.isSimilar(monday, context("2026-11-10T01:51:00+09:00", "instant")));
  }

  @Test
  public void matchingArrivalCohortPrecedesNewerHelpfulButUnrelatedReviews() {
    Review match = review("match", "2026-11-07T17:30:00+09:00", "instant", 1);
    Review weekday = review("weekday", "2026-11-13T18:00:00+09:00", "instant", 90);
    Review differentPurpose = review("purpose", "2026-11-14T17:50:00+09:00", "parking", 100);
    ReviewInsights.Result result =
        ReviewInsights.evaluate(
            List.of(weekday, differentPurpose, match),
            "p1",
            context("2026-11-14T18:04:00+09:00", "instant"),
            ReviewInsights.Sort.SIMILAR,
            NOW);
    assertEquals("match", result.ordered.get(0).id);
    assertEquals(List.of("match"), ids(result.cohort));
    assertEquals(3, result.totalCount);
    assertEquals(1, result.summaryCount);
  }

  @Test
  public void personalizedSummaryUsesOnlyCohortAndCountsOneVotePerReview() {
    Review match =
        tagged(
            "match",
            "2026-11-07T17:30:00+09:00",
            List.of(Review.Tag.EASY_PICKUP, Review.Tag.EASY_PICKUP),
            List.of(Review.Tag.PEDESTRIANS));
    Review unrelated =
        tagged(
            "other",
            "2026-11-10T17:30:00+09:00",
            List.of(Review.Tag.COMFORTABLE_WAIT),
            List.of(Review.Tag.TRAFFIC));
    ReviewInsights.Result similar =
        ReviewInsights.evaluate(
            List.of(match, unrelated),
            "p1",
            context("2026-11-14T18:04:00+09:00", "instant"),
            ReviewInsights.Sort.SIMILAR,
            NOW);
    assertEquals(1, similar.positives.size());
    assertEquals(Review.Tag.EASY_PICKUP, similar.positives.get(0).tag);
    assertEquals(1, similar.positives.get(0).count);
    assertEquals(Review.Tag.PEDESTRIANS, similar.cautions.get(0).tag);
    ReviewInsights.Result all =
        ReviewInsights.evaluate(
            List.of(match, unrelated),
            "p1",
            context("2026-11-14T18:04:00+09:00", "instant"),
            ReviewInsights.Sort.LATEST,
            NOW);
    assertEquals(2, all.summaryCount);
    assertEquals(2, all.positives.size());
    assertEquals(2, all.cautions.size());
    assertFalse(all.personalized);
  }

  @Test
  public void emptyCohortDoesNotBorrowGeneralProsOrCautions() {
    Review unrelated =
        tagged(
            "other",
            "2026-11-10T08:30:00+09:00",
            List.of(Review.Tag.EASY_PICKUP),
            List.of(Review.Tag.TRAFFIC));
    ReviewInsights.Result result =
        ReviewInsights.evaluate(
            List.of(unrelated),
            "p1",
            context("2026-11-14T18:00:00+09:00", "instant"),
            ReviewInsights.Sort.SIMILAR,
            NOW);
    assertEquals(1, result.totalCount);
    assertEquals(0, result.summaryCount);
    assertTrue(result.cohort.isEmpty());
    assertTrue(result.positives.isEmpty());
    assertTrue(result.cautions.isEmpty());
    assertEquals(List.of("other"), ids(result.ordered));
  }

  @Test
  public void futureVisitsPublicationsOtherPlacesAndDuplicateIdsAreExcluded() {
    Review valid = review("valid", "2026-11-07T17:30:00+09:00", "instant", 0);
    Review futureVisit = review("future", "2026-11-15T17:30:00+09:00", "instant", 0);
    Review futurePublication =
        new Review(
            "future-publication",
            "p1",
            "예시",
            "후기",
            valid.visitedAt,
            NOW.plusSeconds(1).toEpochMilli(),
            "instant",
            0,
            true,
            List.of(),
            List.of());
    Review otherPlace =
        new Review(
            "other-place",
            "p2",
            "예시",
            "후기",
            valid.visitedAt,
            valid.createdAt,
            "instant",
            0,
            true,
            List.of(),
            List.of());
    ReviewInsights.Result result =
        ReviewInsights.evaluate(
            List.of(valid, futureVisit, futurePublication, otherPlace, valid),
            "p1",
            context("2026-11-14T18:00:00+09:00", "instant"),
            ReviewInsights.Sort.LATEST,
            NOW);
    assertEquals(List.of("valid"), ids(result.ordered));
  }

  @Test
  public void latestMeansVisitTimeAndHelpfulTiesUseVisitTimeWithStableId() {
    Review newerVisit = review("b", "2026-11-13T14:00:00+09:00", "instant", 7);
    Review olderVisit = review("old", "2026-11-10T14:00:00+09:00", "instant", 9);
    Review sameDate = review("a", "2026-11-13T14:00:00+09:00", "instant", 7);
    Review latePublication =
        new Review(
            "late",
            "p1",
            "예시",
            "나중에 작성",
            olderVisit.visitedAt,
            NOW.toEpochMilli(),
            "instant",
            0,
            true,
            List.of(),
            List.of());
    List<Review> reviews = List.of(newerVisit, olderVisit, sameDate, latePublication);
    ReviewInsights.Context context = context("2026-11-14T18:00:00+09:00", "instant");
    assertEquals(
        List.of("a", "b", "late", "old"),
        ids(
            ReviewInsights.evaluate(reviews, "p1", context, ReviewInsights.Sort.LATEST, NOW)
                .ordered));
    assertEquals(
        List.of("old", "a", "b", "late"),
        ids(
            ReviewInsights.evaluate(reviews, "p1", context, ReviewInsights.Sort.HELPFUL, NOW)
                .ordered));
  }

  @Test
  public void legacyLocalReviewWithUnknownPurposeIsGeneralOnly() {
    Review legacy = review("local", "2026-11-14T17:30:00+09:00", "", 0);
    ReviewInsights.Context context = context("2026-11-14T18:00:00+09:00", "instant");
    assertFalse(ReviewInsights.isSimilar(legacy, context));
    ReviewInsights.Result result =
        ReviewInsights.evaluate(List.of(legacy), "p1", context, ReviewInsights.Sort.SIMILAR, NOW);
    assertEquals(1, result.totalCount);
    assertEquals(0, result.summaryCount);
  }

  @Test
  public void fixtureVisitsArePastStableAndAvailableForEveryBrowsablePlace() {
    List<Review> samples = ReviewRepository.samples(NOW);
    assertFalse(samples.isEmpty());
    assertEquals(ids(samples), ids(ReviewRepository.samples(NOW)));
    for (Review review : samples) {
      assertTrue(review.sample);
      assertTrue(review.visitedAt <= NOW.toEpochMilli());
      assertTrue(review.createdAt <= NOW.toEpochMilli());
      assertTrue(review.createdAt >= review.visitedAt);
      assertTrue(
          PlaceRepository.isOpenAt(
              PlaceRepository.find(review.placeId), ReviewInsights.visitTime(review).getHour()));
    }
    for (String id : List.of("p1", "p2", "p3", "p4", "p5", "p8"))
      assertTrue(samples.stream().anyMatch(r -> r.placeId.equals(id)));
    assertThrows(UnsupportedOperationException.class, samples::clear);
    List<Review.Tag> tags = new ArrayList<>(List.of(Review.Tag.EASY_PICKUP));
    Review copied = new Review("x", "p1", "예시", "후기", 1, 2, "instant", 0, true, tags, List.of());
    tags.clear();
    assertEquals(1, copied.positiveTags.size());
    assertThrows(UnsupportedOperationException.class, copied.positiveTags::clear);
  }

  private static ReviewInsights.Context context(String time, String mode) {
    return new ReviewInsights.Context(ZonedDateTime.parse(time), mode);
  }

  private static Review review(String id, String time, String mode, int helpful) {
    long visited = ZonedDateTime.parse(time).toInstant().toEpochMilli();
    return new Review(
        id, "p1", "예시 운전자", "예시 후기", visited, visited, mode, helpful, true, List.of(), List.of());
  }

  private static Review tagged(
      String id, String time, List<Review.Tag> good, List<Review.Tag> caution) {
    long visited = ZonedDateTime.parse(time).toInstant().toEpochMilli();
    return new Review(
        id, "p1", "예시 운전자", "예시 후기", visited, visited, "instant", 0, true, good, caution);
  }

  private static List<String> ids(List<Review> reviews) {
    return reviews.stream().map(r -> r.id).collect(Collectors.toList());
  }
}
