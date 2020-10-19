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
    	
    	System.out.println(nfzc.isMoveLegal(
    			Point.fromLngLat(-3.1878, 55.9444),
    			Point.fromLngLat(-3.1884, 55.9443)));
    	
    	for (int y = 2020; y <= 2021; y++) {
    		for (int m = 1; m <= 12; m++) {
    			for (int d = 1; d <= 31; d++) {
    				
    				HashMap<Point, Sensor> sensors;
    				
    				try {
    					sensors = web.getSensorData(String.format("%02d", d), String.format("%02d", m), Integer.toString(y));
    				} catch (FileNotFoundException e) {
    					continue;
    				}
    				
    				var drone = new Drone(Point.fromLngLat(-3.1878, 55.9444), sensors);
    			
    								
    				var test = new DronePilot(drone, sensors, nfzc);
    				
    				var gjg = new GeojsonGenerator(test.greedyPlan(), sensors, nfzs);
    				System.out.println(gjg.generateMap());
    				
    				TimeUnit.SECONDS.sleep(1);
    				

    				
    			}
    		}
    	}
    	
    	System.out.println((overCount/allCount)*100);
    	
    }
}
