package ai.illooso.app;

import static org.junit.Assert.*;

import java.util.List;
import org.junit.Test;

public class SelectionPolicyTest {
  @Test
  public void changingVisitTimeClearsAnIdlePlaceThatHasClosed() {
    Place daytimePickup = PlaceRepository.find("p1");
    List<Place> lateCandidates = PlaceRepository.recommend("instant", 23, 500, "");
    assertFalse(PlaceRepository.isOpenAt(daytimePickup, 23));
    assertFalse(lateCandidates.isEmpty());
    assertNull(SelectionPolicy.reconcile(daytimePickup, lateCandidates, "idle"));
  }

  @Test
  public void parkingWithNoCandidatesClearsIdleAndFailedPickupSelections() {
    Place pickup = PlaceRepository.find("p1");
    List<Place> parking = PlaceRepository.recommend("parking", 14, 100, "");
    assertTrue(parking.isEmpty());
    assertNull(SelectionPolicy.reconcile(pickup, parking, "idle"));
    assertNull(SelectionPolicy.reconcile(pickup, parking, "error"));
  }

  @Test
  public void browsingDoesNotReplaceThePlaceInAnOngoingMeetingFlow() {
    Place pickup = PlaceRepository.find("p1");
    List<Place> alternatives = PlaceRepository.recommend("parking", 14, 500, "");
    for (String status : new String[] {"requesting", "unconfirmed", "active", "arrived"}) {
      assertSame(pickup, SelectionPolicy.reconcile(pickup, alternatives, status));
      assertSame(pickup, SelectionPolicy.reconcile(pickup, List.of(), status));
      assertSame(pickup, SelectionPolicy.reconcile(pickup, null, status));
      assertSame(pickup, SelectionPolicy.beginMapBrowsing(pickup, status));
    }
  }

  @Test
  public void eligibleExplicitSelectionRemainsStable() {
    Place current = PlaceRepository.find("p3");
    List<Place> candidates = PlaceRepository.recommend("instant", 14, 500, "");
    assertSame(current, SelectionPolicy.reconcile(current, candidates, "idle"));
    assertSame(current, SelectionPolicy.reconcile(current, candidates, "error"));
    assertNull(SelectionPolicy.reconcile(current, null, "idle"));
  }

  @Test
  public void choosingAnyPurposeShowsCandidatesWithoutAutomaticallySelectingOne() {
    for (String mode : new String[] {"instant", "wait", "parking"}) {
      List<Place> candidates = PlaceRepository.recommend(mode, 14, 500, "");
      assertFalse(mode, candidates.isEmpty());
      assertNull(mode, SelectionPolicy.reconcile(null, candidates, "idle"));

      Place previouslySelected = candidates.get(0);
      Place browsing = SelectionPolicy.beginMapBrowsing(previouslySelected, "idle");
      assertNull(mode, browsing);
      assertNull(mode, SelectionPolicy.reconcile(browsing, candidates, "idle"));
    }
  }

  @Test
  public void anOngoingStatusCannotInventAMissingTarget() {
    List<Place> candidates = PlaceRepository.recommend("instant", 14, 500, "");
    for (String status : new String[] {"requesting", "unconfirmed", "active", "arrived"}) {
      assertTrue(SelectionPolicy.isOngoing(status));
      assertNull(SelectionPolicy.reconcile(null, candidates, status));
      assertNull(SelectionPolicy.beginMapBrowsing(null, status));
    }
  }

  @Test
  public void returningToTheMapClearsIdleAndFailedSelections() {
    Place current = PlaceRepository.find("p3");
    for (String status : new String[] {"idle", "error", "cancelled", "", null}) {
      assertFalse(SelectionPolicy.isOngoing(status));
      assertNull(SelectionPolicy.beginMapBrowsing(current, status));
    }
  }
}
