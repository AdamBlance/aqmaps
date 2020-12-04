package uk.ac.ed.inf.aqmaps;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.geojson.BoundingBox;

public class App {	
	
	// Dimensions of bounding box
	private static final double NORTH_LATITUDE = 55.946233;
	private static final double SOUTH_LATITUDE = 55.942617;
	private static final double EAST_LONGITUDE = -3.184319;
	private static final double WEST_LONGITUDE = -3.192473;
	
	private static final BoundingBox droneConfinementArea = 
			BoundingBox.fromLngLats(WEST_LONGITUDE, SOUTH_LATITUDE, EAST_LONGITUDE, NORTH_LATITUDE);
	
	private static List<Sensor> sensors = null;
	private static List<Polygon> noFlyZones = null;
	
    public static void main( String[] args ) {
    	var day = args[0];
    	var month = args[1];
    	var year = args[2];
    	var startingPoint = Point.fromLngLat(
    			Double.parseDouble(args[4]), 
    			Double.parseDouble(args[3]));
    	var port = args[6];
    	
    	// Populates fields "waypoints" and "noFlyZones"  	
    	retrieveRelevantData(day, month, year, port);
    	
    	// Plans a 2-opt optimised route
    	var route = FlightPlanner.twoOptPath(startingPoint, sensors);

    	// Creates the drone with the initial position startingPoint
    	var drone = new Drone(startingPoint);
    	
    	// Creates the pilot, assigning it a drone and setting its constraints 
    	var pilot = new Pilot(drone, noFlyZones, droneConfinementArea);
    	
    	// Start the flight!
    	attemptFlight(pilot, route);
    	
    	System.out.printf("Drone used %d of %d moves.%n", drone.getTimesMoved(), Drone.MAX_MOVES);
    	
    	// Create and write to appropriate files
    	outputResults(pilot, day, month, year);
    }

    private static void retrieveRelevantData(String day, String month, String year, String port) {
		var webServer = WebServer.getInstanceWithConfig("http://localhost", port);
	   	try {
			sensors = webServer.getSensors(day, month, year);
			noFlyZones = webServer.getNoFlyZones();
		} catch (UnexpectedHTTPResponseException e) {
			System.out.println(e.getMessage());
			System.exit(1);
		}		
	}

	private static boolean attemptFlight(Pilot pilot, List<Sensor> route) {
    	boolean completed = pilot.followRoute(route);
    	if (!completed) {
    		System.out.printf("Did not manage to return to starting point. Map and log will still be generated.%n", Drone.MAX_MOVES);
    		return false;
    	}
    	return true;
	}

	private static void outputResults(Pilot pilot, String day, String month, String year) {
    	var map = FlightMap.generateFromFlightData(pilot.getPathTaken(), pilot.getSensorsVisited());
    	
    	var flightpathFname = String.format("flightpath-%s-%s-%s.txt", day, month, year);
    	var readingsFname =  String.format("readings-%s-%s-%s.geojson", day, month, year);
    	try {
    		writeFile(flightpathFname, pilot.getLog());
    		writeFile(readingsFname, map.toJson());
    	} catch (IOException e) {
    		System.out.println("Fatal error: Failed to write output files. Exiting...");
    		System.exit(1);
    	}
    	
    	System.out.printf("%s and %s created successfully!%n", flightpathFname, readingsFname);
    }
    
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
