package com.atomjack.vcfpht;

import java.util.List;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.atomjack.vcfpht.model.PlexClient;
import com.atomjack.vcfpht.model.PlexDevice;
import com.atomjack.vcfpht.model.PlexServer;

public class PlexListAdapter extends BaseAdapter {
  private final Context context;
  private List<PlexClient> m_clients;
  private ConcurrentHashMap<String, PlexServer> m_servers;

  private String[] mKeys;

  private Dialog m_dialog;

  public final static int TYPE_SERVER = 0;
  public final static int TYPE_CLIENT = 1;

  private int m_type;

  public PlexListAdapter(Context context, int type) {
    m_type = type;
    this.context = context;
  }

  public void setClients(List<PlexClient> clients) {
    m_clients = clients;
  }

  public void setServers(ConcurrentHashMap<String, PlexServer> servers) {
    m_servers = servers;
    mKeys = m_servers.keySet().toArray(new String[servers.size()]);
  }

  @Override
  public int getCount() {
    if(m_type == TYPE_SERVER)
      return m_servers.size();
    else if(m_type == TYPE_CLIENT)
      return m_clients.size();
    return 0;
  }

  @Override
  public Object getItem(int position) {
    if(m_type == TYPE_SERVER)
      return m_servers.get(mKeys[position]);
    else if(m_type == TYPE_CLIENT)
      return m_clients.get(position);
    return null;
  }

  @Override
  public long getItemId(int arg0) {
    return arg0;
  }

  @Override
  public View getView(int pos, View convertView, ViewGroup parent) {
    LayoutInflater inflater = (LayoutInflater) context.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
    View rowView = inflater.inflate(R.layout.server_list_item, parent, false);

    if(m_type == TYPE_SERVER) {
      PlexServer server = (PlexServer)getItem(pos);
      TextView textView = (TextView) rowView.findViewById(R.id.serverListTextView);
      textView.setText(server.getName().equals("") ? "Scan All" : server.getName());
    } else if(m_type == TYPE_CLIENT) {
      PlexClient client = (PlexClient)getItem(pos);
      rowView = inflater.inflate(R.layout.client_list_item, parent, false);
      TextView clientNameView = (TextView) rowView.findViewById(R.id.clientListClientName);
      clientNameView.setText(client.getName());
      TextView clientTypeView = (TextView) rowView.findViewById(R.id.clientListClientType);
      clientTypeView.setText(client.getProduct());
    }

    return rowView;
  }

  public Dialog getDialog() {
    return m_dialog;
  }
  public void setDialog(Dialog dialog) {
    this.m_dialog = dialog;
  }
}
