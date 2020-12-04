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
	
	private String serverURL;
	private String port;
	
	private final HttpClient client = HttpClient.newHttpClient();
	private static final int MAX_HTTP_REQUEST_ATTEMPTS = 10;
	
	private WebServer(String serverURL, String port) {
		this.serverURL = serverURL;
		this.port = port;
	}
	
	// Returns the singleton instance of WebServer and sets its serverURL and port fields
	public static WebServer getInstanceWithConfig(String serverURL, String port) {
		// Create the singleton instance if it doesn't already exist
		if (singletonInstance == null) {
			singletonInstance = new WebServer(serverURL, port);
		}
		return singletonInstance;
	}
	
	// Returns a list of no-fly-zones as polygons defined in /buildings/no-fly-zones.geojson
	public List<Polygon> getNoFlyZones() throws UnexpectedHTTPResponseException {
		var geojsonData = getResourceAsString(String.format("%s:%s/buildings/no-fly-zones.geojson", serverURL, port));			
		
		var noFlyZones = new ArrayList<Polygon>();
		for (var feature : FeatureCollection.fromJson(geojsonData).features()) {
			noFlyZones.add((Polygon) feature.geometry());  // Grab the geometry of the no-fly-zones from their features
		}	
		return noFlyZones;
	}
	
	// Returns a list of Sensor objects created from the contents of the relevant /maps/YYYY/MM/DD/air-quality-data.json file
	public List<Sensor> getSensors(String day, String month, String year) throws UnexpectedHTTPResponseException {
		var sensorJson = getResourceAsString(String.format("%s:%s/maps/%s/%s/%s/air-quality-data.json", serverURL, port, year, month, day));
		var jsonObjList = new Gson().fromJson(sensorJson, JsonObject[].class);  // array of sensors as jsonObjects
		
		var sensors = new ArrayList<Sensor>();
		for (var jsonObj : jsonObjList) {
			var w3wAddress = jsonObj.get("location").getAsString();
			var point = getCoordinateFromWhat3WordsAddress(w3wAddress);
			double battery = jsonObj.get("battery").getAsDouble();
			double reading;
			try {
				// Cheating a wee bit by converting the readings to doubles before the drone or pilot see them
				reading = Double.parseDouble(jsonObj.get("reading").getAsString());
			} catch (NumberFormatException e) {  // Checking for NaN/Null/null etc.
				reading = -1.0;
			}
			// Creating our Sensor objects from the json pollution sensor data
			sensors.add(new Sensor(point, w3wAddress, battery, reading));
		}
		return sensors;
	}

	// Returns the Point that the provided what-3-words address corresponds to
	private Point getCoordinateFromWhat3WordsAddress(String w3wAddress) throws UnexpectedHTTPResponseException {
		String w3wData = getResourceAsString(String.format("%s:%s/words/%s/details.json", serverURL, port, w3wAddress.replace('.', '/')));
		var jsonObj = new Gson().fromJson(w3wData, JsonObject.class);
		var coords = jsonObj.getAsJsonObject("coordinates");
		return Point.fromLngLat(
				coords.get("lng").getAsDouble(),
				coords.get("lat").getAsDouble());
	}
	
	// Returns the contents of a specified file on the web server as a string
	private String getResourceAsString(String pageURL) throws UnexpectedHTTPResponseException {
		var request = HttpRequest.newBuilder().uri(URI.create(pageURL)).build();
		HttpResponse<String> response = null;
		
		int attempts = 0;  // counts how many more HTTP request attempts we have made
		boolean fulfilled = false;
		while (!fulfilled) {
			try {
				attempts += 1;
				response = client.send(request, BodyHandlers.ofString());
				fulfilled = true;
			} catch (ConnectException e) {
				System.out.printf("Fatal error: Unable to connect to %s at port %s. Exiting...%n", serverURL, port);
				System.exit(1);
			} catch (InterruptedException | IOException e) {
				if (attempts == MAX_HTTP_REQUEST_ATTEMPTS) {  
					System.out.println("Fatal error: Exceeded maximum number of request attempts. Exiting...");
					System.exit(1);
				} else {  // If our request gets interrupted somehow, retry
					System.out.printf("Request failed. Retrying (%s/%s)...%n", attempts, MAX_HTTP_REQUEST_ATTEMPTS);
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
