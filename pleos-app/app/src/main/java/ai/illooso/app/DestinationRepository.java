package ai.illooso.app;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * Offline search of public OSM map locations near Pyeonggeo, not a nationwide geocoder.
 *
 * <p>All catalog coordinates are unchanged OSM node coordinates from the archived
 * .runtime/map-research/source-overpass.json response, OSM base 2026-11-13T12:58:57Z. They are map
 * registration points, not surveyed entrances or assertions of current operation. OSM attribution:
 * https://www.openstreetmap.org/copyright (ODbL).
 *
 * <p>Nearby places remain PlaceRepository fixtures. The distance here is a great-circle distance
 * between coordinates, never a walking-route distance. Fixture opening hours, eligibility and
 * arrival-time estimates still apply; this repository does not verify roadside stopping rights.
 */
public final class DestinationRepository {
  public static final int COVERAGE_RADIUS_METERS = 1500;
  public static final String SEARCH_SCOPE = "평거동 주변 등록 목적지";
  private static final double EARTH_RADIUS_METERS = 6371008.8;
  private static final String OSM_SOURCE = "OpenStreetMap 지도 위치";

  // Default corresponds to OSM node 2691429153; d1 remains compatible with the original prototype.
  public static final Destination DEFAULT =
      new Destination(
          "d1",
          PlaceRepository.DEFAULT_DESTINATION,
          "경상남도 진주시 평거로40번길 5",
          PlaceRepository.DEST_LAT,
          PlaceRepository.DEST_LON,
          OSM_SOURCE);

  private static final List<Entry> CATALOG =
      List.of(
          new Entry(DEFAULT, "평거동 주민센터 평거동 동사무소 평거 행정복지센터"),
          node(
              368658611L,
              "진주시어린이전문도서관",
              "경상남도 진주시 · 지도 등록 위치",
              35.1742284,
              128.0654756,
              "어린이 도서관 진주 도서관 Jinjusi Children Jeonmun Library"),
          node(
              368607956L,
              "진주경찰서 진양호지구대",
              "경상남도 진주시 평거로 42",
              35.1738505,
              128.0651947,
              "진양호 지구대 평거 경찰서 Jinju Police Station"),
          node(
              368867165L,
              "신세계수영장",
              "경상남도 진주시 · 지도 등록 위치",
              35.1752369,
              128.0655942,
              "신세계 수영장 Sinsegye Swimming Pool"),
          node(
              368635425L,
              "청춘극장",
              "경상남도 진주시 · 지도 등록 위치",
              35.173092,
              128.061305,
              "청춘 극장 Cheongchun Cinema"),
          node(
              13047744266L,
              "스타벅스",
              "경상남도 진주시 · 평거동 주변 지도 위치",
              35.1726082,
              128.0600215,
              "Starbucks 커피 카페"),
          node(
              12446850818L,
              "다이소",
              "경상남도 진주시 · 평거동 주변 지도 위치",
              35.1720596,
              128.0596729,
              "Daiso 생활용품"),
          node(
              13068037643L,
              "올리브영",
              "경상남도 진주시 · 평거동 주변 지도 위치",
              35.1714915,
              128.062125,
              "Olive Young 화장품"));

  private DestinationRepository() {}

  public static List<Destination> all() {
    return List.copyOf(
        CATALOG.stream().map(entry -> entry.destination).collect(Collectors.toList()));
  }

  /** Returns a catalog destination only; an imported navigation destination is not in this list. */
  public static Destination find(String id) {
    if (id == null) return null;
    return CATALOG.stream()
        .map(entry -> entry.destination)
        .filter(destination -> destination.id.equals(id))
        .findFirst()
        .orElse(null);
  }

  /**
   * Case and whitespace-insensitive local search. Empty input returns the eight catalog locations.
   */
  public static List<Destination> search(String query) {
    String needle = normalize(query);
    if (needle.isEmpty()) return all();
    return List.copyOf(
        CATALOG.stream()
            .filter(
                entry ->
                    normalize(
                            entry.destination.name
                                + " "
                                + entry.destination.address
                                + " "
                                + entry.aliases)
                        .contains(needle))
            .map(entry -> entry.destination)
            .collect(Collectors.toList()));
  }

