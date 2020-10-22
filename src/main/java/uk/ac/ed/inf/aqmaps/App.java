package uk.ac.ed.inf.aqmaps;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import com.mapbox.geojson.Point;

public class App {	
	
    public static void main( String[] args ) throws IOException, InterruptedException {
    	

		var web = new Webserver("http://localhost", "80");

    	double allCount = 0;
    	double overCount = 0;
		
    	var nfzs = web.getNoFlyZones();    
    	var nfzc = new NoFlyZoneChecker(nfzs);
    	
    	for (int y = 2020; y <= 2020; y++) {
    		for (int m = 1; m <= 1; m++) {
    			for (int d = 4; d <= 4; d++) {
    				
    				HashMap<Point, Sensor> sensors;
    				
    				try {
    					sensors = web.getSensorData(String.format("%02d", d), String.format("%02d", m), Integer.toString(y));
    				} catch (FileNotFoundException e) {
    					continue;
    				}
    				
    				var drone = new Drone(Point.fromLngLat(-3.1878, 55.9444), sensors, nfzc);
    			
    								
    				var test = new DronePilot(drone, sensors, nfzc);
    				
    				var path = test.followPath(test.greedyPlan());
    				
    				var gjg = new GeojsonGenerator(path, sensors, nfzs);
    				System.out.println(gjg.generateMap());
    				System.out.println(drone.timesMoved);
    				
    				TimeUnit.SECONDS.sleep(1);
    				

    				
    			}
    		}
    	}
    	
    	
    	
    }
    
}
