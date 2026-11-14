package ai.illooso.app;

import static org.junit.Assert.*;

import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;
import org.junit.Test;

public class DestinationRepositoryTest {
  @Test
  public void catalogUsesKnownOsmLocationsAndDoesNotExposeMutableLists() {
    assertEquals(8, DestinationRepository.all().size());
    assertSame(DestinationRepository.DEFAULT, DestinationRepository.find("d1"));
    Destination library = DestinationRepository.find("osm-node-368658611");
    assertEquals(35.1742284, library.latitude, 0.00000001);
    assertEquals(128.0654756, library.longitude, 0.00000001);
    assertTrue(DestinationRepository.all().stream().allMatch(DestinationRepository::supports));
    assertThrows(UnsupportedOperationException.class, () -> DestinationRepository.all().clear());
    assertNull(DestinationRepository.find(null));
    assertNull(DestinationRepository.find("unknown"));
  }

  @Test
  public void searchesNamesAddressesAndAliasesIgnoringWhitespaceAndEnglishCase() {
    assertEquals("osm-node-368658611", DestinationRepository.search("  어린이 도서관 ").get(0).id);
    assertEquals("osm-node-368867165", DestinationRepository.search("신세계 수영장").get(0).id);
    assertEquals("osm-node-13047744266", DestinationRepository.search("  STARBUCKS  ").get(0).id);
    assertEquals("d1", DestinationRepository.search("평거로40번길 5").get(0).id);
    assertEquals("d1", DestinationRepository.search("동사무소").get(0).id);
    assertEquals(DestinationRepository.all(), DestinationRepository.search("  "));
    assertEquals(DestinationRepository.all(), DestinationRepository.search(null));
    assertTrue(DestinationRepository.search("서울역").isEmpty());
    assertThrows(
        UnsupportedOperationException.class, () -> DestinationRepository.search("도서관").clear());
  }

  @Test
  public void importedDestinationRetainsCoordinatesAndLabelsItsSource() {
    Destination imported = DestinationRepository.fromNavigation("  진주 목적지  ", 35.174, 128.065);
    assertEquals("진주 목적지", imported.name);
    assertEquals(35.174, imported.latitude, 0.00000001);
    assertEquals(128.065, imported.longitude, 0.00000001);
    assertEquals("순정 내비게이션", imported.sourceLabel);
    assertTrue(DestinationRepository.supports(imported));
    assertEquals("내비게이션 목적지", DestinationRepository.fromNavigation(null, 35.174, 128.065).name);
    assertNotEquals(
        imported.id, DestinationRepository.fromNavigation("진주 목적지", 35.175, 128.065).id);
  }

  @Test
  public void invalidAndOutOfCoverageNavigationLocationsNeverProduceLocalCandidates() {
    assertNull(DestinationRepository.fromNavigation("bad", Double.NaN, 128));
    assertNull(DestinationRepository.fromNavigation("bad", 35, Double.POSITIVE_INFINITY));
    assertNull(DestinationRepository.fromNavigation("bad", 91, 128));
    assertNull(DestinationRepository.fromNavigation("bad", 35, -181));
    assertNull(DestinationRepository.fromNavigation("unset", 0, 0));
    Destination seoul = DestinationRepository.fromNavigation("서울역", 37.5547, 126.9706);
    assertNotNull(seoul);
    assertFalse(DestinationRepository.supports(seoul));
    assertTrue(
        DestinationRepository.recommend(seoul, "instant", 14, Integer.MAX_VALUE, "").isEmpty());
    assertTrue(
        DestinationRepository.recommendAtArrival(
                seoul, "instant", departure(), Integer.MAX_VALUE, "")
            .isEmpty());
    assertFalse(DestinationRepository.supports(null));
    assertFalse(
        DestinationRepository.supports(new Destination("bad", "bad", "", Double.NaN, 128, "")));
  }

  @Test
  public void straightLineDistanceIsCalculatedRatherThanReusingFixtureWalkingDistance() {
    Place pickup = PlaceRepository.find("p1");
    Place parking = PlaceRepository.find("p2");
    assertEquals(78, DestinationRepository.distanceMeters(DestinationRepository.DEFAULT, pickup));
    assertEquals(140, DestinationRepository.distanceMeters(DestinationRepository.DEFAULT, parking));
    assertEquals(80, pickup.walk);
    assertEquals(180, parking.walk);
    Destination atPickup =
        DestinationRepository.fromNavigation("같은 점", pickup.latitude, pickup.longitude);
    assertEquals(0, DestinationRepository.distanceMeters(atPickup, pickup));
    assertEquals(Integer.MAX_VALUE, DestinationRepository.distanceMeters(null, pickup));
    assertEquals(
        Integer.MAX_VALUE,
        DestinationRepository.distanceMeters(DestinationRepository.DEFAULT, null));
  }

