package com.atomjack.vcfp.adapters;

import android.app.Activity;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ArrayAdapter;
import android.widget.ImageButton;
import android.widget.TextView;

import com.atomjack.vcfp.R;
import com.atomjack.vcfp.model.MainSetting;

public class MainListAdapter extends ArrayAdapter<MainSetting> {
	Context context;
    int layoutResourceId; 
    MainSetting[] data = null;
    
	public MainListAdapter(Context context, int layoutResourceId,
			MainSetting[] data) {
		super(context, layoutResourceId, data);
		this.layoutResourceId = layoutResourceId;
        this.context = context;
        this.data = data;
	}

	@Override
	public View getView(int position, View convertView, ViewGroup parent) {
			View row = convertView;
			SettingHolder holder;
			if(row == null)
			{
					LayoutInflater inflater = ((Activity)context).getLayoutInflater();
					row = inflater.inflate(layoutResourceId, parent, false);

					holder = new SettingHolder();
					holder.line1 = (TextView)row.findViewById(R.id.mainSettingItem1);
					holder.line2 = (TextView)row.findViewById(R.id.mainSettingItem2);
					holder.helpButton = (ImageButton)row.findViewById(R.id.settingRowHelpButton);

					row.setTag(holder);
			}
			else
			{
					holder = (SettingHolder)row.getTag();
			}

			MainSetting item = data[position];
			holder.line1.setText(item.line1);
			holder.line2.setText(item.line2);
			holder.tag = item.tag;
			holder.helpButton.setTag(item.tag);
			return row;
	}
	
	public static class SettingHolder {
		public TextView line1;
		public TextView line2;
		public ImageButton helpButton;
		public String tag;

		public final static String TAG_SERVER = "server";
		public final static String TAG_CLIENT = "client";
		public final static String TAG_FEEDBACK = "feedback";
		public final static String TAG_ERRORS = "errors";

	}
}
