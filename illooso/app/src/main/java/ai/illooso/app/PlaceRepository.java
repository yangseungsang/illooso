package ai.illooso.app;

import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.ToIntFunction;

/**
 * Place fixtures anchored to real OpenStreetMap geometry, with no Android dependency.
 *
 * <p>The destination is OSM node 2691429153 (Pyeonggeo administrative welfare center). Pickup
 * coordinates lie on nearby OSM road centerlines, not surveyed stopping places or parking
 * entrances. Pickup suitability, verified/prohibited flags, availability, fees, walking distances,
 * driving times, detours and reports remain illustrative fixtures. A real map coordinate does not
 * verify any of these assessments. Do not use these pickup fixtures as live vehicle navigation
 * advice.
 */
public final class PlaceRepository {
  public static final String DEFAULT_DESTINATION = "평거동 행정복지센터";

  /**
   * OSM node 2691429153, from the archived 2026-11-13 Overpass response; not an entrance survey.
   */
  public static final double DEST_LAT = 35.1738345;

  public static final double DEST_LON = 128.0653891;

  private static final List<Place> PLACES =
      List.of(
          new Place(
              "p1",
              "평거로36번길 예시 승하차 지점",
              "목적지 가까이에 배치한 승하차 예시",
              "진주시 평거동 · 평거로36번길 (가상 지점)",
              false,
              55,
              44,
              35.1731309,
              128.0654230,
              80,
              120,
              3,
              true,
              false,
              false,
              7,
              22,
              98,
              24,
              new String[] {"도보 1분", "우회 최소", "보도 인접"},
              "평거로36번길에 배치한 승하차 예시입니다. 도보 거리·차량 시간·우회 거리는 시연값이에요.",
              "평거로36번길 접근 예시 · 실제 진입·정차 조건 미확인",
              "매일 07:00–22:00 · 즉시 승하차만 · 예시 조건",
              "승하차 무료 · 예시"),
          new Place(
              "p2",
              "신평공원길39번길 예시 주차 공간",
              "주차 구획에서 기다리는 이용 예시",
              "진주시 평거동 · 신평공원길39번길 (가상 시설 · 공영 주차 예시)",
              true,
              35,
              60,
              35.1727590,
              128.0661811,
              180,
              240,
              4,
              true,
              false,
              true,
              0,
              24,
              93,
              18,
              new String[] {"대기 가능", "24시간", "넓은 진입로"},
              "신평공원길39번길에 배치한 가상 주차 공간입니다. 실제 주차장이 아니며 거리·시간·대기 조건은 예시예요.",
              "신평공원길39번길 접근 예시 · 실제 주차장 출입구 미확인",
              "매일 24시간 · 주차 구획 내 대기 · 예시 조건",
              "최초 30분 500원 · 예시 요금"),
          new Place(
              "p3",
              "신평공원길47번길 예시 승하차 지점",
              "주변 도로에서 만나는 승하차 예시",
              "진주시 평거동 · 신평공원길47번길 (가상 지점)",
              false,
              69,
              65,
              35.1731020,
              128.0678351,
              240,
              180,
              4,
              true,
              false,
              false,
              6,
              23,
              89,
              12,
              new String[] {"쉬운 만남", "보도 인접", "횡단보도 1회"},
              "신평공원길47번길에 배치한 승하차 예시입니다. 보행 동선·거리·차량 시간은 실제 경로 계산값이 아니에요.",
              "신평공원길47번길 접근 예시 · 실제 진입·정차 조건 미확인",
              "매일 06:00–23:00 · 즉시 승하차만 · 예시 조건",
              "승하차 무료 · 예시"),
          new Place(
              "p4",
              "평거로 북측 예시 주차 공간",
              "오래 기다리는 상황을 위한 주차 예시",
              "진주시 평거동 · 평거로 북측 (가상 주차 시설)",
              true,
              75,
              24,
              35.1764518,
              128.0667172,
              360,
              420,
              6,
              true,
              false,
              true,
              8,
              22,
              82,
              9,
              new String[] {"대기 가능", "유료 주차", "넓은 주차 구획"},
              "평거로 북측에 배치한 가상 주차 공간입니다. 실제 시설·출입구가 아니며 거리·시간·주차 조건은 예시예요.",
              "평거로 북측 접근 예시 · 실제 주차장 출입구 미확인",
              "매일 08:00–22:00 · 예시 운영 시간",
              "30분 1,000원 · 예시 요금"),
          new Place(
              "p5",
              "평거로39번길 예시 승하차 지점",
              "방문 시간에 따라 달라지는 이용 예시",
              "진주시 평거동 · 평거로39번길 (가상 지점)",
              false,
              40,
              30,
              35.1745968,
              128.0634456,
              210,
              260,
              5,
              true,
              false,
              false,
              10,
              16,
              85,
              7,
              new String[] {"시간 제한", "즉시 승하차"},
              "평거로39번길의 시간 제한을 체험하는 예시 지점입니다. 제한 시간·이동 거리·차량 시간은 시연값이에요.",
              "평거로39번길 접근 예시 · 실제 일방통행·정차 조건 미확인",
              "매일 10:00–16:00 · 그 외 시간 제외 · 예시 조건",
              "승하차 무료 · 예시"),
          new Place(
              "p6",
              "신평공원길57번길 미확인 예시 구간",
              "정보가 부족한 구간을 제외하는 예시",
              "진주시 평거동 · 신평공원길57번길 (가상 구간)",
              false,
              83,
              45,
              35.1744244,
              128.0669119,
              100,
              80,
              2,
              false,
              false,
              false,
              0,
              24,
              99,
              45,
              new String[] {"정보 미확인"},
              "신평공원길57번길에 배치한 정보 미확인 예시입니다. 추천에서 제외하며 거리·시간도 시연값이에요.",
              "신평공원길57번길 · 실제 접근 방향 미확인",
              "정차 조건 확인 필요 · 예시 상태",
              "미확인"),
          new Place(
              "p7",
              "평거로40번길 정차 금지 예시 구간",
              "금지 조건이 있는 후보를 제외하는 예시",
              "진주시 평거동 · 평거로40번길 (가상 구간)",
              false,
              61,
              33,
              35.1732680,
              128.0660705,
              20,
              0,
              1,
              true,
              true,
              false,
              0,
              24,
              100,
              63,
              new String[] {"정차 금지"},
              "평거로40번길의 금지 조건을 체험하는 예시입니다. 실제 도로 규정이 아니며 거리·시간도 시연값이에요.",
              "평거로40번길 · 승하차 후보 제외 예시",
              "매일 정차 금지 · 예시 규정",
              "이용 불가 · 예시 조건"),
          new Place(
              "p8",
              "진양호로 예시 야간 주차 공간",
              "야간 대기를 체험하는 가상 주차 공간",
              "진주시 평거동 · 진양호로 (가상 야간 주차 시설)",
              true,
              24,
              43,
              35.1745520,
              128.0613249,
              420,
              320,
              5,
              true,
              false,
              true,
              20,
              7,
              80,
              5,
              new String[] {"야간 운영", "대기 가능"},
              "진양호로에 배치한 가상 야간 주차 공간입니다. 실제 주차장이 아니며 운영 시간·거리·차량 시간은 예시예요.",
              "진양호로 접근 예시 · 실제 주차장·보행 출입구 미확인",
              "매일 20:00–다음 날 07:00 · 예시 운영 시간",
              "1시간 1,000원 · 예시 요금"));

