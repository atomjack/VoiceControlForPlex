package com.atomjack.vcfp;

import android.content.Context;
import android.view.ActionProvider;
import android.view.LayoutInflater;
import android.view.View;

@SuppressWarnings("deprecation")
public class ClientConnectActionProvider extends ActionProvider {
	Context mContext;

	public ClientConnectActionProvider(Context context) {
		super(context);
		mContext = context;
	}

	@Override
	public View onCreateActionView() {
		LayoutInflater layoutInflater = LayoutInflater.from(mContext);
		View view = layoutInflater.inflate(R.layout.client_connect_off, null);
		return view;
	}


}
