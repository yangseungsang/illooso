# Native navigation bridge

`PleosBridge.java` is Java 17 code against NaviHelper 3.0.5 and navi-data 3.0.4. It contains no simulated navigation, destination or vehicle policy.

## Integration

- Call `start()` from the visible Activity lifecycle. Call `close()` when its real connection is no longer needed.
- The listener runs on the main thread; snapshots are immutable. Avoid rebuilding an in-progress input dialog on every position/heartbeat snapshot.
- `State.destinationName/latitude/longitude` describe the preserved source destination. They are distinct from both the app's manually selected final destination and the latest native navigation target.
- `State.nativeDestinationAvailable`, `nativeDestinationName`, `nativeDestinationLatitude`, and `nativeDestinationLongitude` describe the actual latest destination reported by the navigator, including a pickup point sent by this app. Use these fields for an explicit **import native destination** action.
- `currentLatitude/currentLongitude` are vehicle coordinates and are valid only when `hasCurrentLocation` is true. Location timestamps are monotonic **receipt** times, not sensor fix times. An app-side location callback is not a prerequisite for `requestRoute()`: the native navigator owns the route origin, and the request carries the selected destination coordinates. Native failures remain errors; a missing location is never replaced with an invented vehicle position.
- Permission denial, missing Navi Service, no destination, disconnection, native route errors and request timeouts remain visible states. No default location or fake success is injected.
- `start()` may be retried after the Activity obtains permissions. Granting permissions is the Activity's responsibility.
- Treat `!policyKnown || drivingRestricted` as a lock for search, text input, report editing and destination requests. The bridge also rejects native route requests in this condition.
- `requestRoute()` returning does not mean success. A request becomes `active` only after an SDK destination response matches the requested coordinate and either `onRoutingCompleted()` arrives or a fresh route-state response reports `NaviMode.ROUTE_GUIDING` after the target match. An unanswered request becomes `unconfirmed` after 25 seconds; silence is not a confirmed routing failure.
- Switching native navigation to our pickup does not change the captured original destination. A genuinely different external destination can replace it.
- `close()` only removes listeners and releases client bindings; it does **not** cancel the vehicle's active route.

## Explicit import and route handoff

`MainActivity` keeps the final destination separate from the meeting/pickup point. Its destination search can choose a locally registered POI or explicitly import the current native destination. Applying a new final destination changes the app's surrounding-place search; it does not itself change native navigation.

For an explicit import, retain `State.destinationResponseVersion`, call `start()` / `refreshDestination()`, and wait for a strictly larger version. The version increments on every `onDestinationInfo` response, including an empty or invalid destination, and does not reset on reconnect or close. `destinationReceivedAtMs` is the monotonic receipt time of that response. Raw destination fields and their receipt time are cleared on disconnect, navigation inactivity, cancellation, and close.

A fresh response with `nativeDestinationAvailable == false` completes the lookup as **no usable native destination**. The app offers direct selection instead of silently using its default POI. Permission denial and connection problems remain visible, and an unanswered explicit import returns a retryable message after 10 seconds. Closing the destination panel, stopping the Activity, or losing permitted vehicle UX state cancels the pending import. SDK responses contain no correlation ID: a larger version proves a newly received response, not that it was uniquely caused by that individual query.

The main place-card action, **이곳으로 내비 안내**, now calls the real handoff path. It sends the selected pickup point's name and coordinates, not the final destination's coordinates. `MainActivity` checks current place availability at the send boundary, and the bridge independently checks connection, permissions, and parked UX policy. While the request is pending, destination/place changes are blocked. Browsing another destination afterward detaches the app's local display from the previous request without cancelling any native route.

`requestRoute()` submission is not route success. The bridge remains `requesting` until it observes the matching native target and route completion evidence. Evidence is either `onRoutingCompleted()` or a new `RouteStateInfo` response with `NaviMode.ROUTE_GUIDING` received after that target match. The first target match triggers an explicit route-state query; earlier route-state snapshots are not reused. `SIMULATION_GUIDING`, a legacy `onRouteEnded()`, and method submission do not establish success. `ROUTE_DRIVE` navigation-status events trigger a fresh query rather than independently marking the request complete.

The fallback is needed because the [official event guide](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/event-listeners) states that delivered callbacks vary by navigation implementation. The shipped navi-data 3.0.4 exposes `RouteStateInfo.getNaviMode()` and distinct `ROUTE_GUIDING` / `SIMULATION_GUIDING` values. As with destination queries, snapshots have no request correlation ID; the bridge checks the current matching target and new receipt ordering.

