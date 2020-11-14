package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

public class FlightMap {
	
	private static final String[] HUES = {"#00ff00","#40ff00","#80ff00","#c0ff00","#ffc000","#ff8000","#ff4000","#ff0000"};
	private static final String GREY = "#aaaaaa";
	private static final String BLACK = "#000000";
	
	public static String generateFromFlightData(List<Point> flightpath, HashMap<Sensor, SensorReport> sensorReports, List<Polygon> nfzs) {
		
		var markerFeatures = createMarkerFeatures(sensorReports);
		var flightpathFeature = Feature.fromGeometry(LineString.fromLngLats(flightpath));
		
		var allMapFeatures = new ArrayList<Feature>();
		allMapFeatures.addAll(markerFeatures);
		allMapFeatures.add(flightpathFeature);
		
		for (var p : nfzs) {
			allMapFeatures.add(Feature.fromGeometry(p));
		}
		
		return FeatureCollection.fromFeatures(allMapFeatures).toJson();
	}
	
	private static List<Feature> createMarkerFeatures(HashMap<Sensor, SensorReport> sensorReports) {
		
		var markerFeatures = new ArrayList<Feature>();
		for (var sensor: sensorReports.keySet()) {
			var report = sensorReports.get(sensor);
			
			var marker = Feature.fromGeometry(sensor.getPoint());
//			var circ = Feature.fromGeometry(TurfTransformation.circle(sensor.getPoint(), 0.0002, 20, TurfConstants.UNIT_DEGREES));
			marker.addStringProperty("location", sensor.getW3wAddress());
			
			// If sensor wasn't visited, make it grey
			// If reading was invalid, make it black and give it a cross
			if (!report.isVisited()) {
				marker.addStringProperty("rgb-string", GREY);
				marker.addStringProperty("marker-color", GREY);
			} else if (!report.isValid()) {
				marker.addStringProperty("rgb-string", BLACK);
				marker.addStringProperty("marker-color", BLACK);
				marker.addStringProperty("marker-symbol", "cross");
			} else {
				double reading = sensor.getReading();
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
		// Coursework says the max is 256.0 so I explicitly check for that (but I think it's a typo)
		int index = (reading <= 255.0) ? ((int) reading) / 32 : 7; 
		return HUES[index];
	}
	
}
