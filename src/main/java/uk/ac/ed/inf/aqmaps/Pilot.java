package uk.ac.ed.inf.aqmaps;

import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;
import static uk.ac.ed.inf.aqmaps.PointUtils.mod360;
import static uk.ac.ed.inf.aqmaps.PointUtils.nearestBearing;
import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Optional;
import java.util.PriorityQueue;
import java.util.Queue;
import java.util.Random;
import java.util.Stack;

import com.mapbox.geojson.Feature;
import com.mapbox.geojson.LineString;
import com.mapbox.geojson.Point;


public class Pilot {

	private Drone drone;
	
	// Given to gjg
	private List<Point> path = new ArrayList<>();
	private List<String> log = new ArrayList<>();
	
	private int lastBearingTaken;  // This might break if the first move hits a wall
	private Point previousPosition;
	
	private Queue<Integer> precomputedBearings = new LinkedList<>();
	
	private HashMap<Sensor, SensorReport> sensorReports = new HashMap<>();
	
	private NoFlyZoneChecker nfzc;
		
	public Pilot(Drone drone, NoFlyZoneChecker nfzc) {
		this.drone = drone;
		this.nfzc = nfzc;
		path.add(drone.getPosition());
	}
	
	// TODO: Write common terminology somewhere and standardise it
	public boolean followRoute(List<Waypoint> route) {
		
		for (var waypoint : route) {
			sensorReports.put((Sensor) waypoint, new SensorReport(false, false));
		}
		
		var start = new Waypoint(drone.getPosition());
		lastBearingTaken = nearestBearing(drone.getPosition(), route.get(0).getPoint());
		for (Waypoint s : route) {
			boolean arrived = navigateTowards(s);
			if (!arrived) {
				return false;
			}
			var reading = drone.readSensor((Sensor) s);
			var report = sensorReports.get(s);
			if (reading.isPresent()) {
				report.setValid(true);
			}
			report.setVisited(true);
		}
		return navigateTowards(start);  // True if we make it back, false if not
	}
	
	private boolean navigateTowards(Waypoint waypoint) {
		
		var targetPoint = waypoint.getPoint();
		
		boolean arrived = false;
		while (!arrived) {
			
			int breakp = path.size();
			
			previousPosition = drone.getPosition();
			
						
			int bearing = bestLegalBearing(targetPoint);
			
			var move = drone.move(bearing);
			
			if (!move) {
				break;
			}
			
			path.add(drone.getPosition());
			
			lastBearingTaken = bearing;
			
			String w3wLocation = null;
			
			var temp = distanceBetween(drone.getPosition(), targetPoint); 
			
//			System.out.println(temp);
			
			if (temp <= drone.getSensorReadDistance()) {
				if (waypoint instanceof Sensor) {
					w3wLocation = ((Sensor) waypoint).getW3wAddress();
				}
				arrived = true;
			}
			
			var pos = drone.getPosition();
			log.add(String.format("%d,%f,%f,%d,%f,%f,%s",
					drone.getTimesMoved(),
					previousPosition.longitude(),
					previousPosition.latitude(),
					lastBearingTaken,
					pos.longitude(),
					pos.latitude(),
					w3wLocation == null ? "null" : w3wLocation));
			
//			System.out.println(LineString.fromLngLats(path).toJson());
			
		}
		return arrived;
	}
	
	public List<Point> getPath() {
		return path;
	}
	
	public HashMap<Sensor, SensorReport> getSensorReports() {
		return sensorReports;
	}
	
	public List<String> getLog() {
		return log;
	}
	
	public Optional<Point> testMove(Point pos, int bearing) {
		
		
		
		var destination = moveDestination(pos, 0.0003, bearing);
		
		if (nfzc.isMoveLegal(pos, destination)) {
			return Optional.ofNullable(destination);
		} else {
			return Optional.empty();
		}
	}
	
