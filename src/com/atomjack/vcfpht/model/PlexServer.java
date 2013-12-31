package com.atomjack.vcfpht.model;

import java.util.ArrayList;
import java.util.List;

import org.simpleframework.xml.Attribute;
import org.simpleframework.xml.Root;

@Root(strict=false)
public class PlexServer {
	@Attribute
	private String name;
	@Attribute
	private String port;
	@Attribute(name="address")
	private String ipaddress;
	@Attribute
	private String machineIdentifier;

	private List<String> movieSections = new ArrayList<String>();
	private List<String> tvSections = new ArrayList<String>();
	
	public String getName() {
		return this.name == null ? "" : this.name;
	}
	
	public void setName(String name) {
		this.name = name;
	}
	
	public String getPort() {
		return this.port == null ? "" : this.port;
	}
	
	public void setPort(String port) {
		this.port = port;
	}
	
	public String getIPAddress() {
		return this.ipaddress == null ? "" : this.ipaddress;
	}
	
	public void setIPAddress(String ipaddress) {
		this.ipaddress = ipaddress;
	}

	public String getMachineIdentifier() {
		return machineIdentifier;
	}

	public void setMachineIdentifier(String machineIdentifier) {
		this.machineIdentifier = machineIdentifier;
	}

	public List<String> getMovieSections() {
		return movieSections;
	}

	public void setMovieSections(List<String> movieSections) {
		this.movieSections = movieSections;
	}

	public List<String> getTvSections() {
		return tvSections;
	}

	public void setTvSections(List<String> tvSections) {
		this.tvSections = tvSections;
	}
	
	public void addMovieSection(String key) {
		if(!movieSections.contains(key)) {
			movieSections.add(key);
		}
	}

	public void addTvSection(String key) {
		if(!tvSections.contains(key)) {
			tvSections.add(key);
		}
	}
	
	public String getClientsURL() {
		return "http://" + this.getIPAddress() + ":" + this.getPort() + "/clients";
	}
	
	public String getBaseURL() {
		return "http://" + this.getIPAddress() + ":" + this.getPort() + "/";
	}
	
	@Override
	public String toString() {
		String output = "";
		output += "Name: " + this.getName() + "\n";
		output += "IP Address: " + this.getIPAddress() + "\n";
		return output;
	}
}
