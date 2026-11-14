package ai.illooso.app;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.DatePickerDialog;
import android.app.TimePickerDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.RippleDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import com.google.zxing.BarcodeFormat;
import com.google.zxing.MultiFormatWriter;
import com.google.zxing.common.BitMatrix;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import org.json.JSONObject;

/** Native, resizable Pleos app. Canvas map and Android views; no browser or WebView shell. */
public final class MainActivity extends Activity {
  private static final int BLUE = 0xff2563eb,
      TEAL = 0xff087f79,
      INK = 0xff172334,
      MUTED = 0xff596777,
      BG = 0xfff5f7fb,
      LINE = 0xffe0e6ee,
      MINT = 0xffeaf2ff;
  private static final String COMPANION =
      "https://illooso-pyeonggeo-prototype.yangseungsang.chatgpt.site/";
  private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
  private static final String[] PURPOSE_VALUES = {"instant", "wait", "parking"};
  private static final String[] PURPOSE_ICONS = {"car", "clock", "parking"};
  private final Handler handler = new Handler(Looper.getMainLooper());
  private final List<TextView> purposeButtons = new ArrayList<>();
  private PleosBridge bridge;
  private PleosBridge.State state;
  private LocalStore store;
  private LinearLayout root, body, candidateList, summary, sheet, mapControls;
  private FrameLayout scene;
  private PickupMapView map;
  private String panel = "none", listTab = "nearby";
  private String mode = "instant", query = "", route = "idle";
  private Place selected;
  private Destination destination = DestinationRepository.DEFAULT;
  private String destinationSource = "기본 목적지", destinationQuery = "";
  private LinearLayout destinationResults;
  private TextView importDestinationButton, importDestinationStatus;
  private TextView decisionSaveButton;
  private boolean importingDestination, openNativeWhenReady, restoredSelectionNeedsCheck;
  private long importBaseline;
  private int importGeneration;
  private boolean customVisitTime = false, allReviews = false;
  private boolean decisionExpanded = false;
  private ReviewInsights.Sort allReviewSort = ReviewInsights.Sort.LATEST;
  private List<Review> sampleReviews;
  private int hour = 14, maxWalk = 500;
  private int previewRequestId;
  private boolean previewRoute = true, simulatedDriving = false, started = false;
  private final List<AlertDialog> dialogs = new ArrayList<>();
  private String lastUiState = "";
  private final String[] REPORT_LABELS = {
    "승하차가 편리했어요", "차량 통행량이 많았어요", "정차 금지 표지가 있었어요", "실제 도로 상태가 달랐어요"
  };
  private final String[] REPORT_TYPES = {"easy", "busy", "restricted", "different"};

  @Override
  public void onCreate(Bundle saved) {
    super.onCreate(saved);
    store = new LocalStore(this);
    sampleReviews = ReviewRepository.samples(Instant.now());
    restoreDestination();
    hour = ZonedDateTime.now(SEOUL).getHour();
    if (saved != null) {
      previewRoute = saved.getBoolean("routeSimulation", true);
      mode = saved.getString("mode", "instant");
      hour = saved.getInt("hour", hour);
      customVisitTime = saved.getBoolean("customVisitTime", false);
      maxWalk = saved.getInt("maxWalk", 500);
      selected = PlaceRepository.find(saved.getString("selected", ""));
      restoredSelectionNeedsCheck = true;
      decisionExpanded = saved.getBoolean("decisionExpanded", false);
    }
    bridge =
        new PleosBridge(
            this,
            s -> {
              boolean priorBlocked = isBlocked();
              String signature =
                  s.connected
                      + "|"
                      + s.policyKnown
                      + "|"
                      + s.drivingRestricted
                      + "|"
                      + s.destinationName
                      + "|"
                      + s.destinationAvailable
                      + "|"
                      + s.routeStatus
                      + "|"
                      + s.connectionMessage
                      + "|"
                      + s.latitude
                      + "|"
                      + s.longitude
                      + "|"
                      + s.routeMessage
                      + "|"
                      + s.navigationMapStatus
                      + "|"
                      + s.navigationMapMessage;
              boolean changed = !signature.equals(lastUiState);
              lastUiState = signature;
              String previousMapError = state == null ? "" : state.navigationMapMessage;
              state = s;
              if (started
                  && s.navigationMapStatus.equals("error")
                  && !s.navigationMapMessage.equals(previousMapError))
                toast(s.navigationMapMessage);
              if (!previewRoute) {
                boolean wasOngoing = SelectionPolicy.isOngoing(route);
                route = s.routeStatus;
                if (wasOngoing && route.equals("idle")) {
                  openNativeWhenReady = false;
                  if (!decisionExpanded) selected = null;
                }
                if (openNativeWhenReady && route.equals("active")) {
                  openNativeWhenReady = false;
                  handler.post(
                      () -> {
                        if (started && !previewRoute && route.equals("active") && allow())
                          openNavigationMap();
                      });
                } else if (route.equals("error") || route.equals("unconfirmed"))
                  openNativeWhenReady = false;
              }
              if (handleImportedDestination(s)) return;
              boolean changedBlocked = priorBlocked != isBlocked();
              if (isBlocked()) {
                for (AlertDialog dialog : dialogs) if (dialog.isShowing()) dialog.dismiss();
                dialogs.clear();
                cancelDestinationImport();
                panel = "none";
                hideKeyboard();
              }
              if (started
                  && (changedBlocked
                      || (changed && (!previewRoute || state.navigationMapStatus.equals("error")))))
                render();
            });
    render();
    requestSdkPermissions();
  }

  @Override
  protected void onStart() {
    super.onStart();
    started = true;
    bridge.start();
    render();
  }

  @Override
  protected void onStop() {
    started = false;
    handler.removeCallbacksAndMessages(null);
    cancelDestinationImport();
    if (previewRoute && route.equals("requesting")) route = "idle";
    super.onStop();
  }

  @Override
  protected void onDestroy() {
    if (map != null) map.close();
    bridge.close();
    handler.removeCallbacksAndMessages(null);
    for (AlertDialog d : dialogs) if (d.isShowing()) d.dismiss();
    dialogs.clear();
    super.onDestroy();
  }

  @Override
  public void onConfigurationChanged(Configuration config) {
    super.onConfigurationChanged(config);
    render();
  }

  @Override
  protected void onSaveInstanceState(Bundle out) {
    out.putBoolean("routeSimulation", previewRoute);
    out.putString("mode", mode);
    out.putInt("hour", hour);
    out.putBoolean("customVisitTime", customVisitTime);
    out.putInt("maxWalk", maxWalk);
    out.putString("selected", selected == null ? "" : selected.id);
    out.putBoolean("decisionExpanded", decisionExpanded);
    super.onSaveInstanceState(out);
  }

  private void requestSdkPermissions() {
    List<String> missing = new ArrayList<>();
    for (String p :
        new String[] {
          PleosBridge.READ_PERMISSION, PleosBridge.ROUTE_PERMISSION, PleosBridge.MAP_PERMISSION
        }) if (checkSelfPermission(p) != PackageManager.PERMISSION_GRANTED) missing.add(p);
    if (!missing.isEmpty()) requestPermissions(missing.toArray(new String[0]), 44);
  }

  @Override
  public void onRequestPermissionsResult(int request, String[] permissions, int[] grants) {
    super.onRequestPermissionsResult(request, permissions, grants);
    if (request == 44 && started) {
      bridge.close();
      bridge.start();
    }
    if (request == 45 && started && allow()) bridge.openNavigationMap();
  }

  private void openNavigationMap() {
    if (!allow()) return;
    if (checkSelfPermission(PleosBridge.MAP_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {PleosBridge.MAP_PERMISSION}, 45);
      return;
    }
    if (state == null || !state.connected) {
      bridge.start();
      toast("내비게이션에 다시 연결하고 있어요. 연결 후 지도를 열어 주세요.");
      return;
    }
    bridge.openNavigationMap();
  }

  private boolean actualRestricted() {
    return state != null && state.policyKnown && state.drivingRestricted;
  }

  private boolean isBlocked() {
    return actualRestricted()
        || simulatedDriving
        || state == null
        || !state.policyKnown
        || state.drivingRestricted;
  }

  private boolean allow() {
    if (isBlocked()) {
      toast("주차 상태가 확인되면 이용할 수 있어요.");
      return false;
    }
    return true;
  }

  private boolean compact() {
    return getResources().getConfiguration().screenHeightDp < 700;
  }

  private List<Place> nearbyPlaces() {
    return customVisitTime
        ? DestinationRepository.recommend(destination, mode, hour, maxWalk, query)
        : DestinationRepository.recommendAtArrival(
            destination, mode, ZonedDateTime.now(SEOUL), maxWalk, query);
  }

  private String distanceLabel(Place place) {
    int meters = DestinationRepository.distanceMeters(destination, place);
    return meters == Integer.MAX_VALUE ? "거리 확인 중" : "직선 " + meters + "m";
  }

  private void restoreDestination() {
    android.content.SharedPreferences preferences =
        getSharedPreferences("destination", MODE_PRIVATE);
    String id = preferences.getString("id", "d1");
    Destination saved = DestinationRepository.find(id);
    if (saved == null && id.startsWith("navi:")) {
      try {
        saved =
            DestinationRepository.fromNavigation(
                preferences.getString("name", ""),
                Double.parseDouble(preferences.getString("latitude", "NaN")),
                Double.parseDouble(preferences.getString("longitude", "NaN")));
      } catch (NumberFormatException ignored) {
      }
    }
    if (saved != null) {
      destination = saved;
      destinationSource = preferences.getString("source", "직접 선택");
    }
  }

  private boolean canChangeDestination() {
    if (route.equals("requesting")) {
      toast("내비 요청이 끝난 뒤 장소를 바꿀 수 있어요");
      return false;
    }
    return true;
  }

  private void openDestinations() {
    if (!allow() || !canChangeDestination()) return;
    destinationQuery = "";
    showPanel("destinations");
  }

  private void applyDestination(Destination next, String source) {
    if (next == null || !allow() || !canChangeDestination()) return;
    closePanel();
    destination = next;
    destinationSource = source;
    getSharedPreferences("destination", MODE_PRIVATE)
        .edit()
        .putString("id", next.id)
        .putString("name", next.name)
        .putString("latitude", Double.toString(next.latitude))
        .putString("longitude", Double.toString(next.longitude))
        .putString("source", source)
        .apply();
    ++previewRequestId;
    openNativeWhenReady = false;
    previewRoute = true;
    route = "idle";
    selected = null;
    query = "";
    listTab = "nearby";
    allReviews = false;
    decisionExpanded = false;
    if (map != null) {
      map.setPlaces(List.of());
      map.setDestination(next.name, next.latitude, next.longitude);
      map.recenter();
    }
    render();
    if (map != null) map.recenter();
  }

