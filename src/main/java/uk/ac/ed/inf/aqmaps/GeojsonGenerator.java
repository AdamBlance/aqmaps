package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.turf.TurfConstants;
import com.mapbox.turf.TurfTransformation;

public class GeojsonGenerator {

	List<Point> flightpath;
	HashMap<Sensor, SensorReport> visited;
	List<Polygon> nfzs;
	static final String[] COLOURS = {"#00ff00","#40ff00","#80ff00","#c0ff00","#ffc000","#ff8000","#ff4000","#ff0000"};

	
	public GeojsonGenerator(List<Point> flightpath, HashMap<Sensor, SensorReport> visited, List<Polygon> nfzs) {
		this.flightpath = flightpath;
		this.visited = visited;
		this.nfzs = nfzs;
	}
	
	// This is stupid but for the now:
	// - Battery = -1 if not visited
	
	public String generateMap() {
		
		var allFeatures = new ArrayList<Feature>();
		
		for (Sensor sensor: visited.keySet()) {
			
			SensorReport report = visited.get(sensor);
			
			var marker = Feature.fromGeometry(sensor.getPoint());
			
//			var circ = Feature.fromGeometry(TurfTransformation.circle(point, 0.0002, 20, TurfConstants.UNIT_DEGREES));
			
			marker.addStringProperty("location", sensor.getLocation());
			
			if (!report.getVisited()) {
				marker.addStringProperty("rgb-string", "#aaaaaa");
				marker.addStringProperty("marker-color", "#aaaaaa");
			} else if (!report.getValid()) {
				marker.addStringProperty("rgb-string", "#000000");
				marker.addStringProperty("marker-color", "#000000");
				marker.addStringProperty("marker-symbol", "cross");
			} else {
				var reading = Double.parseDouble(sensor.getReading());
				int i = (reading <= 255.0) ? ((int) reading) / 32 : 7;  // If less than 255, calculate colour. If greater, set max colour.
				var colour = COLOURS[i];
				marker.addStringProperty("rgb-string", colour);
				marker.addStringProperty("marker-color", colour);
				marker.addStringProperty("marker-symbol", i<=3 ? "lighthouse" : "danger");	
			}
			allFeatures.add(marker);
//			allMarkers.add(circ);
		}
		
		var flightLine = Feature.fromGeometry(LineString.fromLngLats(flightpath));
		allFeatures.add(flightLine);
		
		for (var p : nfzs) {
			allFeatures.add(Feature.fromGeometry(p));
		}
		
		var markerFeature = FeatureCollection.fromFeatures(allFeatures);
//		System.out.println(markerFeature.toJson());
		
		return markerFeature.toJson();
		
	}
	
	
	
}
