package ai.illooso.app;

import ai.pleos.playground.navi.constants.NaviErrorCode;
import ai.pleos.playground.navi.constants.NaviMode;
import ai.pleos.playground.navi.constants.NavigationStatus;
import ai.pleos.playground.navi.constants.RouteOption;
import ai.pleos.playground.navi.data.CurrentLocationInfo;
import ai.pleos.playground.navi.data.DestinationArrivedInfo;
import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.data.NavigationScreenRequest;
import ai.pleos.playground.navi.data.RouteInfo;
import ai.pleos.playground.navi.data.RouteStartInfo;
import ai.pleos.playground.navi.data.RouteStateInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import android.car.Car;
import android.car.drivingstate.CarUxRestrictions;
import android.car.drivingstate.CarUxRestrictionsManager;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import kotlin.Unit;

/**
 * Real Pleos NaviHelper 3.0.5 / navi-data 3.0.4 bridge. No mock fallback. Every State is an
 * immutable snapshot delivered on the Android main thread. latitude/longitude describe the ORIGINAL
 * destination, not vehicle location. nativeDestination* is the latest destination actually reported
 * by the navigator, including our own pickup target, for an explicit import action.
 */
public final class PleosBridge implements AutoCloseable {
  public interface Listener {
    void onState(State s);
  }

  public static final class State {
    public final boolean connected, drivingRestricted, policyKnown, destinationAvailable;
    public final String connectionMessage, destinationName, routeStatus, routeMessage;
    public final String navigationMapStatus, navigationMapMessage;
    public final double latitude, longitude;
    public final boolean hasCurrentLocation;
    public final double currentLatitude, currentLongitude;
    public final long currentLocationReceivedAtMs;
    public final boolean nativeDestinationAvailable;
    public final String nativeDestinationName;
    public final double nativeDestinationLatitude, nativeDestinationLongitude;
    public final long destinationResponseVersion, destinationReceivedAtMs;

    private State(PleosBridge b) {
      connected = b.connected;
      drivingRestricted = b.drivingRestricted;
      policyKnown = b.policyKnown;
      destinationAvailable = b.destinationAvailable;
      connectionMessage = b.connectionMessage;
      destinationName = b.destinationName;
      routeStatus = b.routeStatus;
      routeMessage = b.routeMessage;
      navigationMapStatus = b.navigationMapStatus;
      navigationMapMessage = b.navigationMapMessage;
      latitude = b.latitude;
      longitude = b.longitude;
      hasCurrentLocation = b.hasCurrentLocation;
      currentLatitude = b.currentLatitude;
      currentLongitude = b.currentLongitude;
      currentLocationReceivedAtMs = b.locationReceivedAt;
      nativeDestinationAvailable = b.nativeDestinationAvailable;
      nativeDestinationName = b.nativeDestinationName;
      nativeDestinationLatitude = b.nativeDestinationLatitude;
      nativeDestinationLongitude = b.nativeDestinationLongitude;
      destinationResponseVersion = b.destinationResponseVersion;
      destinationReceivedAtMs = b.destinationReceivedAt;
    }
  }

