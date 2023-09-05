package org.apache.cordova.firebase;

import android.app.NotificationManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.net.Uri;
import android.text.TextUtils;
import android.util.Base64;
import android.util.Log;
import android.widget.Toast;
import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.analytics.FirebaseAnalytics;
import com.google.firebase.iid.FirebaseInstanceId;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.remoteconfig.FirebaseRemoteConfig;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigInfo;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigSettings;
import com.google.firebase.remoteconfig.FirebaseRemoteConfigValue;
import com.google.firebase.perf.FirebasePerformance;
import com.google.firebase.perf.metrics.Trace;
import me.leolin.shortcutbadger.ShortcutBadger;
import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PluginResult;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import android.app.Activity;

import androidx.annotation.NonNull;
import androidx.core.app.NotificationManagerCompat;

import java.util.Arrays;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executor;

// Firebase PhoneAuth
import java.util.concurrent.TimeUnit;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.AuthResult;
import com.google.firebase.FirebaseException;
import com.google.firebase.auth.FirebaseAuthException;
import com.google.firebase.auth.FirebaseAuthInvalidCredentialsException;
import com.google.firebase.FirebaseTooManyRequestsException;
import com.google.firebase.auth.PhoneAuthCredential;
import com.google.firebase.auth.PhoneAuthProvider;

// Crashlytics
import com.google.firebase.crashlytics.FirebaseCrashlytics;

// Dynamic Links
import com.google.firebase.dynamiclinks.DynamicLink;
import com.google.firebase.dynamiclinks.FirebaseDynamicLinks;
import com.google.firebase.dynamiclinks.PendingDynamicLinkData;

//Import new
import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseError;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import com.google.firebase.database.ValueEventListener;
import org.apache.cordova.firebase.Review;

import com.google.firebase.messaging.RemoteMessage;

//import amazonia.iu.com.amlibrary.client.IUApp;

public class FirebasePlugin extends CordovaPlugin {

  private FirebaseAnalytics mFirebaseAnalytics;
  private FirebaseAuth mAuth;
  private static final String TAG = "FirebasePlugin";
  protected static final String KEY = "badge";

  private static boolean inBackground = true;
  private static ArrayList<Bundle> notificationStack = null;
  private static CallbackContext notificationCallbackContext;
  private static CallbackContext tokenRefreshCallbackContext;
  private static CallbackContext dynamicLinkCallback;

