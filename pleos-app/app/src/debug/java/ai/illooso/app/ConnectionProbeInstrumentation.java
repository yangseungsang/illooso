package ai.illooso.app;

import android.app.Activity;
import android.app.Instrumentation;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.display.DisplayManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.SystemClock;
import android.util.Log;
import android.view.Display;

import ai.pleos.playground.navi.data.DestinationInfo;
import ai.pleos.playground.navi.helper.NaviHelper;
import ai.pleos.playground.navi.helper.listener.NaviHelperEventListener;
import kotlin.Unit;

/** Debug-only native service probe. Read-only unless explicitly passed -e openMap true. */
public final class ConnectionProbeInstrumentation extends Instrumentation {
    private static final String TAG="IlloosoProbe";
    private final Handler main=new Handler(Looper.getMainLooper());
    private PleosBridge bridge;
    private NaviHelper destinationProbe;
    private PleosBridge.State latest;
    private boolean finished, destinationResponded;
    private boolean openMap, mapAttempted, mapRequested;
    private String destinationName="";
    private long beganAt;
    private final Runnable connectionTimeout=()->complete(false,
            "Timed out waiting for actual connection, UX policy and destination response");
    private final Runnable mapTimeout=()->complete(false,
            "Timed out waiting for navigation screen request submission; visibility was not verified");
    private final NaviHelperEventListener destinationListener=new NaviHelperEventListener(){
        @Override public void onDestinationInfo(DestinationInfo info){
            main.post(()->{
                if(finished)return;
                destinationResponded=true;
                destinationName=info==null||info.getName()==null?"":info.getName();
                Log.i(TAG,"Actual onDestinationInfo response; hasName="+!destinationName.isEmpty());
                completeIfReady();
            });
        }
    };

    @Override public void onCreate(Bundle arguments){
        super.onCreate(arguments);
        // am instrument -e options are strings. No option means strictly read-only.
        openMap=arguments!=null&&"true".equalsIgnoreCase(arguments.getString("openMap","false"));
        start();
    }

    @Override public void onStart(){
        super.onStart();
        // Return immediately; all asynchronous SDK work runs on the app main
        // looper without blocking Instrumentation startup or scanning classes.
        main.post(this::begin);
    }

    private void begin(){
        beganAt=SystemClock.elapsedRealtime();
        Log.i(TAG,"Starting direct framework instrumentation; openMap="+openMap+"; no route mutation");
        Context target=getTargetContext();
        if(target.checkSelfPermission(PleosBridge.READ_PERMISSION)!=PackageManager.PERMISSION_GRANTED){
            complete(false,"NAVI_ROUTE permission not granted");return;
        }
        if(openMap&&target.checkSelfPermission(PleosBridge.MAP_PERMISSION)!=PackageManager.PERMISSION_GRANTED){
            complete(false,"NAVI_CUSTOM_MAP permission not granted");return;
        }
        DisplayManager displays=(DisplayManager)target.getSystemService(Context.DISPLAY_SERVICE);
        Display display=displays==null?null:displays.getDisplay(Display.DEFAULT_DISPLAY);
        if(display==null){complete(false,"Default vehicle display unavailable");return;}
        Context displayContext=target.createDisplayContext(display);
        main.postDelayed(connectionTimeout,15000);
        try {
            bridge=new PleosBridge(displayContext,state->{
                if(finished)return;
                latest=state;
                completeIfReady();
            });
            bridge.start();
            if(finished)return;
            destinationProbe=new NaviHelper(displayContext);
            destinationProbe.initialize(()->{
                main.post(()->{
                    if(finished)return;
                    try {
                        destinationProbe.addListener(destinationListener);
                        destinationProbe.getDestinationInfo();
                    } catch(RuntimeException|LinkageError error){complete(false,"Destination query failed: "+error.getClass().getSimpleName());}
                });
                return Unit.INSTANCE;
            },error->{
                main.post(()->complete(false,"Destination probe connection failed: "+error.getClass().getSimpleName()));
                return Unit.INSTANCE;
            });
        } catch(RuntimeException|LinkageError error){
            Log.e(TAG,"Probe initialization failed",error);
            complete(false,"Initialization failed: "+error.getClass().getSimpleName());
        }
    }

