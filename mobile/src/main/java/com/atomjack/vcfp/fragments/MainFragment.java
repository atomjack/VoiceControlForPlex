package com.atomjack.vcfp.fragments;

import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;

import com.atomjack.shared.Logger;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.activities.NewMainActivity;
import com.atomjack.vcfp.activities.ShortcutProviderActivity;
import com.sothree.slidinguppanel.SlidingUpPanelLayout;

public class MainFragment extends Fragment {
  public MainFragment() {
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    final View view = inflater.inflate(R.layout.fragment_main, container, false);


    FloatingActionButton fab = (FloatingActionButton) view.findViewById(R.id.fab);
    fab.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Logger.d("activity: %s", getActivity());
        Intent intent = new Intent(getActivity(), ShortcutProviderActivity.class);

        getActivity().startActivityForResult(intent, NewMainActivity.RESULT_SHORTCUT_CREATED);
      }
    });

//    Button button = (Button) view.findViewById(R.id.button);
//    button.setOnClickListener(new View.OnClickListener() {
//      @Override
//      public void onClick(View v) {
//        testListener.test();
//      }
//    });

    return view;
  }

  @Override
  public void onAttach(Context context) {
    super.onAttach(context);
  }
}
