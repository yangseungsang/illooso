package ai.illooso.app;

import static org.junit.Assert.*;

import ai.illooso.app.MapLabelLayout.Anchor;
import ai.illooso.app.MapLabelLayout.Bounds;
import ai.illooso.app.MapLabelLayout.Label;
import java.util.List;
import org.junit.Test;

public class MapLabelLayoutTest {
  @Test
  public void crowdedPlacesFitWithoutCoveringLabelsControlsOrDestination() {
    Bounds viewport = new Bounds(20, 270, 820, 960);
    List<Anchor> anchors =
        List.of(
            new Anchor("north", 431, 316, 250, 80, true),
            new Anchor("pickup", 263, 844, 250, 80, false),
            new Anchor("parking", 362, 901, 250, 80, false),
            new Anchor("east", 577, 848, 250, 80, false));
    List<Bounds> obstacles =
        List.of(
            new Bounds(754, 270, 820, 526),
            new Bounds(200, 440, 430, 505),
            new Bounds(407, 292, 455, 340),
            new Bounds(239, 820, 287, 868),
            new Bounds(338, 877, 386, 925),
            new Bounds(553, 824, 601, 872));

    List<Label> labels = MapLabelLayout.layout(anchors, viewport, obstacles, 8);

    assertEquals(4, labels.size());
    assertSafe(labels, viewport, obstacles, 8);
  }

  @Test
  public void labelsAtEveryViewportEdgeStayFullyVisible() {
    Bounds viewport = new Bounds(20, 200, 820, 1000);
    List<Anchor> anchors =
        List.of(
            new Anchor("top-left", 21, 201, 220, 76, false),
            new Anchor("top-right", 819, 201, 220, 76, false),
            new Anchor("bottom-left", 21, 999, 220, 76, false),
            new Anchor("bottom-right", 819, 999, 220, 76, false));
    List<Label> labels = MapLabelLayout.layout(anchors, viewport, List.of(), 8);
    assertEquals(4, labels.size());
    assertSafe(labels, viewport, List.of(), 8);
    for (Label label : labels)
      for (Anchor anchor : anchors) assertFalse(label.bounds.contains(anchor.x, anchor.y));
  }

  @Test
  public void recommendationWinsLimitedSpaceWithoutDisplacingTheMap() {
    Bounds viewport = new Bounds(0, 0, 250, 100);
    List<Anchor> anchors =
        List.of(
            new Anchor("ordinary", 125, 125, 240, 80, false),
            new Anchor("recommended", 125, 125, 240, 80, true));
    List<Label> labels = MapLabelLayout.layout(anchors, viewport, List.of(), 8);
    assertEquals(1, labels.size());
    assertEquals("recommended", labels.get(0).id);
    assertSafe(labels, viewport, List.of(), 8);
  }

  @Test
  public void repeatedFramesChooseTheSamePlacementAndPreservePriorityOrder() {
    Bounds viewport = new Bounds(0, 0, 800, 800);
    List<Anchor> anchors =
        List.of(
            new Anchor("first", 400, 400, 240, 80, false),
            new Anchor("second", 400, 420, 240, 80, false),
            new Anchor("recommended", 400, 440, 240, 80, true));
    List<Label> first = MapLabelLayout.layout(anchors, viewport, List.of(), 8);
    List<Label> next = MapLabelLayout.layout(anchors, viewport, List.of(), 8);
    assertEquals(List.of("recommended", "first", "second"), first.stream().map(l -> l.id).toList());
    assertEquals(first.size(), next.size());
    for (int i = 0; i < first.size(); i++) {
      assertEquals(first.get(i).id, next.get(i).id);
      assertEquals(first.get(i).bounds.left, next.get(i).bounds.left, 0);
      assertEquals(first.get(i).bounds.top, next.get(i).bounds.top, 0);
    }
  }

  @Test
  public void impossibleLabelsAreOmittedInsteadOfClippedOrOverlapping() {
    Anchor anchor = new Anchor("large", 50, 50, 250, 80, true);
    assertTrue(
        MapLabelLayout.layout(List.of(anchor), new Bounds(0, 0, 200, 70), List.of(), 8).isEmpty());
    Bounds viewport = new Bounds(0, 0, 500, 500);
    assertTrue(MapLabelLayout.layout(List.of(anchor), viewport, List.of(viewport), 8).isEmpty());
  }

  private static void assertSafe(
      List<Label> labels, Bounds viewport, List<Bounds> obstacles, float gap) {
    for (int i = 0; i < labels.size(); i++) {
      Bounds current = labels.get(i).bounds;
      assertTrue(viewport.contains(current));
      for (Bounds obstacle : obstacles) assertGap(current, obstacle, gap);
      for (int j = 0; j < i; j++) assertGap(current, labels.get(j).bounds, gap);
    }
  }

  private static void assertGap(Bounds first, Bounds second, float gap) {
    assertTrue(
        first.right + gap <= second.left
            || second.right + gap <= first.left
            || first.bottom + gap <= second.top
            || second.bottom + gap <= first.top);
  }
}
