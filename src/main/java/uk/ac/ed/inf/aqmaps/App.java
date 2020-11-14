package uk.ac.ed.inf.aqmaps;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;
import java.util.stream.Collectors;

import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.geojson.BoundingBox;


public class App {	
	
	// Dimensions of bounding box
	private static final double NORTH_LATITUDE = 55.946233;
	private static final double SOUTH_LATITUDE = 55.942617;
	private static final double WEST_LONGITUDE = -3.184319;
	private static final double EAST_LONGITUDE = -3.192473;
	
    public static void main( String[] args ) {
    	
    	var day = args[0];
    	var month = args[1];
    	var year = args[2];
    	var startLat = Double.parseDouble(args[3]);
    	var startLong = Double.parseDouble(args[4]);
    	// int seed = Integer.parseInt(args[5]);
    	var port = args[6];
    	
    	var webserver = WebServer.getInstanceWithConfig("http://localhost", port);
    	
    	// Gets sensors and noFlyZones from the web server
    	List<Sensor> sensors = null;
    	List<Polygon> noFlyZones = null;
    	try {
    		sensors = webserver.getSensorData(day, month, year);
    		noFlyZones = webserver.getNoFlyZones();
    	} catch (UnexpectedHTTPResponseException e) {
    		System.out.println(e.getMessage());
    		System.exit(1);
    	}
    	
    	// Should you always print exception message? 
    	
    	// Casts from Sensor (subclass) to Waypoint (superclass) for FlightPlanner
    	List<Waypoint> waypoints = sensors.stream()
    			.map(sensor -> (Waypoint) sensor)
    			.collect(Collectors.toList());
    	
    	// Plans a route through all the sensors that the drone can take
    	var startPoint = Point.fromLngLat(startLong, startLat);
    	var route = FlightPlanner.twoOptPath(startPoint, waypoints);
    	
    	// Defines the area that the drone cannot leave
    	var droneConfinementArea = BoundingBox.fromLngLats(WEST_LONGITUDE, SOUTH_LATITUDE, EAST_LONGITUDE, NORTH_LATITUDE);
    	
    	var pilot = new Pilot(new Drone(startPoint), noFlyZones, droneConfinementArea);
    	
    	boolean completed = pilot.followRoute(route);
    	if (!completed) {
    		System.out.println("Did not manage to return within 150 moves. Map and log will still be written to file.");
    	}
    	
    	var map = FlightMap.generateFromFlightData(pilot.getPath(), pilot.getSensorReports(), noFlyZones);
    	
    	try {
    		writeFile(String.format("flightpath-%s-%s-%s.txt", day, month, year), pilot.getLog());
    		writeFile(String.format("readings-%s-%s-%s.geojson", day, month, year), map);
    	} catch (IOException e) {
    		System.out.println("Fatal error: Failed to write output files. Exiting...");
    		System.exit(1);
    	}
    }
    
    // Check the throws thing, don't always need to
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
