package ai.illooso.app;

import java.util.Objects;

/**
 * Read-only place snapshot used by the native prototype.
 *
 * <p>All values, including coordinates and access conditions, are demo fixtures. A verified fixture
 * does not establish a real-world right to stop or park. The public tags array is defensively
 * copied for the UI contract; repository reads return fresh snapshots so callers cannot mutate the
 * stored fixtures.
 */
public final class Place {
  public final String id;
  public final String name;
  public final String subtitle;
  public final String address;
  public final String reason;
  public final String hours;
  public final String direction;
  public final String fee;
  public final int walk;
  public final int detour;
  public final int driveMinutes;
  public final int score;
  public final int reports;
  public final int openHour;
  public final int closeHour;
  public final boolean parking;
  public final boolean verified;
  public final boolean prohibited;
  public final boolean waitAllowed;
  public final float x;
  public final float y;
  public final double latitude;
  public final double longitude;
  public final String[] tags;

  public Place(
      String id,
      String name,
      String subtitle,
      String address,
      boolean parking,
      float x,
      float y,
      double latitude,
      double longitude,
      int walk,
      int detour,
      int driveMinutes,
      boolean verified,
      boolean prohibited,
      boolean waitAllowed,
      int openHour,
      int closeHour,
      int score,
      int reports,
      String[] tags,
      String reason,
      String direction,
      String hours,
      String fee) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = Objects.requireNonNull(name, "name");
    this.subtitle = Objects.requireNonNull(subtitle, "subtitle");
    this.address = Objects.requireNonNull(address, "address");
    this.parking = parking;
    this.x = x;
    this.y = y;
    this.latitude = latitude;
    this.longitude = longitude;
    this.walk = walk;
    this.detour = detour;
    this.driveMinutes = driveMinutes;
    this.verified = verified;
    this.prohibited = prohibited;
    this.waitAllowed = waitAllowed;
    this.openHour = openHour;
    this.closeHour = closeHour;
    this.score = score;
    this.reports = reports;
    this.tags = Objects.requireNonNull(tags, "tags").clone();
    this.reason = Objects.requireNonNull(reason, "reason");
    this.direction = Objects.requireNonNull(direction, "direction");
    this.hours = Objects.requireNonNull(hours, "hours");
    this.fee = Objects.requireNonNull(fee, "fee");
  }

  Place copy() {
    return new Place(
        id,
        name,
        subtitle,
        address,
        parking,
        x,
        y,
        latitude,
        longitude,
        walk,
        detour,
        driveMinutes,
        verified,
        prohibited,
        waitAllowed,
        openHour,
        closeHour,
        score,
        reports,
        tags,
        reason,
        direction,
        hours,
        fee);
  }
}
