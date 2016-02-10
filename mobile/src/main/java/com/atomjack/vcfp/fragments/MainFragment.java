package com.atomjack.vcfp.fragments;

import android.content.Intent;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.atomjack.shared.Logger;
import com.atomjack.vcfp.R;
import com.atomjack.vcfp.activities.NewMainActivity;
import com.atomjack.vcfp.activities.ShortcutProviderActivity;

public class MainFragment extends Fragment {
  public MainFragment() {
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
                           Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_main, container, false);


    FloatingActionButton fab = (FloatingActionButton) view.findViewById(R.id.fab);
    fab.setOnClickListener(new View.OnClickListener() {
      @Override
      public void onClick(View view) {
        Logger.d("activity: %s", getActivity());
        Intent intent = new Intent(getActivity(), ShortcutProviderActivity.class);

        getActivity().startActivityForResult(intent, NewMainActivity.RESULT_SHORTCUT_CREATED);
      }
    });
    return view;
  }

}
