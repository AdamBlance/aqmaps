package uk.ac.ed.inf.aqmaps;

import java.io.IOException;
import java.net.URL;
import java.util.Scanner;

import com.google.gson.Gson;
import com.mapbox.geojson.FeatureCollection;

public class Webserver {
	
	String serverURL;
	
	// We just give it the root of the webserver
	public Webserver( String address, String port ) throws IOException {
		
		serverURL = address + ":" + port;	    
	}
	
	// should keep no-fly-zones/buildings consistent
	
	public FeatureCollection getNoFlyZones() throws IOException {
		
		String page = getPageAsString(serverURL + "/buildings/no-fly-zones.geojson");
		return FeatureCollection.fromJson(page);
		
	}
	
	public Sensor[] getSensorArray(String day, String month, String year) throws IOException {
		// don't do this use String.format
		String page = getPageAsString(serverURL + String.format("/%s/%s/%s/air-quality-data.json", year, month, day));
		Gson gson = new Gson();
		
		return gson.fromJson(page, Sensor[].class);
	}
	
	// url, address, serverURL are all used here, exceptions
	
	public String getPageAsString(String pageUrl) throws IOException {
		URL url = new URL(pageUrl);
		Scanner scanner = new Scanner(url.openStream(), "UTF-8");
		scanner.useDelimiter("\\A");
		String page = scanner.next();
		scanner.close();
		return page;
	}
	
}
