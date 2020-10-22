package uk.ac.ed.inf.aqmaps;

import java.util.AbstractMap;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;
import java.lang.Math;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.FeatureCollection;
import com.mapbox.geojson.Point;
import com.mapbox.geojson.Polygon;

import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;
import static uk.ac.ed.inf.aqmaps.PointUtils.mod360;
import static uk.ac.ed.inf.aqmaps.PointUtils.nearestBearing;
import static uk.ac.ed.inf.aqmaps.PointUtils.oppositeBearing;

public class DronePilot {

	// recording the path should be the responsibility of the drone
	
	// This should take in the start point, sensor points and try to devise a good path
	
	private Drone drone;
	private NoFlyZoneChecker noFlyZoneChecker;
	private HashMap<Point, SensorData> sensors;	
	
	private List<Point> flightPlan;
	
	public DronePilot(Drone drone, HashMap<Point, SensorData> sensors, NoFlyZoneChecker noFlyZoneChecker) {
		this.noFlyZoneChecker = noFlyZoneChecker;
		this.sensors = sensors;
		this.drone = drone;
	}
	
	
	public List<Point> navigateTowards(Point target) {
		
		var path = new ArrayList<Point>();
				
//		System.out.println("navigating");
		
		while (distanceBetween(drone.getPosition(), target) >= 0.0002 && !drone.outOfMoves()) {
			
			int bearing = nearestBearing(drone.getPosition(), target);

			var newPosition = drone.move(bearing); // try to move the drone and see what happens
			
			
			// lets rewrite
			// we need to record or look at the last move / bearing the drone took
			// we need to record whether the drone is currently navigating around a building
			// don't think that matters much since we are navigating between points
			
			if (newPosition.isPresent()) {
//				System.out.println("Moved normally");
				path.add(drone.getPosition());
			} else {
				
				
//				System.out.println("Feeling...");
				
				Optional<Point> newClockwisePosition = Optional.empty();
				Optional<Point> newAnticlockwisePosition = Optional.empty();

				// this is a mess
				
				int cwBearing = -69;
				int acwBearing = 420;
				
				int invLastBear = oppositeBearing(drone.getLastBearing());
				
				for (int offset = 10; mod360(bearing + offset) != invLastBear; offset += 10) {
					newClockwisePosition = drone.testMove(mod360(bearing + offset));
					if (newClockwisePosition.isPresent()) {
						cwBearing = mod360(bearing + offset);
						break;
					}
				}
				
				for (int offset = 10; mod360(bearing - offset) != invLastBear; offset += 10) {
					newAnticlockwisePosition = drone.testMove(mod360(bearing - offset));
					if (newAnticlockwisePosition.isPresent()) {
						acwBearing = mod360(bearing - offset);
						break;
					}
				}
				
				if (newClockwisePosition.isEmpty() && newAnticlockwisePosition.isEmpty()) {
					throw new RuntimeException("We're stuck. Nice.");
				} else if (newClockwisePosition.isEmpty()) {
					drone.move(acwBearing);
				} else if (newAnticlockwisePosition.isEmpty()) {
					drone.move(cwBearing);
				} else {
					
					if (distanceBetween(newClockwisePosition.get(), target) > distanceBetween(newAnticlockwisePosition.get(), target)) {
						drone.move(acwBearing);
					} else {
						drone.move(cwBearing);
					}
				}
				
//				while (true) {
//					offset += 10;
					
					// might need a catch here for out of moves, not sure					
//					System.out.println(String.format("%03d,%03d", mod360(bearing-offset), mod360(bearing+offset)));
					
					
					// if mod360(bearing - offset) == mod360(lastBearingTaken - 180)
						// anticlockwise_doubleback = true
						// stop checking anti_clockwise
					
//					int returningBearing = mod360(drone.getLastBearing() - 180);
//					int acwOffset = mod360(bearing - offset);
//					int cwOffset = mod360(bearing + offset);
//					
//					if (acwOffset == returningBearing) acwLock = true;
//					if (cwOffset == returningBearing) cwLock = true;
					
//					if (!acwLock) {
//						if (drone.move(acwOffset) == DroneStatus.OK) {
//							lastBearingTaken = acwOffset;
//							System.out.println("Moved (anti-clockwise)");
//							break;
//						}
//					} 
//					if (!cwLock) {
//						if (drone.move(cwOffset) == DroneStatus.OK) {
//							lastBearingTaken = cwOffset;
//							System.out.println("Moved (clockwise)");
//							break;
//						}
//					}
					// terrible code
//				}
				
				
				// I think this is all broken because either drone pos isn't updated or break breaks from both while loops
				
				path.add(drone.getPosition());
			}
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
				var dist = distanceBetween(curr, sensor);
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
	
	
}