An actual SDK route error remains `error`. A 25-second acknowledgement timeout or connection loss after submission becomes `unconfirmed`, with an action to inspect the native navigator rather than a claim that routing failed or an automatic duplicate request. Fresh target-and-guidance evidence can later change `unconfirmed` to `active`. Target mismatch, invalid destination, and disconnection clear cached guidance proof. Only confirmed `active` state triggers the optional foreground request for the native navigation screen. `requestNavigationScreen()` has no screen-visible acknowledgement; accepting that call does not prove that the map became visible. The local meeting preview remains a separate action and never establishes native routing success.

Replacing an existing route can emit `onRouteCancelled()` for the previous route before the new one is ready. While the app's replacement is still `requesting` or `unconfirmed`, that uncorrelated event clears old confirmation evidence and triggers a new query, but preserves the new target and timeout. It must not reset the request to `idle`. If the user actually cancels that pending route, the app retains an unconfirmed outcome until fresh evidence; the event alone cannot identify which request was cancelled. Cancellation after confirmed `active` guidance still ends the tracked flow.

## Required platform configuration

```xml
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE" />
<uses-permission android:name="pleos.car.permission.NAVI_ROUTE_SEARCH" />
<uses-permission android:name="pleos.car.permission.NAVI_CUSTOM_MAP" />
<!-- inside application -->
<uses-library android:name="android.car" android:required="false" />
```

Compile with Android platform 35 and `useLibrary 'android.car', false`. On Pleos emulator `release_connect_v2.1.0`, Android Car APIs are provided by the boot-classpath module `/apex/com.android.car.framework/javalib/android.car-module.jar`, rather than a PackageManager shared library named `android.car`. Requiring that shared-library name prevents installation even though the typed Car APIs exist. Keep the manifest declaration optional; do not substitute the different `ai.pleos.playground.car` vendor-constants library. The normal Gradle manifest merger includes NaviHelper's `ai.pleos.playground.intent.action.NAVI_START` query and SDK compatibility metadata from its AAR. Keep those merged declarations intact.

The Maven dependency brings navi-data 3.0.4, navi-aidl 3.0.1 and Kotlin runtime dependencies. The adjacent reference project's artifact set uses Kotlin stdlib 1.9.25, coroutines 1.6.3 and serialization 1.6.3.

## RouteInfo contract verification

The published 3.0.4 AAR exposes one ordinary Java constructor with **11** arguments:

```java
new RouteInfo(latitude, longitude, displayLatitude, displayLongitude,
    poiName, poiId, address, poiSubId, routeOption, poiProvider, isMobileRequest);
```

Its bytecode assigns the first `double` to `latitude` and second to `longitude`. The guide's older Java sample reverses the numeric example and uses fewer parameters, so the implementation follows the shipped AAR and its typed getters. Blank POI identifiers are deliberately used for coordinate-only pickup points instead of inventing provider IDs; the native navigator determines whether it accepts them. Its errors/timeouts are surfaced.

## Sources and validation

- [NaviHelper lifecycle and event sequence](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/intro)
- [Destination requests and completion callbacks](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/destinations)
- [Required navigation permissions](https://document.pleos.ai/en/docs/pleos-only/connect-sdk-pleos/NaviHelper/declaring-permissions)
- [AOSP Car UX restrictions](https://source.android.com/docs/automotive/driver_distraction/consume)

The SDK constructor contract was checked against Android 35 `android.jar`, `android.car.jar`, and the actual NaviHelper/navi-data AAR classes. Device runtime validation is a separate integration step; a successful build or connected SDK alone does not prove the native provider accepted a route. Per-version AVD observations belong in the `verification/` records; this contract document does not claim successful navigation for the new handoff flow.

## Pleos emulator library diagnosis

The running `emulator-5582` boot classpath and its Android Car module DEX were inspected read-only. The module contains `android.car.Car`, `android.car.drivingstate.CarUxRestrictions`, and `CarUxRestrictionsManager`. The `ai.pleos.playground.car.jar` shared library contains vendor property constants, not replacements for these classes. Therefore no reflection or Vehicle SDK gear inference is needed. The [official Pleos driving restriction guide](https://document.pleos.ai/en/docs/pleos-only/vehicle-app-developer-guide/driving-restriction-development-guide) also specifies the AAOS CarUxRestrictionsManager API.