  private PlaceRepository() {}

  /** All fixture places, including excluded roads; never use as the recommendation list. */
  public static List<Place> all() {
    return snapshots(PLACES);
  }

  /** Returns a fresh snapshot, or null for an unknown/null ID. */
  public static Place find(String id) {
    if (id == null) return null;
    for (Place place : PLACES) {
      if (place.id.equals(id)) return place.copy();
    }
    return null;
  }

  /**
   * Saved fixtures in repository order, independent of exploration mode, time, distance and search.
   * Unknown IDs and fixtures with unverified or prohibited access are omitted. A null ID set is
   * empty. Returned places are fresh snapshots in an unmodifiable list; inclusion does not imply
   * that a place is open at the selected time.
   */
  public static List<Place> savedPlaces(Set<String> ids) {
    if (ids == null || ids.isEmpty()) return Collections.emptyList();
    List<Place> saved = new ArrayList<>();
    for (Place place : PLACES) {
      if (ids.contains(place.id) && place.verified && !place.prohibited) saved.add(place);
    }
    return snapshots(saved);
  }

  /**
   * Applies mandatory eligibility before ranking. Invalid mode/time/distance inputs return an empty
   * list rather than silently relaxing the conditions. A null query is treated as an empty search.
   */
  public static List<Place> recommend(String mode, int hour, int maxWalk, String query) {
    if (hour < 0 || hour >= 24) return Collections.emptyList();
    return recommendForHours(mode, maxWalk, query, place -> hour);
  }

