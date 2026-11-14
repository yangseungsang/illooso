package ai.illooso.app;

import java.util.List;

/**
 * Keeps a running meeting flow stable while reconciling idle selections with search eligibility.
 */
public final class SelectionPolicy {
  private SelectionPolicy() {}

  /**
   * Keeps the current place during requesting, unconfirmed, active or arrived flows. Otherwise the
   * current place must still be present in the filtered candidates. Browsing never automatically
   * selects a candidate. Candidates must already have passed the repository's eligibility checks.
   */
  public static Place reconcile(Place current, List<Place> candidates, String routeStatus) {
    if (current == null) return null;
    if (isOngoing(routeStatus)) return current;
    if (candidates == null) return null;
    for (Place candidate : candidates) {
      if (current.id.equals(candidate.id)) return current;
    }
    return null;
  }

  /** Returns to map exploration without discarding the target of an ongoing meeting flow. */
  public static Place beginMapBrowsing(Place current, String routeStatus) {
    return isOngoing(routeStatus) ? current : null;
  }

  public static boolean isOngoing(String routeStatus) {
    return "requesting".equals(routeStatus)
        || "unconfirmed".equals(routeStatus)
        || "active".equals(routeStatus)
        || "arrived".equals(routeStatus);
  }
}
