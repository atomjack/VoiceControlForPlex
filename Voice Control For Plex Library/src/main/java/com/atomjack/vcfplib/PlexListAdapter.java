package com.atomjack.vcfplib;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import android.app.Dialog;
import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.TextView;

import com.atomjack.vcfplib.model.PlexClient;
import com.atomjack.vcfplib.model.PlexServer;

public class PlexListAdapter extends BaseAdapter {
  private final Context context;
  private Map<String, PlexClient> m_clients;
  private ConcurrentHashMap<String, PlexServer> m_servers;

  private String[] m_serverKeys;
  private String[] m_clientKeys;
  private Dialog m_dialog;

  public final static int TYPE_SERVER = 0;
  public final static int TYPE_CLIENT = 1;

  private int m_type;

  public PlexListAdapter(Context context, int type) {
    m_type = type;
    this.context = context;
  }

  public void setClients(Map<String, PlexClient> clients) {
    m_clients = clients;
    m_clientKeys = m_clients.keySet().toArray(new String[clients.size()]);
  }

  public void setServers(ConcurrentHashMap<String, PlexServer> servers) {
    m_servers = servers;
    m_serverKeys = m_servers.keySet().toArray(new String[servers.size()]);
  }

  @Override
  public int getCount() {
    if(m_type == TYPE_SERVER)
      return m_servers.size() + 1;
    else if(m_type == TYPE_CLIENT)
      return m_clients.size();
    return 0;
  }

  @Override
  public Object getItem(int position) {
    if(m_type == TYPE_SERVER)
      return position == 0 ? new PlexServer("Scan All") : m_servers.get(m_serverKeys[position-1]);
    else if(m_type == TYPE_CLIENT)
      return m_clients.get(m_clientKeys[position]);
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
      textView.setText(server.name.equals("") ? "Scan All" : server.name);
    } else if(m_type == TYPE_CLIENT) {
      PlexClient client = (PlexClient)getItem(pos);
      rowView = inflater.inflate(R.layout.client_list_item, parent, false);
      TextView clientNameView = (TextView) rowView.findViewById(R.id.clientListClientName);
      clientNameView.setText(client.name);
      TextView clientTypeView = (TextView) rowView.findViewById(R.id.clientListClientType);
      clientTypeView.setText(client.product);
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