  /**
   * Preserves navigation-provided coordinates, including valid locations outside our sample area.
   */
  public static Destination fromNavigation(String name, double latitude, double longitude) {
    if (!validPoint(latitude, longitude)) return null;
    String title = name == null ? "" : name.trim();
    return new Destination(
        "navi:" + latitude + ":" + longitude,
        title.isEmpty() ? "내비게이션 목적지" : title,
        "순정 내비게이션에서 가져온 목적지",
        latitude,
        longitude,
        "순정 내비게이션");
  }

  /** A data coverage bound, not a guarantee that any eligible place exists nearby. */
  public static boolean supports(Destination destination) {
    return destination != null
        && validPoint(destination.latitude, destination.longitude)
        && meters(DEFAULT.latitude, DEFAULT.longitude, destination.latitude, destination.longitude)
            <= COVERAGE_RADIUS_METERS;
  }

  /** Straight-line meters rounded to the nearest meter; invalid input has no finite UI distance. */
  public static int distanceMeters(Destination destination, Place place) {
    if (destination == null
        || place == null
        || !validPoint(destination.latitude, destination.longitude)
        || !validPoint(place.latitude, place.longitude)) return Integer.MAX_VALUE;
    return (int)
        Math.round(
            meters(destination.latitude, destination.longitude, place.latitude, place.longitude));
  }

  public static List<Place> recommend(
      Destination destination, String mode, int visitHour, int maxDistance, String query) {
    if (!supports(destination) || maxDistance < 0) return List.of();
    return nearby(
        destination,
        PlaceRepository.recommend(mode, visitHour, Integer.MAX_VALUE, query),
        maxDistance);
  }

  public static List<Place> recommendAtArrival(
      Destination destination,
      String mode,
      ZonedDateTime departure,
      int maxDistance,
      String query) {
    if (!supports(destination) || maxDistance < 0) return List.of();
    return nearby(
        destination,
        PlaceRepository.recommendAtArrival(mode, departure, Integer.MAX_VALUE, query),
        maxDistance);
  }

  private static List<Place> nearby(
      Destination destination, List<Place> eligible, int maxDistance) {
    List<Place> result = new ArrayList<>();
    for (Place place : eligible)
      if (distanceMeters(destination, place) <= maxDistance) result.add(place);
    // Java's stable sort retains repository recommendation order for equally rounded distances.
    result.sort(Comparator.comparingInt(place -> distanceMeters(destination, place)));
    return List.copyOf(result);
  }

  private static String normalize(String value) {
    return value == null ? "" : value.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
  }

  private static boolean validPoint(double latitude, double longitude) {
    return Double.isFinite(latitude)
        && Double.isFinite(longitude)
        && latitude >= -90
        && latitude <= 90
        && longitude >= -180
        && longitude <= 180
        && (latitude != 0 || longitude != 0);
  }

  private static double meters(double lat1, double lon1, double lat2, double lon2) {
    double latitudeDelta = Math.toRadians(lat2 - lat1);
    double longitudeDelta = Math.toRadians(lon2 - lon1);
    double sinLat = Math.sin(latitudeDelta / 2), sinLon = Math.sin(longitudeDelta / 2);
    double h =
        sinLat * sinLat
            + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2)) * sinLon * sinLon;
    return 2 * EARTH_RADIUS_METERS * Math.asin(Math.sqrt(Math.max(0, Math.min(1, h))));
  }

  private static Entry node(
      long id, String name, String address, double latitude, double longitude, String aliases) {
    return new Entry(
        new Destination("osm-node-" + id, name, address, latitude, longitude, OSM_SOURCE), aliases);
  }

  private static final class Entry {
    final Destination destination;
    final String aliases;

    Entry(Destination destination, String aliases) {
      this.destination = destination;
      this.aliases = aliases;
    }
  }
}
