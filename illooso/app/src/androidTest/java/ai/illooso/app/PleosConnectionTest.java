package ai.illooso.app;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Looper;
import android.util.Log;
import android.view.Display;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;
import kotlin.Unit;
import org.junit.Test;
import org.junit.runner.RunWith;

/**
 * Device integration test against the installed Pleos services. Read-only: does not launch an
 * Activity, request/cancel a route, modify vehicle state, inject a mock, or interact with the UI.
 */
@RunWith(AndroidJUnit4.class)
public final class PleosConnectionTest {
  private static final String TAG = "IlloosoIntegration";

  @Test
  public void actualSdkConnectsAndDeliversPolicyAndDestination() throws Exception {
    Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    Context target = instrumentation.getTargetContext();
    assertEquals(
        "Grant the declared Pleos navigation read permission before running integration tests",
        PackageManager.PERMISSION_GRANTED,
        target.checkSelfPermission(PleosBridge.READ_PERMISSION));
    DisplayManager displayManager =
        (DisplayManager) target.getSystemService(Context.DISPLAY_SERVICE);
    Display display =
        displayManager == null ? null : displayManager.getDisplay(Display.DEFAULT_DISPLAY);
    assertNotNull("A vehicle display must exist for display-scoped UX restrictions", display);
    Context displayContext = target.createDisplayContext(display);

    CountDownLatch observations = new CountDownLatch(3);
    AtomicBoolean sawConnection = new AtomicBoolean();
    AtomicBoolean sawPolicy = new AtomicBoolean();
    AtomicBoolean sawDestinationCallback = new AtomicBoolean();
    AtomicBoolean allBridgeCallbacksOnMain = new AtomicBoolean(true);
    AtomicBoolean closed = new AtomicBoolean();
    AtomicReference<PleosBridge.State> latest = new AtomicReference<>();
    AtomicReference<DestinationInfo> nativeDestination = new AtomicReference<>();
    AtomicReference<String> failure = new AtomicReference<>();
    AtomicReference<PleosBridge> bridge = new AtomicReference<>();
    AtomicReference<NaviHelper> readProbe = new AtomicReference<>();

    NaviHelperEventListener destinationListener =
        new NaviHelperEventListener() {
          @Override
          public void onDestinationInfo(DestinationInfo info) {
            if (closed.get()) return;
            nativeDestination.set(info);
            if (sawDestinationCallback.compareAndSet(false, true)) observations.countDown();
            Log.i(
                TAG,
                "actual onDestinationInfo callback received; destinationPresent="
                    + (info != null && !info.getName().trim().isEmpty()));
          }
        };

    try {
      instrumentation.runOnMainSync(
          () -> {
            PleosBridge live =
                new PleosBridge(
                    displayContext,
                    state -> {
                      if (closed.get()) return;
                      if (Looper.myLooper() != Looper.getMainLooper())
                        allBridgeCallbacksOnMain.set(false);
                      latest.set(state);
                      if (state.connected && sawConnection.compareAndSet(false, true))
                        observations.countDown();
                      if (state.policyKnown && sawPolicy.compareAndSet(false, true))
                        observations.countDown();
                    });
            bridge.set(live);
            live.start();

            // A separate read listener distinguishes a real no-destination
            // response from State.destinationAvailable=false before receipt.
            NaviHelper probe = new NaviHelper(displayContext);
            readProbe.set(probe);
            probe.initialize(
                () -> {
                  if (!closed.get()) {
                    try {
                      probe.addListener(destinationListener);
                      probe.getDestinationInfo();
                    } catch (RuntimeException | LinkageError error) {
                      failure.set("Destination query failed: " + error.getClass().getSimpleName());
                      while (observations.getCount() > 0) observations.countDown();
                    }
                  }
                  return Unit.INSTANCE;
                },
                error -> {
                  failure.set(
                      "Destination probe could not connect: " + error.getClass().getSimpleName());
                  while (observations.getCount() > 0) observations.countDown();
                  return Unit.INSTANCE;
                });
          });

      boolean completed = observations.await(15, TimeUnit.SECONDS);
      assertTrue(
          failure.get() == null
              ? "Native services returned no explicit initialization error"
              : failure.get(),
          failure.get() == null);
      assertTrue(
          "Within 15 s expected actual SDK connection, known vehicle UX policy, and an"
              + " onDestinationInfo response; observed connection="
              + sawConnection.get()
              + ", policy="
              + sawPolicy.get()
              + ", destinationCallback="
              + sawDestinationCallback.get(),
          completed);
      PleosBridge.State state = latest.get();
      assertNotNull(state);
      assertTrue("The bridge must still be connected", state.connected);
      assertTrue(
          "Vehicle policy must be known, rather than inferred from demo mode", state.policyKnown);
      assertTrue("Every bridge callback must run on main", allBridgeCallbacksOnMain.get());
      assertEquals("Connecting must not request a route", "idle", state.routeStatus);
      assertFalse(
          "No navigation mutation should be pending", "requesting".equals(state.routeStatus));
      if (state.destinationAvailable) {
        assertFalse(
            "An available destination needs a name", state.destinationName.trim().isEmpty());
        assertTrue(
            "Destination coordinates must be finite",
            Double.isFinite(state.latitude) && Double.isFinite(state.longitude));
        assertTrue("Destination latitude is in range", Math.abs(state.latitude) <= 90);
        assertTrue("Destination longitude is in range", Math.abs(state.longitude) <= 180);
      }
      DestinationInfo actual = nativeDestination.get();
      Log.i(
          TAG,
          "PASS real connection="
              + state.connected
              + ", policyKnown="
              + state.policyKnown
              + ", drivingRestricted="
              + state.drivingRestricted
              + ", route="
              + state.routeStatus
              + ", destinationCallback="
              + sawDestinationCallback.get()
              + ", destinationAvailable="
              + state.destinationAvailable
              + ", nativeDestinationName="
              + (actual == null ? "(none)" : actual.getName()));
    } finally {
      closed.set(true);
      instrumentation.runOnMainSync(
          () -> {
            NaviHelper probe = readProbe.get();
            try {
              if (probe != null) {
                try {
                  probe.removeListener(destinationListener);
                } finally {
                  probe.release();
                }
              }
            } finally {
              PleosBridge live = bridge.get();
              if (live != null) live.close();
            }
          });
    }
  }
}
