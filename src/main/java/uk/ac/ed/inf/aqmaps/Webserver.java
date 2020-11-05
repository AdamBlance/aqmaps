package uk.ac.ed.inf.aqmaps;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Scanner;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

public class Webserver {
	
	String serverURL;
	
	public Webserver(String url) throws IOException {		
		serverURL = url;	    
	}
	
	// should keep no-fly-zones/buildings consistent
	
	public List<Polygon> getNoFlyZones() throws IOException {
		
		String page = getPageAsString(serverURL + "/buildings/no-fly-zones.geojson");
		
		List<Polygon> noFlyZones = new ArrayList<>();
				
		for (Feature feature : FeatureCollection.fromJson(page).features()) {
			noFlyZones.add((Polygon) feature.geometry());
		}	
		return noFlyZones;
	}
	
	// TODO: Use arrays where possible instead of lists
	public List<Sensor> getSensorData(String day, String month, String year) throws IOException {
		var page = getPageAsString(serverURL + String.format("/maps/%s/%s/%s/air-quality-data.json", year, month, day));
		
		var jsonSensors = new Gson().fromJson(page, JsonObject[].class);
		var sensors = new ArrayList<Sensor>();
		
		for (var j : jsonSensors) {
			String w3wAddress = j.get("location").getAsString();
			Point point = getWhat3WordsCoordinates(w3wAddress);
			double battery = j.get("battery").getAsDouble();
			double reading;
			try {
				reading = Double.parseDouble(j.get("reading").getAsString());
			} catch (NumberFormatException e) {
				reading = -1.0;
			}
			sensors.add(new Sensor(point, w3wAddress, battery, reading));
		}
		return sensors;
	}
	
	// This broke, that's really bad 
	// Exception in thread "main" java.net.BindException: Address already in use: connect

	
	// We're meant to use another thing for web stuff, should change to that
	
	private String getPageAsString(String pageUrl) throws IOException {
		URL url = new URL(pageUrl);
		Scanner scanner = new Scanner(url.openStream(), "UTF-8");
		scanner.useDelimiter("\\A");
		String page = scanner.next();
		scanner.close();
		return page;
	}
	
	private Point getWhat3WordsCoordinates(String w3wAddress) throws IOException {
		String page = getPageAsString(serverURL + String.format("/words/%s/details.json", w3wAddress.replace('.', '/')));
		var jsonAddress = new Gson().fromJson(page, JsonObject.class);
		var coords = jsonAddress.getAsJsonObject("coordinates");
		return Point.fromLngLat(
				coords.get("lng").getAsDouble(),
				coords.get("lat").getAsDouble());
	}
	
}