  public static final String READ_PERMISSION = "pleos.car.permission.NAVI_ROUTE";
  public static final String ROUTE_PERMISSION = "pleos.car.permission.NAVI_ROUTE_SEARCH";
  public static final String MAP_PERMISSION = "pleos.car.permission.NAVI_CUSTOM_MAP";
  private static final String TAG = "IlloosoPleos";
  private static final long CONNECT_TIMEOUT = 10000, ROUTE_TIMEOUT = 25000;
  private static final long POLL_INTERVAL = 2500, STALE_AFTER = 15000;
  private final Context context;
  private final Listener listener;
  private final Handler main = new Handler(Looper.getMainLooper());
  private Car car;
  private CarUxRestrictionsManager ux;
  private NaviHelper helper;
  private NaviHelperEventListener events;
  private boolean started, connected, policyKnown, destinationAvailable, hasCurrentLocation;
  private boolean drivingRestricted = true;
  private String connectionMessage = "Pleos 연결 전",
      destinationName = "",
      routeStatus = "idle",
      routeMessage = "";
  private String navigationMapStatus = "idle", navigationMapMessage = "";
  private double latitude = Double.NaN, longitude = Double.NaN;
  private double currentLatitude = Double.NaN, currentLongitude = Double.NaN;
  private long lastResponseAt, locationReceivedAt;
  private boolean nativeDestinationAvailable;
  private String nativeDestinationName = "";
  private double nativeDestinationLatitude = Double.NaN, nativeDestinationLongitude = Double.NaN;
  // Never reset this counter on reconnect: a caller can await a genuinely new SDK response,
  // including an empty response, rather than treating cached destination data as a fresh import.
  private long destinationResponseVersion, destinationReceivedAt;
  private int lifecycleGeneration, naviGeneration, requestNumber;
  private boolean ownTarget, targetObserved, routingCompleted, nativeGuidanceConfirmed;
  private long routeStateResponseVersion, targetRouteStateBaseline;
  private double targetLatitude = Double.NaN, targetLongitude = Double.NaN;
  private boolean originalAvailable;
  private String originalName = "";
  private double originalLatitude = Double.NaN, originalLongitude = Double.NaN;
  private String lastLog = "";
  private String lastPolicyDiagnostic = "";

  public PleosBridge(Context context, Listener listener) {
    if (context == null || listener == null)
      throw new IllegalArgumentException("Context and listener are required");
    // Retain Activity/display context: the UX policy can differ by display.
    this.context = context;
    this.listener = listener;
  }