  @Test
  public void changingDestinationChangesNearestEligiblePickup() {
    assertEquals(
        "p1",
        DestinationRepository.recommend(DestinationRepository.DEFAULT, "instant", 14, 500, "")
            .get(0)
            .id);
    Destination pool = DestinationRepository.find("osm-node-368867165");
    assertEquals("p4", DestinationRepository.recommend(pool, "instant", 14, 500, "").get(0).id);
    Destination cinema = DestinationRepository.find("osm-node-368635425");
    assertEquals("p5", DestinationRepository.recommend(cinema, "instant", 14, 500, "").get(0).id);
    assertEquals(
        "p1",
        DestinationRepository.recommend(DestinationRepository.DEFAULT, "instant", 14, 500, "")
            .get(0)
            .id);
  }

  @Test
  public void distanceLimitUsesNewDestinationAndRoundedStraightLineMeters() {
    assertEquals(
        List.of("p1"),
        ids(DestinationRepository.recommend(DestinationRepository.DEFAULT, "instant", 14, 78, "")));
    assertTrue(
        DestinationRepository.recommend(DestinationRepository.DEFAULT, "instant", 14, 77, "")
            .isEmpty());
    assertTrue(
        DestinationRepository.recommend(DestinationRepository.DEFAULT, "instant", 14, -1, "")
            .isEmpty());
    Place pickup = PlaceRepository.find("p3");
    Destination atPickup =
        DestinationRepository.fromNavigation("지점", pickup.latitude, pickup.longitude);
    assertEquals(
        List.of("p3"), ids(DestinationRepository.recommend(atPickup, "instant", 14, 0, "")));
  }

  @Test
  public void mandatoryEligibilityModesAndSearchStillApplyBeforeDistanceSort() {
    for (int hour = 0; hour < 24; hour++) {
      for (String mode : new String[] {"instant", "wait", "parking"}) {
        for (Place place :
            DestinationRepository.recommend(DestinationRepository.DEFAULT, mode, hour, 2000, "")) {
          assertTrue(place.verified);
          assertFalse(place.prohibited);
          assertTrue(PlaceRepository.isOpenAt(place, hour));
          if (mode.equals("parking")) assertTrue(place.parking);
          if (mode.equals("wait")) assertTrue(place.waitAllowed);
        }
      }
    }
    assertEquals(
        List.of("p2"),
        ids(
            DestinationRepository.recommend(
                DestinationRepository.DEFAULT, "instant", 14, 500, "공영")));
    assertTrue(
        DestinationRepository.recommend(DestinationRepository.DEFAULT, "unknown", 14, 500, "")
            .isEmpty());
    assertTrue(
        DestinationRepository.recommend(DestinationRepository.DEFAULT, "instant", 24, 500, "")
            .isEmpty());
  }

  @Test
  public void arrivalTimeStillExcludesClosingPickupsAndUsesKoreanTime() {
    List<String> beforeClosing =
        ids(
            DestinationRepository.recommendAtArrival(
                DestinationRepository.DEFAULT,
                "instant",
                ZonedDateTime.parse("2026-11-14T21:56:00+09:00"),
                500,
                ""));
    List<String> atClosing =
        ids(
            DestinationRepository.recommendAtArrival(
                DestinationRepository.DEFAULT,
                "instant",
                ZonedDateTime.parse("2026-11-14T21:57:00+09:00"),
                500,
                ""));
    assertTrue(beforeClosing.contains("p1"));
    assertFalse(atClosing.contains("p1"));
    assertEquals(
        atClosing,
        ids(
            DestinationRepository.recommendAtArrival(
                DestinationRepository.DEFAULT,
                "instant",
                ZonedDateTime.parse("2026-11-14T12:57:00Z"),
                500,
                "")));
    assertTrue(
        DestinationRepository.recommendAtArrival(
                DestinationRepository.DEFAULT, "instant", null, 500, "")
            .isEmpty());
  }

  @Test
  public void resultsAreOrderedByDistanceAndFixtureMutationsCannotCorruptOtherQueries() {
    List<Place> nearby =
        DestinationRepository.recommendAtArrival(
            DestinationRepository.DEFAULT, "instant", departure(), 500, "");
    int previous = -1;
    for (Place place : nearby) {
      int distance = DestinationRepository.distanceMeters(DestinationRepository.DEFAULT, place);
      assertTrue(distance >= previous);
      previous = distance;
    }
    nearby.get(0).tags[0] = "changed";
    assertNotEquals(
        "changed",
        DestinationRepository.recommendAtArrival(
                DestinationRepository.DEFAULT, "instant", departure(), 500, "")
            .get(0)
            .tags[0]);
    assertThrows(UnsupportedOperationException.class, nearby::clear);
  }

  private static ZonedDateTime departure() {
    return ZonedDateTime.parse("2026-11-14T14:00:00+09:00");
  }

  private static List<String> ids(List<Place> places) {
    return places.stream().map(place -> place.id).collect(Collectors.toList());
  }
}