  /**
   * Checks each place at its estimated arrival time rather than at departure. Fixture opening hours
   * are local to Korea; the supplied departure instant is normalized to that zone first. All other
   * eligibility checks, scoring and returned snapshot guarantees match {@link #recommend}.
   */
  public static List<Place> recommendAtArrival(
      String mode, ZonedDateTime departure, int maxWalk, String query) {
    if (departure == null) return Collections.emptyList();
    ZonedDateTime localDeparture = departure.withZoneSameInstant(ZoneId.of("Asia/Seoul"));
    return recommendForHours(
        mode, maxWalk, query, place -> localDeparture.plusMinutes(place.driveMinutes).getHour());
  }

  private static List<Place> recommendForHours(
      String mode, int maxWalk, String query, ToIntFunction<Place> arrivalHour) {
    if ((!"instant".equals(mode) && !"wait".equals(mode) && !"parking".equals(mode))
        || maxWalk < 0) {
      return Collections.emptyList();
    }
    String search = query == null ? "" : query.trim().toLowerCase(Locale.KOREAN);
    List<Place> candidates = new ArrayList<>();
    for (Place place : PLACES) {
      if (!place.verified || place.prohibited || !isOpenAt(place, arrivalHour.applyAsInt(place)))
        continue;
      if ("wait".equals(mode) && !place.waitAllowed) continue;
      if ("parking".equals(mode) && !place.parking) continue;
      if (place.walk > maxWalk) continue;
      String searchable =
          place.name
              + " "
              + place.subtitle
              + " "
              + place.address
              + " "
              + String.join(" ", place.tags);
      if (!search.isEmpty() && !searchable.toLowerCase(Locale.KOREAN).contains(search)) continue;
      candidates.add(place);
    }
    candidates.sort(
        Comparator.comparingDouble(PlaceRepository::recommendationScore)
            .reversed()
            .thenComparingInt(place -> place.walk)
            .thenComparing(place -> place.id));
    return snapshots(candidates);
  }

  /** Opening is inclusive; closing is exclusive. Overnight hours cross midnight. */
  public static boolean isOpenAt(Place place, int hour) {
    if (place == null || hour < 0 || hour >= 24) return false;
    if (place.openHour == 0 && place.closeHour == 24) return true;
    if (place.openHour == place.closeHour) return false;
    return place.openHour < place.closeHour
        ? hour >= place.openHour && hour < place.closeHour
        : hour >= place.openHour || hour < place.closeHour;
  }

  private static double recommendationScore(Place place) {
    return place.score - Math.max(0, place.walk - 80) / 35.0;
  }

  private static List<Place> snapshots(List<Place> source) {
    List<Place> result = new ArrayList<>(source.size());
    for (Place place : source) result.add(place.copy());
    return Collections.unmodifiableList(result);
  }
}