  /** Safe to call again after granting permission or after a failed connection. */
  public void start() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      main.post(this::start);
      return;
    }
    if (!started) {
      started = true;
      final int generation = ++lifecycleGeneration;
      policyKnown = false;
      drivingRestricted = true;
      try {
        Log.i(TAG, "UX connecting Car service; context=" + context.getClass().getName());
        car =
            Car.createCar(
                context,
                main,
                Car.CAR_WAIT_TIMEOUT_DO_NOT_WAIT,
                (service, ready) ->
                    main.post(
                        () -> {
                          if (!started || generation != lifecycleGeneration) return;
                          Log.i(
                              TAG,
                              "UX Car lifecycle ready="
                                  + ready
                                  + " servicePresent="
                                  + (service != null));
                          if (!ready) {
                            ux = null;
                            unknownPolicy();
                            emit();
                            return;
                          }
                          try {
                            ux =
                                (CarUxRestrictionsManager)
                                    service.getCarManager(Car.CAR_UX_RESTRICTION_SERVICE);
                            Log.i(TAG, "UX manager present=" + (ux != null));
                            if (ux != null) {
                              ux.registerListener(
                                  restrictions ->
                                      main.post(
                                          () -> {
                                            if (started && generation == lifecycleGeneration) {
                                              Log.i(
                                                  TAG,
                                                  "UX restriction callback received; present="
                                                      + (restrictions != null));
                                              applyPolicy(restrictions);
                                              emit();
                                            }
                                          }));
                              applyPolicy(ux.getCurrentCarUxRestrictions());
                            } else unknownPolicy();
                          } catch (RuntimeException | LinkageError error) {
                            Log.e(TAG, "UX manager registration/read failed", error);
                            unknownPolicy();
                          }
                          emit();
                        }));
        Log.i(TAG, "UX createCar returned present=" + (car != null));
        main.postDelayed(
            () -> {
              if (started && generation == lifecycleGeneration && !policyKnown)
                Log.w(
                    TAG,
                    "UX policy still unknown after 10s; CarService may be unavailable; carPresent="
                        + (car != null));
            },
            CONNECT_TIMEOUT);
      } catch (RuntimeException | LinkageError error) {
        Log.e(TAG, "UX Car.createCar failed", error);
        unknownPolicy();
      }
    }
    if (helper == null) connectNavi();
    else if (connected) refreshDestination();
    emit();
  }

  private void connectNavi() {
    if (!hasPermission(READ_PERMISSION)) {
      connectionMessage = "내비게이션 조회 권한이 필요합니다";
      connected = false;
      clearDestination();
      clearNativeDestination();
      emit();
      return;
    }
    final int generation = ++naviGeneration;
    connectionMessage = "Pleos 내비게이션에 연결 중입니다";
    connected = false;
    clearDestination();
    clearNativeDestination();
    try {
      helper = new NaviHelper(context);
      events = new Events(generation);
      helper.initialize(
          () -> {
            postNavi(
                generation,
                () -> {
                  try {
                    helper.addListener(events);
                    connected = true;
                    connectionMessage = "Pleos 내비게이션 연결됨";
                    lastResponseAt = SystemClock.elapsedRealtime();
                    main.removeCallbacks(poll);
                    poll.run();
                    emit();
                  } catch (RuntimeException | LinkageError error) {
                    failNavi("내비게이션 이벤트를 연결하지 못했습니다");
                  }
                });
            return Unit.INSTANCE;
          },
          error -> {
            postNavi(generation, () -> failNavi("Pleos 내비게이션 연결 실패 · 다시 연결해 주세요"));
            return Unit.INSTANCE;
          });
      main.postDelayed(
          () -> {
            if (validNavi(generation) && !connected) failNavi("내비게이션 연결 시간이 초과되었습니다");
          },
          CONNECT_TIMEOUT);
    } catch (RuntimeException | LinkageError error) {
      failNavi("Pleos Navi Service를 사용할 수 없습니다");
    }
  }

  /**
   * Requests a new navigator destination snapshot. Callers awaiting an explicit import should
   * retain State.destinationResponseVersion before calling and wait for a larger version or their
   * own timeout. Native destination fields describe the navigator's current target, not the
   * preserved source destination used by the meeting flow. The SDK does not provide query
   * correlation IDs.
   */
  public void refreshDestination() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      main.post(this::refreshDestination);
      return;
    }
    refreshPolicy();
    if (!started || helper == null || !connected) {
      emit();
      return;
    }
    if (!hasPermission(READ_PERMISSION)) {
      failNavi("내비게이션 조회 권한이 해제되었습니다");
      return;
    }
    try {
      helper.getDestinationInfo();
      helper.getCurrentLocationInfo();
      helper.getRouteStateInfo();
    } catch (RuntimeException | LinkageError error) {
      failNavi("내비게이션 정보를 읽지 못했습니다");
    }
  }

  /**
   * Asks Pleos to bring its existing navigation screen to the foreground without changing its
   * destination or route. State.navigationMapStatus is idle/requested/error. "requested" means only
   * that NaviHelper accepted the call: this SDK has no screen-visible acknowledgment, so the UI
   * must never interpret it as confirmed map display or invent a completion timeout.
   */
  public void openNavigationMap() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      main.post(this::openNavigationMap);
      return;
    }
    refreshPolicy();
    if (!policyKnown || drivingRestricted) {
      navigationMapError("주차 상태가 확인된 뒤 내비 지도를 열 수 있습니다");
      return;
    }
    if (!started || helper == null || !connected) {
      navigationMapError("먼저 Pleos 내비게이션을 다시 연결해 주세요");
      return;
    }
    if (!hasPermission(READ_PERMISSION) || !hasPermission(MAP_PERMISSION)) {
      navigationMapError("내비 지도 열기 권한이 필요합니다. 권한을 허용한 뒤 다시 시도해 주세요");
      return;
    }
    if (lastResponseAt <= 0 || SystemClock.elapsedRealtime() - lastResponseAt > STALE_AFTER) {
      navigationMapStatus = "error";
      navigationMapMessage = "내비게이션 응답이 끊겼습니다. 다시 연결한 뒤 지도를 열어 주세요";
      failNavi(navigationMapMessage);
      return;
    }
    try {
      // Kotlin object, verified against NaviHelper 3.0.5 / navi-data 3.0.4. Foreground has
      // no coordinates, route, or embedded Surface; it preserves the navigator's current route.
      helper.requestNavigationScreen(NavigationScreenRequest.Foreground.INSTANCE);
      navigationMapStatus = "requested";
      navigationMapMessage = "내비 지도 열기를 요청했습니다. 화면이 열리지 않으면 다시 시도해 주세요";
      Log.i(TAG, "Navigation screen request submitted; visibility is not acknowledged by SDK");
      emit();
    } catch (RuntimeException | LinkageError error) {
      Log.e(TAG, "Navigation screen request failed", error);
      navigationMapError("내비 지도 열기 요청을 전달하지 못했습니다. 다시 시도해 주세요");
    }
  }

  private void navigationMapError(String message) {
    navigationMapStatus = "error";
    navigationMapMessage = message;
    emit();
  }

  /** Starts a real native route search. A method return is never considered success. */
  public void requestRoute(String name, double lat, double lon) {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      main.post(() -> requestRoute(name, lat, lon));
      return;
    }
    refreshPolicy();
    if (!policyKnown || drivingRestricted) {
      routeError("주차 상태가 확인된 뒤 경로를 설정할 수 있습니다");
      return;
    }
    if (!started || helper == null || !connected) {
      routeError("먼저 Pleos 내비게이션을 연결해 주세요");
      return;
    }
    if (!hasPermission(READ_PERMISSION) || !hasPermission(ROUTE_PERMISSION)) {
      routeError("경로 설정 권한이 필요합니다");
      return;
    }
    if (!validPoint(lat, lon) || clean(name).isEmpty()) {
      routeError("선택한 승하차 지점의 위치를 확인해 주세요");
      return;
    }
    if ("requesting".equals(routeStatus)) return;
    originalAvailable = destinationAvailable;
    originalName = destinationName;
    originalLatitude = latitude;
    originalLongitude = longitude;
    targetLatitude = lat;
    targetLongitude = lon;
    ownTarget = true;
    targetObserved = false;
    routingCompleted = false;
    nativeGuidanceConfirmed = false;
    targetRouteStateBaseline = routeStateResponseVersion;
    routeStatus = "requesting";
    routeMessage = "중앙 내비게이션에서 경로를 탐색하고 있습니다";
    final int request = ++requestNumber;
    emit();
    try {
      // Verified against navi-data-3.0.4 bytecode: latitude precedes longitude.
      // No fabricated POI ID/provider. Coordinate-only acceptance is decided
      // by the native navigator, and failures remain visible in onError/timeout.
      RouteInfo route =
          new RouteInfo(
              lat, lon, null, null, clean(name), "", "", "", RouteOption.RECOMMENDED, "", false);
      helper.requestRoute(route);
      main.postDelayed(
          () -> {
            if (started && request == requestNumber && "requesting".equals(routeStatus)) {
              // Some navigation implementations omit the completion event. No acknowledgement
              // proves neither failure nor success; a later matching guidance snapshot may confirm.
              ++requestNumber;
              routeStatus = "unconfirmed";
              routeMessage = "요청은 전송했지만 안내 시작 여부를 확인하지 못했어요. 순정 내비에서 확인해 주세요.";
              emit();
            }
          },
          ROUTE_TIMEOUT);
    } catch (RuntimeException | LinkageError error) {
      routeError("경로 요청을 전달하지 못했습니다. 다시 시도해 주세요");
    }
  }

  private final Runnable poll =
      new Runnable() {
        @Override
        public void run() {
          if (!started || helper == null || !connected) return;
          long now = SystemClock.elapsedRealtime();
          if (now - lastResponseAt > STALE_AFTER) {
            failNavi("내비게이션 응답이 끊겼습니다. 다시 연결해 주세요");
            return;
          }
          if (hasCurrentLocation && now - locationReceivedAt > STALE_AFTER) {
            clearLocation();
            emit();
          }
          refreshDestination();
          if (started && helper != null && connected) main.postDelayed(this, POLL_INTERVAL);
        }
      };

  private void refreshPolicy() {
    try {
      if (car == null || !car.isConnected() || ux == null) {
        String diagnostic =
            "UX unavailable: carPresent="
                + (car != null)
                + " connected="
                + (car != null && car.isConnected())
                + " managerPresent="
                + (ux != null);
        if (!diagnostic.equals(lastPolicyDiagnostic)) {
          Log.w(TAG, diagnostic);
          lastPolicyDiagnostic = diagnostic;
        }
        unknownPolicy();
        return;
      }
      applyPolicy(ux.getCurrentCarUxRestrictions());
    } catch (RuntimeException | LinkageError error) {
      Log.e(TAG, "UX policy refresh failed", error);
      unknownPolicy();
    }
  }

  private void applyPolicy(CarUxRestrictions restrictions) {
    if (restrictions == null) {
      unknownPolicy();
      return;
    }
    policyKnown = true;
    // This prototype's browsing, typing and route selection UI is parked-only.
    drivingRestricted =
        restrictions.isRequiresDistractionOptimization()
            || restrictions.getActiveRestrictions() != CarUxRestrictions.UX_RESTRICTIONS_BASELINE;
    String diagnostic =
        "UX known: requiresOptimization="
            + restrictions.isRequiresDistractionOptimization()
            + " flags="
            + restrictions.getActiveRestrictions();
    if (!diagnostic.equals(lastPolicyDiagnostic)) {
      Log.i(TAG, diagnostic);
      lastPolicyDiagnostic = diagnostic;
    }
  }

  private void unknownPolicy() {
    policyKnown = false;
    drivingRestricted = true;
  }

  private boolean hasPermission(String permission) {
    return context.checkSelfPermission(permission) == PackageManager.PERMISSION_GRANTED;
  }

  private boolean validNavi(int generation) {
    return started && helper != null && generation == naviGeneration;
  }

  private void postNavi(int generation, Runnable action) {
    main.post(
        () -> {
          if (!validNavi(generation)) return;
          if (!hasPermission(READ_PERMISSION)) {
            failNavi("내비게이션 조회 권한이 해제되었습니다");
            return;
          }
          action.run();
        });
  }

  private void received() {
    lastResponseAt = SystemClock.elapsedRealtime();
    connected = true;
  }

  private void clearDestination() {
    destinationAvailable = false;
    destinationName = "";
    latitude = Double.NaN;
    longitude = Double.NaN;
  }

  private void clearLocation() {
    hasCurrentLocation = false;
    currentLatitude = Double.NaN;
    currentLongitude = Double.NaN;
    locationReceivedAt = 0;
  }

  private void clearNativeDestination() {
    nativeDestinationAvailable = false;
    nativeDestinationName = "";
    nativeDestinationLatitude = Double.NaN;
    nativeDestinationLongitude = Double.NaN;
    destinationReceivedAt = 0;
  }

  private void recordNativeDestination(DestinationInfo info) {
    ++destinationResponseVersion;
    clearNativeDestination();
    // Receipt time is monotonic, and an empty destination response is a completed lookup too.
    destinationReceivedAt = SystemClock.elapsedRealtime();
    if (info == null || !validPoint(info.getLatitude(), info.getLongitude())) return;
    nativeDestinationAvailable = true;
    nativeDestinationName = clean(info.getName());
    if (nativeDestinationName.isEmpty()) nativeDestinationName = "내비게이션 목적지";
    nativeDestinationLatitude = info.getLatitude();
    nativeDestinationLongitude = info.getLongitude();
  }

  private void routeError(String message) {
    ++requestNumber;
    routeStatus = "error";
    routeMessage = message;
    // Keep ownTarget while native destination is this pickup; an error must
    // not reinterpret that pickup as a newly chosen original destination.
    emit();
  }

  private void confirmRouteIfReady() {
    if (("requesting".equals(routeStatus) || "unconfirmed".equals(routeStatus))
        && targetObserved
        && (routingCompleted || nativeGuidanceConfirmed)) {
      ++requestNumber;
      routeStatus = "active";
      routeMessage = "중앙 내비게이션에서 승하차 지점으로 안내 중입니다";
    }
  }

  private void failNavi(String message) {
    connected = false;
    connectionMessage = message;
    clearDestination();
    clearNativeDestination();
    clearLocation();
    targetObserved = false;
    nativeGuidanceConfirmed = false;
    routingCompleted = false;
    if ("requested".equals(navigationMapStatus)) {
      navigationMapStatus = "error";
      navigationMapMessage = "내비게이션 연결이 끊겼습니다. 다시 연결한 뒤 지도를 열어 주세요";
    }
    if ("requesting".equals(routeStatus)
        || "unconfirmed".equals(routeStatus)
        || "active".equals(routeStatus)) {
      routeStatus = "unconfirmed";
      routeMessage = "내비 연결이 끊겨 안내 상태를 확인하지 못했어요. 순정 내비에서 확인해 주세요.";
    }
    ++requestNumber;
    releaseNavi();
    emit();
  }

  private void releaseNavi() {
    ++naviGeneration;
    main.removeCallbacks(poll);
    NaviHelper previous = helper;
    NaviHelperEventListener previousEvents = events;
    helper = null;
    events = null;
    if (previous != null) {
      try {
        if (previousEvents != null) previous.removeListener(previousEvents);
      } catch (RuntimeException | LinkageError ignored) {
      }
      try {
        previous.release();
      } catch (RuntimeException | LinkageError ignored) {
      }
    }
  }

  private void emit() {
    String log =
        "connected="
            + connected
            + " policyKnown="
            + policyKnown
            + " restricted="
            + drivingRestricted
            + " destination="
            + destinationAvailable
            + " nativeDestination="
            + nativeDestinationAvailable
            + " targetMatched="
            + targetObserved
            + " nativeGuidance="
            + nativeGuidanceConfirmed
            + " route="
            + routeStatus;
    if (!log.equals(lastLog)) {
      Log.i(TAG, log);
      lastLog = log;
    }
    listener.onState(new State(this));
  }

  private static String clean(String s) {
    return s == null ? "" : s.trim();
  }

  private static boolean validPoint(double lat, double lon) {
    return Double.isFinite(lat)
        && Double.isFinite(lon)
        && lat >= -90
        && lat <= 90
        && lon >= -180
        && lon <= 180
        && (lat != 0 || lon != 0);
  }

  private static boolean samePoint(double lat1, double lon1, double lat2, double lon2) {
    return validPoint(lat1, lon1)
        && validPoint(lat2, lon2)
        && Math.abs(lat1 - lat2) < 0.0001
        && Math.abs(lon1 - lon2) < 0.0001;
  }

  private final class Events implements NaviHelperEventListener {
    private final int generation;

    Events(int generation) {
      this.generation = generation;
    }

    @Override
    public void onDestinationInfo(DestinationInfo info) {
      postNavi(
          generation,
          () -> {
            received();
            recordNativeDestination(info);
            if (info == null || !validPoint(info.getLatitude(), info.getLongitude())) {
              targetObserved = false;
              nativeGuidanceConfirmed = false;
              clearDestination();
              emit();
              return;
            }
            if (ownTarget
                && samePoint(
                    info.getLatitude(), info.getLongitude(), targetLatitude, targetLongitude)) {
              boolean firstTargetObservation = !targetObserved;
              targetObserved = true;
              if (firstTargetObservation) {
                // Do not reuse an old route's guiding snapshot. Query again after observing our
                // requested coordinates; polling is also available if this query fails.
                nativeGuidanceConfirmed = false;
                targetRouteStateBaseline = routeStateResponseVersion;
                try {
                  helper.getRouteStateInfo();
                } catch (RuntimeException | LinkageError error) {
                  Log.w(TAG, "Could not refresh route state after target match", error);
                }
              }
              destinationAvailable = originalAvailable;
              destinationName = originalName;
              latitude = originalLatitude;
              longitude = originalLongitude;
              confirmRouteIfReady();
              emit();
              return;
            }
            boolean previouslyObservedTarget = targetObserved;
            targetObserved = false;
            nativeGuidanceConfirmed = false;
            if (ownTarget
                && ("requesting".equals(routeStatus)
                    || "unconfirmed".equals(routeStatus)
                    || "error".equals(routeStatus)
                    || !previouslyObservedTarget)
                && samePoint(
                    info.getLatitude(), info.getLongitude(), originalLatitude, originalLongitude)) {
              destinationAvailable = originalAvailable;
              destinationName = originalName;
              latitude = originalLatitude;
              longitude = originalLongitude;
              // A pre-request query response or the unchanged destination after
              // a failed search must not erase either the source or its error.
              emit();
              return;
            }
            if (ownTarget
                && !"requesting".equals(routeStatus)
                && !"unconfirmed".equals(routeStatus)) {
              ownTarget = false;
              routeStatus = "idle";
              routeMessage = "";
              ++requestNumber;
            }
            destinationAvailable = true;
            destinationName = clean(info.getName());
            if (destinationName.isEmpty()) destinationName = "내비게이션 목적지";
            latitude = info.getLatitude();
            longitude = info.getLongitude();
            emit();
          });
    }

    @Override
    public void onDestinationChanged() {
      postNavi(
          generation,
          () -> {
            // Do not clear the source destination here: this event also fires
            // when our own pickup request changes the native destination.
            received();
            refreshDestination();
          });
    }

    @Override
    public void onRouteStarted(RouteStartInfo info) {
      postNavi(
          generation,
          () -> {
            received();
            if ("requesting".equals(routeStatus)) routeMessage = "중앙 내비게이션이 경로를 탐색하고 있습니다";
            refreshDestination();
            emit();
          });
    }

    @Override
    public void onRoutingCompleted() {
      postNavi(
          generation,
          () -> {
            received();
            if ("requesting".equals(routeStatus) || "unconfirmed".equals(routeStatus)) {
              routingCompleted = true;
              confirmRouteIfReady();
            }
            refreshDestination();
            emit();
          });
    }

    // Legacy onRouteEnded is not treated as success. A fresh native ROUTE_GUIDING state after
    // observing our target is independently useful when a navigation implementation omits
    // onRoutingCompleted. SIMULATION_GUIDING and method submission are never accepted as proof.
    @Override
    public void onRouteStateInfo(RouteStateInfo info) {
      postNavi(
          generation,
          () -> {
            if (info == null) return;
            received();
            ++routeStateResponseVersion;
            if (ownTarget
                && targetObserved
                && routeStateResponseVersion > targetRouteStateBaseline) {
              boolean guiding = info.getNaviMode() == NaviMode.ROUTE_GUIDING;
              if (guiding != nativeGuidanceConfirmed)
                Log.i(
                    TAG,
                    "Native route state after target: mode="
                        + info.getNaviMode()
                        + " isRouting="
                        + info.isRouting());
              nativeGuidanceConfirmed = guiding;
              confirmRouteIfReady();
            }
            connectionMessage = "Pleos 내비게이션 연결됨";
            emit();
          });
    }

    @Override
    public void onCurrentLocationInfo(CurrentLocationInfo info) {
      postNavi(
          generation,
          () -> {
            received();
            if (info == null || !validPoint(info.getLatitude(), info.getLongitude()))
              clearLocation();
            else {
              hasCurrentLocation = true;
              currentLatitude = info.getLatitude();
              currentLongitude = info.getLongitude();
              locationReceivedAt = SystemClock.elapsedRealtime();
            }
            emit();
          });
    }

    @Override
    public void onRouteCancelled() {
      postNavi(
          generation,
          () -> {
            received();
            if (ownTarget
                && ("requesting".equals(routeStatus) || "unconfirmed".equals(routeStatus))) {
              // Replacing an existing native route cancels that OLD route first. The event has no
              // target/request ID, so it cannot cancel our new pending request. Wait for a fresh
              // matching destination and guidance state, or retain the unconfirmed timeout result.
              targetObserved = false;
              nativeGuidanceConfirmed = false;
              routingCompleted = false;
              clearDestination();
              clearNativeDestination();
              Log.i(TAG, "Native cancellation during pending replacement; retaining new target");
              if ("requesting".equals(routeStatus)) routeMessage = "새 목적지로 내비 안내를 준비하고 있어요";
              refreshDestination();
              emit();
              return;
            }
            ++requestNumber;
            ownTarget = false;
            routeStatus = "idle";
            routeMessage = "내비게이션 안내가 취소되었습니다";
            clearDestination();
            clearNativeDestination();
            refreshDestination();
            emit();
          });
    }

    @Override
    public void onDestinationArrived(DestinationArrivedInfo info) {
      postNavi(
          generation,
          () -> {
            received();
            if (ownTarget && "active".equals(routeStatus)) {
              ++requestNumber;
              routeStatus = "arrived";
              routeMessage = "승하차 지점에 도착했습니다";
            }
            emit();
          });
    }

    @Override
    public void onNavigationStatus(NavigationStatus status) {
      postNavi(
          generation,
          () -> {
            received();
            if (status == NavigationStatus.INACTIVE) {
              connectionMessage = "중앙 내비게이션을 실행해 주세요";
              clearDestination();
              clearNativeDestination();
              clearLocation();
              if ("requesting".equals(routeStatus)) routeError("내비게이션이 비활성 상태입니다");
            } else if (status == NavigationStatus.ROUTE_DRIVE) refreshDestination();
            emit();
          });
    }

    @Override
    public void onError(NaviErrorCode error) {
      postNavi(
          generation,
          () -> {
            received();
            String message =
                error == NaviErrorCode.CAUSE_SEARCH_ROUTE_TOO_SHORT_DISTANCE
                    ? "승하차 지점이 너무 가까워 경로를 탐색할 수 없습니다"
                    : error == NaviErrorCode.CAUSE_SEARCH_ROUTE_OVER_DISTANCE
                        ? "경로 탐색 가능한 거리를 벗어났습니다"
                        : "내비게이션 경로 탐색에 실패했습니다";
            Log.w(TAG, "Native route error: " + String.valueOf(error));
            routeError(message);
          });
    }
  }

  @Override
  public void close() {
    if (Looper.myLooper() != Looper.getMainLooper()) {
      main.post(this::close);
      return;
    }
    if (!started && helper == null && car == null) return;
    started = false;
    ++lifecycleGeneration;
    ++requestNumber;
    main.removeCallbacksAndMessages(null);
    releaseNavi();
    try {
      if (ux != null) ux.unregisterListener();
    } catch (RuntimeException | LinkageError ignored) {
    }
    try {
      if (car != null) car.disconnect();
    } catch (RuntimeException | LinkageError ignored) {
    }
    ux = null;
    car = null;
    unknownPolicy();
    connected = false;
    ownTarget = false;
    clearDestination();
    clearNativeDestination();
    clearLocation();
    routeStatus = "idle";
    routeMessage = "";
    navigationMapStatus = "idle";
    navigationMapMessage = "";
    connectionMessage = "Pleos 연결 종료";
    emit();
  }
}
