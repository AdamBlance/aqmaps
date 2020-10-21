package uk.ac.ed.inf.aqmaps;

import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

import com.mapbox.geojson.Point;

public class App {	
	
    public static void main( String[] args ) throws IOException, InterruptedException {
    	

//		var web = new Webserver("http://localhost", "80");
//
//    	double allCount = 0;
//    	double overCount = 0;
//		
//    	var nfzs = web.getNoFlyZones();    
//    	var nfzc = new NoFlyZoneChecker(nfzs);
//    	
//    	for (int y = 2020; y <= 2021; y++) {
//    		for (int m = 1; m <= 12; m++) {
//    			for (int d = 1; d <= 31; d++) {
//    				
//    				HashMap<Point, Sensor> sensors;
//    				
//    				try {
//    					sensors = web.getSensorData(String.format("%02d", d), String.format("%02d", m), Integer.toString(y));
//    				} catch (FileNotFoundException e) {
//    					continue;
//    				}
//    				
//    				var drone = new Drone(Point.fromLngLat(-3.1878, 55.9444), sensors);
//    			
//    								
//    				var test = new DronePilot(drone, sensors, nfzc);
//    				
//    				var gjg = new GeojsonGenerator(test.greedyPlan(), sensors, nfzs);
//    				System.out.println(gjg.generateMap());
//    				
//    				TimeUnit.SECONDS.sleep(1);
//    				
//
//    				
//    			}
//    		}
//    	}
//    	
//    	

    	// Get theta and round to the nearest 10
    	int temp = (int) Math.round(Math.toDegrees(Math.atan2(1, -1)) / 10.0) * 10;
    	
    	System.out.println(temp);
    	
    	// Offset polar angles by 90 degrees then convert anti-clockwise to clockwise
    	var bearing = 360 - Math.floorMod(temp - 90, 360);
    	
    	System.out.println(bearing);

    	
    	
    }
    
}
