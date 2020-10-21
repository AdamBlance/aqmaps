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
	
	
	public List<Point> navigateTowards(Point target) {
		
		var path = new ArrayList<Point>();
		
		Point dronePos = drone.getPosition();
		while (distance(dronePos, target) >= 0.0002) {
			
			int bearing = nearestBearing(dronePos, target);
			DroneStatus s = drone.move(bearing);
			
			// check or something for illegal moves
			
			dronePos = drone.getPosition();
			path.add(dronePos);
			
		}
		
		return path;
		
	}
	
	// Should return a greedy flight path between all the sensors, I guess ordered by their coords? 
	public List<Point> greedyPlan() {

		
		// Do greedy but let it navigate 
		
		
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
			if (minSensor.latitude() != 0)	{
				curr = minSensor;
				dronePath.add(curr);  // Path acts as the visited thing
			} else {
				System.out.println("Stuck!");
				break;
			}
			
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
	
	// drone move function takes a bearing and moves
	// we also need to get nearest bearing to the direction we're trying to move
	// so we need to calculate the bearing of a line and then round it to nearest 10
	
	private static int nearestBearing(Point origin, Point destination) {
		
		double upDist = destination.latitude() - origin.latitude();
		double sideDist = destination.longitude() - origin.longitude();
		
		// Gets polar theta, converts to degrees, rounds to nearest 10
		int temp = (int) Math.round(Math.toDegrees(Math.atan2(upDist, sideDist)) / 10.0) * 10;
		// Rotate coordinates by 90 degrees (polar theta is 0 on y-axis) and then subtract from 360 to make bearing move clockwise
		int bearing = 360 - Math.floorMod(temp - 90, 360);
		
		return bearing;
		
	}
	
}
