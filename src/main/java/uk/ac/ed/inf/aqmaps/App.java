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

import static uk.ac.ed.inf.aqmaps.PointUtils.pointStrictlyInsideBoundingBox;

public class App {	
	
	// Dimensions of bounding box
	private static final double NORTH_LATITUDE = 55.946233;
	private static final double SOUTH_LATITUDE = 55.942617;
	private static final double EAST_LONGITUDE = -3.184319;
	private static final double WEST_LONGITUDE = -3.192473;
	
	private static final BoundingBox droneConfinementArea = 
			BoundingBox.fromLngLats(WEST_LONGITUDE, SOUTH_LATITUDE, EAST_LONGITUDE, NORTH_LATITUDE);
	
	private static final boolean INCLUDE_NO_FLY_ZONES_IN_MAP = true;
	
	private static List<Waypoint> waypoints = null;
	private static List<Polygon> noFlyZones = null;
	
    public static void main( String[] args ) {
    	var day = args[0];
    	var month = args[1];
    	var year = args[2];
    	var startLat = Double.parseDouble(args[3]);
    	var startLong = Double.parseDouble(args[4]);
    	int seed = Integer.parseInt(args[5]);  // unused
    	var port = args[6];
    	
    	// Before we do anything, make sure that the provided start location is legal
    	var startPoint = Point.fromLngLat(startLong, startLat);
    	exitIfInvalid(startPoint);
    	
    	// Populates fields "waypoints" and "noFlyZones"
    	retrieveRelevantData(day, month, year, port);
    	
    	var drone = new Drone(startPoint);
    	var pilot = new Pilot(drone, noFlyZones, droneConfinementArea);
    	
    	// Plans a 2-Opt optimised route
    	var route = FlightPlanner.twoOptPath(startPoint, waypoints);

    	attemptFlight(pilot, route);

    	outputResults(pilot, day, month, year);
    }
    
    private static void exitIfInvalid(Point p) {
       	if (!pointStrictlyInsideBoundingBox(p, droneConfinementArea)) {
    		System.out.printf("Fatal error: Cannot start navigation at %f, %f (outside drone confinement area). Exiting...", 
    				p.latitude(), 
    				p.longitude());
    		System.exit(1);
    	}
    }
    
    private static void attemptFlight(Pilot pilot, List<Waypoint> route) {
    	boolean completed = pilot.followRoute(route);
    	if (!completed) {
    		System.out.printf(
    				"Did not manage to return within %d moves. Map and log will still be generated.", 
    				Drone.MAX_MOVES);
    	}
	}

	private static void retrieveRelevantData(String day, String month, String year, String port) {
    	var webServer = WebServer.getInstanceWithConfig("http://localhost", port);
       	try {
    		var sensors = webServer.getSensorData(day, month, year);
        	// Casts from Sensor (subclass) to Waypoint (superclass) for FlightPlanner
        	waypoints = sensors.stream()
        			.map(sensor -> (Waypoint) sensor)
        			.collect(Collectors.toList());
    		noFlyZones = webServer.getNoFlyZones();
    	} catch (UnexpectedHTTPResponseException e) {
    		System.out.println(e.getMessage());
    		System.exit(1);
    	}		
	}

    private static void outputResults(Pilot pilot, String day, String month, String year) {
    	var map = FlightMap.generateFromFlightData(pilot.getPath(), pilot.getSensorReports());
    	
    	// I could have left this out in my submission but maybe it's helpful to someone
    	if (INCLUDE_NO_FLY_ZONES_IN_MAP) {
    		var mapFeatures = new ArrayList<Feature>(map.features());  
    		var noFlyZoneFeatures = noFlyZones.stream()
    				.map(poly -> Feature.fromGeometry(poly))
    				.collect(Collectors.toList());
    		mapFeatures.addAll(noFlyZoneFeatures);
    		map = FeatureCollection.fromFeatures(mapFeatures);
    	}
    	
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
