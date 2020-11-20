package uk.ac.ed.inf.aqmaps;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.geojson.BoundingBox;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;


public class App {	
	
	// Dimensions of bounding box
	private static final double NORTH_LATITUDE = 55.946233;
	private static final double SOUTH_LATITUDE = 55.942617;
	private static final double EAST_LONGITUDE = -3.184319;
	private static final double WEST_LONGITUDE = -3.192473;
	
	private static final boolean INCLUDE_NO_FLY_ZONES_IN_MAP = true;
	
    public static void main( String[] args ) {
    	var day = args[0];
    	var month = args[1];
    	var year = args[2];
    	var startLat = Double.parseDouble(args[3]);
    	var startLong = Double.parseDouble(args[4]);
    	// int seed = Integer.parseInt(args[5]);
    	var port = args[6];
    	
    	var webServer = WebServer.getInstanceWithConfig("http://localhost", port);
    	
    	// Gets sensors and noFlyZones from the web server
    	List<Sensor> sensors = null;
    	List<Polygon> noFlyZones = null;
    	try {
    		sensors = webServer.getSensorData(day, month, year);
    		noFlyZones = webServer.getNoFlyZones();
    	} catch (UnexpectedHTTPResponseException e) {
    		System.out.println(e.getMessage());
    		System.exit(1);
    	}
    	    	
    	// Casts from Sensor (subclass) to Waypoint (superclass) for FlightPlanner
    	List<Waypoint> waypoints = sensors.stream()
    			.map(sensor -> (Waypoint) sensor)
    			.collect(Collectors.toList());
    	
    	// Plans an efficient through all the sensors, taking into account where the drone should start and end
    	var startPoint = Point.fromLngLat(startLong, startLat);
    	var route = FlightPlanner.twoOptPath(startPoint, waypoints);
    	
//    	System.out.println();
    	
    	// Defines the area that the drone cannot leave
    	var droneConfinementArea = BoundingBox.fromLngLats(WEST_LONGITUDE, SOUTH_LATITUDE, EAST_LONGITUDE, NORTH_LATITUDE);
    	
    	// Creates drone with the specified initial position
    	var drone = new Drone(startPoint);
    	
    	// Creates our pilot object, giving it our no-fly-zones and the confinement area so it knows where not to send the drone
    	var pilot = new Pilot(drone, noFlyZones, droneConfinementArea);
    	
    	// Tells the pilot to attempt to navigate our route
    	boolean completed = pilot.followRoute(route);
    	if (!completed) {
    		System.out.println("Did not manage to return within 150 moves. Map and log will still be written to file.");
    	}
    	    	
    	// Generates our geojson output map from the data we've gathered
    	var map = FlightMap.generateFromFlightData(pilot.getPath(), pilot.getSensorReports());
    	
    	// You can change this constant to add the no-fly-zones to the output map
    	// I could have left this out in my submission but maybe it's helpful to someone
    	if (INCLUDE_NO_FLY_ZONES_IN_MAP) {
    		var mapFeatures = new ArrayList<Feature>(map.features());
    		var noFlyZoneFeatures = noFlyZones.stream()
    				.map(poly -> Feature.fromGeometry(poly))
    				.collect(Collectors.toList());
    		mapFeatures.addAll(noFlyZoneFeatures);
    		map = FeatureCollection.fromFeatures(mapFeatures);
    	}
    	
    	// Attempts to write output files
    	try {
    		writeFile(String.format("flightpath-%s-%s-%s.txt", day, month, year), pilot.getLog());
    		writeFile(String.format("readings-%s-%s-%s.geojson", day, month, year), map.toJson());
    	} catch (IOException e) {
    		System.out.println("Fatal error: Failed to write output files. Exiting...");
    		System.exit(1);
    	}
    }
    
    // Writes a string to a file, overwriting any existing file with the same filename
    private static void writeFile(String filename, String contents) throws IOException {
		var file = new File(filename);
		if (file.exists()) {
			file.delete();
		}
		file.createNewFile();
		
    	var writer = new FileWriter(filename);
    	writer.write(contents);
    	writer.close();
    }
    
}
