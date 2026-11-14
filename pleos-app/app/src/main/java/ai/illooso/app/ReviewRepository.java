package ai.illooso.app;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZonedDateTime;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Clearly labelled fictional arrival experiences for an offline prototype. Times use fixed slots on
 * historical Tuesdays/Saturdays, never the requested viewing context. These are not live road
 * conditions, real user identities, or verified facility information.
 */
public final class ReviewRepository {
  private static final String[] PLACE_IDS = {"p1", "p2", "p3", "p4", "p5", "p8"};
  private static final String[] AUTHORS = {
    "산책길", "퇴근길", "동네운전자", "느긋한오후", "강변드라이브", "주말나들이", "파란하늘", "저녁약속"
  };

  private ReviewRepository() {}

  public static List<Review> samples(Instant now) {
    Objects.requireNonNull(now, "now");
    List<Review> result = new ArrayList<>();
    LocalDate today = now.atZone(ReviewInsights.SEOUL).toLocalDate();
    int sequence = 0;
    for (String id : PLACE_IDS) {
      Place place = PlaceRepository.find(id);
      if (place == null) continue;
      int[] slots =
          id.equals("p8")
              ? new int[] {20, 330, 1240, 1390}
              : id.equals("p5")
                  ? new int[] {620, 760, 910}
                  : place.parking
                      ? new int[] {500, 760, 1000, 1070, 1105, 1270}
                      : new int[] {465, 515, 740, 785, 850, 1010, 1060, 1100, 1275};
      String[] modes =
          place.parking ? new String[] {"instant", "wait", "parking"} : new String[] {"instant"};
      for (DayOfWeek day : new DayOfWeek[] {DayOfWeek.TUESDAY, DayOfWeek.SATURDAY}) {
        LocalDate anchor = today.with(TemporalAdjusters.previousOrSame(day));
        for (int slotIndex = 0; slotIndex < slots.length; slotIndex++) {
          for (int modeIndex = 0; modeIndex < modes.length; modeIndex++) {
            String mode = modes[modeIndex];
            int minute = slots[slotIndex];
            LocalDate date = anchor.minusWeeks((slotIndex + modeIndex) % 3);
            ZonedDateTime visit =
                date.atTime(LocalTime.of(minute / 60, minute % 60)).atZone(ReviewInsights.SEOUL);
            Instant published = visit.plusMinutes(35 + slotIndex * 9L).toInstant();
            while (published.isAfter(now)) {
              visit = visit.minusWeeks(1);
              published = visit.plusMinutes(35 + slotIndex * 9L).toInstant();
            }
            List<Review.Tag> positive = new ArrayList<>();
            List<Review.Tag> caution = new ArrayList<>();
            String text = experience(place, mode, slotIndex, positive);
            if (positive.contains(Review.Tag.SHORT_WALK)) text += " 동승자가 걸어오는 거리도 짧았어요.";
            if (positive.contains(Review.Tag.CLEAR_ACCESS) && mode.equals("wait"))
              text += " 입구 방향도 찾기 쉬웠어요.";
            if (positive.contains(Review.Tag.COMFORTABLE_WAIT) && mode.equals("parking"))
              text += " 주차 구획 안에서 잠시 기다리기도 편했어요.";
            boolean weekend = ReviewInsights.isWeekend(day);
            if (!weekend && minute >= 1020 && minute <= 1140) {
              text += " 퇴근 차량이 이어져서 진입할 때 한 번 더 기다렸어요.";
              caution.add(Review.Tag.TRAFFIC);
            } else if (weekend && id.equals("p3")) {
              text += " 공원 산책객이 오가서 보행자를 먼저 살폈어요.";
              caution.add(Review.Tag.PEDESTRIANS);
            } else if (minute >= 1200 || minute < 360) {
              text += " 어두운 시간이라 입구와 보행자를 천천히 확인했어요.";
              caution.add(Review.Tag.LIMITED_VISIBILITY);
            } else if (id.equals("p1") || id.equals("p5")) {
              text += " 골목 폭이 좁아 마주 오는 차를 살펴야 했어요.";
              caution.add(Review.Tag.NARROW_ACCESS);
            } else if (place.parking && mode.equals("parking")) {
              text += " 나가기 전에 요금 안내를 다시 확인했어요.";
              caution.add(Review.Tag.PAID_PARKING);
            } else if (slotIndex % 2 == 1) {
              text += " 현장 표지는 방문할 때 다시 확인했어요.";
              caution.add(Review.Tag.SIGNAGE);
            }
            result.add(
                new Review(
                    "sample-" + id + "-" + day.name() + "-" + slotIndex + "-" + mode,
                    id,
                    AUTHORS[sequence % AUTHORS.length],
                    text,
                    visit.toInstant().toEpochMilli(),
                    published.toEpochMilli(),
                    mode,
                    2 + (sequence * 7 + slotIndex * 3) % 28,
                    true,
                    positive,
                    caution));
            sequence++;
          }
        }
      }
    }
    return List.copyOf(result);
  }

  private static String experience(
      Place place, String mode, int variant, List<Review.Tag> positive) {
    if (mode.equals("wait")) {
      positive.add(Review.Tag.COMFORTABLE_WAIT);
      if (variant % 2 == 0) positive.add(Review.Tag.CLEAR_ACCESS);
      return place.id.equals("p4")
          ? "북쪽 출입구를 찾고 주차 구획 안에서 기다렸어요. 동승자가 도착한 뒤 연락해서 만났어요."
          : "차를 주차 구획에 세운 뒤 기다리니 만날 시간을 맞추기 편했어요.";
    }
    if (mode.equals("parking")) {
      positive.add(Review.Tag.CLEAR_ACCESS);
      if (variant % 2 == 0) positive.add(Review.Tag.COMFORTABLE_WAIT);
      return place.id.equals("p8")
          ? "야간에 방문했어요. 출입구를 확인한 뒤 주차 구획을 찾아 들어갔어요."
          : "출입구 방향을 미리 보고 들어가니 주차 구획을 찾기 편했어요.";
    }
    positive.add(Review.Tag.EASY_PICKUP);
    if (place.walk <= 240 && variant % 2 == 0) positive.add(Review.Tag.SHORT_WALK);
    switch (place.id) {
      case "p1":
        return variant % 2 == 0
            ? "센터 옆 골목이라는 설명 덕분에 만날 위치를 바로 찾았어요. 동승자가 준비된 뒤 만났어요."
            : "목적지에서 걸어오는 거리가 짧아 서로 기다리는 시간을 줄였어요.";
      case "p3":
        return variant % 2 == 0
            ? "공원 입구가 눈에 띄어서 서로 위치를 설명하기 쉬웠어요."
            : "공원 동쪽 입구에서 만나기로 정하니 동승자가 쉽게 찾았어요.";
      case "p5":
        return "상가 서쪽 모퉁이를 기준으로 만나니 찾기 편했어요. 서로 준비된 것을 확인하고 만났어요.";
      case "p4":
        return "북쪽 주차장 입구를 기준으로 위치를 전달하니 만날 곳을 쉽게 찾았어요.";
      case "p8":
        return "밤에 주차장 출입구를 기준으로 만나기로 했어요. 위치를 전화로 한 번 확인했어요.";
      default:
        return "주차장 출입구를 기준으로 만나니 위치를 설명하기 쉬웠어요. 걸어오는 길도 짧았어요.";
    }
  }
}
