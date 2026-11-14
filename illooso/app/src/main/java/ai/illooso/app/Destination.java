package ai.illooso.app;

import java.util.Objects;

/** A final destination, separate from the illustrative roadside place used to meet the vehicle. */
public final class Destination {
  public final String id, name, address, sourceLabel;
  public final double latitude, longitude;

  public Destination(
      String id,
      String name,
      String address,
      double latitude,
      double longitude,
      String sourceLabel) {
    this.id = Objects.requireNonNull(id, "id");
    this.name = Objects.requireNonNull(name, "name");
    this.address = Objects.requireNonNull(address, "address");
    this.latitude = latitude;
    this.longitude = longitude;
    this.sourceLabel = Objects.requireNonNull(sourceLabel, "sourceLabel");
  }
}