    private void completeIfReady(){
        if(finished||latest==null)return;
        if(!"idle".equals(latest.routeStatus)){
            complete(false,"Unexpected route mutation/status: "+latest.routeStatus);return;
        }
        if(mapAttempted){
            if("error".equals(latest.navigationMapStatus)){
                complete(false,"Navigation screen request failed: "+latest.navigationMapMessage);
            } else if("requested".equals(latest.navigationMapStatus)){
                mapRequested=true;
                complete(true,"");
            }
            return;
        }
        if(!latest.connected||!latest.policyKnown||!destinationResponded)return;
        if(!openMap){complete(true,"");return;}
        if(latest.drivingRestricted){
            complete(false,"Navigation screen request blocked: vehicle is not confirmed parked");return;
        }
        main.removeCallbacks(connectionTimeout);
        main.postDelayed(mapTimeout,10000);
        // openNavigationMap can synchronously emit State, so set this before calling it.
        mapAttempted=true;
        try {
            bridge.openNavigationMap();
        } catch(RuntimeException|LinkageError error){
            complete(false,"Navigation screen request threw: "+error.getClass().getSimpleName());
        }
    }

    private void complete(boolean success,String error){
        if(finished)return;
        finished=true;
        main.removeCallbacksAndMessages(null);
        Bundle evidence=new Bundle();
        evidence.putBoolean("success",success);
        evidence.putBoolean("connected",latest!=null&&latest.connected);
        evidence.putBoolean("policyKnown",latest!=null&&latest.policyKnown);
        evidence.putBoolean("drivingRestricted",latest==null||latest.drivingRestricted);
        evidence.putBoolean("destinationResponded",destinationResponded);
        evidence.putBoolean("destinationAvailable",latest!=null&&latest.destinationAvailable);
        evidence.putString("destinationName",destinationName);
        evidence.putString("routeStatus",latest==null?"unknown":latest.routeStatus);
        evidence.putBoolean("openMap",openMap);
        evidence.putBoolean("mapAttempted",mapAttempted);
        evidence.putBoolean("mapRequested",mapRequested);
        evidence.putBoolean("visibleMapVerified",false);
        evidence.putString("navigationMapStatus",latest==null?"unknown":latest.navigationMapStatus);
        evidence.putString("navigationMapMessage",latest==null?"":latest.navigationMapMessage);
        evidence.putLong("elapsedMs",SystemClock.elapsedRealtime()-beganAt);
        evidence.putString("error",error);
        String summary=(success?"PASS":"FAIL")+" real SDK connected="+evidence.getBoolean("connected")
                +", policyKnown="+evidence.getBoolean("policyKnown")
                +", drivingRestricted="+evidence.getBoolean("drivingRestricted")
                +", destinationResponded="+destinationResponded
                +", destinationAvailable="+evidence.getBoolean("destinationAvailable")
                +", route="+evidence.getString("routeStatus")
                +", openMap="+openMap
                +", mapRequested="+mapRequested
                +", visibleMapVerified=false"
                +", elapsedMs="+evidence.getLong("elapsedMs")
                +(error.isEmpty()?"":"; "+error);
        evidence.putString("stream","\n"+summary+"\n");
        Log.i(TAG,summary);
        if(destinationProbe!=null){
            try{destinationProbe.removeListener(destinationListener);}catch(RuntimeException|LinkageError ignored){}
            try{destinationProbe.release();}catch(RuntimeException|LinkageError ignored){}
            destinationProbe=null;
        }
        if(bridge!=null){bridge.close();bridge=null;}
        finish(success?Activity.RESULT_OK:Activity.RESULT_CANCELED,evidence);
    }
}
