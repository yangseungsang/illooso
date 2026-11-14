package ai.illooso.app;

import android.content.Context;
import android.content.SharedPreferences;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

/** Browser-independent device-local prototype persistence. Reports never mutate road rules. */
public final class LocalStore {
  private final SharedPreferences prefs;

  public LocalStore(Context context) {
    prefs = context.getSharedPreferences("illooso-prototype", Context.MODE_PRIVATE);
  }

  public Set<String> saved() {
    return new HashSet<>(prefs.getStringSet("saved", new HashSet<>()));
  }

  public boolean toggle(String id) {
    Set<String> ids = saved();
    boolean added = ids.add(id);
    if (!added) ids.remove(id);
    prefs.edit().putStringSet("saved", ids).apply();
    return added;
  }

  /** Device-local helpful votes; the shared preference's backing set is never mutated. */
  public boolean isHelpful(String reviewId) {
    return reviewId != null && helpfulReviews().contains(reviewId);
  }

  public boolean toggleHelpful(String reviewId) {
    if (reviewId == null || reviewId.isBlank()) return false;
    Set<String> ids = helpfulReviews();
    boolean added = ids.add(reviewId);
    if (!added) ids.remove(reviewId);
    prefs.edit().putStringSet("helpfulReviews", ids).apply();
    return added;
  }

  private Set<String> helpfulReviews() {
    return new HashSet<>(prefs.getStringSet("helpfulReviews", new HashSet<>()));
  }

  public static final class Report {
    public String id, placeId, type, text, mode;
    public long visitedAt, createdAt;

    Report(
        String id,
        String placeId,
        String type,
        String text,
        long visitedAt,
        long createdAt,
        String mode) {
      this.id = id;
      this.placeId = placeId;
      this.type = type;
      this.text = text;
      this.visitedAt = visitedAt;
      this.createdAt = createdAt;
      this.mode = normalizeMode(mode);
    }
  }

  public List<Report> reports() {
    List<Report> items = new ArrayList<>();
    try {
      JSONArray a = new JSONArray(prefs.getString("reports", "[]"));
      for (int i = 0; i < a.length(); i++) {
        JSONObject j = a.getJSONObject(i);
        String id = j.getString("id"),
            place = j.getString("placeId"),
            type = j.getString("type"),
            text = j.optString("text");
        long v = j.getLong("visitedAt"), c = j.getLong("createdAt");
        if (PlaceRepository.find(place) != null && v > 0 && c > 0 && text.length() <= 300)
          items.add(new Report(id, place, type, text, v, c, j.optString("mode", "")));
      }
    } catch (Exception ignored) {
    }
    return items;
  }

  public void saveReport(
      String id, String placeId, String type, String text, long visitedAt, String mode) {
    List<Report> list = reports();
    long created = System.currentTimeMillis();
    for (Report old : list) if (old.id.equals(id)) created = old.createdAt;
    list.removeIf(r -> r.id.equals(id));
    list.add(
        0,
        new Report(
            id == null ? UUID.randomUUID().toString() : id,
            placeId,
            type,
            text,
            visitedAt,
            created,
            mode));
    write(list);
  }

  public void removeReport(String id) {
    List<Report> list = reports();
    list.removeIf(r -> r.id.equals(id));
    write(list);
  }

  private void write(List<Report> items) {
    JSONArray a = new JSONArray();
    try {
      for (Report r : items)
        a.put(
            new JSONObject()
                .put("id", r.id)
                .put("placeId", r.placeId)
                .put("type", r.type)
                .put("text", r.text)
                .put("visitedAt", r.visitedAt)
                .put("mode", r.mode)
                .put("createdAt", r.createdAt));
      prefs.edit().putString("reports", a.toString()).apply();
    } catch (Exception ignored) {
    }
  }

  private static String normalizeMode(String mode) {
    return "instant".equals(mode) || "wait".equals(mode) || "parking".equals(mode) ? mode : "";
  }
}
