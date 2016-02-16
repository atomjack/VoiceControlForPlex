package com.atomjack.vcfp.fragments;

import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.atomjack.shared.Logger;
import com.atomjack.vcfp.R;

public class SetupFragment extends Fragment {

  public SetupFragment() {
    Logger.d("SetupFragment()");
  }

  @Nullable
  @Override
  public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container, @Nullable Bundle savedInstanceState) {
    View view = inflater.inflate(R.layout.fragment_loading, container, false);
    return view;
  }
}
