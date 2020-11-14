package uk.ac.ed.inf.aqmaps;

import java.io.IOException;
import java.net.ConnectException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.HttpResponse.BodyHandlers;
import java.util.ArrayList;
import java.util.List;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

public class WebServer {
	
	private static WebServer singletonInstance;
	
	private final String serverURL;
	private final String port;
	
	private final HttpClient client = HttpClient.newHttpClient();
	private final int MAX_HTTP_REQUEST_ATTEMPTS = 10;
	
	private WebServer(String serverURL, String port) {
		this.serverURL = serverURL;
		this.port = port;
	}
	
	public static WebServer getInstanceWithConfig(String serverURL, String port) {
		if (singletonInstance == null) {
			singletonInstance = new WebServer(serverURL, port);
		}
		return singletonInstance;
	}
	
	public List<Polygon> getNoFlyZones() throws UnexpectedHTTPResponseException {
		var geojsonData = getResourceAsString(String.format("%s:%s/buildings/no-fly-zones.geojson", serverURL, port));			
		
		var noFlyZones = new ArrayList<Polygon>();
		for (var feature : FeatureCollection.fromJson(geojsonData).features()) {
			noFlyZones.add((Polygon) feature.geometry());
		}	
		return noFlyZones;
	}
	
	public List<Sensor> getSensorData(String day, String month, String year) throws UnexpectedHTTPResponseException {
		var sensorJson = getResourceAsString(String.format("%s:%s/maps/%s/%s/%s/air-quality-data.json", serverURL, port, year, month, day));
		var jsonObjList = new Gson().fromJson(sensorJson, JsonObject[].class);
		
		var sensors = new ArrayList<Sensor>();
		for (var jsonObj : jsonObjList) {
			var w3wAddress = jsonObj.get("location").getAsString();
			var point = getWhat3WordsCoordinates(w3wAddress);
			double battery = jsonObj.get("battery").getAsDouble();
			double reading;
			try {
				reading = Double.parseDouble(jsonObj.get("reading").getAsString());
			} catch (NumberFormatException e) {  // Checking for NaN/Null/null etc.
				reading = -1.0;
			}
			sensors.add(new Sensor(point, w3wAddress, battery, reading));
		}
		return sensors;
	}

	private Point getWhat3WordsCoordinates(String w3wAddress) throws UnexpectedHTTPResponseException {
		String w3wData = getResourceAsString(String.format("%s:%s/words/%s/details.json", serverURL, port, w3wAddress.replace('.', '/')));
		var jsonObj = new Gson().fromJson(w3wData, JsonObject.class);
		var coords = jsonObj.getAsJsonObject("coordinates");
		return Point.fromLngLat(
				coords.get("lng").getAsDouble(),
				coords.get("lat").getAsDouble());
	}
	
	private String getResourceAsString(String pageUrl) throws UnexpectedHTTPResponseException {
		var request = HttpRequest.newBuilder().uri(URI.create(pageUrl)).build();
		HttpResponse<String> response = null;
		
		int attempts = 0;
		boolean fulfilled = false;
		while (!fulfilled) {
			try {
				attempts += 1;
				response = client.send(request, BodyHandlers.ofString());
				fulfilled = true;
			} catch (ConnectException e) {
				System.out.println(String.format("Fatal error: Unable to connect to %s at port %s. Exiting...", serverURL, port));
				System.exit(1);
			} catch (InterruptedException | IOException e) {
				if (attempts == MAX_HTTP_REQUEST_ATTEMPTS) {
					System.out.println("Fatal error: Exceeded maximum number of request attempts. Exiting...");
					System.exit(1);
				} else {
					System.out.println(String.format("Request failed. Retrying (%s/%s)...", attempts, MAX_HTTP_REQUEST_ATTEMPTS));
				}
			}
		}
		if (response.statusCode() == 200) {
			return response.body();
		} else {
			throw new UnexpectedHTTPResponseException(
					String.format("Fatal error: Did not receive HTTP status code 200 (got %d instead). Perhaps your date is invalid? Exiting...", response.statusCode()));
		}
	}
}