	private int bestLegalBearing(Point target) {
		
		if (!precomputedBearings.isEmpty()) {
			return precomputedBearings.poll();
		}
		
		int nearestBearing = nearestBearing(drone.getPosition(), target);
		var dronePos = drone.getPosition();
		
		if (testMove(dronePos, nearestBearing).isPresent()) {
			return nearestBearing;
		}
		
		// Distance around the building going clockwise/anticlockwise
		
		List<Integer> clockwiseDirections = new ArrayList<>();
		List<Integer> antiClockwiseDirections = new ArrayList<>();
		
		double clockwiseDist = 0;
		double antiClockwiseDist = 0;
		
		int clockwiseLength = 0;
		int antiClockwiseLength = 0;
		
		Point clockwisePosition = dronePos;
		Point antiClockwisePosition = dronePos;
		
		
		
		while (clockwiseDist < Double.MAX_VALUE || antiClockwiseDist < Double.MAX_VALUE) {
			
			if (clockwiseDist < antiClockwiseDist) {
				int nearest = nearestBearing(clockwisePosition, target);
				var newBearing = bearingScan(clockwisePosition, nearest, -10, clockwiseDirections.isEmpty() ? nearest : mod360(clockwiseDirections.get(clockwiseDirections.size()-1) - 180));
				if (newBearing.isPresent()) {
					clockwisePosition = testMove(clockwisePosition, newBearing.get()).get();
					clockwiseDirections.add(newBearing.get());
					clockwiseLength += 1;
					clockwiseDist = clockwiseLength*0.0003 + distanceBetween(clockwisePosition, target);
					
					nearest = nearestBearing(clockwisePosition, target);

					
					boolean tMove = testMove(clockwisePosition, nearest).isPresent();
					int backtrack = mod360(clockwiseDirections.get(clockwiseDirections.size() - 1) - 180);
					
//					System.out.println(nearest);
//					System.out.println(tMove);
//					System.out.println(backtrack);
					
					if ((tMove && (nearest != backtrack)) || distanceBetween(clockwisePosition, target) < 0.0002) {
						precomputedBearings.addAll(clockwiseDirections);
						return precomputedBearings.poll();
					}
					
					
				} else {
					clockwiseDist = Double.MAX_VALUE;
					continue;
				}
			} else {
				int nearest = nearestBearing(antiClockwisePosition, target);
				var newBearing = bearingScan(antiClockwisePosition, nearest, 10, antiClockwiseDirections.isEmpty() ? nearest : mod360(antiClockwiseDirections.get(antiClockwiseDirections.size()-1) - 180));
				if (newBearing.isPresent()) {
					antiClockwisePosition = testMove(antiClockwisePosition, newBearing.get()).get();
					antiClockwiseDirections.add(newBearing.get());
					antiClockwiseLength += 1;
					antiClockwiseDist = antiClockwiseLength*0.0003 + distanceBetween(antiClockwisePosition, target);
					
					nearest = nearestBearing(antiClockwisePosition, target);
					
					
					// End if there is a straight shot to the waypoint and the straight shot doesn't put us in the space we were just in
					// We know the space we were just in did not give us a straight shot
					// Also, 
					if ((testMove(antiClockwisePosition, nearest).isPresent() && nearest != mod360(antiClockwiseDirections.get(antiClockwiseDirections.size() - 1) - 180)) || distanceBetween(antiClockwisePosition, target) < 0.0002) {
						precomputedBearings.addAll(antiClockwiseDirections);
						return precomputedBearings.poll();
					}
					
				} else {
					antiClockwiseDist = Double.MAX_VALUE;
					continue;
				}
			}
		}
		throw new IllegalStateException("The drone cannot escape and is stuck for eternity :(");
	}
	
	
	// Will return empty if nothing is found which is very unlikely
	private Optional<Integer> bearingScan(Point position, int startBearing, int offset, int limitBearing) {
		
		Optional<Integer> newBearing = Optional.empty();
		
		int bearing = mod360(startBearing + offset);
		while (bearing != limitBearing) {
			 if (testMove(position, bearing).isPresent()) {
				 newBearing = Optional.of(bearing);
				 break;
			 }
			 bearing = mod360(bearing + offset);
		}
		return newBearing;
		
	}
	
}
