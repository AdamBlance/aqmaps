package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

public class GeojsonGenerator {

	List<Point> flightpath;
	HashMap<Point,Sensor> visited;
	List<Polygon> nfzs;
	static final String[] COLOURS = {"#00ff00","#40ff00","#80ff00","#c0ff00","#ffc000","#ff8000","#ff4000","#ff0000"};

	
	public GeojsonGenerator(List<Point> flightpath, HashMap<Point,Sensor> visited, List<Polygon> nfzs) {
		this.flightpath = flightpath;
		this.visited = visited;
		this.nfzs = nfzs;
	}
	
	// This is stupid but for the now:
	// - Battery = -1 if not visited
	
	public String generateMap() {
		
		var allMarkers = new ArrayList<Feature>();
		
		for (var entry : visited.entrySet()) {
			var point = entry.getKey();
			var sensor = entry.getValue();
			
			var marker = Feature.fromGeometry(point);
			
			marker.addStringProperty("location", sensor.getLocation());
			
			if (sensor.getBattery() == -1) {
				marker.addStringProperty("rgb-string", "#aaaaaa");
				marker.addStringProperty("marker-color", "#aaaaaa");
				
			} else if (sensor.getBattery() < 10) {
				marker.addStringProperty("rgb-string", "#000000");
				marker.addStringProperty("marker-color", "#000000");
				marker.addStringProperty("marker-symbol", "cross");
				
			} else {
				// This assumes that 255 is the max, not 256. If it breaks, we know why.
				var i = ((int) Double.parseDouble(sensor.getReading())) / 32;
				var colour = COLOURS[i];
				
				marker.addStringProperty("rgb-string", colour);
				marker.addStringProperty("marker-color", colour);
				marker.addStringProperty("marker-symbol", i<=3 ? "lighthouse" : "danger");	
			}
			allMarkers.add(marker);
		}
		
		var flightLine = Feature.fromGeometry(LineString.fromLngLats(flightpath));
		allMarkers.add(flightLine);
		
		for (var p : nfzs) {
			allMarkers.add(Feature.fromGeometry(p));
		}
		
		var markerFeature = FeatureCollection.fromFeatures(allMarkers);
//		System.out.println(markerFeature.toJson());
		
		
		
		return markerFeature.toJson();
		
	}
	
}
