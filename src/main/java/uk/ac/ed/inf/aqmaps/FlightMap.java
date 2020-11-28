package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;

public class FlightMap {
	
	private static final String[] HUES = {"#00ff00","#40ff00","#80ff00","#c0ff00","#ffc000","#ff8000","#ff4000","#ff0000"};
	private static final String GREY = "#aaaaaa";
	private static final String BLACK = "#000000";
	
	public static FeatureCollection generateFromFlightData(List<Point> flightpath, HashMap<Sensor, Boolean> sensorsAndVisitedStatus) {
		
		var allFeatures = new ArrayList<Feature>();

		// Uses both the sensor (key) and whether or not it was visited (value) to create each marker
		var markerFeatures = createMarkerFeatures(sensorsAndVisitedStatus);
		allFeatures.addAll(markerFeatures);
		
		// Line showing the drone's path
		var flightpathFeature = Feature.fromGeometry(LineString.fromLngLats(flightpath));
		allFeatures.add(flightpathFeature);
				
		return FeatureCollection.fromFeatures(allFeatures);
	}
	
	private static List<Feature> createMarkerFeatures(HashMap<Sensor, Boolean> sensorsAndVisitedStatus) {
		
		var markerFeatures = new ArrayList<Feature>();
		
		for (var sensor : sensorsAndVisitedStatus.keySet()) {  // For each sensor
			
			boolean visited = sensorsAndVisitedStatus.get(sensor);  // Get whether it was visited 
			
			var marker = Feature.fromGeometry(sensor.getPoint());
			
			marker.addStringProperty("location", sensor.getW3wAddress());
			
			if (!visited) {
				marker.addStringProperty("rgb-string", GREY);
				marker.addStringProperty("marker-color", GREY);
			} else if (sensor.getBattery() < 10.0) {
				marker.addStringProperty("rgb-string", BLACK);
				marker.addStringProperty("marker-color", BLACK);
				marker.addStringProperty("marker-symbol", "cross");
			} else {
				var reading = sensor.getReading();
				var colour = pollutionToColour(reading);
				
				marker.addStringProperty("rgb-string", colour);
				marker.addStringProperty("marker-color", colour);
				marker.addStringProperty("marker-symbol", reading < 128 ? "lighthouse" : "danger");	
			}
			
			markerFeatures.add(marker);
		}
		return markerFeatures;
	}
	
	private static String pollutionToColour(double reading) {
		// Integer division to get the right colour
		// Coursework says the max is 256.0 (not 255.0), in that case just set to bright red (7)
		int index = (reading <= 255.0) ? ((int) reading) / 32 : 7; 
		return HUES[index];
	}
	
}
