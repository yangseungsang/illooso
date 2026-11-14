package ai.illooso.app;

import static org.junit.Assert.*;

import org.junit.Test;

public class MapProjectionTest {
  @Test
  public void zeroZoomUsesOne256PixelTileWithClampedDateLineEdges() {
    assertEquals(0, MapProjection.worldX(-180, 0), 0);
    assertEquals(128, MapProjection.worldX(0, 0), 0);
    assertEquals(256, MapProjection.worldX(180, 0), 0);
    assertEquals(128, MapProjection.worldY(0, 0), 1e-10);
    assertEquals(0, MapProjection.worldX(-200, 0), 0);
    assertEquals(256, MapProjection.worldX(200, 0), 0);
    assertEquals(-180, MapProjection.longitude(-10, 0), 0);
    assertEquals(180, MapProjection.longitude(270, 0), 0);
  }

  @Test
  public void jinjuCoordinateFallsInExpectedStreetTileAndReturnsToSameLocation() {
    double x = MapProjection.worldX(128.0653891, 16);
    double y = MapProjection.worldY(35.1738345, 16);
    assertEquals(56081, (int) Math.floor(x / 256));
    assertEquals(25919, (int) Math.floor(y / 256));
    assertEquals(14356887.708485404, x, 1e-6);
    assertEquals(6635518.507270081, y, 1e-6);
    assertEquals(128.0653891, MapProjection.longitude(x, 16), 1e-9);
    assertEquals(35.1738345, MapProjection.latitude(y, 16), 1e-9);
  }

  @Test
  public void coordinatesRoundTripAcrossHemispheresAndZoomLevels() {
    double[][] coordinates = {
      {37.5665, 126.9780}, {-33.8688, 151.2093}, {51.5074, -0.1278}, {-45, -120}, {0, 0}
    };
    for (int zoom : new int[] {0, 8, 16, 22}) {
      for (double[] point : coordinates) {
        assertEquals(
            point[0], MapProjection.latitude(MapProjection.worldY(point[0], zoom), zoom), 1e-9);
        assertEquals(
            point[1], MapProjection.longitude(MapProjection.worldX(point[1], zoom), zoom), 1e-9);
      }
    }
  }

  @Test
  public void polarInputsClampToFiniteMapEdgesWithNorthAboveSouth() {
    double size = 256 * Math.pow(2, 16);
    assertEquals(0, MapProjection.worldY(90, 16), 0);
    assertEquals(size, MapProjection.worldY(-90, 16), 0);
    assertEquals(
        MapProjection.worldY(MapProjection.MAX_LATITUDE, 16), MapProjection.worldY(100, 16), 0);
    assertEquals(MapProjection.MAX_LATITUDE, MapProjection.latitude(-1, 16), 1e-8);
    assertEquals(-MapProjection.MAX_LATITUDE, MapProjection.latitude(size + 1, 16), 1e-8);
    assertTrue(MapProjection.worldY(36, 16) < MapProjection.worldY(35, 16));
  }

  @Test
  public void nextZoomDoublesPixelDistancesAndInvalidInputsAreRejected() {
    double west = MapProjection.worldX(128.06, 15);
    double east = MapProjection.worldX(128.07, 15);
    assertEquals(
        (east - west) * 2,
        MapProjection.worldX(128.07, 16) - MapProjection.worldX(128.06, 16),
        1e-8);
    assertEquals(MapProjection.worldY(35.17, 15) * 2, MapProjection.worldY(35.17, 16), 1e-8);
    assertThrows(IllegalArgumentException.class, () -> MapProjection.worldX(0, -1));
    assertThrows(IllegalArgumentException.class, () -> MapProjection.worldY(0, 31));
    assertThrows(IllegalArgumentException.class, () -> MapProjection.worldX(Double.NaN, 16));
    assertThrows(
        IllegalArgumentException.class, () -> MapProjection.latitude(Double.POSITIVE_INFINITY, 16));
  }
}
