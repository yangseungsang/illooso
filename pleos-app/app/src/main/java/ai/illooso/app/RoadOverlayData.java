package ai.illooso.app;

import android.content.Context;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

/** OSM geometry with a separate, explicitly fictional pickup assessment layer. */
public final class RoadOverlayData {
  public enum Assessment {
    RECOMMEND("추천 예시", 0xff0d9c83),
    CAUTION("주의 예시", 0xffd98a11),
    EXCLUDED("제외 예시", 0xffe45862),
    UNKNOWN("미평가", 0xff8d9baa);

    public final String label;
    public final int color;

    Assessment(String label, int color) {
      this.label = label;
      this.color = color;
    }
  }

  // Explicit demonstration fixtures, not OSM facts or verified road regulations.
  // Unlisted ways (including p6's unverified road) remain UNKNOWN; road type never implies safety.
  private static final Map<String, Assessment> EXAMPLE_ASSESSMENTS =
      Map.ofEntries(
          // Original place-linked examples.
          Map.entry("904348798", Assessment.RECOMMEND),
          Map.entry("306128547", Assessment.RECOMMEND),
          Map.entry("306128573", Assessment.RECOMMEND),
          Map.entry("109340713", Assessment.CAUTION),
          Map.entry("839822363", Assessment.CAUTION),
          Map.entry("839822364", Assessment.EXCLUDED),
          Map.entry("226990386", Assessment.CAUTION),
          // Pyeonggeo side streets around the destination and western approaches.
          Map.entry("263519354", Assessment.RECOMMEND),
          Map.entry("904348797", Assessment.RECOMMEND),
          Map.entry("904348808", Assessment.RECOMMEND),
          Map.entry("904348811", Assessment.RECOMMEND),
          Map.entry("904348812", Assessment.RECOMMEND),
          Map.entry("263514278", Assessment.RECOMMEND),
          Map.entry("263514276", Assessment.RECOMMEND),
          Map.entry("904348799", Assessment.CAUTION),
          Map.entry("839822365", Assessment.CAUTION),
          Map.entry("1338790234", Assessment.CAUTION),
          Map.entry("904348809", Assessment.EXCLUDED),
          // Sinpyeong Park neighborhood, including short connecting streets.
          Map.entry("306128542", Assessment.RECOMMEND),
          Map.entry("904348800", Assessment.RECOMMEND),
          Map.entry("904348801", Assessment.RECOMMEND),
          Map.entry("306128552", Assessment.RECOMMEND),
          Map.entry("306128562", Assessment.RECOMMEND),
          Map.entry("306806220", Assessment.RECOMMEND),
          Map.entry("263514281", Assessment.RECOMMEND),
          Map.entry("109340715", Assessment.CAUTION),
          Map.entry("263514284", Assessment.CAUTION),
          Map.entry("306128560", Assessment.CAUTION),
          Map.entry("904348789", Assessment.CAUTION),
          Map.entry("306128575", Assessment.CAUTION),
          Map.entry("263514282", Assessment.CAUTION),
          Map.entry("109340711", Assessment.CAUTION),
          Map.entry("306128567", Assessment.EXCLUDED),
          Map.entry("904348787", Assessment.EXCLUDED),
          Map.entry("904348788", Assessment.EXCLUDED),
          Map.entry("904348790", Assessment.EXCLUDED),
          Map.entry("1351803491", Assessment.EXCLUDED),
          // Northern parking neighborhood and east/west connections from the same OSM archive.
          Map.entry("306806213", Assessment.RECOMMEND),
          Map.entry("904348769", Assessment.RECOMMEND),
          Map.entry("904348770", Assessment.RECOMMEND),
          Map.entry("306806222", Assessment.RECOMMEND),
          Map.entry("306806216", Assessment.RECOMMEND),
          Map.entry("306128550", Assessment.RECOMMEND),
          Map.entry("904348775", Assessment.RECOMMEND),
          Map.entry("461554838", Assessment.RECOMMEND),
          Map.entry("306806225", Assessment.CAUTION),
          Map.entry("306128548", Assessment.CAUTION),
          Map.entry("109340712", Assessment.CAUTION),
          Map.entry("1338509497", Assessment.CAUTION),
          Map.entry("904348785", Assessment.CAUTION),
          Map.entry("306128544", Assessment.CAUTION),
          Map.entry("306128570", Assessment.CAUTION),
          Map.entry("904348774", Assessment.CAUTION),
          Map.entry("306128568", Assessment.EXCLUDED),
          Map.entry("1351803507", Assessment.EXCLUDED),
          Map.entry("461554840", Assessment.EXCLUDED),
          Map.entry("461554837", Assessment.EXCLUDED));
  private static final Map<String, String> EXAMPLE_PLACES =
      Map.of(
          "904348798",
          "p1",
          "306128547",
          "p2",
          "306128573",
          "p3",
          "109340713",
          "p4",
          "839822363",
          "p5",
          "263514283",
          "p6",
          "839822364",
          "p7",
          "226990386",
          "p8");

  public static final class Road {
    public final String id, name, placeId;
    public final Assessment assessment;

    /** Original OSM geometry: longitude, latitude pairs. */
    public final double[][] points;

    private Road(JSONObject json) throws Exception {
      id = json.getString("id");
      name = json.optString("name", "");
      placeId = EXAMPLE_PLACES.get(id);
      assessment = EXAMPLE_ASSESSMENTS.getOrDefault(id, Assessment.UNKNOWN);
      JSONArray coordinates = json.getJSONArray("points");
      points = new double[coordinates.length()][2];
      for (int i = 0; i < coordinates.length(); i++) {
        points[i][0] = coordinates.getJSONArray(i).getDouble(0);
        points[i][1] = coordinates.getJSONArray(i).getDouble(1);
      }
    }

    public String displayName() {
      return name.isEmpty() ? "이름 없는 도로" : name;
    }
  }

  public static List<Road> load(Context context) throws Exception {
    try (InputStream input = context.getAssets().open("pyeonggeo-roads.json")) {
      JSONObject source = new JSONObject(new String(input.readAllBytes(), StandardCharsets.UTF_8));
      JSONArray values = source.getJSONArray("roads");
      List<Road> roads = new ArrayList<>();
      for (int i = 0; i < values.length(); i++) roads.add(new Road(values.getJSONObject(i)));
      return Collections.unmodifiableList(roads);
    }
  }

  private RoadOverlayData() {}
}
