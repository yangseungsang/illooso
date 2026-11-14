package ai.illooso.app;

import static org.junit.Assert.*;

import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class PlaceRepositoryTest {
  @Test
  public void unverifiedAndProhibitedRoadsNeverBecomeCandidates() {
    for (int hour = 0; hour < 24; hour++) {
      for (String mode : new String[] {"instant", "wait", "parking"}) {
        for (Place place : PlaceRepository.recommend(mode, hour, 2000, "")) {
          assertTrue(place.verified);
          assertFalse(place.prohibited);
          assertNotEquals("p6", place.id);
          assertNotEquals("p7", place.id);
        }
      }
    }
  }

  @Test
  public void waitingRequiresWaitingAccessAndParkingRequiresParking() {
    List<Place> waiting = PlaceRepository.recommend("wait", 14, 800, "");
    assertFalse(waiting.isEmpty());
    for (Place place : waiting) assertTrue(place.waitAllowed);
    assertFalse(ids(waiting).contains("p1"));
    for (Place place : PlaceRepository.recommend("parking", 14, 800, "")) {
      assertTrue(place.parking);
    }
  }

  @Test
  public void openingIncludedAndClosingExcluded() {
    Place daytime = PlaceRepository.find("p5");
    assertFalse(PlaceRepository.isOpenAt(daytime, 9));
    assertTrue(PlaceRepository.isOpenAt(daytime, 10));
    assertTrue(PlaceRepository.isOpenAt(daytime, 15));
    assertFalse(PlaceRepository.isOpenAt(daytime, 16));
    assertTrue(ids(PlaceRepository.recommend("instant", 10, 800, "")).contains("p5"));
    assertFalse(ids(PlaceRepository.recommend("instant", 16, 800, "")).contains("p5"));
  }

  @Test
  public void overnightAndAllDayHoursWorkAcrossMidnight() {
    Place night = PlaceRepository.find("p8");
    assertTrue(PlaceRepository.isOpenAt(night, 20));
    assertTrue(PlaceRepository.isOpenAt(night, 0));
    assertTrue(PlaceRepository.isOpenAt(night, 6));
    assertFalse(PlaceRepository.isOpenAt(night, 7));
    assertFalse(PlaceRepository.isOpenAt(night, 19));
    Place allDay = PlaceRepository.find("p2");
    for (int hour = 0; hour < 24; hour++) assertTrue(PlaceRepository.isOpenAt(allDay, hour));
  }

  @Test
  public void walkingLimitIsInclusiveAndCombinesWithMode() {
    assertEquals(List.of("p1"), ids(PlaceRepository.recommend("instant", 14, 80, "")));
    assertTrue(PlaceRepository.recommend("instant", 14, 79, "").isEmpty());
    assertTrue(PlaceRepository.recommend("wait", 14, 80, "").isEmpty());
    assertEquals(List.of("p2"), ids(PlaceRepository.recommend("wait", 14, 180, "")));
  }

  @Test
  public void searchTrimsWhitespaceAndSearchesNamesAddressesAndTags() {
    assertEquals(List.of("p2"), ids(PlaceRepository.recommend("instant", 14, 800, " 공영 ")));
    assertEquals(
        List.of("p1", "p3", "p5"), ids(PlaceRepository.recommend("instant", 14, 800, "가상 지점")));
    assertEquals(List.of("p2"), ids(PlaceRepository.recommend("instant", 14, 800, "24시간")));
    assertTrue(PlaceRepository.recommend("instant", 14, 800, "없는 장소").isEmpty());
    assertTrue(PlaceRepository.recommend("instant", 14, 800, "미확인").isEmpty());
    assertEquals(
        ids(PlaceRepository.recommend("instant", 14, 800, "")),
        ids(PlaceRepository.recommend("instant", 14, 800, null)));
  }

  @Test
  public void defaultOrderUsesEligibilityThenWeightedRecommendation() {
    assertEquals(
        List.of("p1", "p2", "p3", "p5", "p4"),
        ids(PlaceRepository.recommend("instant", 14, 500, "")));
  }

  @Test
  public void arrivalEligibilityExcludesPlacesClosingBeforeTheVehicleReachesThem() {
    assertTrue(
        ids(PlaceRepository.recommendAtArrival(
                "instant", ZonedDateTime.parse("2026-11-14T21:56:00+09:00"), 500, ""))
            .contains("p1"));
    // p1 needs three minutes: 21:57 reaches the exclusive closing boundary exactly.
    assertFalse(
        ids(PlaceRepository.recommendAtArrival(
                "instant", ZonedDateTime.parse("2026-11-14T21:57:00+09:00"), 500, ""))
            .contains("p1"));
    assertFalse(
        ids(PlaceRepository.recommendAtArrival(
                "instant", ZonedDateTime.parse("2026-11-14T21:59:00+09:00"), 500, ""))
            .contains("p1"));
  }

  @Test
  public void arrivalEligibilityIncludesPlacesOpeningDuringTheDrive() {
    assertFalse(
        ids(PlaceRepository.recommendAtArrival(
                "instant", ZonedDateTime.parse("2026-11-14T06:56:00+09:00"), 500, ""))
            .contains("p1"));
    assertTrue(
        ids(PlaceRepository.recommendAtArrival(
                "instant", ZonedDateTime.parse("2026-11-14T06:57:00+09:00"), 500, ""))
            .contains("p1"));
    // The night lot needs five minutes and opens at 20:00.
    assertTrue(
        ids(PlaceRepository.recommendAtArrival(
                "parking", ZonedDateTime.parse("2026-11-14T19:55:00+09:00"), 500, ""))
            .contains("p8"));
  }

  @Test
  public void arrivalRankingPreservesEligibilitySearchAndOrderAwayFromTimeBoundaries() {
    ZonedDateTime departure = ZonedDateTime.parse("2026-11-14T14:00:00+09:00");
    for (String mode : new String[] {"instant", "wait", "parking"})
      for (int distance : new int[] {80, 180, 500, 800})
        for (String search : new String[] {"", " 공영 ", "미확인", "가상 지점"})
          assertEquals(
              ids(PlaceRepository.recommend(mode, 14, distance, search)),
              ids(PlaceRepository.recommendAtArrival(mode, departure, distance, search)));
    assertThrows(
        UnsupportedOperationException.class,
        () -> PlaceRepository.recommendAtArrival("instant", departure, 500, "").clear());
    assertTrue(PlaceRepository.recommendAtArrival("instant", null, 500, "").isEmpty());
    assertTrue(PlaceRepository.recommendAtArrival("unknown", departure, 500, "").isEmpty());
    assertTrue(PlaceRepository.recommendAtArrival("instant", departure, -1, "").isEmpty());
  }

  @Test
  public void arrivalUsesKoreanLocalHoursAcrossMidnightAndInputTimeZones() {
    ZonedDateTime koreanDeparture = ZonedDateTime.parse("2026-11-14T23:59:00+09:00");
    List<String> afterMidnight =
        ids(PlaceRepository.recommendAtArrival("parking", koreanDeparture, 500, ""));
    assertEquals(List.of("p2", "p8"), afterMidnight);
    assertEquals(
        afterMidnight,
        ids(
            PlaceRepository.recommendAtArrival(
                "parking", ZonedDateTime.parse("2026-11-14T14:59:00Z"), 500, "")));
  }

  @Test
  public void invalidInputsDoNotSilentlyBroadenRecommendations() {
    assertTrue(PlaceRepository.recommend(null, 14, 500, "").isEmpty());
    assertTrue(PlaceRepository.recommend("unknown", 14, 500, "").isEmpty());
    assertTrue(PlaceRepository.recommend("instant", -1, 500, "").isEmpty());
    assertTrue(PlaceRepository.recommend("instant", 24, 500, "").isEmpty());
    assertTrue(PlaceRepository.recommend("instant", 14, -1, "").isEmpty());
    assertFalse(PlaceRepository.isOpenAt(null, 14));
    assertFalse(PlaceRepository.isOpenAt(PlaceRepository.find("p2"), 24));
  }

  @Test
  public void unknownIdsAreNullableAndFixtureIdsAreUnique() {
    assertNull(PlaceRepository.find(null));
    assertNull(PlaceRepository.find("missing"));
    Set<String> uniqueIds = new HashSet<>();
    for (Place place : PlaceRepository.all()) assertTrue(uniqueIds.add(place.id));
    assertEquals(8, uniqueIds.size());
  }

  @Test
  public void savedPlacesKeepParkingAndClosedDistantPlacesWithoutExploreFilters() {
    // The night lot is closed at the default 14:00 and beyond a 100m walking filter. A saved
    // daytime pickup also remains visible alongside parking, regardless of the explore mode.
    Set<String> savedIds = Set.of("p8", "p5", "p2", "p6", "p7", "missing");
    assertFalse(PlaceRepository.isOpenAt(PlaceRepository.find("p8"), 14));
    assertTrue(PlaceRepository.find("p8").walk > 100);
    assertEquals(List.of("p2", "p5", "p8"), ids(PlaceRepository.savedPlaces(savedIds)));
    assertTrue(PlaceRepository.savedPlaces(null).isEmpty());
    assertTrue(PlaceRepository.savedPlaces(Set.of()).isEmpty());
  }

  @Test
  public void callerChangesCannotCorruptRepositoryOrOtherReads() {
    List<Place> first = PlaceRepository.all();
    first.get(0).tags[0] = "modified";
    assertNotEquals("modified", PlaceRepository.find("p1").tags[0]);
    Place lookup = PlaceRepository.find("p1");
    lookup.tags[0] = "also modified";
    assertNotEquals(
        "also modified", PlaceRepository.recommend("instant", 14, 500, "").get(0).tags[0]);
    assertThrows(UnsupportedOperationException.class, first::clear);
    assertThrows(
        UnsupportedOperationException.class,
        () -> PlaceRepository.recommend("instant", 14, 500, "").clear());
  }

  private static List<String> ids(List<Place> places) {
    List<String> result = new ArrayList<>();
    for (Place place : places) result.add(place.id);
    return result;
  }
}
