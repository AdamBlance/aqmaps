package uk.ac.ed.inf.aqmaps;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
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
		DroneStatus s = DroneStatus.OK; // should fix this
		int lastBearingTaken = 1000;  // just initialising
		
		
		// oh shit this is just navigate towards
		
		System.out.println("navigating");
		
		// this is so broken
		
		while (distance(dronePos, target) >= 0.0002 && s != DroneStatus.OUT_OF_MOVES) {
			
			
			
			int bearing = nearestBearing(dronePos, target);

			s = drone.move(bearing); // try to move the drone and see what happens
			
			
			// can move the while up here instead of if else
			
			// lets rewrite
			// we need to record or look at the last move / bearing the drone took
			// we need to record whether the drone is currently navigating around a building
			// don't think that matters much since we are navigating between points
			
			if (s != DroneStatus.ILLEGAL) {
				System.out.println("Moved normally");
				dronePos = drone.getPosition();
				lastBearingTaken = bearing;
				path.add(dronePos);
			} else {
				// first look to left, then right
				
				// this will probably get stuck in concave areas
				
				int offset = 0;
				System.out.println("feeling...");
				
				boolean acwLock = false;
				boolean cwLock = false;
				
				while (true) {
					offset += 10;
					
					// might need a catch here for out of moves, not sure					
//					System.out.println(String.format("%03d,%03d", mod360(bearing-offset), mod360(bearing+offset)));
					
					
					// if mod360(bearing - offset) == mod360(lastBearingTaken - 180)
						// anticlockwise_doubleback = true
						// stop checking anti_clockwise
					
					int returningBearing = mod360(lastBearingTaken - 180);
					int acwOffset = mod360(bearing - offset);
					int cwOffset = mod360(bearing + offset);
					
					if (acwOffset == returningBearing) acwLock = true;
					if (cwOffset == returningBearing) cwLock = true;
					
					// it's moving even though it shouldn't
					if (!acwLock) {
						if (drone.move(acwOffset) == DroneStatus.OK) {
							lastBearingTaken = acwOffset;
							System.out.println("Moved (anti-clockwise)");
							break;
						}
					} 
					if (!cwLock) {
						if (drone.move(cwOffset) == DroneStatus.OK) {
							lastBearingTaken = cwOffset;
							System.out.println("Moved (clockwise)");
							break;
						}
					}
					// terrible code
				}
				
				
				// I think this is all broken because either drone pos isn't updated or break breaks from both while loops
				
				dronePos = drone.getPosition();
				path.add(dronePos);
				
			}
			
			System.out.println(distance(dronePos, target) + " " + target.longitude());
			
		}
		
		return path;
		
	}
	
	public List<Point> followPath(List<Point> path) {
		// we ignore the first one because that's where we start
		// also note that we only need to finish 0.0003 from the start not 0.0002
		
		var output = new ArrayList<Point>(Arrays.asList(path.get(0)));
		
		for (Point p : path.subList(1, path.size())) {
			output.addAll(navigateTowards(p));
		}
		
		return output;
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
				if (dist < minDistance) { //  && noFlyZoneChecker.isMoveLegal(curr, sensor)
					if (!dronePath.contains(sensor)) {
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
		
//		double totalDist = 0;
//		for (int i = 0; i < 34; i++) {
//			totalDist += distance(dronePath.get(i), dronePath.get(i+1));
//		}
//		
//		System.out.println(totalDist/0.0003);
		
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
		int bearing = Math.floorMod(360 - Math.floorMod(temp - 90, 360), 360); // second mod incase 360-0 = 0
		
		
		return bearing;
		
	}
	
	private static int mod360(int bearing) {
		return Math.floorMod(bearing, 360);
	}
	
}
