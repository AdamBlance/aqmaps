package uk.ac.ed.inf.aqmaps;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import com.mapbox.geojson.Point;

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
    	
    	var webserver = new Webserver("http://localhost:" + port);
    	
    	var sensors = webserver.getSensorData(day, month, year);
    	var noFlyZoneChecker = new NoFlyZoneChecker(webserver.getNoFlyZones());
    	
    	var drone = new Drone(startPoint, sensors, noFlyZoneChecker);
    	
    	var planner = new FlightPlanner(sensors, noFlyZoneChecker);
    	var route = planner.greedyPath(startPoint);
    	
    	drone.followPath(route);
    	
    }
    
//    private void helper() throws IOException {
//		var web = new Webserver("http://localhost:80");
//		
//    	var nfzs = web.getNoFlyZones();    
//    	var nfzc = new NoFlyZoneChecker(nfzs);
//    	
//    	for (int y = 2020; y <= 2021; y++) {
//    		for (int m = 1; m <= 12; m++) {
//    			for (int d = 1; d <= 31; d++) {
//    				
//    				HashMap<Point, SensorData> sensors;
//    				
//    				try {
//    					sensors = web.getSensorData(String.format("%02d", d), String.format("%02d", m), Integer.toString(y));
//    				} catch (FileNotFoundException e) {
//    					continue;
//    				}
//    				
//    				var drone = new Drone(Point.fromLngLat(-3.1878, 55.9444), sensors, nfzc);
//    							
//    				var test = new DronePilot(drone, sensors, nfzc);
//    				
//    				var path = test.followPath(test.greedyPlan());
//    				
//    				var gjg = new GeojsonGenerator(path, sensors, nfzs);
//    				
//    				int count = drone.getTimesMoved();
//    				
//    				System.out.println(count);
//    				if (true) {
//    					System.out.println(gjg.generateMap());
//    				}					
//    			}
//    		}
//    	}
//    }
}
