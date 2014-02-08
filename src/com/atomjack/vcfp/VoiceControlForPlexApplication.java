package com.atomjack.vcfp;

import java.util.concurrent.ConcurrentHashMap;

import org.simpleframework.xml.Serializer;
import org.simpleframework.xml.core.Persister;

import android.content.res.Resources.NotFoundException;

import com.atomjack.vcfp.model.MediaContainer;
import com.atomjack.vcfp.model.PlexServer;
import com.loopj.android.http.AsyncHttpClient;
import com.loopj.android.http.AsyncHttpResponseHandler;

public class VoiceControlForPlexApplication
{
    private static ConcurrentHashMap<String, PlexServer> plexmediaServers = new ConcurrentHashMap<String, PlexServer>();
    
    public static ConcurrentHashMap<String, PlexServer> getPlexMediaServers() {
		return plexmediaServers;
	}
    
    private static Serializer serial = new Persister();
    
    public static void addPlexServer(final PlexServer server) {
    	Logger.d("ADDING PLEX SERVER: %s", server.getName());
    	if(server.getName().equals("") || server.getAddress().equals("")) {
    		return;
    	}
    	if (!plexmediaServers.containsKey(server.getName())) {
    		try {
    		    String url = "http://" + server.getAddress() + ":" + server.getPort() + "/library/sections/";
    		    AsyncHttpClient httpClient = new AsyncHttpClient();
    		    httpClient.get(url, new AsyncHttpResponseHandler() {
    		        @Override
                public void onSuccess(int statusCode, org.apache.http.Header[] headers, byte[] responseBody) {
//    		            Logger.d("HTTP REQUEST: %s", response);
    		            MediaContainer mc = new MediaContainer();
    		            try {
    		            	mc = serial.read(MediaContainer.class, new String(responseBody, "UTF-8"));
    		            } catch (NotFoundException e) {
    		                e.printStackTrace();
    		            } catch (Exception e) {
    		                e.printStackTrace();
    		            }
    		            for(int i=0;i<mc.directories.size();i++) {
    		            	if(mc.directories.get(i).getType().equals("movie")) {
    		            		server.addMovieSection(mc.directories.get(i).getKey());
    		            	}
    		            	if(mc.directories.get(i).getType().equals("show")) {
    		            		server.addTvSection(mc.directories.get(i).getKey());
    		            	}
    		            	if(mc.directories.get(i).getType().equals("artist")) {
    		            		server.addMusicSection(mc.directories.get(i).getKey());
    		            	}
    		            }
    		            Logger.d("title1: %s", mc.title1);
    		            if(mc.directories != null)
    		            	Logger.d("Directories: %d", mc.directories.size());
    		            else
    		            	Logger.d("No directories found!");
    		            if(!server.getName().equals("") && !server.getAddress().equals("")) {
    		            	plexmediaServers.putIfAbsent(server.getName(), server);
    		            }
    		        }
    		    });

    		} catch (Exception e) {
    			Logger.e("Exception getting clients: %s", e.toString());
    		}

			Logger.d("Adding %s", server.getName());
		} else {
			Logger.d("%s already added.", server.getName());
		}
    }
    
}
