package uk.ac.ed.inf.aqmaps;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;
import com.mapbox.geojson.BoundingBox;
import com.mapbox.turf.TurfJoins;



public class App {	
		
    public static void main( String[] args ) throws IOException, InterruptedException {
    	
    	String day = args[0];
    	String month = args[1];
    	String year = args[2];
    	var startLat = Double.parseDouble(args[3]);
    	var startLng = Double.parseDouble(args[4]);
    	var seed = Integer.parseInt(args[5]);
    	String port = args[6];
    	
    	var startPoint = Point.fromLngLat(startLng, startLat);
    	
    	var webserver = Webserver.getInstance();
    	webserver.configure("http://localhost", port);
    	
//    	var sensors = webserver.getSensorData(day, month, year);
    	
    	
    	List<Polygon> nfzs = null;
    	
    	try {
    		nfzs = webserver.getNoFlyZones();
    	} catch (UnexpectedHTTPResponseException e) {
    		System.out.println("shit");
    		System.exit(1);
    	}
    	

    	
    		fly(webserver, nfzs);
    	
//    	
//    	var pilot = new Pilot(new Drone(startPoint, sensors, noFlyZoneChecker));
//    	
//    	var route = new FlightPlanner(sensors, noFlyZoneChecker).greedyPath(startPoint);
//    	
//    	pilot.followRoute(route);
//
//    	var gjg = new GeojsonGenerator(pilot.getPath(), pilot.getSensorReports(), nfzs);
//    	
//    	writeFile(String.format("flightpath-%s-%s-%s.txt", day, month, year), pilot.getLog());
//    	writeFile(String.format("readings-%s-%s-%s.geojson", day, month, year), new ArrayList<String>(Arrays.asList(gjg.generateMap())));
//    	
//    	System.out.println("DONE");
    	
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
    
    private static void writeFile(String filename, List<String> lines) throws IOException {
		var file = new File(filename);
		if (file.exists()) {
			file.delete();
		}
		file.createNewFile();
		
    	var writer = new BufferedWriter(new FileWriter(filename));
    	for (var line : lines) {
    		writer.write(line);
    		writer.newLine();
    	}
    	writer.close();
    }
    
    private static void fly(Webserver web, List<Polygon> nfzs) throws IOException, InterruptedException {
    	
    	double count = 0;
    	double avg = 0;
	    for (int y = 2020; y <= 2021; y++) {
			for (int m = 1; m <= 12; m++) {
				for (int d = 1; d <= 31; d++) {
					
					List<Sensor> sensors;
					
					try {
						sensors = web.getSensorData(String.format("%02d", d), String.format("%02d", m), Integer.toString(y));
					} catch (UnexpectedHTTPResponseException e) {
						continue;
					}
					
			    	var test = new Random(System.currentTimeMillis());
			    	
			    	Point startPoint = null;
			    	
			    	var fine = false;
			    	while (!fine) {
			    		
			    		fine = true;
			    		startPoint = Point.fromLngLat(randlong(test), randlat(test));
			    		if (TurfJoins.inside(startPoint, nfzs.get(0))) fine = false;
			    		if (TurfJoins.inside(startPoint, nfzs.get(1))) fine = false;
			    		if (TurfJoins.inside(startPoint, nfzs.get(2))) fine = false;
			    		if (TurfJoins.inside(startPoint, nfzs.get(3))) fine = false;
			    		
			    	}
					
					
			    	startPoint = Point.fromLngLat(-3.188613902367063, 55.94298190554946);
			    	startPoint = Point.fromLngLat(-3.1900330714830343, 55.94428188653317);
			    	startPoint = Point.fromLngLat(-3.190883027742702, 55.946091804161746);
			    	startPoint = Point.fromLngLat(-3.1850482878705586, 55.94600155706771);
			    	startPoint = Point.fromLngLat(-3.1866978391282847, 55.944058236553516);
			    	startPoint = Point.fromLngLat(-3.1895788643733054, 55.946092672971474);
			    	
			    	// All fine!
					
					List<Feature> features = new ArrayList<>();
					for (var s : sensors) {
						features.add(Feature.fromGeometry(s.getPoint()));
					}
					
					
					
					count += 1;
					
					
					var drone = new Drone(startPoint);
					
			    	var pilot = new Pilot(drone, nfzs, BoundingBox.fromLngLats(-3.192473, 55.942617, -3.184319, 55.946233));
			    	
			    	List<Waypoint> penis = new ArrayList<>();
			    	for (var s : sensors) {
			    		penis.add((Waypoint) s);
			    	}
			    	
			    	var route = new FlightPlanner(penis).twoOptPath(startPoint);
			    	
			    	features.add(Feature.fromGeometry(LineString.fromLngLats(route.stream().map(Waypoint::getPoint).collect(Collectors.toList()))));
			    				    	
			    	boolean arrived = pilot.followRoute(route);

			    	var gjg = new GeojsonGenerator(pilot.getPath(), pilot.getSensorReports(), nfzs).generateMap();
			    	

			    	if (drone.getTimesMoved() >= 150) {
			    		System.out.println("AHHHHH");
			    		System.out.println(count);
			    		System.out.println(gjg);
			    		System.out.println(startPoint.toString());
			    		System.out.println(d);
			    		System.out.println(m);
			    		System.out.println(y);
			    		
			    	}
			    	
//			    	System.out.println(drone.getTimesMoved());

			    	
			    	
			    	
			    	System.out.println(count);
//			    	System.out.println(FeatureCollection.fromFeatures(features).toJson());
			    	avg += drone.getTimesMoved();
//			    	TimeUnit.MILLISECONDS.sleep(10);
//			    	System.in.read();
			    	
				}
			}
		}
	    System.out.println(avg/count);
    }
    
}
