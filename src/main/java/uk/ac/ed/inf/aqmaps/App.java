package uk.ac.ed.inf.aqmaps;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.stream.Collectors;

import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.turf.TurfJoins;
import com.mapbox.geojson.BoundingBox;
import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;

public class App {	
	
	// Dimensions of bounding box
	private static final double NORTH_LATITUDE = 55.946233;
	private static final double SOUTH_LATITUDE = 55.942617;
	private static final double EAST_LONGITUDE = -3.184319;
	private static final double WEST_LONGITUDE = -3.192473;
	
	private static final BoundingBox droneConfinementArea = 
			BoundingBox.fromLngLats(WEST_LONGITUDE, SOUTH_LATITUDE, EAST_LONGITUDE, NORTH_LATITUDE);
	
	private static final boolean INCLUDE_NO_FLY_ZONES_IN_MAP = true;
	
	public static double moves = 0;
	
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
    	
//    	day = "06";
//    	month = "09";
//    	year = "2021";
    	
    	if (!retrieveRelevantData(day, month, year, port)) {
    		moves = -1;
    		return;
    	}

//    	exitIfInvalid(startPoint);
    	
    	startingPoint = getRandPoint(noFlyZones);
    	exitIfInvalid(startingPoint);
    	
//    	startPoint = Point.fromJson("{\"type\":\"Point\",\"coordinates\":[-3.1886875,55.9457096]}");
    	
    	// Plans a 2-Opt optimised route
    	var route = FlightPlanner.twoOptPath(startingPoint, sensors);

    	var drone = new Drone(startingPoint);
    	var pilot = new Pilot(drone, noFlyZones, droneConfinementArea);
    	
    	boolean yay = attemptFlight(pilot, route);

    	moves = drone.getTimesMoved();
    	
    	if (moves >= 110 || !yay) {
    		outputResults(pilot, day, month, year);
    		System.out.printf("%s/%s/%s - %s%n", day, month, year, Feature.fromGeometry(startingPoint).toJson());
    		System.out.println(startingPoint.longitude());
    		System.out.println(startingPoint.latitude());
    		System.out.println(pilot.getPathTaken().get(0).longitude());
    		System.out.println(pilot.getPathTaken().get(0).latitude());
    		System.out.println(moves);
    	}
    	
//    	var times = drone.getTimesMoved();
//    	System.out.printf("Drone completed flight in %d/%d moves.%n", 
//    			drone.getTimesMoved(),
//    			Drone.MAX_MOVES);
    	
    }
    
    private static double randlong(Random r) {
    	double left = -3.192473;
    	double right = -3.184319;
    	return left + r.nextDouble() * (right-left);
    }
    
    private static double randlat(Random r) {
    	double down = 55.942617;
    	double up = 55.946233;
    	return down + r.nextDouble() * (up-down);
    }
    
    private static Point getRandPoint(List<Polygon> nfzs) {
    	var test = new Random();
    	var fine = false;
    	Point p = null;
    	while (!fine) {
    		
    		fine = true;
    		p = Point.fromLngLat(randlong(test), randlat(test));
    		if (TurfJoins.inside(p, nfzs.get(0))) fine = false;
    		if (TurfJoins.inside(p, nfzs.get(1))) fine = false;
    		if (TurfJoins.inside(p, nfzs.get(2))) fine = false;
    		if (TurfJoins.inside(p, nfzs.get(3))) fine = false;
    		
    	}
    	return p;
    	
    }
    

    
    private static void exitIfInvalid(Point p) {
       	for (var zone : noFlyZones) {
       		if (TurfJoins.inside(p, zone)) {
        		System.out.printf("Fatal error: Cannot start navigation at %f, %f (inside a no-fly-zone). Exiting...%n", 
        				p.latitude(), 
        				p.longitude());
        		System.exit(1);
       		}
       	}
    }
    
    private static boolean attemptFlight(Pilot pilot, List<Sensor> route) {
    	boolean completed = pilot.followRoute(route);
    	if (!completed) {
    		System.out.printf("Did not manage to return within %d moves. Map and log will still be generated.%n", 
    				Drone.MAX_MOVES);
    		return false;
    	}
    	return true;
	}

	private static boolean retrieveRelevantData(String day, String month, String year, String port) {
    	var webServer = WebServer.getInstanceWithConfig("http://localhost", port);
       	try {
    		sensors = webServer.getSensors(day, month, year);
    		noFlyZones = webServer.getNoFlyZones();
    		return true;
    	} catch (UnexpectedHTTPResponseException e) {
    		return false;
//    		System.out.println(e.getMessage());
//    		System.exit(1);
    	}		
	}

    private static void outputResults(Pilot pilot, String day, String month, String year) {
    	var map = FlightMap.generateFromFlightData(pilot.getPathTaken(), pilot.getSensorsVisited());
    	
    	// I could have left this out in my submission but maybe it's helpful to someone
    	if (INCLUDE_NO_FLY_ZONES_IN_MAP) {
    		var mapFeatures = new ArrayList<Feature>(map.features());  
    		var noFlyZoneFeatures = noFlyZones.stream()
    				.map(poly -> Feature.fromGeometry(poly))
    				.collect(Collectors.toList());
    		mapFeatures.addAll(noFlyZoneFeatures);
    		
    		var line = LineString.fromLngLats(new ArrayList<Point>(Arrays.asList(
    				droneConfinementArea.northeast(),
    				Point.fromLngLat(WEST_LONGITUDE, NORTH_LATITUDE),
    				droneConfinementArea.southwest(),
    				Point.fromLngLat(EAST_LONGITUDE, SOUTH_LATITUDE),
    				droneConfinementArea.northeast())));
    		
    		mapFeatures.add(Feature.fromGeometry(line));
    				
    		map = FeatureCollection.fromFeatures(mapFeatures);
    	}
    	
    	System.out.println(map.toJson());
    	
//    	var flightpathFname = String.format("flightpath-%s-%s-%s.txt", day, month, year);
//    	var readingsFname =  String.format("readings-%s-%s-%s.geojson", day, month, year);
//    	try {
//    		writeFile(flightpathFname, pilot.getLog());
//    		writeFile(readingsFname, map.toJson());
//    	} catch (IOException e) {
//    		System.out.println("Fatal error: Failed to write output files. Exiting...");
//    		System.exit(1);
//    	}
//    	
//    	System.out.printf("%s and %s created successfully!%n", flightpathFname, readingsFname);
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
