package com.atomjack.wear;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.wearable.view.WatchViewStub;
import android.view.View;
import android.widget.TextView;

import com.atomjack.shared.Logger;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.WearConstants;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Wearable;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonPrimitive;
import com.google.gson.JsonSerializationContext;
import com.google.gson.JsonSerializer;

import java.lang.reflect.Type;
import java.util.HashMap;
import java.util.List;

public class MainActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
  public static final String SHOW_WEAR_UNAUTHORIZED = "com.atomjack.vcfp.intent.show_wear_unauthorized";
  public static final String FINISH = "com.atomjack.vcfp.intent.finish";
  public static final String START_SPEECH_RECOGNITION = "com.atomjack.vcfp.intent.start_speech_recognition";
  private static final int SPEECH_RECOGNIZER_REQUEST_CODE = 0;

  GoogleApiClient googleApiClient;
  WatchViewStub watchViewStub;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    Logger.d("[MainActivity] onCreate: %s", savedInstanceState);

    setMainView();

    googleApiClient = new GoogleApiClient.Builder(this)
            .addApi(Wearable.API)
            .build();
    googleApiClient.connect();

    String action = getIntent().getAction();
    if(action != null) {
      if (action.equals(Intent.ACTION_MAIN)) {
        // Send a message to the paired device asking if it's connected to a Plex Client that is currently playing
        DataMap dataMap = new DataMap();
//      dataMap.putBoolean(WearConstants.LAUNCHED, true);
        new SendToDataLayerThread(WearConstants.GET_PLAYBACK_STATE, this).start();
      } else if(action.equals(WearConstants.SET_INFO)) {
        setInformationView(getIntent().getStringExtra(WearConstants.INFORMATION));
      }
    }
  }

  @Override
  protected void onPause() {
    super.onPause();
    Logger.d("[MainActivity] onPause");
  }

  @Override
  public void onConnectionSuspended(int i) {

  }

  @Override
  public void onConnected(Bundle bundle) {

  }

  @Override
  public void onConnectionFailed(ConnectionResult connectionResult) {

  }

  @Override
  protected void onNewIntent(final Intent intent) {
    super.onNewIntent(intent);

    if(intent.getAction() != null) {
      String action = intent.getAction();
      Logger.d("[MainActivity] onNewIntent: %s", action);
      if(action.equals(SHOW_WEAR_UNAUTHORIZED)) {
        watchViewStub.setRectLayout(R.layout.main_unauthorized_rect);
        watchViewStub.setRoundLayout(R.layout.main_unauthorized_round);
      } else if(action.equals(FINISH)) {
        finish();
      } else if(action.equals(WearConstants.SPEECH_QUERY_RESULT)) {
        boolean result = intent.getBooleanExtra(WearConstants.SPEECH_QUERY_RESULT, false);
        if(result) {
          finish();
        }
      } else if(action.equals(WearConstants.SET_INFO)) {
        setInformationView(intent.getStringExtra(WearConstants.INFORMATION));
      } else if (action.equals(START_SPEECH_RECOGNITION)) {
        Logger.d("[MainActivity] starting speech recognition.");
        Logger.d("client: %s", intent.getStringExtra(WearConstants.CLIENT_NAME));
        Intent recIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        recIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        startActivityForResult(recIntent, SPEECH_RECOGNIZER_REQUEST_CODE);
      }
    }
  }

  private void setMainView() {
    setMainView(null);
  }
  private void setMainView(final Runnable runThis) {
    setContentView(R.layout.activity_main);
    watchViewStub = (WatchViewStub) findViewById(R.id.watch_view_stub);
    watchViewStub.setRectLayout(R.layout.activity_main_rect);
    watchViewStub.setRoundLayout(R.layout.activity_main_round);
//    watchViewStub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
//      @Override
//      public void onLayoutInflated(WatchViewStub stub) {
//        castingToTextView = (TextView) findViewById(R.id.casting_to);
//        if (runThis != null)
//          runThis.run();
//        Logger.d("set castingToTextView to %s", castingToTextView);
//      }
//    });
  }

  private void setInformationView(String info) {
    watchViewStub = (WatchViewStub) findViewById(R.id.watch_view_stub);

    watchViewStub.setRectLayout(R.layout.activity_information_rect);
    watchViewStub.setRoundLayout(R.layout.activity_information_round);
    TextView textView = (TextView)findViewById(R.id.textView);
    textView.setText(info);
  }

  @Override
  protected void onStart() {
    super.onStart();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    Logger.d("[VoiceInputActivity] onActivityResult: %s", requestCode);
    if (requestCode == SPEECH_RECOGNIZER_REQUEST_CODE) {
      // When the speech recognizer finishes its work, Android invokes this callback with requestCode equal to SPEECH_RECOGNIZER_REQUEST_CODE
      if (resultCode == RESULT_OK) {
        DataMap dataMap = new DataMap();
        dataMap.putStringArrayList(WearConstants.SPEECH_QUERY, data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS));
        new SendToDataLayerThread(WearConstants.SPEECH_QUERY, dataMap, MainActivity.this).start();
        finish();
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }
}
