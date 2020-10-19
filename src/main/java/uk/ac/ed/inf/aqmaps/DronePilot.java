package uk.ac.ed.inf.aqmaps;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import java.lang.Math;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;


public class DronePilot {

	// This should take in the start point, sensor points and try to devise a good path
	
	private Drone drone;
	private NoFlyZoneChecker noFlyZoneChecker;
	private HashMap<Point, Sensor> sensors;	
	
	private List<Point> flightPlan;
	
	public DronePilot(Drone drone, HashMap<Point, Sensor> sensors, NoFlyZoneChecker noFlyZoneChecker) {
		this.noFlyZoneChecker = noFlyZoneChecker;
		this.sensors = sensors;
		this.drone = drone;
	}
	
	// Should return a greedy flight path between all the sensors, I guess ordered by their coords? 
	public List<Point> greedyPlan() {

		// Would be much quicker to use hashmaps I think?
		var startingPos = drone.getPosition();

		var dronePath = new ArrayList<Point>();
		dronePath.add(startingPos);
		
		var curr = startingPos;
		double minDistance;
		Point minSensor;
		
		// For every single sensor
		for (int i = 0; i < 33; i++) {
		
			minDistance = Double.MAX_VALUE;
			minSensor = Point.fromLngLat(0, 0);
			
			for (Point sensor : sensors.keySet()) {
				var dist = distance(curr, sensor);
				if (dist < minDistance) {
					if (!dronePath.contains(sensor) && noFlyZoneChecker.isMoveLegal(curr, sensor)) {
						minDistance = dist;
						minSensor = sensor;
					}
				}
			}
			curr = minSensor;
			dronePath.add(curr);  // Path acts as the visited thing
			
		}
		
		dronePath.add(startingPos);
		
		double totalDist = 0;
		for (int i = 0; i < 34; i++) {
			totalDist += distance(dronePath.get(i), dronePath.get(i+1));
		}
		
		System.out.println(totalDist/0.0003);
		
		return dronePath;
		
	}
	
	private static double distance(Point a, Point b) {
		return Math.sqrt( Math.pow(a.longitude()-b.longitude(), 2) + Math.pow(a.latitude()-b.latitude(), 2));
	}
	
}