  private void buildDestinationPanel() {
    map.setMapObscured(true);
    if (summary != null) summary.setVisibility(View.GONE);
    sheet = card();
    sheet.setElevation(dp(16));
    sheet.setClickable(true);
    sheet.setAccessibilityPaneTitle("목적지 찾기");
    LinearLayout heading = row();
    addWeight(heading, text("어디로 갈까요?", 30, INK, true));
    heading.addView(
        iconButton("close", "목적지 찾기 닫기", v -> closePanel()),
        new LinearLayout.LayoutParams(dp(64), dp(64)));
    add(sheet, heading, 0, 0, 0, 14);
    EditText search = input("장소명 또는 주소");
    search.setSingleLine(true);
    search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
    search.setText(destinationQuery);
    search.setContentDescription("평거동 목적지 이름이나 주소 검색");
    search.setOnEditorActionListener(
        (v, action, event) -> {
          hideKeyboard();
          return true;
        });
    withIcon(search, "search", MUTED, 24);
    add(sheet, search, 0, 0, 0, 12);
    importDestinationButton =
        actionButton("순정 내비 목적지 가져오기", "navigation", false, v -> importNavigationDestination());
    importDestinationButton.setTextSize(24);
    add(sheet, importDestinationButton, 0, 0, 0, 12);
    importDestinationStatus =
        text(DestinationRepository.SEARCH_SCOPE + " · 지도에 등록된 장소를 검색해요", 20, MUTED, false);
    importDestinationStatus.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
    add(sheet, importDestinationStatus, 0, 0, 0, 14);
    if (importingDestination) {
      importDestinationButton.setEnabled(false);
      importDestinationButton.setText("목적지를 가져오는 중…");
      importDestinationStatus.setText("순정 내비게이션의 목적지를 확인하고 있어요");
    }
    ScrollView sc = new ScrollView(this);
    sc.setVerticalScrollBarEnabled(true);
    sc.setContentDescription("목적지 검색 결과, 이 영역만 스크롤");
    destinationResults = column();
    sc.addView(destinationResults);
    sheet.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
    search.addTextChangedListener(
        new TextWatcher() {
          public void beforeTextChanged(CharSequence value, int start, int count, int after) {}

          public void onTextChanged(CharSequence value, int start, int before, int count) {
            if (isBlocked()) return;
            destinationQuery = value.toString();
            renderDestinationResults();
          }

          public void afterTextChanged(Editable value) {}
        });
    renderDestinationResults();
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(700), Gravity.BOTTOM);
    params.setMargins(dp(24), 0, dp(24), dp(24));
    scene.addView(sheet, params);
    layoutOverlays();
  }

  private void renderDestinationResults() {
    if (destinationResults == null) return;
    destinationResults.removeAllViews();
    List<Destination> matches = DestinationRepository.search(destinationQuery);
    if (matches.isEmpty()) {
      add(destinationResults, text("일치하는 목적지가 없어요", 26, INK, true), 0, 24, 0, 12);
      add(
          destinationResults,
          text("현재 검색은 평거동 주변 등록 장소를 제공합니다. 장소명을 바꾸거나 순정 내비 목적지를 가져와 보세요.", 22, MUTED, false),
          0,
          0,
          0,
          0);
      return;
    }
    for (Destination match : matches) {
      LinearLayout item = column();
      item.setPadding(dp(8), dp(20), dp(8), dp(20));
      item.setBackground(
          new RippleDrawable(
              ColorStateList.valueOf(0x182563eb), shape(Color.WHITE, 14, Color.TRANSPARENT), null));
      add(item, text(match.name, 26, INK, true), 0, 0, 0, 8);
      add(item, text(match.address, 20, MUTED, false), 0, 0, 0, 0);
      item.setFocusable(true);
      item.setContentDescription(match.name + ", " + match.address + ", 주변 정차지 찾기");
      item.setOnClickListener(v -> applyDestination(match, "직접 선택"));
      destinationResults.addView(item);
      View divider = new View(this);
      divider.setBackgroundColor(LINE);
      add(destinationResults, divider, 0, 0, 0, 0, 1);
    }
  }

  private void cancelDestinationImport() {
    importingDestination = false;
    ++importGeneration;
    if (importDestinationButton != null) {
      importDestinationButton.setEnabled(true);
      importDestinationButton.setText("순정 내비 목적지 가져오기");
    }
  }

  private void importNavigationDestination() {
    if (!allow() || importingDestination || !canChangeDestination()) return;
    if (checkSelfPermission(PleosBridge.READ_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {PleosBridge.READ_PERMISSION}, 46);
      importDestinationStatus.setText("내비 조회 권한을 허용한 뒤 다시 눌러 주세요");
      return;
    }
    importingDestination = true;
    final int generation = ++importGeneration;
    importBaseline = state == null ? 0 : state.destinationResponseVersion;
    importDestinationButton.setEnabled(false);
    importDestinationButton.setText("목적지를 가져오는 중…");
    importDestinationStatus.setText("순정 내비게이션의 목적지를 확인하고 있어요");
    bridge.start();
    bridge.refreshDestination();
    handler.postDelayed(
        () -> {
          if (!started || !importingDestination || generation != importGeneration) return;
          cancelDestinationImport();
          if (panel.equals("destinations") && importDestinationStatus != null)
            importDestinationStatus.setText("내비 응답이 없어요. 연결을 확인한 뒤 다시 가져와 주세요");
        },
        10000);
  }

  private boolean handleImportedDestination(PleosBridge.State latest) {
    if (!started || !importingDestination || latest.destinationResponseVersion <= importBaseline)
      return false;
    cancelDestinationImport();
    if (!panel.equals("destinations") || isBlocked()) return false;
    if (!latest.nativeDestinationAvailable) {
      if (importDestinationStatus != null)
        importDestinationStatus.setText("순정 내비에 설정된 목적지가 없어요. 아래에서 직접 선택할 수 있어요");
      return false;
    }
    Destination imported =
        DestinationRepository.fromNavigation(
            latest.nativeDestinationName,
            latest.nativeDestinationLatitude,
            latest.nativeDestinationLongitude);
    if (imported != null) {
      applyDestination(imported, "순정 내비에서 가져옴");
      return true;
    }
    if (importDestinationStatus != null)
      importDestinationStatus.setText("목적지 좌표를 확인하지 못했어요. 다시 가져와 주세요");
    return false;
  }

  private void showDestinationShare() {
    String message =
        "목적지: "
            + destination.name
            + "\n만날 곳: "
            + selected.name
            + "\n목적지까지 "
            + distanceLabel(selected)
            + "\n위치: "
            + selected.latitude
            + ", "
            + selected.longitude
            + "\nhttps://www.openstreetmap.org/?mlat="
            + selected.latitude
            + "&mlon="
            + selected.longitude
            + "#map=18/"
            + selected.latitude
            + "/"
            + selected.longitude
            + "\n예시 정차지입니다. 현장 표지와 도로 상황을 확인해 주세요.";
    showDialog(
        new AlertDialog.Builder(this)
            .setTitle("만날 곳 공유")
            .setMessage(message)
            .setNegativeButton("닫기", null)
            .setPositiveButton(
                "정보 복사",
                (dialog, which) -> {
                  if (!allow()) return;
                  ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                      .setPrimaryClip(ClipData.newPlainText("만날 곳", message));
                  toast("목적지와 만날 곳을 복사했어요");
                })
            .create());
  }

  /** The map is retained across UI updates. Only the optional sheet owns a ScrollView. */
  private void render() {
    purposeButtons.clear();
    if (map != null && map.getParent() instanceof ViewGroup)
      ((ViewGroup) map.getParent()).removeView(map);
    candidateList = null;
    sheet = null;
    summary = null;
    root = column();
    root.setBackgroundColor(BG);
    setContentView(root);
    LinearLayout header = row();
    header.setPadding(dp(24), dp(12), dp(24), dp(12));
    TextView brand = text("일로오소", 28, INK, true);
    withIcon(brand, "pin", BLUE, 28);
    addWeight(header, brand);
    TextView nativeMap = actionButton("순정 내비", "navigation", false, v -> openNavigationMap());
    nativeMap.setTextSize(22);
    nativeMap.setMinHeight(dp(64));
    nativeMap.setPadding(dp(16), dp(12), dp(16), dp(12));
    nativeMap.setContentDescription("Pleos 중앙 순정 내비게이션 열기");
    header.addView(nativeMap);
    TextView menu = iconButton("menu", "메뉴: 저장한 곳, 내 기록, 앱 정보", v -> showPanel("menu"));
    LinearLayout.LayoutParams menuParams = new LinearLayout.LayoutParams(dp(64), dp(64));
    menuParams.leftMargin = dp(12);
    header.addView(menu, menuParams);
    root.addView(header);
    nativeMap.setEnabled(!isBlocked());
    menu.setEnabled(!isBlocked());
    if (state == null || !state.policyKnown) {
      panel = "none";
      renderBlocked();
      return;
    }
    if (restoredSelectionNeedsCheck) {
      selected = SelectionPolicy.reconcile(selected, nearbyPlaces(), route);
      restoredSelectionNeedsCheck = false;
    }
    scene = new FrameLayout(this);
    root.addView(scene, new LinearLayout.LayoutParams(-1, 0, 1));
    if (map == null) {
      map = new PickupMapView(this);
      map.setListener(this::openPlace);
    }
    map.setDestination(destination.name, destination.latitude, destination.longitude);
    scene.addView(map, new FrameLayout.LayoutParams(-1, -1));
    map.setMapObscured(false);
    updateMap();
    TextView attribution = text("© OpenStreetMap contributors", 16, MUTED, false);
    attribution.setGravity(Gravity.END);
    attribution.setPadding(dp(20), dp(2), dp(24), dp(4));
    attribution.setContentDescription("지도 출처: OpenStreetMap 기여자, Open Database License");
    root.addView(attribution);
    LinearLayout tools = isBlocked() ? row() : column();
    if (isBlocked()) {
      TextView locked = text("주행 중 · 조작은 주차 후", 26, INK, true);
      locked.setPadding(dp(20), dp(18), dp(20), dp(18));
      locked.setBackground(shape(Color.WHITE, 20, LINE));
      addWeight(tools, locked);
      if (simulatedDriving && !actualRestricted()) {
        TextView end =
            button(
                "확인 종료",
                false,
                v -> {
                  simulatedDriving = false;
                  render();
                });
        end.setTextSize(22);
        tools.addView(end);
      }
    } else {
      LinearLayout destinationTools = row();
      TextView destinationButton =
          actionButton(destination.name, "search", false, v -> openDestinations());
      destinationButton.setTextSize(23);
      destinationButton.setMinHeight(dp(64));
      destinationButton.setPadding(dp(20), dp(12), dp(20), dp(12));
      destinationButton.setGravity(Gravity.CENTER_VERTICAL | Gravity.START);
      destinationButton.setSingleLine(true);
      destinationButton.setEllipsize(android.text.TextUtils.TruncateAt.END);
      destinationButton.setContentDescription(
          "목적지 변경, 현재 " + destination.name + ", " + destinationSource);
      destinationButton.setEnabled(!route.equals("requesting"));
      destinationTools.addView(destinationButton, new LinearLayout.LayoutParams(0, dp(64), 1));
      TextView filters = iconButton("filter", "방문 시간과 목적지 직선 거리 필터", v -> showFilters());
      filters.setBackground(reviewRipple(Color.WHITE, Color.WHITE, 18, LINE));
      LinearLayout.LayoutParams filterParams = new LinearLayout.LayoutParams(dp(64), dp(64));
      filterParams.leftMargin = dp(12);
      destinationTools.addView(filters, filterParams);
      tools.addView(destinationTools, new LinearLayout.LayoutParams(-1, dp(64)));
      LinearLayout purposes = row();
      String[] labels = {"바로 태우기", "기다리기", "주차장"};
      for (int i = 0; i < PURPOSE_VALUES.length; i++) {
        String value = PURPOSE_VALUES[i];
        TextView purpose = actionButton(labels[i], PURPOSE_ICONS[i], false, v -> changeMode(value));
        purpose.setTextSize(23);
        purpose.setMinHeight(dp(64));
        purpose.setPadding(dp(16), dp(12), dp(16), dp(12));
        purpose.setContentDescription(labels[i] + ", 지도에서 장소 찾기");
        LinearLayout.LayoutParams purposeParams = new LinearLayout.LayoutParams(0, dp(64), 1);
        if (i > 0) purposeParams.leftMargin = dp(12);
        purposes.addView(purpose, purposeParams);
        purposeButtons.add(purpose);
      }
      updatePurposeButtons();
      add(tools, purposes, 0, 12, 0, 0);
    }
    FrameLayout.LayoutParams toolsParams = new FrameLayout.LayoutParams(-1, -2, Gravity.TOP);
    toolsParams.setMargins(dp(24), dp(12), dp(24), 0);
    scene.addView(tools, toolsParams);
    mapControls = column();
    mapControls.setElevation(dp(8));
    mapControls.setBackground(shape(Color.WHITE, 18, LINE));
    TextView colors =
        button(
            "도로",
            false,
            v -> {
              if (!allow()) return;
              map.setRoadColors(!map.hasRoadColors());
              ((TextView) v).setTextColor(map.hasRoadColors() ? BLUE : MUTED);
              v.setSelected(map.hasRoadColors());
              v.setContentDescription(map.hasRoadColors() ? "도로별 예시 색상 끄기" : "도로별 예시 색상 켜기");
            });
    colors.setTextSize(20);
    colors.setPadding(dp(8), dp(12), dp(8), dp(12));
    colors.setMinHeight(dp(64));
    colors.setSelected(map.hasRoadColors());
    colors.setTextColor(map.hasRoadColors() ? BLUE : MUTED);
    colors.setContentDescription(map.hasRoadColors() ? "도로별 예시 색상 끄기" : "도로별 예시 색상 켜기");
    mapControls.addView(colors, new LinearLayout.LayoutParams(dp(64), dp(64)));
    mapControls.addView(
        iconButton(
            "plus",
            "지도 확대",
            v -> {
              if (allow()) map.zoomBy(1);
            }),
        new LinearLayout.LayoutParams(dp(64), dp(64)));
    mapControls.addView(
        iconButton(
            "minus",
            "지도 축소",
            v -> {
              if (allow()) map.zoomBy(-1);
            }),
        new LinearLayout.LayoutParams(dp(64), dp(64)));
    mapControls.addView(
        iconButton(
            "locate",
            "현재 목적지와 정차지 전체 보기",
            v -> {
              if (allow()) map.recenter();
            }),
        new LinearLayout.LayoutParams(dp(64), dp(64)));
    FrameLayout.LayoutParams mapToolsParams =
        new FrameLayout.LayoutParams(dp(64), -2, Gravity.TOP | Gravity.END);
    mapToolsParams.setMargins(0, dp(mapTopInsetDp() + 104), dp(24), 0);
    scene.addView(mapControls, mapToolsParams);
    renderSummary();
    if (isBlocked()) panel = "none";
    else {
      if (panel.equals("none") && decisionExpanded && selected != null && route.equals("idle"))
        panel = "detail";
      if (!panel.equals("none")) buildPanel();
    }
    scene.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layoutOverlays());
    scene.post(this::layoutOverlays);
  }

  private String modeLabel() {
    return mode.equals("parking") ? "주차장" : mode.equals("wait") ? "기다리기" : "바로 태우기";
  }

  private void updatePurposeButtons() {
    for (int i = 0; i < purposeButtons.size(); i++) {
      TextView purpose = purposeButtons.get(i);
      boolean active = mode.equals(PURPOSE_VALUES[i]);
      int color = active ? Color.WHITE : BLUE;
      purpose.setSelected(active);
      purpose.setStateDescription(active ? "선택됨" : "선택 안 됨");
      purpose.setTextColor(color);
      withIcon(purpose, PURPOSE_ICONS[i], color, 26);
      purpose.setBackground(
          new RippleDrawable(
              ColorStateList.valueOf(active ? 0x33ffffff : 0x182563eb),
              shape(active ? BLUE : Color.WHITE, 18, active ? BLUE : LINE),
              null));
      purpose.setEnabled(!isBlocked() && !route.equals("requesting"));
    }
  }

  private int mapTopInsetDp() {
    return isBlocked() ? 96 : 164;
  }

  private void updateMap() {
    if (map == null) return;
    List<Place> eligible = nearbyPlaces();
    List<Place> places = new ArrayList<>(eligible);
    if (selected != null
        && (SelectionPolicy.isOngoing(route) || decisionExpanded)
        && places.stream().noneMatch(p -> p.id.equals(selected.id))) places.add(selected);
    map.setPlaces(places);
    map.setRecommendedPlace(eligible.isEmpty() ? null : eligible.get(0).id);
    map.setSelected(selected == null ? null : selected.id);
    map.setRouteActive(route.equals("active") || route.equals("arrived"));
    map.setEnabled(!isBlocked());
    map.setImportantForAccessibility(
        isBlocked()
            ? View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
            : View.IMPORTANT_FOR_ACCESSIBILITY_YES);
  }

  private void renderSummary() {
    if (summary != null) scene.removeView(summary);
    summary = null;
    if (selected == null && !nearbyPlaces().isEmpty()) {
      scene.post(this::layoutOverlays);
      return;
    }
    summary = card();
    summary.setPadding(dp(24), dp(20), dp(24), dp(20));
    summary.setElevation(dp(8));
    summary.setClickable(true);
    if (selected == null) {
      add(
          summary,
          text(
              DestinationRepository.supports(destination)
                  ? "주변에 맞는 정차지가 없어요"
                  : "아직 정차지 정보가 없는 지역이에요",
              30,
              INK,
              true),
          0,
          0,
          0,
          18);
      add(summary, text(destination.name, 24, MUTED, false), 0, 0, 0, 16);
      if (!isBlocked())
        summary.addView(
            button(
                DestinationRepository.supports(destination) ? "정차지 조건 바꾸기" : "다른 목적지 찾기",
                true,
                v -> {
                  if (DestinationRepository.supports(destination)) {
                    listTab = "nearby";
                    showPanel("places");
                  } else openDestinations();
                }));
    } else {
      LinearLayout statusRow = row();
      String status =
          route.equals("arrived")
              ? "도착했어요"
              : route.equals("requesting")
                  ? "안내 준비 중…"
                  : route.equals("active")
                      ? (previewRoute ? "안내 미리보기" : "순정 내비에서 안내 중")
                      : route.equals("unconfirmed")
                          ? "순정 내비에서 확인해 주세요"
                          : route.equals("error") ? "안내를 준비하지 못했어요" : "만날 곳";
      if (route.equals("idle") && !PlaceRepository.isOpenAt(selected, arrivalHourFor(selected)))
        status = "선택 시간에는 이용 불가";
      TextView statusText = text(status, 22, route.equals("error") ? 0xffa94535 : BLUE, true);
      statusText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);
      addWeight(statusRow, statusText);
      summary.addView(statusRow);
      if ((route.equals("error") || route.equals("unconfirmed")) && !previewRoute && state != null)
        add(summary, text(state.routeMessage, 22, 0xff925016, false), 0, 12, 0, 8);
      TextView name = text(selected.name, compact() ? 30 : 36, INK, true);
      name.setMaxLines(2);
      if (!isBlocked()) {
        name.setMinHeight(dp(64));
        name.setGravity(Gravity.CENTER_VERTICAL);
        name.setOnClickListener(v -> showPanel("detail"));
        name.setFocusable(true);
        name.setContentDescription(selected.name + ", 장소 정보와 후기 펼치기");
        UiIconDrawable expand = new UiIconDrawable("expand", BLUE);
        expand.setBounds(0, 0, dp(28), dp(28));
        name.setCompoundDrawablesRelative(null, null, expand, null);
        name.setCompoundDrawablePadding(dp(12));
      }
      add(summary, name, 0, 4, 0, compact() ? 12 : 16);
      LinearLayout metrics = row();
      TextView drive = text("차량 " + selected.driveMinutes + "분", compact() ? 28 : 34, BLUE, true);
      addWeight(metrics, drive);
      TextView walk = text(distanceLabel(selected), compact() ? 26 : 30, INK, true);
      metrics.addView(walk);
      add(summary, metrics, 0, 0, 0, isBlocked() ? 8 : 20);
      if (isBlocked()) {
        add(summary, text("길 안내는 순정 내비게이션을 확인하세요", 22, MUTED, false), 0, 10, 0, 0);
      } else {
        summary.addView(placeActions());
      }
    }
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, -2, Gravity.BOTTOM);
    params.setMargins(dp(24), 0, dp(24), dp(24));
    scene.addView(summary, params);
    summary.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> layoutOverlays());
    scene.post(this::layoutOverlays);
  }

  private LinearLayout placeActions() {
    LinearLayout actions = row();
    actions.addView(primaryAction(), new LinearLayout.LayoutParams(0, -2, 1));
    LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(dp(72), dp(72));
    shareParams.leftMargin = dp(12);
    actions.addView(iconButton("share", "동승자에게 공유", v -> showShare()), shareParams);
    return actions;
  }

  private LinearLayout decisionActions() {
    LinearLayout actions = row();
    boolean narrow = getResources().getConfiguration().screenWidthDp < 740;
    int side = narrow ? 64 : 76;
    final String placeId = selected.id;
    decisionSaveButton =
        iconButton(
            "bookmark",
            "장소 저장",
            v -> {
              if (!allow() || selected == null || !selected.id.equals(placeId)) return;
              boolean saved = store.toggle(placeId);
              updateDecisionSaveButton();
              toast(saved ? "장소를 저장했어요" : "저장을 취소했어요");
            });
    decisionSaveButton.setPadding(dp((side - 30) / 2f), dp(23), dp((side - 30) / 2f), dp(23));
    updateDecisionSaveButton();
    actions.addView(decisionSaveButton, new LinearLayout.LayoutParams(dp(side), dp(80)));
    TextView primary = primaryAction();
    primary.setMinHeight(dp(80));
    primary.setTextSize(narrow ? 24 : 28);
    primary.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    LinearLayout.LayoutParams primaryParams = new LinearLayout.LayoutParams(0, dp(80), 1);
    primaryParams.setMargins(dp(14), 0, dp(14), 0);
    actions.addView(primary, primaryParams);
    TextView share = iconButton("share", "동승자에게 공유", v -> showShare());
    share.setPadding(dp((side - 28) / 2f), dp(23), dp((side - 28) / 2f), dp(23));
    share.setBackground(reviewRipple(0xfff0f5fd, 0xffeaf0fa, 24, Color.TRANSPARENT));
    actions.addView(share, new LinearLayout.LayoutParams(dp(side), dp(80)));
    return actions;
  }

  private void updateDecisionSaveButton() {
    if (decisionSaveButton == null || selected == null) return;
    boolean saved = store.saved().contains(selected.id);
    withIcon(decisionSaveButton, saved ? "saved" : "bookmark", BLUE, 30);
    decisionSaveButton.setSelected(saved);
    decisionSaveButton.setStateDescription(saved ? "저장됨" : "저장 안 됨");
    decisionSaveButton.setContentDescription(saved ? "저장한 장소, 저장 취소" : "장소 저장");
    decisionSaveButton.setBackground(
        reviewRipple(
            saved ? 0xffdce8ff : 0xfff0f5fd,
            saved ? 0xffe7efff : 0xffeaf0fa,
            24,
            Color.TRANSPARENT));
  }

  private TextView primaryAction() {
    String label =
        route.equals("requesting")
            ? (previewRoute ? "안내 준비 중…" : "내비에 보내는 중…")
            : route.equals("active")
                ? (previewRoute ? "도착 확인" : "순정 내비 보기")
                : route.equals("arrived")
                    ? "이용 경험 남기기"
                    : route.equals("unconfirmed")
                        ? "순정 내비 확인"
                        : route.equals("error") ? "다시 보내기" : "이곳으로 내비 안내";
    boolean unavailable =
        route.equals("idle")
            && selected != null
            && !PlaceRepository.isOpenAt(selected, arrivalHourFor(selected));
    if (unavailable) label = "다른 장소 찾기";
    final boolean chooseAnother = unavailable;
    TextView primary =
        actionButton(
            label,
            route.equals("arrived") ? "message" : "navigation",
            true,
            v -> {
              if (!allow()) return;
              if (chooseAnother) {
                listTab = "nearby";
                showPanel("places");
                return;
              }
              closePanel();
              if (route.equals("active")) {
                if (previewRoute) {
                  route = "arrived";
                  updateMap();
                  renderSummary();
                } else openNavigationMap();
              } else if (route.equals("unconfirmed")) openNavigationMap();
              else if (route.equals("arrived")) showReport(null);
              else sendSelectedToNavigation();
            });
    primary.setEnabled(!route.equals("requesting"));
    primary.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x30ffffff),
            softSurface(0xff278dff, 0xff0756ef, 24, 0xff3085ff),
            null));
    primary.setAlpha(route.equals("requesting") ? .6f : 1f);
    return primary;
  }

  private int summaryBottomInset() {
    return summary == null ? 0 : summary.getHeight() + dp(40);
  }

  private void layoutOverlays() {
    if (scene == null || map == null || scene.getHeight() == 0) return;
    float density = getResources().getDisplayMetrics().density;
    int bottom = summaryBottomInset();
    map.setContentInsets(mapTopInsetDp(), bottom / density);
    if (mapControls != null)
      mapControls.setVisibility(
          panel.equals("detail") || panel.equals("destinations") || isBlocked()
              ? View.GONE
              : View.VISIBLE);
    if (sheet != null) {
      FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) sheet.getLayoutParams();
      if (panel.equals("detail") || panel.equals("destinations")) {
        int height = Math.max(dp(180), scene.getHeight() - dp(mapTopInsetDp() + 40));
        if (lp.height != height || lp.bottomMargin != dp(24)) {
          lp.height = height;
          lp.bottomMargin = dp(24);
          sheet.setLayoutParams(lp);
        }
        return;
      }
      int sheetBottom = Math.max(dp(24), bottom);
      int free = Math.max(dp(180), scene.getHeight() - sheetBottom - dp(mapTopInsetDp() + 16));
      int height =
          Math.min(
              dp(
                  panel.equals("menu")
                      ? 510
                      : getResources().getConfiguration().screenWidthDp < 740 ? 380 : 510),
              free);
      if (lp.height != height || lp.bottomMargin != sheetBottom) {
        lp.height = height;
        lp.bottomMargin = sheetBottom;
        sheet.setLayoutParams(lp);
      }
    }
  }

  private void hideKeyboard() {
    View focused = getCurrentFocus();
    if (focused != null) {
      ((InputMethodManager) getSystemService(INPUT_METHOD_SERVICE))
          .hideSoftInputFromWindow(focused.getWindowToken(), 0);
      focused.clearFocus();
    }
  }

  private void closePanel() {
    cancelDestinationImport();
    hideKeyboard();
    decisionExpanded = false;
    panel = "none";
    if (sheet != null && scene != null) scene.removeView(sheet);
    sheet = null;
    candidateList = null;
    if (summary != null) summary.setVisibility(View.VISIBLE);
    if (map != null) map.setMapObscured(false);
    if (scene != null) scene.post(this::layoutOverlays);
  }

  private void showPanel(String next) {
    if (!allow()) return;
    hideKeyboard();
    if (next.equals("detail")) decisionExpanded = true;
    panel = next;
    buildPanel();
  }

  private void returnToMap() {
    closePanel();
    selected = SelectionPolicy.beginMapBrowsing(selected, route);
    updateMap();
    renderSummary();
  }

  private void buildPanel() {
    if (sheet != null) scene.removeView(sheet);
    if (summary != null) summary.setVisibility(View.VISIBLE);
    map.setMapObscured(false);
    if (panel.equals("destinations")) {
      buildDestinationPanel();
      return;
    }
    if (panel.equals("detail")) {
      if (selected == null) closePanel();
      else buildDecisionPanel();
      return;
    }
    sheet = column();
    sheet.setPadding(dp(20), dp(12), dp(20), dp(12));
    sheet.setBackground(shape(Color.WHITE, 24, LINE));
    sheet.setElevation(dp(16));
    sheet.setClickable(true);
    sheet.setFocusableInTouchMode(true);
    sheet.requestFocus();
    LinearLayout heading = row();
    String title = panel.equals("places") ? "만날 곳 찾기" : panel.equals("reports") ? "내 기록" : "메뉴";
    addWeight(heading, text(title, 28, INK, true));
    heading.addView(
        iconButton("close", title + " 닫기", v -> closePanel()),
        new LinearLayout.LayoutParams(dp(64), dp(64)));
    add(sheet, heading, 0, 0, 0, 8);
    sheet.setAccessibilityPaneTitle(title);
    if (panel.equals("places")) buildPlacesPanel();
    else {
      ScrollView sc = new ScrollView(this);
      sc.setFillViewport(false);
      sc.setVerticalScrollBarEnabled(true);
      body = column();
      body.setPadding(0, dp(8), dp(6), dp(8));
      sc.addView(body);
      sheet.addView(sc, new LinearLayout.LayoutParams(-1, 0, 1));
      if (panel.equals("reports")) renderReports();
      else {
        add(body, actionButton("목적지 변경", "search", false, v -> openDestinations()), 0, 0, 0, 12);
        add(
            body,
            actionButton(
                "주변 정차지",
                "pin",
                false,
                v -> {
                  listTab = "nearby";
                  showPanel("places");
                }),
            0,
            0,
            0,
            12);
        add(
            body,
            actionButton(
                "저장한 곳",
                "bookmark",
                false,
                v -> {
                  listTab = "saved";
                  showPanel("places");
                }),
            0,
            0,
            0,
            12);
        add(body, actionButton("내 기록", "message", false, v -> showPanel("reports")), 0, 0, 0, 12);
        add(body, actionButton("앱 정보 및 설정", "settings", false, v -> showSettings()), 0, 0, 0, 0);
      }
    }
    int screenWidth = getResources().getConfiguration().screenWidthDp;
    int width =
        screenWidth < 740
            ? ViewGroup.LayoutParams.MATCH_PARENT
            : dp(Math.min(540, Math.round(screenWidth * .48f)));
    FrameLayout.LayoutParams lp =
        new FrameLayout.LayoutParams(width, dp(440), Gravity.BOTTOM | Gravity.START);
    lp.setMargins(dp(24), 0, dp(24), Math.max(dp(24), summaryBottomInset()));
    scene.addView(sheet, lp);
    layoutOverlays();
  }

  /** One place card owns the decision: identity and action stay fixed around the review body. */
  private void buildDecisionPanel() {
    map.setMapObscured(true);
    if (summary != null) summary.setVisibility(View.GONE);
    sheet = card();
    sheet.setPadding(dp(24), dp(24), dp(24), dp(20));
    sheet.setBackground(softSurface(0xfffcfdff, Color.WHITE, 30, 0xffdae4f3));
    sheet.setElevation(dp(14));
    sheet.setClickable(true);
    sheet.setFocusableInTouchMode(true);
    sheet.setAccessibilityPaneTitle(selected.name + " 장소 정보와 후기");
    sheet.requestFocus();

    LinearLayout heading = row();
    heading.addView(
        reviewIconTile("pin", BLUE, 0xffe8efff, 0xffdbe7ff, 56, 18),
        new LinearLayout.LayoutParams(dp(56), dp(56)));
    TextView name = strongText(selected.name, compact() ? 28 : 30, 0xff101827);
    name.setMaxLines(2);
    name.setLineSpacing(dp(3), 1.06f);
    LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(0, -2, 1);
    nameParams.leftMargin = dp(16);
    heading.addView(name, nameParams);
    TextView mapButton = actionButton("지도 보기", "map", false, v -> returnToMap());
    mapButton.setTextSize(22);
    mapButton.setMinHeight(dp(64));
    mapButton.setPadding(dp(16), dp(12), dp(16), dp(12));
    mapButton.setBackground(reviewRipple(0xffeaf0ff, 0xffe3ebff, 20, Color.TRANSPARENT));
    LinearLayout.LayoutParams mapParams = new LinearLayout.LayoutParams(-2, dp(64));
    mapParams.leftMargin = dp(16);
    heading.addView(mapButton, mapParams);
    add(sheet, heading, 0, 0, 0, 16);

    LinearLayout metrics = row();
    LinearLayout travel = row();
    TextView drive = strongText("차량 " + selected.driveMinutes + "분", 26, BLUE);
    withIcon(drive, "car", BLUE, 26);
    drive.setPadding(dp(18), dp(12), dp(18), dp(12));
    drive.setBackground(softSurface(0xffeaf4ff, 0xffdeecff, 30, Color.TRANSPARENT));
    travel.addView(drive);
    View metricDivider = new View(this);
    metricDivider.setBackgroundColor(0xffcbd5e5);
    LinearLayout.LayoutParams metricDividerParams = new LinearLayout.LayoutParams(dp(1), dp(28));
    metricDividerParams.setMargins(dp(18), 0, dp(18), 0);
    travel.addView(metricDivider, metricDividerParams);
    TextView walk = text(distanceLabel(selected), 25, INK, false);
    withIcon(walk, "distance", MUTED, 25);
    LinearLayout.LayoutParams walkParams = new LinearLayout.LayoutParams(-2, -2);
    travel.addView(walk, walkParams);
    addWeight(metrics, travel);
    TextView writeReview = actionButton("후기 남기기", "message", false, v -> showReport(null));
    writeReview.setTextSize(22);
    writeReview.setMinHeight(dp(64));
    writeReview.setPadding(dp(16), dp(12), dp(16), dp(12));
    writeReview.setBackground(reviewRipple(0xffeaf0ff, 0xffe3ebff, 20, Color.TRANSPARENT));
    metrics.addView(writeReview, new LinearLayout.LayoutParams(-2, dp(64)));
    add(sheet, metrics, 0, 0, 0, 16);
    if (route.equals("idle") && !PlaceRepository.isOpenAt(selected, arrivalHourFor(selected)))
      add(sheet, text("선택 시간에는 이용할 수 없어요", 22, 0xff925016, true), 0, 0, 0, 12);
    else if (!route.equals("idle"))
      add(
          sheet,
          text(
              route.equals("arrived")
                  ? "도착했어요"
                  : route.equals("error")
                      ? "안내를 준비하지 못했어요"
                      : route.equals("requesting")
                          ? "안내 준비 중…"
                          : route.equals("unconfirmed")
                              ? "순정 내비에서 확인해 주세요"
                              : previewRoute ? "안내 미리보기 진행 중" : "순정 내비에서 안내 중",
              22,
              BLUE,
              true),
          0,
          0,
          0,
          12);

    ScrollView content = new ScrollView(this);
    content.setVerticalScrollBarEnabled(true);
    content.setClipToOutline(true);
    content.setBackground(softSurface(0xfff3f6fc, Color.WHITE, 28, Color.TRANSPARENT));
    if ((route.equals("error") || route.equals("unconfirmed")) && !previewRoute && state != null)
      add(sheet, text(state.routeMessage, 22, 0xff925016, false), 0, 0, 0, 12);
    content.setContentDescription("장소 후기와 이용 정보, 이 영역만 스크롤");
    body = column();
    body.setPadding(dp(20), dp(20), dp(20), dp(16));
    content.addView(body);
    sheet.addView(content, new LinearLayout.LayoutParams(-1, 0, 1));
    renderReviewDetail();

    LinearLayout footer = decisionActions();
    add(sheet, footer, 0, 16, 0, 0);
    FrameLayout.LayoutParams params = new FrameLayout.LayoutParams(-1, dp(700), Gravity.BOTTOM);
    params.setMargins(dp(24), 0, dp(24), dp(24));
    scene.addView(sheet, params);
    layoutOverlays();
  }

  private void buildPlacesPanel() {
    ScrollView listScroll = new ScrollView(this);
    listScroll.setVerticalScrollBarEnabled(true);
    listScroll.setContentDescription("장소 찾기 패널, 이 영역만 스크롤");
    LinearLayout content = column();
    content.setPadding(0, 0, dp(6), 0);
    listScroll.addView(content);
    sheet.addView(listScroll, new LinearLayout.LayoutParams(-1, 0, 1));
    LinearLayout tabs = row();
    tabs.setBackground(shape(BG, 18, BG));
    tabs.addView(
        tab(
            "주변 장소",
            listTab.equals("nearby"),
            () -> {
              listTab = "nearby";
              buildPanel();
            }),
        weight());
    tabs.addView(
        tab(
            "저장한 곳",
            listTab.equals("saved"),
            () -> {
              listTab = "saved";
              buildPanel();
            }),
        weight());
    add(content, tabs, 0, 0, 0, 12);
    if (!listTab.equals("saved")) {
      LinearLayout tools = row();
      EditText search = input("장소 검색");
      search.setSingleLine(true);
      search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_SEARCH);
      search.setText(query);
      search.setContentDescription("평거동 주변 장소 검색");
      search.setOnEditorActionListener(
          (v, action, event) -> {
            hideKeyboard();
            return true;
          });
      withIcon(search, "search", MUTED, 24);
      tools.addView(search, new LinearLayout.LayoutParams(0, -2, 1));
      TextView filter = iconButton("filter", "방문 시간과 목적지 직선 거리 필터", v -> showFilters());
      filter.setBackground(shape(!customVisitTime && maxWalk == 500 ? BG : MINT, 18, LINE));
      LinearLayout.LayoutParams fp = new LinearLayout.LayoutParams(dp(72), dp(72));
      fp.leftMargin = dp(12);
      tools.addView(filter, fp);
      add(content, tools, 0, 0, 0, 12);
      if (customVisitTime || maxWalk != 500) {
        add(
            content,
            text(
                String.format(
                    Locale.KOREA,
                    "%s · 직선 %dm 이내",
                    customVisitTime ? String.format(Locale.KOREA, "%02d:00", hour) : "현재 시간",
                    maxWalk),
                20,
                BLUE,
                true),
            0,
            0,
            0,
            10);
      }
      search.addTextChangedListener(
          new TextWatcher() {
            public void beforeTextChanged(CharSequence s, int st, int c, int a) {}

            public void onTextChanged(CharSequence s, int st, int before, int count) {
              if (isBlocked()) return;
              query = s.toString();
              selected = SelectionPolicy.reconcile(selected, nearbyPlaces(), route);
              renderCandidates();
              renderSummary();
            }

            public void afterTextChanged(Editable e) {}
          });
    }
    candidateList = column();
    content.addView(candidateList);
    renderCandidates();
  }

  @Override
  public void onBackPressed() {
    if (panel.equals("detail")) returnToMap();
    else if (!panel.equals("none")) closePanel();
    else super.onBackPressed();
  }

  private void renderBlocked() {
    LinearLayout box = column();
    box.setPadding(dp(32), dp(24), dp(32), dp(24));
    box.setGravity(Gravity.CENTER);
    root.addView(box, new LinearLayout.LayoutParams(-1, 0, 1));
    TextView title = text("차량의 주차 상태를\n확인하고 있어요", 36, INK, true);
    title.setGravity(Gravity.CENTER);
    add(box, title, 0, 0, 0, 24);
    box.addView(
        button(
            "차량 상태 다시 확인",
            true,
            v -> {
              bridge.start();
              bridge.refreshDestination();
            }));
    add(
        box,
        text(state == null ? "플랫폼 연결 대기" : state.connectionMessage, 22, MUTED, false),
        0,
        20,
        0,
        0);
  }

  private void showFilters() {
    if (!allow()) return;
    final int initialHour = activeVisitHour();
    LinearLayout content = column();
    content.setPadding(dp(24), dp(12), dp(24), dp(12));
    add(content, text("방문 시간", 24, INK, true), 0, 0, 0, 12);
    String[] hours = new String[24];
    for (int i = 0; i < hours.length; i++) hours[i] = String.format(Locale.KOREA, "%02d:00", i);
    Spinner time = new Spinner(this, Spinner.MODE_DROPDOWN);
    time.setAdapter(choiceAdapter(hours, false));
    time.setSelection(initialHour);
    time.setContentDescription("방문 시간");
    add(content, time, 0, 0, 0, 24, 72);
    add(content, text("목적지까지 최대 직선 거리", 24, INK, true), 0, 0, 0, 12);
    String[] labels = {"100m", "300m", "500m", "800m"};
    int[] distances = {100, 300, 500, 800};
    Spinner distance = new Spinner(this, Spinner.MODE_DROPDOWN);
    distance.setAdapter(choiceAdapter(labels, false));
    for (int i = 0; i < distances.length; i++)
      if (distances[i] == maxWalk) distance.setSelection(i);
    distance.setContentDescription("목적지까지 최대 직선 거리");
    add(content, distance, 0, 0, 0, 12, 72);
    showDialog(
        new AlertDialog.Builder(this)
            .setTitle("어떤 장소를 찾을까요?")
            .setView(dialogBody(content))
            .setNegativeButton("취소", null)
            .setNeutralButton(
                "초기화",
                (d, w) -> {
                  if (!allow()) return;
                  customVisitTime = false;
                  hour = ZonedDateTime.now(SEOUL).getHour();
                  maxWalk = 500;
                  selected = SelectionPolicy.reconcile(selected, nearbyPlaces(), route);
                  render();
                })
            .setPositiveButton(
                "적용",
                (d, w) -> {
                  if (!allow()) return;
                  hour = time.getSelectedItemPosition();
                  customVisitTime = customVisitTime || hour != initialHour;
                  maxWalk = distances[distance.getSelectedItemPosition()];
                  selected = SelectionPolicy.reconcile(selected, nearbyPlaces(), route);
                  render();
                })
            .create());
  }

  private void renderCandidates() {
    if (candidateList == null) return;
    updateMap();
    candidateList.removeAllViews();
    boolean saved = listTab.equals("saved");
    if (saved && !DestinationRepository.supports(destination)) {
      add(candidateList, text("저장한 정차지는 평거동에 있어요", 26, INK, true), 0, 16, 0, 12);
      add(candidateList, button("주변 목적지 선택", false, v -> openDestinations()), 0, 0, 0, 0);
      return;
    }
    List<Place> candidates = saved ? PlaceRepository.savedPlaces(store.saved()) : nearbyPlaces();
    if (candidates.isEmpty()) {
      add(
          candidateList,
          text(saved ? "아직 저장한 곳이 없어요" : "조건에 맞는 장소가 없어요", 26, INK, true),
          8,
          18,
          8,
          14);
      add(
          candidateList,
          text(saved ? "장소 카드의 ··· 메뉴에서 저장할 수 있어요" : "다른 검색어나 필터를 선택해 주세요", 22, MUTED, false),
          8,
          0,
          8,
          18);
      if (!saved)
        candidateList.addView(
            button(
                "검색 조건 초기화",
                false,
                v -> {
                  if (!allow()) return;
                  query = "";
                  customVisitTime = false;
                  hour = ZonedDateTime.now(SEOUL).getHour();
                  maxWalk = 500;
                  selected = SelectionPolicy.reconcile(selected, nearbyPlaces(), route);
                  updateMap();
                  renderSummary();
                  buildPanel();
                }));
      return;
    }
    for (Place place : candidates) {
      boolean active = selected != null && selected.id.equals(place.id);
      LinearLayout item = column();
      item.setPadding(dp(18), dp(18), dp(18), dp(18));
      item.setBackground(
          new RippleDrawable(
              ColorStateList.valueOf(0x182563eb),
              shape(active ? MINT : BG, 18, active ? 0xffb6cef9 : BG),
              null));
      TextView name = text(place.name, 28, INK, true);
      name.setMaxLines(2);
      add(item, name, 0, 0, 0, 10);
      String extra = !PlaceRepository.isOpenAt(place, arrivalHourFor(place)) ? " · 이용 시간 확인" : "";
      add(
          item,
          text(
              "차량 " + place.driveMinutes + "분 · " + distanceLabel(place) + extra,
              24,
              active ? BLUE : MUTED,
              true),
          0,
          0,
          0,
          0);
      item.setContentDescription(
          place.name
              + ", 차량 "
              + place.driveMinutes
              + "분, "
              + distanceLabel(place)
              + (active ? ", 선택됨" : ", 선택"));
      item.setFocusable(true);
      item.setSelected(active);
      item.setOnClickListener(v -> openPlace(place.id));
      add(candidateList, item, 0, 0, 0, 12);
    }
  }

  private void openPlace(String id) {
    if (!allow() || !canChangeDestination()) return;
    if (!DestinationRepository.supports(destination)) {
      toast("저장한 정차지를 보려면 평거동 주변 목적지를 선택해 주세요");
      openDestinations();
      return;
    }
    Place place = PlaceRepository.find(id);
    if (place == null || place.prohibited || !place.verified) return;
    closePanel();
    if (selected == null || !selected.id.equals(id)) {
      previewRoute = true;
      ++previewRequestId;
      selected = place;
      route = "idle";
    }
    decisionExpanded = true;
    updateMap();
    renderSummary();
    showPanel("detail");
  }

  private void changeMode(String next) {
    if (!allow() || !canChangeDestination()) return;
    mode = next;
    query = "";
    selected = SelectionPolicy.beginMapBrowsing(selected, route);
    closePanel();
    updatePurposeButtons();
    updateMap();
    renderSummary();
    scene.post(this::layoutOverlays);
  }

  private int activeVisitHour() {
    return customVisitTime ? hour : ZonedDateTime.now(SEOUL).getHour();
  }

  private int arrivalHourFor(Place place) {
    return customVisitTime
        ? hour
        : ZonedDateTime.now(SEOUL).plusMinutes(place.driveMinutes).getHour();
  }

  private ReviewInsights.Context reviewContext(Instant now) {
    ZonedDateTime arrival = now.atZone(SEOUL);
    if (customVisitTime) arrival = arrival.withHour(hour).withMinute(0).withSecond(0);
    else arrival = arrival.plusMinutes(selected == null ? 0 : selected.driveMinutes);
    return new ReviewInsights.Context(arrival, mode);
  }

  private List<Review> reviewsForDisplay() {
    List<Review> reviews = new ArrayList<>(sampleReviews);
    for (LocalStore.Report report : store.reports()) {
      List<Review.Tag> positives =
          report.type.equals("easy") ? List.of(Review.Tag.EASY_PICKUP) : List.of();
      List<Review.Tag> cautions =
          report.type.equals("busy")
              ? List.of(Review.Tag.TRAFFIC)
              : report.type.equals("restricted")
                  ? List.of(Review.Tag.SIGNAGE)
                  : report.type.equals("different") ? List.of(Review.Tag.ROAD_CHANGE) : List.of();
      reviews.add(
          new Review(
              report.id,
              report.placeId,
              "나",
              report.text,
              report.visitedAt,
              report.createdAt,
              report.mode,
              0,
              false,
              positives,
              cautions));
    }
    List<Review> adjusted = new ArrayList<>();
    for (Review review : reviews)
      adjusted.add(store.isHelpful(review.id) ? review.withHelpful(review.helpful + 1) : review);
    return adjusted;
  }

  /** Review evidence is an explicit tag aggregation, never a claim of live road conditions. */
  private void renderReviewDetail() {
    if (selected == null) return;
    Instant now = Instant.now();
    ReviewInsights.Context context = reviewContext(now);
    ReviewInsights.Sort sort = allReviews ? allReviewSort : ReviewInsights.Sort.SIMILAR;
    ReviewInsights.Result result =
        ReviewInsights.evaluate(reviewsForDisplay(), selected.id, context, sort, now);

    // One control sets both the summary's evidence and the review order.
    LinearLayout heading = row();
    TextView reviewTitle = strongText("방문 후기 " + result.totalCount, 28, 0xff101827);
    android.text.SpannableString title = new android.text.SpannableString(reviewTitle.getText());
    title.setSpan(
        new android.text.style.ForegroundColorSpan(BLUE),
        "방문 후기 ".length(),
        title.length(),
        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    reviewTitle.setText(title);
    addWeight(heading, reviewTitle);
    TextView order = quietButton(reviewOrderLabel(), v -> chooseReviewOrder());
    UiIconDrawable down = new UiIconDrawable("down", MUTED);
    down.setBounds(0, 0, dp(20), dp(20));
    order.setCompoundDrawablesRelative(null, null, down, null);
    order.setCompoundDrawablePadding(dp(10));
    order.setContentDescription("후기 기준 선택, 현재 " + reviewOrderLabel());
    order.setTextColor(0xff34435d);
    order.setBackground(reviewRipple(0xfff0f4fc, 0xffe8eef9, 20, 0xffd9e3f3));
    heading.addView(order, new LinearLayout.LayoutParams(-2, -2));
    add(body, heading, 0, 0, 0, 12);

    if (!allReviews) {
      String dayType = context.arrival.getDayOfWeek().getValue() >= 6 ? "주말" : "평일";
      String clock = context.arrival.format(DateTimeFormatter.ofPattern("HH:mm", Locale.KOREA));
      add(
          body,
          text(
              (customVisitTime ? "방문 " : "도착 ")
                  + clock
                  + " 전후 2시간 · "
                  + dayType
                  + " · "
                  + modeLabel(),
              20,
              MUTED,
              false),
          0,
          0,
          0,
          18);
    }

    LinearLayout insights = column();
    insights.setPadding(dp(14), dp(16), dp(14), dp(14));
    insights.setBackground(softSurface(0xffedf2fc, 0xfff5f8fd, 24, Color.TRANSPARENT));
    if (result.summaryCount == 0) {
      add(insights, text(allReviews ? "아직 후기가 없어요" : "비슷한 방문 후기가 없어요", 24, INK, true), 0, 0, 0, 8);
      add(
          insights,
          text(allReviews ? "첫 경험을 남겨 주세요" : "아래에서 다른 상황의 후기를 볼 수 있어요", 22, MUTED, false),
          0,
          0,
          0,
          0);
    } else {
      LinearLayout basis = row();
      addWeight(
          basis,
          text(
              (allReviews ? "전체 " : "비슷한 방문 ") + result.summaryCount + "건 요약",
              21,
              0xff54617e,
              true));
      boolean includesSamples =
          (allReviews ? result.ordered : result.cohort).stream().anyMatch(review -> review.sample);
      if (includesSamples) basis.addView(text("예시 포함", 18, MUTED, false));
      add(insights, basis, 10, 0, 10, 14);
      insightLine(insights, "장점", result.positives, 0xff087d49);
      insightLine(insights, "주의", result.cautions, 0xffad480d);
    }
    add(body, insights, 0, 0, 0, 10);
    addPlaceInfoDisclosure();

    boolean otherHeading = false;
    for (Review review : result.ordered) {
      boolean similar = ReviewInsights.isSimilar(review, context);
      if (!allReviews && !similar && !otherHeading) {
        add(body, text("다른 상황의 후기", 22, MUTED, true), 0, 22, 0, 0);
        otherHeading = true;
      }
      renderReviewCard(review, similar);
    }
    if (result.ordered.isEmpty())
      add(body, quietButton("첫 후기 남기기", v -> showReport(null)), 0, 8, 0, 16);
    add(body, text("후기는 과거 방문 경험입니다. 현재 도로 상황은 현장에서 확인해 주세요.", 20, MUTED, false), 0, 20, 0, 8);
  }

  private void insightLine(
      LinearLayout parent, String label, List<ReviewInsights.TagCount> evidence, int accent) {
    boolean positive = label.equals("장점");
    LinearLayout line = row();
    line.setPadding(dp(14), dp(12), dp(12), dp(12));
    line.setMinimumHeight(dp(58));
    line.setBackground(
        softSurface(
            positive ? 0xffe8f6f0 : 0xfffff2e9,
            positive ? 0xffdbefeb : 0xfff8e9e0,
            22,
            Color.TRANSPARENT));
    TextView tag = strongText(label, 22, accent);
    withIcon(tag, positive ? "thumbs-up" : "alert", accent, 26);
    LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(-2, -2);
    line.addView(tag, tagParams);
    View separator = new View(this);
    separator.setBackgroundColor(positive ? 0xffa7cfc1 : 0xffe3bda5);
    LinearLayout.LayoutParams separatorParams = new LinearLayout.LayoutParams(dp(1), dp(25));
    separatorParams.setMargins(dp(14), 0, dp(16), 0);
    line.addView(separator, separatorParams);
    addWeight(
        line, text(evidence.isEmpty() ? "아직 언급이 없어요" : evidence.get(0).tag.label, 23, INK, true));
    if (!evidence.isEmpty()) {
      TextView count = text(evidence.get(0).count + "건", 20, 0xff465369, true);
      count.setPadding(dp(12), dp(6), dp(12), dp(6));
      count.setBackground(shape(0xb3ffffff, 22, Color.TRANSPARENT));
      LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(-2, -2);
      countParams.leftMargin = dp(10);
      line.addView(count, countParams);
    }
    add(parent, line, 0, 0, 0, positive ? 8 : 0);
  }

  private String reviewOrderLabel() {
    return !allReviews
        ? "내 상황 우선"
        : allReviewSort == ReviewInsights.Sort.HELPFUL ? "전체 · 도움순" : "전체 · 최신순";
  }

  private void chooseReviewOrder() {
    if (!allow()) return;
    showDialog(
        new AlertDialog.Builder(this)
            .setTitle("후기 보기 기준")
            .setSingleChoiceItems(
                choiceAdapter(
                    new String[] {"내 상황과 비슷한 방문 우선", "전체 · 최신 방문순", "전체 · 도움이 많은 순"}, true),
                !allReviews ? 0 : allReviewSort == ReviewInsights.Sort.HELPFUL ? 2 : 1,
                (d, which) -> {
                  if (!allow()) return;
                  allReviews = which != 0;
                  if (allReviews)
                    allReviewSort =
                        which == 2 ? ReviewInsights.Sort.HELPFUL : ReviewInsights.Sort.LATEST;
                  d.dismiss();
                  buildPanel();
                })
            .setNegativeButton("닫기", null)
            .create());
  }

  private void addPlaceInfoDisclosure() {
    LinearLayout details = column();
    details.setPadding(dp(12), dp(16), dp(12), dp(16));
    renderDetail(details);
    details.setVisibility(View.GONE);
    TextView toggle =
        quietButton(
            "이용 정보 보기",
            v -> {
              if (!allow()) return;
              boolean expanded = details.getVisibility() != View.VISIBLE;
              details.setVisibility(expanded ? View.VISIBLE : View.GONE);
              TextView control = (TextView) v;
              control.setText(expanded ? "이용 정보 접기" : "이용 정보 보기");
              control.setStateDescription(expanded ? "펼쳐짐" : "접힘");
            });
    toggle.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
    toggle.setMinHeight(dp(64));
    toggle.setStateDescription("접힘");
    add(body, toggle, 0, 0, 0, 0);
    add(body, details, 0, 0, 0, 0);
  }

  private void renderReviewCard(Review review, boolean similar) {
    LinearLayout item = row();
    item.setGravity(Gravity.TOP);
    item.setPadding(0, dp(22), 0, dp(24));
    int avatarStyle = Math.floorMod(review.author.hashCode(), 3);
    String avatarIcon = avatarStyle == 0 ? "person" : avatarStyle == 1 ? "car" : "trees";
    ImageView avatar =
        reviewIconTile(
            avatarIcon, avatarStyle == 2 ? 0xff3e8478 : 0xff476da7, 0xffeef4ff, 0xffdfeafa, 52, 52);
    LinearLayout.LayoutParams avatarParams = new LinearLayout.LayoutParams(dp(52), dp(52));
    avatarParams.rightMargin = dp(16);
    item.addView(avatar, avatarParams);
    LinearLayout reviewBody = column();
    item.addView(reviewBody, new LinearLayout.LayoutParams(0, -2, 1));
    ZonedDateTime visit = Instant.ofEpochMilli(review.visitedAt).atZone(SEOUL);
    LinearLayout byline = row();
    String authorLine =
        review.author
            + " · "
            + visit.format(DateTimeFormatter.ofPattern("M/d(E) HH:mm", Locale.KOREA));
    android.text.SpannableString author = new android.text.SpannableString(authorLine);
    author.setSpan(
        new android.text.style.StyleSpan(Typeface.BOLD),
        0,
        review.author.length(),
        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    author.setSpan(
        new android.text.style.ForegroundColorSpan(INK),
        0,
        review.author.length(),
        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    author.setSpan(
        new android.text.style.RelativeSizeSpan(1.12f),
        0,
        review.author.length(),
        android.text.Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
    TextView authorLabel = text(authorLine, 20, MUTED, false);
    authorLabel.setText(author);
    addWeight(byline, authorLabel);
    final int baseHelpful = review.helpful - (store.isHelpful(review.id) ? 1 : 0);
    TextView helpful = quietButton("", null);
    updateHelpfulButton(helpful, review.id, baseHelpful);
    helpful.setOnClickListener(
        v -> {
          if (!allow()) return;
          store.toggleHelpful(review.id);
          // Keep the current reading position; the new count affects the next helpful-sort
          // evaluation.
          updateHelpfulButton(helpful, review.id, baseHelpful);
        });
    LinearLayout.LayoutParams helpfulParams = new LinearLayout.LayoutParams(-2, dp(56));
    helpfulParams.leftMargin = dp(12);
    byline.addView(helpful, helpfulParams);
    add(reviewBody, byline, 0, 0, 0, 10);
    String purpose =
        review.mode.equals("parking")
            ? "주차장"
            : review.mode.equals("wait")
                ? "기다리기"
                : review.mode.equals("instant") ? "바로 태우기" : "목적 미기록";
    TextView note = text(review.text, 24, INK, false);
    note.setLineSpacing(dp(4), 1.08f);
    note.setMaxLines(3);
    note.setEllipsize(android.text.TextUtils.TruncateAt.END);
    reviewBody.addView(note);
    {
      TextView expand = quietButton("더 읽기", null);
      expand.setGravity(Gravity.START | Gravity.CENTER_VERTICAL);
      expand.setVisibility(View.GONE);
      expand.setTextSize(20);
      expand.setMinHeight(dp(56));
      expand.setPadding(dp(12), dp(10), dp(12), dp(10));
      expand.setOnClickListener(
          v -> {
            if (!allow()) return;
            boolean expanded = note.getMaxLines() > 3;
            note.setMaxLines(expanded ? 3 : Integer.MAX_VALUE);
            note.setEllipsize(expanded ? android.text.TextUtils.TruncateAt.END : null);
            expand.setText(expanded ? "더 읽기" : "접기");
          });
      add(reviewBody, expand, 0, 8, 0, 0);
      note.addOnLayoutChangeListener(
          (v, l, t, r, b, ol, ot, or, ob) -> {
            android.text.Layout layout = note.getLayout();
            boolean truncated =
                layout != null
                    && layout.getLineCount() > 0
                    && layout.getEllipsisCount(layout.getLineCount() - 1) > 0;
            expand.setVisibility(truncated || note.getMaxLines() > 3 ? View.VISIBLE : View.GONE);
          });
    }
    LinearLayout provenance = row();
    provenance.addView(
        reviewChip(
            purpose,
            review.mode.equals("parking")
                ? "parking"
                : review.mode.equals("wait") ? "clock" : "walk",
            0xff405676,
            0xffeaf0fa));
    LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(-2, -2);
    sourceParams.leftMargin = dp(10);
    provenance.addView(
        reviewChip(review.sample ? "예시 후기" : "내 기록 · 확인 중", "message", 0xff405676, 0xffeaf0fa),
        sourceParams);
    if (similar && !allReviews && getResources().getConfiguration().screenWidthDp >= 740) {
      LinearLayout.LayoutParams similarParams = new LinearLayout.LayoutParams(-2, -2);
      similarParams.leftMargin = dp(10);
      provenance.addView(reviewChip("비슷한 방문", "check", TEAL, 0xffe5f3ef), similarParams);
    }
    add(reviewBody, provenance, 0, 14, 0, 0);
    if (similar && !allReviews && getResources().getConfiguration().screenWidthDp < 740)
      add(reviewBody, text("내 상황과 비슷한 방문", 18, TEAL, true), 0, 10, 0, 0);
    add(body, item, 0, 0, 0, 0);
    View divider = new View(this);
    divider.setBackgroundColor(LINE);
    add(body, divider, 0, 0, 0, 0, 1);
  }

  private void updateHelpfulButton(TextView button, String reviewId, int baseCount) {
    boolean marked = store.isHelpful(reviewId);
    button.setText("도움 " + (baseCount + (marked ? 1 : 0)));
    button.setTextSize(20);
    button.setTextColor(marked ? BLUE : 0xff405676);
    button.setPadding(dp(12), dp(10), dp(12), dp(10));
    withIcon(button, "thumbs-up", BLUE, 24);
    button.setSelected(marked);
    button.setStateDescription(marked ? "도움 선택됨" : "도움 선택 안 됨");
    button.setContentDescription(
        "이 후기가 도움이 돼요, " + (baseCount + (marked ? 1 : 0)) + "명, " + (marked ? "선택 취소" : "선택"));
    button.setBackground(
        reviewRipple(
            marked ? 0xffdce9ff : 0xfff9fbff, marked ? 0xffeaf1ff : 0xfff0f5ff, 28, 0xffd4e0f6));
  }

  private void renderDetail(LinearLayout target) {
    if (selected == null) return;
    info(target, "최종 목적지", destination.name);
    info(target, "목적지와의 거리", distanceLabel(selected) + " · 보행 경로 거리와 다를 수 있어요");
    add(target, text(selected.reason, 24, INK, false), 0, 0, 0, 24);
    info(target, "진입 방향", selected.direction);
    info(target, "이용 시간", selected.hours);
    info(target, "요금", selected.fee);
    info(target, "추가 이동", "차량 우회 +" + selected.detour + "m");

    add(target, text("예시 장소와 경로입니다. 실제 이용 시 현장 표지와 도로 상황을 확인해 주세요.", 20, MUTED, false), 0, 0, 0, 0);
    if ((route.equals("error") || route.equals("unconfirmed")) && state != null)
      add(target, text(state.routeMessage, 22, 0xffa94535, false), 0, 16, 0, 0);
  }

  private void sendSelectedToNavigation() {
    if (!allow() || selected == null || !canChangeDestination()) return;
    if (!PlaceRepository.isOpenAt(selected, arrivalHourFor(selected))) {
      toast("선택한 시간에는 이용할 수 없어요. 다른 정차지를 골라 주세요");
      listTab = "nearby";
      showPanel("places");
      return;
    }
    if (checkSelfPermission(PleosBridge.ROUTE_PERMISSION) != PackageManager.PERMISSION_GRANTED) {
      requestPermissions(new String[] {PleosBridge.ROUTE_PERMISSION}, 46);
      toast("내비 경로 권한을 허용한 뒤 다시 눌러 주세요");
      return;
    }
    if (state == null || !state.connected) {
      bridge.start();
      toast("순정 내비에 연결 중이에요. 연결되면 다시 눌러 주세요");
      return;
    }
    ++previewRequestId;
    previewRoute = false;
    openNativeWhenReady = true;
    route = "requesting";
    decisionExpanded = false;
    panel = "none";
    bridge.requestRoute(selected.name, selected.latitude, selected.longitude);
    render();
  }

  private void startMeetingPreview() {
    if (!allow() || selected == null || !canChangeDestination()) return;
    if (!PlaceRepository.isOpenAt(selected, arrivalHourFor(selected))) {
      toast("선택한 시간에는 이용할 수 없어요");
      return;
    }
    for (AlertDialog dialog : dialogs) if (dialog.isShowing()) dialog.dismiss();
    closePanel();
    previewRoute = true;
    openNativeWhenReady = false;
    requestNavigation();
  }

  private void requestNavigation() {
    if (!allow() || selected == null) return;
    final int requestId = ++previewRequestId;
    final String requestedPlaceId = selected.id;
    if (!previewRoute) {
      if (state == null || !state.connected) {
        bridge.start();
        toast("연결을 복구하고 있어요. 연결 후 다시 안내를 눌러 주세요.");
        return;
      }
      route = "requesting";
      bridge.requestRoute(selected.name, selected.latitude, selected.longitude);
      render();
      return;
    }
    route = "requesting";
    render();
    handler.postDelayed(
        () -> {
          if (started
              && previewRoute
              && requestId == previewRequestId
              && selected != null
              && requestedPlaceId.equals(selected.id)
              && route.equals("requesting")) {
            route = "active";
            render();
          }
        },
        1000);
  }

  private void renderReports() {
    List<LocalStore.Report> reports = store.reports();
    if (reports.isEmpty()) {
      add(body, text("아직 남긴 제보가 없어요.", 32, MUTED, true), 0, 25, 0, 18);
      body.addView(
          button(
              "장소 둘러보기",
              true,
              v -> {
                closePanel();
              }));
    }
    for (LocalStore.Report r : reports) {
      Place p = PlaceRepository.find(r.placeId);
      if (p == null) continue;
      LinearLayout c = card();
      add(c, text("확인 중  ·  " + typeLabel(r.type), 20, BLUE, true), 0, 0, 0, 10);
      add(c, text(p.name, 32, INK, true), 0, 0, 0, 12);
      add(c, text(r.text, 24, MUTED, false), 0, 0, 0, 15);
      add(c, text(formatDate(r.visitedAt) + " 이용", 20, MUTED, false), 0, 0, 0, 12);
      LinearLayout buttons = row();
      buttons.addView(
          button(
              "수정",
              false,
              v -> {
                if (!allow()) return;
                showReport(r);
              }),
          weight());
      buttons.addView(
          button(
              "철회",
              false,
              v -> {
                if (allow())
                  showDialog(
                      new AlertDialog.Builder(this)
                          .setTitle("제보를 철회할까요?")
                          .setMessage("이 기기의 제보 목록에서 삭제됩니다.")
                          .setNegativeButton("유지", null)
                          .setPositiveButton(
                              "철회",
                              (d, w) -> {
                                if (!allow()) return;
                                store.removeReport(r.id);
                                render();
                                toast("제보를 철회했어요");
                              })
                          .create());
              }),
          weight());
      c.addView(buttons);
      add(body, c, 0, 0, 0, 14);
    }
  }

  private void showReport(LocalStore.Report existing) {
    if (!allow() || (existing == null && selected == null)) return;
    Place reportingPlace = existing == null ? selected : PlaceRepository.find(existing.placeId);
    if (reportingPlace == null) return;
    LinearLayout form = column();
    form.setPadding(dp(22), dp(10), dp(22), dp(12));
    add(form, text(reportingPlace.name, 24, INK, true), 0, 0, 0, 16);
    final int[] selectedType = {0};
    if (existing != null)
      for (int i = 0; i < 4; i++) if (REPORT_TYPES[i].equals(existing.type)) selectedType[0] = i;
    Spinner kinds = new Spinner(this);
    kinds.setAdapter(choiceAdapter(REPORT_LABELS, false));
    kinds.setSelection(selectedType[0]);
    form.addView(kinds, new LinearLayout.LayoutParams(-1, dp(72)));
    add(form, text("방문 목적", 22, INK, true), 0, 16, 0, 8);
    String[] purposeValues = {"", "instant", "wait", "parking"};
    Spinner purpose = new Spinner(this);
    purpose.setAdapter(choiceAdapter(new String[] {"목적 미기록", "바로 태우기", "기다리기", "주차장"}, false));
    String reportingMode = existing == null ? mode : existing.mode;
    for (int i = 0; i < purposeValues.length; i++)
      if (purposeValues[i].equals(reportingMode)) purpose.setSelection(i);
    purpose.setContentDescription("후기 방문 목적");
    form.addView(purpose, new LinearLayout.LayoutParams(-1, dp(72)));
    Calendar visited = Calendar.getInstance(TimeZone.getTimeZone("Asia/Seoul"));
    if (existing != null) visited.setTimeInMillis(existing.visitedAt);
    visited.set(Calendar.SECOND, 0);
    visited.set(Calendar.MILLISECOND, 0);
    TextView time =
        button(
            formatDate(visited.getTimeInMillis()) + "  변경",
            false,
            v -> {
              DatePickerDialog date =
                  new DatePickerDialog(
                      this,
                      (picker, y, m, d) -> {
                        visited.set(y, m, d);
                        TimePickerDialog clock =
                            new TimePickerDialog(
                                this,
                                (tp, h, min) -> {
                                  visited.set(Calendar.HOUR_OF_DAY, h);
                                  visited.set(Calendar.MINUTE, min);
                                  timeButtonUpdate(form, visited);
                                },
                                visited.get(Calendar.HOUR_OF_DAY),
                                visited.get(Calendar.MINUTE),
                                true);
                        showDialog(clock);
                      },
                      visited.get(Calendar.YEAR),
                      visited.get(Calendar.MONTH),
                      visited.get(Calendar.DAY_OF_MONTH));
              date.getDatePicker().setMaxDate(System.currentTimeMillis());
              showDialog(date);
            });
    time.setTag("visit-time");
    add(form, time, 0, 10, 0, 10);
    EditText note = input("추가 설명 (선택, 300자 이내)");
    note.setMinLines(3);
    note.setMaxLines(5);
    note.setFilters(
        new android.text.InputFilter[] {new android.text.InputFilter.LengthFilter(300)});
    note.setGravity(Gravity.TOP);
    if (existing != null) note.setText(existing.text);
    form.addView(note);
    add(
        form,
        text("후기는 ‘확인 중’으로 기기에 저장됩니다.\n실제 서버에 전송되거나 규정을 변경하지 않습니다.", 20, MUTED, false),
        0,
        13,
        0,
        0);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle(existing == null ? "후기 남기기" : "후기 수정하기")
            .setView(dialogBody(form))
            .setNegativeButton("취소", null)
            .setPositiveButton("저장", null)
            .create();
    showDialog(dialog);
    dialog
        .getButton(AlertDialog.BUTTON_POSITIVE)
        .setOnClickListener(
            v -> {
              if (!allow()) return;
              if (visited.getTimeInMillis() > System.currentTimeMillis()) {
                toast("미래의 경험은 등록할 수 없어요");
                return;
              }
              String text = note.getText().toString().trim();
              int type = kinds.getSelectedItemPosition();
              store.saveReport(
                  existing == null ? null : existing.id,
                  reportingPlace.id,
                  REPORT_TYPES[type],
                  text.isEmpty() ? REPORT_LABELS[type] : text,
                  visited.getTimeInMillis(),
                  purposeValues[purpose.getSelectedItemPosition()]);
              dialog.dismiss();
              toast(existing == null ? "후기를 저장했어요" : "후기를 수정했어요");
              if (panel.equals("reports") || panel.equals("detail")) buildPanel();
            });
  }

  private void timeButtonUpdate(LinearLayout form, Calendar visited) {
    TextView t = form.findViewWithTag("visit-time");
    if (t != null) t.setText(formatDate(visited.getTimeInMillis()) + "  변경");
  }

  private void showShare() {
    if (!allow() || selected == null) return;
    if (!destination.id.equals(DestinationRepository.DEFAULT.id)) {
      showDestinationShare();
      return;
    }
    try {
      JSONObject data =
          new JSONObject()
              .put("id", UUID.randomUUID().toString())
              .put("placeId", selected.id)
              .put("destinationId", "d1")
              .put("note", "지도에 표시된 쪽 인도에서 만나요. 차량과 도로 정보는 시연용입니다.")
              .put("createdAt", Instant.now().toString())
              .put("expiresAt", Instant.now().plusSeconds(7200).toString())
              .put("cancelled", false);
      String encoded =
          Base64.getUrlEncoder()
              .withoutPadding()
              .encodeToString(data.toString().getBytes(StandardCharsets.UTF_8));
      String link = COMPANION + "#meet=" + encoded;
      LinearLayout content = column();
      content.setPadding(dp(24), dp(8), dp(24), dp(14));
      add(content, text(selected.name, 32, INK, true), 0, 0, 0, 10);
      add(content, text("동승자가 QR을 스캔하면 만날 곳을 확인할 수 있어요.", 24, MUTED, false), 0, 0, 0, 17);
      BitMatrix matrix =
          new MultiFormatWriter().encode(link, BarcodeFormat.QR_CODE, dp(288), dp(288));
      Bitmap qr =
          Bitmap.createBitmap(matrix.getWidth(), matrix.getHeight(), Bitmap.Config.ARGB_8888);
      for (int x = 0; x < matrix.getWidth(); x++)
        for (int y = 0; y < matrix.getHeight(); y++)
          qr.setPixel(x, y, matrix.get(x, y) ? BLUE : Color.WHITE);
      ImageView image = new ImageView(this);
      image.setImageBitmap(qr);
      image.setContentDescription("동승자 공유 링크 QR 코드");
      LinearLayout.LayoutParams imgParams = new LinearLayout.LayoutParams(dp(288), dp(288));
      imgParams.gravity = Gravity.CENTER;
      content.addView(image, imgParams);
      add(
          content,
          text(
              "2시간 유효 · 발급 시점의 장소 정보\n"
                  + "공유 웹은 소유자 검토용입니다. 계정 로그인에 따라 접근이 제한될 수 있어요. 다른 기기와 변경·취소가 동기화되지 않습니다.",
              20,
              MUTED,
              false),
          0,
          14,
          0,
          0);
      showDialog(
          new AlertDialog.Builder(this)
              .setTitle("우리, 여기서 만나요")
              .setView(dialogBody(content))
              .setNegativeButton("닫기", null)
              .setPositiveButton(
                  "링크 복사",
                  (d, w) -> {
                    ((ClipboardManager) getSystemService(CLIPBOARD_SERVICE))
                        .setPrimaryClip(ClipData.newPlainText("일로오소 만남 링크", link));
                    toast("공유 링크를 복사했어요");
                  })
              .create());
    } catch (Exception ex) {
      toast("공유 링크를 만들지 못했어요. 다시 시도해 주세요.");
    }
  }

  private void showSettings() {
    if (!allow()) return;
    LinearLayout content = column();
    content.setPadding(dp(24), dp(8), dp(24), dp(14));
    info(content, "현재 목적지", destination.name);
    info(content, "목적지 출처", destinationSource);
    info(content, "차량 연결", state != null && state.connected ? "연결됨" : "연결 대기 중");
    add(content, text("현재는 평거동의 예시 장소와 안내 미리보기를 제공합니다.", 22, MUTED, false), 0, 0, 0, 22);
    AlertDialog dialog =
        new AlertDialog.Builder(this)
            .setTitle("일로오소")
            .setView(dialogBody(content))
            .setNegativeButton("닫기", null)
            .create();
    add(
        content,
        button(
            "연동 점검",
            false,
            v -> {
              dialog.dismiss();
              showDiagnostics();
            }),
        0,
        0,
        0,
        4);
    showDialog(dialog);
  }

  private void showDiagnostics() {
    if (!allow()) return;
    LinearLayout content = column();
    content.setPadding(dp(24), dp(8), dp(24), dp(14));
    add(
        content,
        text(state == null ? "연결 준비 중" : state.connectionMessage, 22, MUTED, false),
        0,
        0,
        0,
        18);
    add(
        content,
        button(
            "차량 연결 다시 확인",
            false,
            v -> {
              if (!allow()) return;
              bridge.start();
              bridge.refreshDestination();
              toast("차량 연결을 확인하고 있어요");
            }),
        0,
        0,
        0,
        12);
    add(
        content,
        button(
            "주행 제한 확인",
            false,
            v -> {
              if (!allow()) return;
              simulatedDriving = true;
              for (AlertDialog d : dialogs) if (d.isShowing()) d.dismiss();
              render();
            }),
        0,
        0,
        0,
        12);
    if (selected != null) {
      add(content, button("선택한 장소 만남 미리보기", false, v -> startMeetingPreview()), 0, 0, 0, 12);
      add(
          content,
          button(
              "선택한 장소로 실제 경로 요청",
              false,
              v -> {
                if (!allow()) return;
                final Place requested = selected;
                showDialog(
                    new AlertDialog.Builder(this)
                        .setTitle("Pleos에 경로를 요청할까요?")
                        .setMessage("중앙 내비게이션 목적지가 이 예시 지점으로 변경됩니다. 좌표와 정차 조건은 시연용입니다.")
                        .setNegativeButton("취소", null)
                        .setPositiveButton(
                            "경로 요청",
                            (d, w) -> {
                              if (!allow() || selected == null || !selected.id.equals(requested.id))
                                return;
                              for (AlertDialog open : dialogs) if (open.isShowing()) open.dismiss();
                              sendSelectedToNavigation();
                            })
                        .create());
              }),
          0,
          0,
          0,
          12);
    }
    showDialog(
        new AlertDialog.Builder(this)
            .setTitle("연동 점검")
            .setView(dialogBody(content))
            .setNegativeButton("닫기", null)
            .create());
  }

  private ScrollView dialogBody(View content) {
    ScrollView holder = new ScrollView(this);
    holder.setFillViewport(false);
    holder.addView(content);
    return holder;
  }

  private void showDialog(AlertDialog dialog) {
    dialogs.removeIf(d -> !d.isShowing());
    dialogs.add(dialog);
    dialog.show();
    if (dialog.getWindow() != null) {
      int width = Math.min(900, getResources().getConfiguration().screenWidthDp - 40);
      dialog.getWindow().setLayout(dp(width), WindowManager.LayoutParams.WRAP_CONTENT);
      dialog.getWindow().setBackgroundDrawable(shape(Color.WHITE, 28, Color.WHITE));
      dialog.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
      if (!(dialog instanceof DatePickerDialog) && !(dialog instanceof TimePickerDialog))
        readableDialogText(dialog.getWindow().getDecorView());
    }
    for (int id :
        new int[] {
          AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE, AlertDialog.BUTTON_NEUTRAL
        }) {
      android.widget.Button b = dialog.getButton(id);
      if (b != null) {
        b.setTextSize(24);
        b.setMinHeight(dp(64));
        b.setTextColor(BLUE);
        b.setAllCaps(false);
      }
    }
  }

  private void readableDialogText(View view) {
    if (view instanceof TextView) {
      TextView t = (TextView) view;
      float scaled = getResources().getDisplayMetrics().scaledDensity;
      if (t.getTextSize() / scaled < 22) t.setTextSize(22);
      if (t.getId() == android.R.id.message) t.setTextColor(MUTED);
    }
    if (view instanceof ViewGroup) {
      ViewGroup group = (ViewGroup) view;
      for (int i = 0; i < group.getChildCount(); i++) readableDialogText(group.getChildAt(i));
    }
  }

  private ArrayAdapter<String> choiceAdapter(String[] values, boolean radio) {
    return new ArrayAdapter<String>(
        this,
        radio
            ? android.R.layout.simple_list_item_single_choice
            : android.R.layout.simple_list_item_1,
        values) {
      @Override
      public View getView(int position, View recycled, ViewGroup parent) {
        TextView v = (TextView) super.getView(position, recycled, parent);
        v.setTextSize(24);
        v.setTextColor(INK);
        v.setMinHeight(dp(72));
        // Platform radio rows can carry a fixed 48dp height; minHeight alone cannot enlarge them.
        ViewGroup.LayoutParams params = v.getLayoutParams();
        if (params == null) params = new AbsListView.LayoutParams(-1, dp(72));
        else params.height = dp(72);
        v.setLayoutParams(params);
        v.setGravity(Gravity.CENTER_VERTICAL);
        v.setPadding(dp(24), dp(8), dp(24), dp(8));
        return v;
      }

      @Override
      public View getDropDownView(int position, View recycled, ViewGroup parent) {
        return getView(position, recycled, parent);
      }
    };
  }

  private void info(LinearLayout parent, String label, String value) {
    LinearLayout line = column();
    add(line, text(label, 20, MUTED, false), 0, 0, 0, 8);
    line.addView(text(value, 24, INK, true));
    add(parent, line, 0, 0, 0, 24);
  }

  private TextView tab(String label, boolean active, Runnable action) {
    TextView b =
        button(
            label,
            false,
            v -> {
              if (allow()) action.run();
            });
    b.setTextSize(24);
    b.setMinHeight(dp(64));
    b.setSelected(active);
    b.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x182563eb),
            shape(active ? Color.WHITE : Color.TRANSPARENT, 16, Color.TRANSPARENT),
            null));
    b.setTextColor(active ? BLUE : MUTED);
    if (active) b.setElevation(dp(1));
    return b;
  }

  private LinearLayout column() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.VERTICAL);
    return l;
  }

  private LinearLayout row() {
    LinearLayout l = new LinearLayout(this);
    l.setOrientation(LinearLayout.HORIZONTAL);
    l.setGravity(Gravity.CENTER_VERTICAL);
    return l;
  }

  private LinearLayout card() {
    LinearLayout l = column();
    l.setPadding(dp(24), dp(24), dp(24), dp(24));
    l.setBackground(shape(Color.WHITE, 24, LINE));
    return l;
  }

  private TextView text(String value, float size, int color, boolean bold) {
    TextView t = new TextView(this);
    t.setText(value);
    t.setTextSize(size);
    t.setTextColor(color);
    t.setFontFeatureSettings("kern");
    t.setLineSpacing(dp(4), 1.05f);
    t.setIncludeFontPadding(false);
    t.setTypeface(Typeface.create(bold ? "sans-serif-medium" : "sans-serif", Typeface.NORMAL));
    t.setBreakStrategy(android.text.Layout.BREAK_STRATEGY_HIGH_QUALITY);
    return t;
  }

  private TextView button(String label, boolean primary, View.OnClickListener action) {
    TextView t = text(label, 26, primary ? Color.WHITE : BLUE, true);
    t.setGravity(Gravity.CENTER);
    t.setMinHeight(dp(72));
    t.setPadding(dp(22), dp(18), dp(22), dp(18));
    t.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(primary ? 0x33ffffff : 0x182563eb),
            shape(primary ? BLUE : Color.WHITE, 18, primary ? BLUE : LINE),
            null));
    t.setOnClickListener(action);
    t.setFocusable(true);
    t.setClickable(true);
    t.setAccessibilityDelegate(
        new View.AccessibilityDelegate() {
          @Override
          public void onInitializeAccessibilityNodeInfo(
              View host, android.view.accessibility.AccessibilityNodeInfo info) {
            super.onInitializeAccessibilityNodeInfo(host, info);
            info.setClassName(android.widget.Button.class.getName());
          }
        });
    return t;
  }

  private TextView quietButton(String label, View.OnClickListener action) {
    TextView control = button(label, false, action);
    control.setTextSize(22);
    control.setTextColor(MUTED);
    control.setMinHeight(dp(56));
    control.setPadding(dp(16), dp(12), dp(16), dp(12));
    control.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x182563eb),
            shape(Color.TRANSPARENT, 14, Color.TRANSPARENT),
            null));
    return control;
  }

  private void withIcon(TextView target, String icon, int color, int size) {
    UiIconDrawable drawable = new UiIconDrawable(icon, color);
    drawable.setBounds(0, 0, dp(size), dp(size));
    target.setCompoundDrawablesRelative(drawable, null, null, null);
    target.setCompoundDrawablePadding(dp(12));
  }

  private TextView actionButton(
      String label, String icon, boolean primary, View.OnClickListener action) {
    TextView t = button(label, primary, action);
    withIcon(t, icon, primary ? Color.WHITE : BLUE, 26);
    return t;
  }

  private TextView iconButton(String icon, String description, View.OnClickListener action) {
    TextView t = button("", false, action);
    t.setPadding(dp(18), dp(18), dp(18), dp(18));
    t.setMinHeight(dp(64));
    t.setMinWidth(dp(64));
    UiIconDrawable drawable = new UiIconDrawable(icon, BLUE);
    drawable.setBounds(0, 0, dp(28), dp(28));
    t.setCompoundDrawables(drawable, null, null, null);
    t.setContentDescription(description);
    t.setBackground(
        new RippleDrawable(
            ColorStateList.valueOf(0x182563eb), shape(Color.WHITE, 18, Color.TRANSPARENT), null));
    return t;
  }

  private EditText input(String placeholder) {
    EditText e = new EditText(this);
    e.setHint(placeholder);
    e.setHintTextColor(MUTED);
    e.setTextColor(INK);
    e.setTextSize(24);
    e.setPadding(dp(22), dp(18), dp(22), dp(18));
    StateListDrawable background = new StateListDrawable();
    background.addState(new int[] {android.R.attr.state_focused}, shape(Color.WHITE, 18, BLUE));
    background.addState(new int[] {}, shape(Color.WHITE, 18, LINE));
    e.setBackgroundTintList(null);
    e.setBackground(background);
    e.setMinHeight(dp(72));
    return e;
  }

  private GradientDrawable shape(int color, int radius, int stroke) {
    GradientDrawable d = new GradientDrawable();
    d.setColor(color);
    d.setCornerRadius(dp(radius));
    d.setStroke(dp(1), stroke);
    return d;
  }

  private GradientDrawable softSurface(int start, int end, int radius, int stroke) {
    GradientDrawable surface =
        new GradientDrawable(GradientDrawable.Orientation.TL_BR, new int[] {start, end});
    surface.setCornerRadius(dp(radius));
    surface.setStroke(dp(1), stroke);
    return surface;
  }

  private RippleDrawable reviewRipple(int start, int end, int radius, int stroke) {
    return new RippleDrawable(
        ColorStateList.valueOf(0x182563eb), softSurface(start, end, radius, stroke), null);
  }

  private TextView strongText(String value, float size, int color) {
    TextView label = text(value, size, color, true);
    label.setTypeface(Typeface.create("sans-serif", Typeface.BOLD));
    return label;
  }

  private ImageView reviewIconTile(
      String icon, int color, int start, int end, int size, int radius) {
    ImageView tile = new ImageView(this);
    tile.setImageDrawable(new UiIconDrawable(icon, color));
    tile.setScaleType(ImageView.ScaleType.FIT_CENTER);
    int padding = dp(size * .23f);
    tile.setPadding(padding, padding, padding, padding);
    tile.setBackground(softSurface(start, end, radius, Color.TRANSPARENT));
    tile.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);
    return tile;
  }

  private TextView reviewChip(String value, String icon, int color, int background) {
    TextView chip = text(value, 18, color, false);
    withIcon(chip, icon, color, 18);
    chip.setCompoundDrawablePadding(dp(7));
    chip.setPadding(dp(10), dp(6), dp(10), dp(6));
    chip.setBackground(shape(background, 12, Color.TRANSPARENT));
    return chip;
  }

  private LinearLayout.LayoutParams weight() {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
    p.setMargins(dp(4), 0, dp(4), 0);
    return p;
  }

  private void addWeight(LinearLayout parent, View view) {
    parent.addView(view, new LinearLayout.LayoutParams(0, -2, 1));
  }

  private void add(LinearLayout parent, View child, int left, int top, int right, int bottom) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, -2);
    p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
    parent.addView(child, p);
  }

  private void add(
      LinearLayout parent, View child, int left, int top, int right, int bottom, int height) {
    LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(-1, dp(height));
    p.setMargins(dp(left), dp(top), dp(right), dp(bottom));
    parent.addView(child, p);
  }

  private int dp(float v) {
    return Math.round(v * getResources().getDisplayMetrics().density);
  }

  private void toast(String s) {
    Toast.makeText(this, s, Toast.LENGTH_SHORT).show();
  }

  private String typeLabel(String type) {
    for (int i = 0; i < 4; i++) if (REPORT_TYPES[i].equals(type)) return REPORT_LABELS[i];
    return "이용 경험";
  }

  private String formatDate(long ms) {
    SimpleDateFormat f = new SimpleDateFormat("MM월 dd일 HH:mm", Locale.KOREA);
    f.setTimeZone(TimeZone.getTimeZone("Asia/Seoul"));
    return f.format(new Date(ms));
  }
}
