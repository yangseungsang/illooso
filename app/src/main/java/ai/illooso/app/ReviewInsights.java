package ai.illooso.app;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/** Deterministic, explainable review ranking and tag aggregation; no generated claims or AI API. */
public final class ReviewInsights {
  public static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  public static final int SIMILAR_WINDOW_MINUTES = 120;

  public enum Sort {
    SIMILAR,
    LATEST,
    HELPFUL
  }

  public static final class Context {
    public final ZonedDateTime arrival;
    public final String mode;

    public Context(ZonedDateTime arrival, String mode) {
      this.arrival = Objects.requireNonNull(arrival, "arrival").withZoneSameInstant(SEOUL);
      this.mode = mode == null ? "" : mode;
    }
  }

  public static final class TagCount {
    public final Review.Tag tag;
    public final int count;

    TagCount(Review.Tag tag, int count) {
      this.tag = tag;
      this.count = count;
    }
  }

  public static final class Result {
    public final List<Review> ordered, cohort;
    public final List<TagCount> positives, cautions;
    public final int totalCount, summaryCount;
    public final boolean personalized;

    Result(List<Review> ordered, List<Review> cohort, boolean personalized) {
      this.ordered = List.copyOf(ordered);
      this.cohort = List.copyOf(cohort);
      this.personalized = personalized;
      totalCount = ordered.size();
      List<Review> basis = personalized ? cohort : ordered;
      summaryCount = basis.size();
      positives = countTags(basis, true);
      cautions = countTags(basis, false);
    }
  }

  private ReviewInsights() {}

  /**
   * Filters the selected place and excludes future visits/publications before ranking. Similar
   * sorting keeps non-matching reviews available after the matching cohort, but those reviews never
   * enter the personalized summary. Callers can label the two groups separately.
   */
  public static Result evaluate(
      List<Review> source, String placeId, Context context, Sort sort, Instant now) {
    Objects.requireNonNull(context, "context");
    Objects.requireNonNull(sort, "sort");
    Objects.requireNonNull(now, "now");
    List<Review> ordered = new ArrayList<>();
    Set<String> seen = new HashSet<>();
    long present = now.toEpochMilli();
    if (source != null) {
      for (Review review : source) {
        if (review == null
            || !review.placeId.equals(placeId)
            || review.visitedAt <= 0
            || review.createdAt <= 0
            || review.visitedAt > present
            || review.createdAt > present
            || !seen.add(review.id)) continue;
        ordered.add(review);
      }
    }
    Comparator<Review> latest =
        Comparator.comparingLong((Review r) -> r.visitedAt)
            .reversed()
            .thenComparing(Comparator.comparingLong((Review r) -> r.createdAt).reversed())
            .thenComparing(r -> r.id);
    Comparator<Review> comparator;
    if (sort == Sort.HELPFUL) {
      comparator =
          Comparator.comparingInt((Review r) -> r.helpful).reversed().thenComparing(latest);
    } else if (sort == Sort.SIMILAR) {
      comparator =
          Comparator.comparingInt((Review r) -> isSimilar(r, context) ? 0 : 1)
              .thenComparingInt(r -> similarityPenalty(r, context))
              .thenComparing(latest);
    } else comparator = latest;
    ordered.sort(comparator);
    List<Review> cohort = new ArrayList<>();
    for (Review review : ordered) if (isSimilar(review, context)) cohort.add(review);
    return new Result(ordered, cohort, sort == Sort.SIMILAR);
  }

  public static boolean isSimilar(Review review, Context context) {
    return review != null
        && context != null
        && isKnownMode(context.mode)
        && context.mode.equals(review.mode)
        && sameDayType(review, context)
        && timeDistanceMinutes(review, context) <= SIMILAR_WINDOW_MINUTES;
  }

  /** Distance on a clock, not elapsed date distance: 23:50 and 00:10 are 20 minutes apart. */
  public static int timeDistanceMinutes(Review review, Context context) {
    ZonedDateTime visit = visitTime(review);
    int visitMinutes = visit.getHour() * 60 + visit.getMinute();
    int arrivalMinutes = context.arrival.getHour() * 60 + context.arrival.getMinute();
    int distance = Math.abs(visitMinutes - arrivalMinutes);
    return Math.min(distance, 1440 - distance);
  }

  public static boolean sameDayType(Review review, Context context) {
    return isWeekend(visitTime(review).getDayOfWeek()) == isWeekend(context.arrival.getDayOfWeek());
  }

  public static ZonedDateTime visitTime(Review review) {
    return Instant.ofEpochMilli(review.visitedAt).atZone(SEOUL);
  }

  public static boolean isWeekend(DayOfWeek day) {
    return day == DayOfWeek.SATURDAY || day == DayOfWeek.SUNDAY;
  }

  private static boolean isKnownMode(String mode) {
    return "instant".equals(mode) || "wait".equals(mode) || "parking".equals(mode);
  }

  private static int similarityPenalty(Review review, Context context) {
    // Explainable approximate similarity: time is continuous; day type and purpose add penalties.
    return timeDistanceMinutes(review, context)
        + (sameDayType(review, context) ? 0 : 240)
        + (isKnownMode(review.mode) && review.mode.equals(context.mode) ? 0 : 180);
  }

  private static List<TagCount> countTags(List<Review> reviews, boolean positive) {
    Map<Review.Tag, Integer> counts = new EnumMap<>(Review.Tag.class);
    for (Review review : reviews) {
      // One person mentioning the same tag twice still contributes one supporting review.
      for (Review.Tag tag : new HashSet<>(positive ? review.positiveTags : review.cautionTags))
        counts.merge(tag, 1, Integer::sum);
    }
    List<TagCount> result = new ArrayList<>();
    for (Map.Entry<Review.Tag, Integer> entry : counts.entrySet())
      result.add(new TagCount(entry.getKey(), entry.getValue()));
    result.sort(
        Comparator.comparingInt((TagCount point) -> point.count)
            .reversed()
            .thenComparingInt(point -> point.tag.ordinal()));
    return List.copyOf(result);
  }
}
