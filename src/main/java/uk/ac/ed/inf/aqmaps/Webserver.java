package uk.ac.ed.inf.aqmaps;

import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
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
	
	// We just give it the root of the webserver
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
	
	public HashMap<Point, SensorData> getSensorData(String day, String month, String year) throws IOException {
		String page = getPageAsString(serverURL + String.format("/maps/%s/%s/%s/air-quality-data.json", year, month, day));
		Gson gson = new Gson();
		
		HashMap<Point, SensorData> output = new HashMap<>();
		for (SensorData sensor : gson.fromJson(page, SensorData[].class)) {
			output.put(getWhat3WordsCoordinates(sensor.getLocation()), sensor);
		}
		return output;
	}
	
	// TODO: Use arrays where possible instead of lists
	public Sensor[] getSensorData2(String day, String month, String year) throws IOException {
		String page = getPageAsString(serverURL + String.format("/maps/%s/%s/%s/air-quality-data.json", year, month, day));
		Gson gson = new Gson();
		
		var sensors = gson.fromJson(page, Sensor[].class);
		for (var s : sensors) {
			s.setPoint(getWhat3WordsCoordinates(s.getLocation()));
		}
		return sensors;
	}
	
	public String getPageAsString(String pageUrl) throws IOException {
		URL url = new URL(pageUrl);
		Scanner scanner = new Scanner(url.openStream(), "UTF-8");
		scanner.useDelimiter("\\A");
		String page = scanner.next();
		scanner.close();
		return page;
	}
	
	public Point getWhat3WordsCoordinates(String w3wAddress) throws IOException {
		String page = getPageAsString(serverURL + String.format("/words/%s/details.json", w3wAddress.replace('.', '/')));
		Gson gson = new Gson();
		JsonObject json = gson.fromJson(page, JsonObject.class);
		JsonObject coords = json.getAsJsonObject("coordinates");
		return Point.fromLngLat(
				coords.get("lng").getAsDouble(),
				coords.get("lat").getAsDouble());
		
	}
	
}
