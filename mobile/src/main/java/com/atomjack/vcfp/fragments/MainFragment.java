package com.atomjack.vcfp.fragments;

import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.speech.RecognizerIntent;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ImageButton;
import android.widget.TextView;

import com.atomjack.shared.NewLogger;
import com.atomjack.shared.Preferences;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.VCFPHint;
import com.atomjack.vcfp.VoiceControlForPlexApplication;
import com.atomjack.vcfp.model.PlexClient;
import com.atomjack.vcfp.model.PlexServer;
import com.atomjack.vcfp.services.PlexSearchService;

import java.math.BigInteger;
import java.security.SecureRandom;

import butterknife.BindView;
import butterknife.ButterKnife;

public class MainFragment extends Fragment {
  private NewLogger logger;
  TextView mainStreamingFromTo;
  @BindView(R.id.mainViewUsageHint) TextView mainViewUsageHint;
  PlexServer server;
  PlexClient client;
  private VCFPHint vcfpHint;

  public MainFragment() {
    logger = new NewLogger(this);
  }

  public void setClient(PlexClient client) {
    this.client = client;
    setMainStreamingFromTo();
  }

  public void setServer(PlexServer server) {
    this.server = server;
    setMainStreamingFromTo();
  }

  private void setMainStreamingFromTo() {
    if(mainStreamingFromTo != null) {
      if(client == null)
        mainStreamingFromTo.setText(R.string.connect_to_a_client_to_begin);
      else if(server.isScanAllServer)
        mainStreamingFromTo.setText(String.format(getString(R.string.ready_to_scan_servers_to), client.name));
      else
        mainStreamingFromTo.setText(String.format(getString(R.string.ready_to_cast_from_to), server.name, client.name));
    }
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.fragment_main, container, false);

    ButterKnife.bind(this, view);
    vcfpHint = new VCFPHint(mainViewUsageHint);

    client = VoiceControlForPlexApplication.gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.CLIENT, ""), PlexClient.class);
    server = VoiceControlForPlexApplication.gsonRead.fromJson(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SERVER, ""), PlexServer.class);
    if (server == null)
      server = PlexServer.getScanAllServer();

    mainStreamingFromTo = (TextView)view.findViewById(R.id.mainStreamingFromTo);
    setMainStreamingFromTo();

    ImageButton mainMicButton = (ImageButton)view.findViewById(R.id.mainMicButton);
    mainMicButton.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View v) {
        if(client == null)
          return;
        Intent serviceIntent = new Intent(getActivity(), PlexSearchService.class);

        serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_SERVER, VoiceControlForPlexApplication.gsonWrite.toJson(server));
        serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_CLIENT, VoiceControlForPlexApplication.gsonWrite.toJson(client));
        serviceIntent.putExtra(com.atomjack.shared.Intent.EXTRA_RESUME, false);
        serviceIntent.putExtra(com.atomjack.shared.Intent.USE_CURRENT, true);

        SecureRandom random = new SecureRandom();
        serviceIntent.setData(Uri.parse(new BigInteger(130, random).toString(32)));
        PendingIntent resultsPendingIntent = PendingIntent.getService(getActivity(), 0, serviceIntent, Intent.FLAG_ACTIVITY_NEW_TASK);

        Intent listenerIntent = new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
        listenerIntent.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
        listenerIntent.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE, "voice.recognition.test");
        listenerIntent.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 5);
        listenerIntent.putExtra(RecognizerIntent.EXTRA_RESULTS_PENDINGINTENT, resultsPendingIntent);
        listenerIntent.putExtra(RecognizerIntent.EXTRA_PROMPT, getResources().getString(R.string.voice_prompt));

        startActivity(listenerIntent);
      }
    });
    return view;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
  }

  @Override
  public void onPause() {
    super.onPause();
    vcfpHint.stop();
  }

  @Override
  public void onResume() {
    super.onResume();
    if(VoiceControlForPlexApplication.getInstance().prefs.get(Preferences.SHOW_USAGE_HINTS, false))
      vcfpHint.start();
  }

  public void setUsageHintsActive(boolean active) {
    if(active)
      vcfpHint.start();
    else
      vcfpHint.stop();
  }
}