  @Override
  protected void pluginInitialize() {

    //IUApp.init(this.cordova.getActivity().getApplication(), this.cordova.getClass());//inicia el plugin de DF
    
    final Context context = this.cordova.getActivity().getApplicationContext();
    final Bundle extras = this.cordova.getActivity().getIntent().getExtras();
    this.cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        Log.d(TAG, "Starting Firebase plugin");
        mFirebaseAnalytics = FirebaseAnalytics.getInstance(context);
        mAuth= FirebaseAuth.getInstance();
        mFirebaseAnalytics.setAnalyticsCollectionEnabled(true);
        if (extras != null && extras.size() > 1) {
          if (FirebasePlugin.notificationStack == null) {
            FirebasePlugin.notificationStack = new ArrayList<Bundle>();
          }
          if (extras.containsKey("google.message_id")) {
            extras.putBoolean("tap", true);
            notificationStack.add(extras);
          }
        }
        
      }
    });
  
  }

  @Override
  public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
    if (action.equals("getId")) {
      this.getId(callbackContext);
      return true;      
    } else if (action.equals("getToken")) {
      this.getToken(callbackContext);
      return true;
    } else if (action.equals("hasPermission")) {
      this.hasPermission(callbackContext);
      return true;
    } else if (action.equals("setBadgeNumber")) {
      this.setBadgeNumber(callbackContext, args.getInt(0));
      return true;
    } else if (action.equals("getBadgeNumber")) {
      this.getBadgeNumber(callbackContext);
      return true;
    } else if (action.equals("subscribe")) {
      this.subscribe(callbackContext, args.getString(0));
      return true;
    } else if (action.equals("unsubscribe")) {
      this.unsubscribe(callbackContext, args.getString(0));
      return true;
    } else if (action.equals("unregister")) {
      this.unregister(callbackContext);
      return true;
    } else if (action.equals("onNotificationOpen")) {
      this.onNotificationOpen(callbackContext);
      return true;
    } else if (action.equals("onTokenRefresh")) {
      this.onTokenRefresh(callbackContext);
      return true;
    } else if (action.equals("logEvent")) {
      this.logEvent(callbackContext, args.getString(0), args.getJSONObject(1));
      return true;
    } else if (action.equals("logError")) {
      this.logError(callbackContext, args.getString(0));
      return true;
    } else if (action.equals("setCrashlyticsUserId")) {
      this.setCrashlyticsUserId(callbackContext, args.getString(0));
      return true;
    } else if (action.equals("setScreenName")) {
      this.setScreenName(callbackContext, args.getString(0));
      return true;
    } else if (action.equals("setUserId")) {
      this.setUserId(callbackContext, args.getString(0));
      return true;
    } else if (action.equals("setUserProperty")) {
      this.setUserProperty(callbackContext, args.getString(0), args.getString(1));
      return true;
    } else if (action.equals("activateFetched")) {
      this.activateFetched(callbackContext);
      return true;
    } else if (action.equals("fetch")) {
      if (args.length() > 0) this.fetch(callbackContext, args.getLong(0));
      else this.fetch(callbackContext);
      return true;
    } else if (action.equals("getValue")) {
      if (args.length() > 1) this.getValue(callbackContext, args.getString(0), args.getString(1));
      else this.getValue(callbackContext, args.getString(0), null);
      return true;
    } else if (action.equals("getInfo")) {
      this.getInfo(callbackContext);
      return true;
    } else if (action.equals("setConfigSettings")) {
      this.setConfigSettings(callbackContext, args.getJSONObject(0));
      return true;
    } else if (action.equals("setDefaults")) {
      if (args.length() > 1) this.setDefaults(callbackContext, args.getJSONObject(0), args.getString(1));
      else this.setDefaults(callbackContext, args.getJSONObject(0), null);
      return true;
    } else if (action.equals("startTrace")) {
      this.startTrace(callbackContext, args.getString(0));
      return true;
    } else if (action.equals("incrementCounter")) {
      this.incrementCounter(callbackContext, args.getString(0), args.getString(1));
      return true;
    } else if (action.equals("stopTrace")) {
      this.stopTrace(callbackContext, args.getString(0));
      return true;
    } else if (action.equals("addTraceAttribute")) {
      this.addTraceAttribute(callbackContext, args.getString(0), args.getString(1), args.getString(2));
      return true;  
    } else if (action.equals("forceCrashlytics")) {
      this.forceCrashlytics(callbackContext);
      return true;
    } else if (action.equals("setPerformanceCollectionEnabled")) {
      this.setPerformanceCollectionEnabled(callbackContext, args.getBoolean(0));
      return true;
    } else if (action.equals("setAnalyticsCollectionEnabled")) {
      this.setAnalyticsCollectionEnabled(callbackContext, args.getBoolean(0));
      return true;
    } else if (action.equals("onDynamicLink")) {
      this.onDynamicLink(callbackContext);
      return true;
    } else if (action.equals("clearAllNotifications")) {
      this.clearAllNotifications(callbackContext);
      return true;
    }else if (action.equals("writeReviews")){
     this.writeReviews(callbackContext, args.getString(0), args.getString(1),args.getString(2));
     return true;
    }else if(action.equals("authCustomToken")){
      this.authCustomToken(callbackContext,args.getString(0));
      return true;
    }else if(action.equals("writeReviewsWithComment")){
      this.writeReviewsWithComment(callbackContext, args.getString(0), args.getString(1),args.getString(2), args.getString(3));
      return true;
    }else if(action.equals("signOut")){
     this.signOut(callbackContext);
     return true;
    }else if(action.equals("writeUsers")){
     this.writeUsers(callbackContext, args.getString(0), args.getString(1),args.getString(2));
     return true;
    }else if(action.equals("userExist")){
     this.userExist(callbackContext, args.getString(0));
     return true;
    }else if(action.equals("writeDate")){
     this.writeDate(callbackContext, args.getString(0), args.getString(1));
     return true;
    }else if(action.equals("validateLastUserReview")){
     this.validateLastUserReview(callbackContext, args.getString(0));
     return true;
    }
    return false;
  }

  @Override
  public void onPause(boolean multitasking) {
    FirebasePlugin.inBackground = true;
  }

  @Override
  public void onResume(boolean multitasking) {
    FirebasePlugin.inBackground = false;
  }

  @Override
  public void onReset() {
    FirebasePlugin.notificationCallbackContext = null;
    FirebasePlugin.tokenRefreshCallbackContext = null;
  }

  @Override
  public void onNewIntent(Intent intent) {
    super.onNewIntent(intent);
    final Bundle data = intent.getExtras();
    if (this.dynamicLinkCallback != null) {
      respondWithDynamicLink(intent);
    }
    if (data != null && data.containsKey("google.message_id")) {
      data.putBoolean("tap", true);
      FirebasePlugin.sendNotification(data, this.cordova.getActivity().getApplicationContext());
    }
  }

  public static boolean inBackground() {
    return FirebasePlugin.inBackground;
  }

  public static boolean hasNotificationsCallback() {
    return FirebasePlugin.notificationCallbackContext != null;
  }

  //
  // Cloud Messaging FCM
  //
  public static void sendNotification(Bundle bundle, Context context) {
    Log.d(TAG, "sendNotification called");
    if (!FirebasePlugin.hasNotificationsCallback()) {
      if (FirebasePlugin.notificationStack == null) {
        FirebasePlugin.notificationStack = new ArrayList<Bundle>();
      }
      notificationStack.add(bundle);

      Log.d(TAG, "sendNotification notificationStack.add");
      return;
    }

    final CallbackContext callbackContext = FirebasePlugin.notificationCallbackContext;
    if (callbackContext != null && bundle != null) {
      JSONObject json = new JSONObject();
      Set<String> keys = bundle.keySet();
      for (String key : keys) {
        try {
          json.put(key, bundle.get(key));
        } catch (JSONException e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
          return;
        }
      }

      PluginResult pluginresult = new PluginResult(PluginResult.Status.OK, json);
      pluginresult.setKeepCallback(true);
      callbackContext.sendPluginResult(pluginresult);
      Log.d(TAG, "sendNotification success");
    }
  }

  public static void sendToken(String token) {
    Log.d(TAG, "sendToken called");
    if (FirebasePlugin.tokenRefreshCallbackContext == null) {
      Log.d(TAG, "sendToken tokenRefreshCallbackContext null");
      return;
    }

    final CallbackContext callbackContext = FirebasePlugin.tokenRefreshCallbackContext;
    if (callbackContext != null && token != null) {
      PluginResult pluginresult = new PluginResult(PluginResult.Status.OK, token);
      pluginresult.setKeepCallback(true);
      callbackContext.sendPluginResult(pluginresult);
      Log.d(TAG, "sendToken success. token: " + token);
    }
  }

  private void onNotificationOpen(final CallbackContext callbackContext) {
    Log.d(TAG, "onNotificationOpen called");
    FirebasePlugin.notificationCallbackContext = callbackContext;
    if (FirebasePlugin.notificationStack != null) {
      for (Bundle bundle : FirebasePlugin.notificationStack) {
        FirebasePlugin.sendNotification(bundle, this.cordova.getActivity().getApplicationContext());
        Log.d(TAG, "onNotificationOpen sendNotification");
      }
      FirebasePlugin.notificationStack.clear();
      Log.d(TAG, "onNotificationOpen notificationStack.clear");
    }
  }

  private void onTokenRefresh(final CallbackContext callbackContext) {
    Log.d(TAG, "onTokenRefresh called");
    FirebasePlugin.tokenRefreshCallbackContext = callbackContext;

    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          String currentToken = FirebaseInstanceId.getInstance().getToken();
          if (currentToken != null) {
            FirebasePlugin.sendToken(currentToken);
            Log.d(TAG, "onTokenRefresh success. token: " + currentToken);
          }
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void getId(final CallbackContext callbackContext) {
    Log.d(TAG, "getId called");
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          String id = FirebaseInstanceId.getInstance().getId();
          callbackContext.success(id);
          Log.d(TAG, "getId success. id: " + id);
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void getToken(final CallbackContext callbackContext) {
    Log.d(TAG, "getToken called");
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          String token = FirebaseInstanceId.getInstance().getToken();
          callbackContext.success(token);
          Log.d(TAG, "getToken success. token: " + token);
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void hasPermission(final CallbackContext callbackContext) {
    Log.d(TAG, "hasPermission called");
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          Context context = cordova.getActivity();
          NotificationManagerCompat notificationManagerCompat = NotificationManagerCompat.from(context);
          boolean areNotificationsEnabled = notificationManagerCompat.areNotificationsEnabled();
          JSONObject object = new JSONObject();
          object.put("isEnabled", areNotificationsEnabled);
          callbackContext.success(object);
          Log.d(TAG, "hasPermission success. areEnabled: " + (areNotificationsEnabled ? "true" : "false"));
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void setBadgeNumber(final CallbackContext callbackContext, final int number) {
    Log.d(TAG, "setBadgeNumber called. number: " + Integer.toString(number));
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          Context context = cordova.getActivity();
          SharedPreferences.Editor editor = context.getSharedPreferences(KEY, Context.MODE_PRIVATE).edit();
          editor.putInt(KEY, number);
          editor.apply();
          ShortcutBadger.applyCount(context, number);
          callbackContext.success();
          Log.d(TAG, "setBadgeNumber success");
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void getBadgeNumber(final CallbackContext callbackContext) {
    Log.d(TAG, "getBadgeNumber called");
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          Context context = cordova.getActivity();
          SharedPreferences settings = context.getSharedPreferences(KEY, Context.MODE_PRIVATE);
          int number = settings.getInt(KEY, 0);
          callbackContext.success(number);
          Log.d(TAG, "getBadgeNumber success. number: " + Integer.toString(number));
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }
  
  private void subscribe(final CallbackContext callbackContext, final String topic) {
    Log.d(TAG, "subscribe called. topic: " + topic);
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          FirebaseMessaging.getInstance().subscribeToTopic(topic);
          callbackContext.success();
          Log.d(TAG, "subscribe success");
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void unsubscribe(final CallbackContext callbackContext, final String topic) {
    Log.d(TAG, "unsubscribe called. topic: " + topic);
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          FirebaseMessaging.getInstance().unsubscribeFromTopic(topic);
          callbackContext.success();
          Log.d(TAG, "unsubscribe success");
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }
  
  private void unregister(final CallbackContext callbackContext) {
    Log.d(TAG, "unregister called");
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          FirebaseInstanceId.getInstance().deleteInstanceId();
          String currentToken = FirebaseInstanceId.getInstance().getToken();
          if (currentToken != null) {
            FirebasePlugin.sendToken(currentToken);
          }
          callbackContext.success();
          Log.d(TAG, "unregister success. currentToken: " + currentToken);
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void clearAllNotifications(final CallbackContext callbackContext) {
    Log.d(TAG, "clearAllNotifications called");
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          Context context = cordova.getActivity();
          NotificationManager nm = (NotificationManager) context.getSystemService(Context.NOTIFICATION_SERVICE);
          nm.cancelAll();
          callbackContext.success();
          Log.d(TAG, "clearAllNotifications success");
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().log(e.getMessage());
        }
      }
    });
  }

    //
  // Dynamic Links
  //
  private void onDynamicLink(final CallbackContext callbackContext) {
    this.dynamicLinkCallback = callbackContext;

    respondWithDynamicLink(cordova.getActivity().getIntent());
  }

  private void respondWithDynamicLink(Intent intent) {
    FirebaseDynamicLinks.getInstance().getDynamicLink(intent)
      .addOnSuccessListener(cordova.getActivity(), new OnSuccessListener<PendingDynamicLinkData>() {
        @Override
        public void onSuccess(PendingDynamicLinkData pendingDynamicLinkData) {
          if (pendingDynamicLinkData != null) {
            Uri deepLink = pendingDynamicLinkData.getLink();

            if (deepLink != null) {
              JSONObject response = new JSONObject();
              try {
                response.put("deepLink", deepLink);
                response.put("clickTimestamp", pendingDynamicLinkData.getClickTimestamp());

                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, response);
                pluginResult.setKeepCallback(true);
                dynamicLinkCallback.sendPluginResult(pluginResult);

                doOnDynamicLink(deepLink.toString());
              } catch (JSONException e) {
				        PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, (String)null);
                dynamicLinkCallback.sendPluginResult(pluginResult);
                Log.e(TAG, "Fail to handle dynamic link data", e);
              }
            }else{
              PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, (String)null);
              dynamicLinkCallback.sendPluginResult(pluginResult);
            }
          }else{
            PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, (String)null);
            dynamicLinkCallback.sendPluginResult(pluginResult);
          }
        }
      });
  }

  private void doOnDynamicLink(final String dynamicLink) {
    cordova.getActivity().runOnUiThread(new Runnable() {
      @Override
      public void run() {
        String method = String.format("javascript:window.fp.dynamicLinkCallback( '%s' );", dynamicLink );;
        webView.loadUrl(method);
      }
    });
  }

  // 
  // Analytics
  //
  private void logEvent(final CallbackContext callbackContext, final String name, final JSONObject params) throws JSONException {
    Log.d(TAG, "logEvent called. name: " + name);
    final Bundle bundle = new Bundle();
    Iterator iter = params.keys();
    while (iter.hasNext()) {
      String key = (String) iter.next();
      Object value = params.get(key);

      if (value instanceof Integer || value instanceof Double) {
        bundle.putFloat(key, ((Number) value).floatValue());
      } else {
        bundle.putString(key, value.toString());
      }
    }

    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          mFirebaseAnalytics.logEvent(name, bundle);
          callbackContext.success();
          Log.d(TAG, "logEvent success");
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void setScreenName(final CallbackContext callbackContext, final String name) {
    Log.d(TAG, "setScreenName called. name: " + name);
    cordova.getActivity().runOnUiThread(new Runnable() {
      public void run() {
        try {
          mFirebaseAnalytics.setCurrentScreen(cordova.getActivity(), name, null);
          callbackContext.success();
          Log.d(TAG, "setScreenName success");
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void setUserId(final CallbackContext callbackContext, final String id) {
    Log.d(TAG, "setUserId called. id: " + id);
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          mFirebaseAnalytics.setUserId(id);
          callbackContext.success();
          Log.d(TAG, "setUserId success");
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void setUserProperty(final CallbackContext callbackContext, final String name, final String value) {
    Log.d(TAG, "setUserProperty called. name: " + name + " value: " + value);
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          mFirebaseAnalytics.setUserProperty(name, value);
          callbackContext.success();
          Log.d(TAG, "setUserProperty success");
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void setAnalyticsCollectionEnabled(final CallbackContext callbackContext, final boolean enabled) {
    Log.d(TAG, "setAnalyticsCollectionEnabled called. enabled: " + (enabled ? "true" : "false"));
    final FirebasePlugin self = this;
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          mFirebaseAnalytics.setAnalyticsCollectionEnabled(enabled);
          callbackContext.success();
          Log.d(TAG, "setAnalyticsCollectionEnabled success");
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().log(e.getMessage());
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  // 
  // Performance monitoring
  //
  private HashMap<String,Trace> traces = new HashMap<String,Trace>();

  private void startTrace(final CallbackContext callbackContext, final String name) {
    Log.d(TAG, "startTrace called. name: " + name);
    final FirebasePlugin self = this;
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          Trace myTrace = null;
          if (self.traces.containsKey(name)) {
            myTrace = self.traces.get(name);
          }
          if (myTrace == null) {
            myTrace = FirebasePerformance.getInstance().newTrace(name);
            myTrace.start();
            self.traces.put(name, myTrace);
          }
          callbackContext.success();
          Log.d(TAG, "startTrace success");
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void incrementCounter(final CallbackContext callbackContext, final String name, final String counterNamed) {
    Log.d(TAG, "incrementCounter called. name: " + name + " counterNamed: " + counterNamed);
    final FirebasePlugin self = this;
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          Trace myTrace = null;
          if (self.traces.containsKey(name)) {
            myTrace = self.traces.get(name);
          }
          if (myTrace != null && myTrace instanceof Trace) {
            //myTrace.incrementCounter(counterNamed);
            myTrace.incrementMetric(counterNamed, 1);
            callbackContext.success();
            Log.d(TAG, "incrementCounter success");
          } else {
            callbackContext.error("Trace not found");
            Log.d(TAG, "incrementCounter trace not found");
          }
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void stopTrace(final CallbackContext callbackContext, final String name) {
    Log.d(TAG, "stopTrace called. name: " + name);
    final FirebasePlugin self = this;
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          Trace myTrace = null;
          if (self.traces.containsKey(name)) {
            myTrace = self.traces.get(name);
          }
          if (myTrace != null && myTrace instanceof Trace) {
            myTrace.stop();
            self.traces.remove(name);
            callbackContext.success();
            Log.d(TAG, "stopTrace success");
          } else {
            callbackContext.error("Trace not found");
            Log.d(TAG, "stopTrace trace not found");
          }
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void addTraceAttribute(final CallbackContext callbackContext, final String traceName, final String attribute, final String value) {
    Log.d(TAG, "addTraceAttribute called. traceName: " + traceName + " attribute: " + attribute + " value: " + value);
    final FirebasePlugin self = this;
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          Trace myTrace = null;
          if (self.traces.containsKey(traceName)) {
            myTrace = self.traces.get(traceName);
          }
          if (myTrace != null && myTrace instanceof Trace) {
            myTrace.putAttribute(attribute, value);
            callbackContext.success();
            Log.d(TAG, "addTraceAttribute success");
          } else {
            callbackContext.error("Trace not found");
            Log.d(TAG, "addTraceAttribute trace not found");
          }
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().log(e.getMessage());
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void setPerformanceCollectionEnabled(final CallbackContext callbackContext, final boolean enabled) {
    Log.d(TAG, "setPerformanceCollectionEnabled called. enabled: " + (enabled ? "true" : "false"));
    final FirebasePlugin self = this;
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          FirebasePerformance.getInstance().setPerformanceCollectionEnabled(enabled);
          callbackContext.success();
          Log.d(TAG, "setPerformanceCollectionEnabled success");
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().log(e.getMessage());
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  // 
  // Crashlytics
  //
  private void forceCrashlytics(final CallbackContext callbackContext) {
    Log.d(TAG, "forceCrashlytics called");
    final FirebasePlugin self = this;
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        throw new RuntimeException("Test Crash");
      }
    });
  }
  
  private void logError(final CallbackContext callbackContext, final String message) throws JSONException {
    Log.d(TAG, "logError called. message: " + message);
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          FirebaseCrashlytics.getInstance().recordException(new Exception(message));
          callbackContext.success(1);
          Log.d(TAG, "logError success");
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().log(e.getMessage());
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void setCrashlyticsUserId(final CallbackContext callbackContext, final String userId) {
    Log.d(TAG, "setCrashlyticsUserId called. userId: " + userId);
    cordova.getActivity().runOnUiThread(new Runnable() {
      public void run() {
        try {
          FirebaseCrashlytics.getInstance().setUserId(userId);
          callbackContext.success();
          Log.d(TAG, "setCrashlyticsUserId success");
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  //
  // Remote Configuration
  //
  private void activateFetched(final CallbackContext callbackContext) {
    /*cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          final boolean activated = FirebaseRemoteConfig.getInstance().activateFetched();
          callbackContext.success(String.valueOf(activated));
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });*/
  }

  private void fetch(CallbackContext callbackContext) {
    fetch(callbackContext, FirebaseRemoteConfig.getInstance().fetch());
  }

  private void fetch(CallbackContext callbackContext, long cacheExpirationSeconds) {
    fetch(callbackContext, FirebaseRemoteConfig.getInstance().fetch(cacheExpirationSeconds));
  }

  private void fetch(final CallbackContext callbackContext, final Task<Void> task) {
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          task.addOnSuccessListener(new OnSuccessListener<Void>() {
            @Override
            public void onSuccess(Void data) {
              callbackContext.success();
            }
          }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(Exception e) {
              callbackContext.error(e.getMessage());
            }
          });
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void getValue(final CallbackContext callbackContext, final String key, final String namespace) {
    cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          FirebaseRemoteConfigValue value = FirebaseRemoteConfig.getInstance().getValue(key);
          callbackContext.success(value.asString());
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });
  }

  private void getInfo(final CallbackContext callbackContext) {
    /*cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          FirebaseRemoteConfigInfo remoteConfigInfo = FirebaseRemoteConfig.getInstance().getInfo();
          JSONObject info = new JSONObject();

          JSONObject settings = new JSONObject();
          settings.put("developerModeEnabled", remoteConfigInfo.getConfigSettings().isDeveloperModeEnabled());
          info.put("configSettings", settings);

          info.put("fetchTimeMillis", remoteConfigInfo.getFetchTimeMillis());
          info.put("lastFetchStatus", remoteConfigInfo.getLastFetchStatus());

          callbackContext.success(info);
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });*/
  }

  private void setConfigSettings(final CallbackContext callbackContext, final JSONObject config) {
    /*cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          boolean devMode = config.getBoolean("developerModeEnabled");
          FirebaseRemoteConfigSettings.Builder settings = new FirebaseRemoteConfigSettings.Builder()
              .setDeveloperModeEnabled(devMode);
          FirebaseRemoteConfig.getInstance().setConfigSettings(settings.build());
          callbackContext.success();
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });*/
  }

  private void setDefaults(final CallbackContext callbackContext, final JSONObject defaults, final String namespace) {
    /*cordova.getThreadPool().execute(new Runnable() {
      public void run() {
        try {
          if (namespace == null)
            FirebaseRemoteConfig.getInstance().setDefaults(defaultsToMap(defaults));
          else
            FirebaseRemoteConfig.getInstance().setDefaults(defaultsToMap(defaults));
          callbackContext.success();
        } catch (Exception e) {
          FirebaseCrashlytics.getInstance().recordException(e);
          callbackContext.error(e.getMessage());
        }
      }
    });*/
  }

  private static Map<String, Object> defaultsToMap(JSONObject object) throws JSONException {
    final Map<String, Object> map = new HashMap<String, Object>();

    for (Iterator<String> keys = object.keys(); keys.hasNext(); ) {
      String key = keys.next();
      Object value = object.get(key);

      if (value instanceof Integer) {
        //setDefaults() should take Longs
        value = new Long((Integer) value);
      } else if (value instanceof JSONArray) {
        JSONArray array = (JSONArray) value;
        if (array.length() == 1 && array.get(0) instanceof String) {
          //parse byte[] as Base64 String
          value = Base64.decode(array.getString(0), Base64.DEFAULT);
        } else {
          //parse byte[] as numeric array
          byte[] bytes = new byte[array.length()];
          for (int i = 0; i < array.length(); i++)
            bytes[i] = (byte) array.getInt(i);
          value = bytes;
        }
      }

      map.put(key, value);
    }
    return map;
  }


  /////////////////////
  // Authentication //
  ////////////////////

  public void authCustomToken(final CallbackContext callbackContext,String mCustomToken){
    //FirebaseAuth mAuth= FirebaseAuth.getInstance();
    mAuth.signInWithCustomToken(mCustomToken)
          .addOnCompleteListener(this.cordova.getActivity(), new OnCompleteListener<AuthResult>() {
                @Override
                public void onComplete(@NonNull Task<AuthResult> task) {
                    if (task.isSuccessful()) {
                        Log.d("authCustomToken", "signInWithCustomToken:success");
                        callbackContext.success("signInWithCustomToken:success");
                    } else {
                        Log.w("authCustomToken", "signInWithCustomToken:failure", task.getException());
                        callbackContext.error(task.getException()+"");
                    }
                }
            });
  }

  public void signOut(CallbackContext callbackContext){
    try {
          mAuth.getInstance().signOut();
          callbackContext.success();
        } catch (Exception e) {
          callbackContext.error(e.getMessage());
        }
  }


  ////////////////////////
  // Database Realtime //
  ///////////////////////


public void writeUsers(final CallbackContext callbackContext, String phoneNumber, String registerDate, String documentNumber){
    try{
      DatabaseReference mDatabase;
      mDatabase = FirebaseDatabase.getInstance().getReference().child("users").child(documentNumber);
      Users users = new Users(phoneNumber, registerDate);
      mDatabase.setValue(users, new DatabaseReference.CompletionListener(){
        @Override
        public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference){
          if (databaseError != null){
                System.out.println("Data could not be saved " + databaseError.getMessage());
                callbackContext.error("Data could not be saved " + databaseError.getMessage());
          }else{
                System.out.println("Data saved successfully.");
                callbackContext.success();
                }
            }
      });

    }catch(Exception e){
          Log.e("error",e.getMessage());
          callbackContext.error(e.getMessage());
    }
  }


public void writeDate(final CallbackContext callbackContext, String date, String documentNumber) {    
        try {  
          DatabaseReference mDatabase;
          mDatabase = FirebaseDatabase.getInstance().getReference().child("reviews").child(documentNumber);
          mDatabase.setValue(date, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
              if (databaseError != null) {
                System.out.println("Data could not be saved " + databaseError.getMessage());
                callbackContext.error("Data could not be saved " + databaseError.getMessage());
              } else {
                System.out.println("Data saved successfully.");
                callbackContext.success();
              }
            }
          });
          
        } catch (Exception e) {
          Log.e("error",e.getMessage());
          callbackContext.error(e.getMessage());
        }   
  }


public void writeReviews(final CallbackContext callbackContext, String calification, String date, String documentNumber) {    
        try {  
          DatabaseReference mDatabase;
          mDatabase = FirebaseDatabase.getInstance().getReference().child("reviews").child(documentNumber);
          Review reviews = new Review(calification, date);
          mDatabase.push().setValue(reviews, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
              if (databaseError != null) {
                System.out.println("Data could not be saved " + databaseError.getMessage());
                callbackContext.error("Data could not be saved " + databaseError.getMessage());
              } else {
                System.out.println("Data saved successfully.");
                callbackContext.success();
              }
            }
          });
          
        } catch (Exception e) {
          Log.e("error",e.getMessage());
          callbackContext.error(e.getMessage());
        }    
  }


public void writeReviewsWithComment(final CallbackContext callbackContext, String calification, String comment, String date, String documentNumber) {    
        try { 
          DatabaseReference mDatabase;
          mDatabase = FirebaseDatabase.getInstance().getReference().child("reviews").child(documentNumber);
          Review reviews = new Review(calification, comment, date);
          mDatabase.push().setValue(reviews, new DatabaseReference.CompletionListener() {
            @Override
            public void onComplete(DatabaseError databaseError, DatabaseReference databaseReference) {
              if (databaseError != null) {
                System.out.println("Data could not be saved " + databaseError.getMessage());
                callbackContext.error("Data could not be saved " + databaseError.getMessage());
              } else {
                System.out.println("Data saved successfully.");
                callbackContext.success();
              }
            }
          });
          
        } catch (Exception e) {
          Log.e("error",e.getMessage());
          callbackContext.error(e.getMessage());
        }   
  }


public void userExist(final CallbackContext callbackContext, final String documentNumber){
    
    //final ArrayList<String> isOnFirebase = new ArrayList<String>();
    DatabaseReference mDatabase;
      mDatabase = FirebaseDatabase.getInstance().getReference().child("users").child(documentNumber);
      mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
          
            if (dataSnapshot.exists()) {
              //isOnFirebase.add("true"); 
              //Log.i(TAG, String.valueOf(isOnFirebase.size()));
              callbackContext.success("True"); //isOnFirebase 1 = true - isOnFirebase.size()
                
            } else {
              //Log.i(TAG, String.valueOf(isOnFirebase.size()));
              callbackContext.success("False"); // isOnFirebase 0 = false - isOnFirebase.size()
            }
            //isOnFirebase.clear();
        }

        @Override
        public void onCancelled(DatabaseError databaseError) {
            //Log.e(TAG, databaseError.toString());
            //throw databaseError.toException();
            //callbackContext.error(databaseError.toString());
            callbackContext.error(databaseError.getMessage());
        }
    });   
  }


public void validateLastUserReview(final CallbackContext callbackContext, String documentNumber){
      final ArrayList<Review> lastReview = new ArrayList<Review>();
      DatabaseReference mDatabase;
      mDatabase = FirebaseDatabase.getInstance().getReference().child("reviews").child(documentNumber);

      mDatabase.addListenerForSingleValueEvent(new ValueEventListener() {
        @Override
        public void onDataChange(DataSnapshot dataSnapshot) {
          
            if (dataSnapshot.exists()) {
              int orderOfReview = 0;
              for (DataSnapshot postSnapshot : dataSnapshot.getChildren()){
                lastReview.add(postSnapshot.getValue(Review.class));
              }
              if (lastReview.size() == 0){
                callbackContext.success("false");
              }else{
                  for (Review userReview : lastReview){
                  if (orderOfReview == lastReview.size() - 1){
                    callbackContext.success(userReview.getCalification());
                  }else{
                    orderOfReview++;
                  }
                }
              }  
          //****************************************
            }else{
              callbackContext.success("false");
            }
            lastReview.clear();
        }
        @Override
        public void onCancelled(DatabaseError databaseError) {
            callbackContext.error(databaseError.getMessage());
        }
    });
  }
}
