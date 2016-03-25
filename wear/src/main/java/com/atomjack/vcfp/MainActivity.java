package com.atomjack.vcfp;

import android.app.Activity;
import android.app.RemoteInput;
import android.content.Intent;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.wearable.view.WatchViewStub;
import android.view.View;
import android.widget.ImageButton;
import android.widget.TextView;

import com.atomjack.shared.NewLogger;
import com.atomjack.shared.SendToDataLayerThread;
import com.atomjack.shared.WearConstants;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.Wearable;

import java.util.ArrayList;
import java.util.Arrays;

import butterknife.Bind;
import butterknife.ButterKnife;
import butterknife.OnClick;

public class MainActivity extends Activity implements
        GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {
  public static final String SHOW_WEAR_UNAUTHORIZED = "com.atomjack.vcfp.intent.show_wear_unauthorized";
  public static final String FINISH = "com.atomjack.vcfp.intent.finish";
  public static final String START_SPEECH_RECOGNITION = "com.atomjack.vcfp.intent.start_speech_recognition";
  public static final String RECEIVE_VOICE_INPUT = "com.atomjack.vcfp.intent.receive_voice_input";

  private static final int SPEECH_RECOGNIZER_REQUEST_CODE = 0;

  GoogleApiClient googleApiClient;
  WatchViewStub watchViewStub;

  @Bind(R.id.mainMicButton)
  public ImageButton mainMicButton;
  
  private NewLogger logger;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    logger = new NewLogger(this);
    
    logger.d("onCreate: %s", getIntent().getAction());

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
        dataMap.putBoolean(WearConstants.LAUNCHED, true);
        new SendToDataLayerThread(WearConstants.GET_PLAYBACK_STATE, dataMap, this).start();
      } else if(action.equals(WearConstants.SET_INFO)) {
        logger.d("setting info to %s", getIntent().getStringExtra(WearConstants.INFORMATION));
        setInformationView(getIntent().getStringExtra(WearConstants.INFORMATION));
      } else if(action.equals(RECEIVE_VOICE_INPUT)) {
        String query = getMessageText(getIntent());
        DataMap dataMap = new DataMap();
        dataMap.putStringArrayList(WearConstants.SPEECH_QUERY, new ArrayList<>(Arrays.asList(query)));
        new SendToDataLayerThread(WearConstants.SPEECH_QUERY, dataMap, this).start();
        finish();
      } else if(action.equals(WearConstants.SPEECH_QUERY_RESULT)) {
        boolean result = getIntent().getBooleanExtra(WearConstants.SPEECH_QUERY_RESULT, false);
        if (result) {
          finish();
        }
      }
    }
  }

  private String getMessageText(Intent intent) {
    Bundle remoteInput = RemoteInput.getResultsFromIntent(intent);
    if (remoteInput != null) {
      CharSequence charSequence = remoteInput.getCharSequence(WearConstants.SPEECH_QUERY);
      return new StringBuilder(charSequence.length()).append(charSequence).toString();
    }
    return null;
  }
  @Override
  protected void onPause() {
    super.onPause();
    logger.d("onPause");
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
      logger.d("onNewIntent: %s", action);
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
        logger.d("starting speech recognition.");
        logger.d("client: %s", intent.getStringExtra(WearConstants.CLIENT_NAME));
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

    watchViewStub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
      @Override
      public void onLayoutInflated(WatchViewStub stub) {
        ButterKnife.bind(MainActivity.this, stub);
      }
    });
  }

  @OnClick(R.id.mainMicButton)
  public void onMicClick(View v) {
    logger.d("onMicClick");
    Intent recIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    recIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    startActivityForResult(recIntent, SPEECH_RECOGNIZER_REQUEST_CODE);
  }

  private void setInformationView(String info) {
    watchViewStub = (WatchViewStub) findViewById(R.id.watch_view_stub);

    watchViewStub.setRectLayout(R.layout.activity_information_rect);
    watchViewStub.setRoundLayout(R.layout.activity_information_round);
    watchViewStub.setOnLayoutInflatedListener(new WatchViewStub.OnLayoutInflatedListener() {
      @Override
      public void onLayoutInflated(WatchViewStub watchViewStub) {

      }
    });
    TextView textView = (TextView)findViewById(R.id.textView);
    logger.d("Setting Information View: %s", info);
    textView.setText(info);
  }

  @Override
  protected void onStart() {
    super.onStart();
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, Intent data) {
    logger.d("onActivityResult. requestCode: %d, resultCode: %d", requestCode, resultCode);
    if (requestCode == SPEECH_RECOGNIZER_REQUEST_CODE) {
      // When the speech recognizer finishes its work, Android invokes this callback with requestCode equal to SPEECH_RECOGNIZER_REQUEST_CODE
      if (resultCode == RESULT_OK) {
        DataMap dataMap = new DataMap();
        dataMap.putStringArrayList(WearConstants.SPEECH_QUERY, data.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS));
        new SendToDataLayerThread(WearConstants.SPEECH_QUERY, dataMap, MainActivity.this).start();
        finish();
      } else if(resultCode == RESULT_CANCELED) {
        finish();
      }
    }
    super.onActivityResult(requestCode, resultCode, data);
  }
}
