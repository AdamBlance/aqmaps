package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import com.mapbox.geojson.Point;

import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;
import static uk.ac.ed.inf.aqmaps.PointUtils.nearestBearing;
import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;
import static uk.ac.ed.inf.aqmaps.PointUtils.mod360;


public class Drone {
	
	private Point position;
	private HashMap<Point, SensorData> sensors;
	private NoFlyZoneChecker nfzc;
	
	private int timesMoved = 0;
	private int lastBearing = -1;  // This might break if the first move hits a wall
	private Point lastPosition;
	
	// This is good for logging, probably less helpful for geojson map
	private List<LogData> flightLog = new ArrayList<>();
	
	// This will be the one we give to the geojson thing
	private List<Point> path;
	
	// it would be interesting to know what to abbreviate, maybe look it up
	private static final double MOVE_DISTANCE = 0.0003;
	private static final double FLIGHT_COMPLETION_DISTANCE = 0.0003;
	private static final double SENSOR_READ_DISTANCE = 0.0002;
	private static final int MAX_MOVES = 150;
	
	public Drone(Point startPosition, HashMap<Point, SensorData> sensors, NoFlyZoneChecker nfzc) {
		position = startPosition;
		path = new ArrayList<>(Arrays.asList(position));
		this.nfzc = nfzc;
	}
	
	// TODO: Write common terminology somewhere and standardise it
	public void followPath(List<Point> path) {
		// we ignore the first one because that's where we start
		for (Point target : path.subList(1, path.size())) {
			navigateTowards(target);
			readSensor(target);
			
			// lol idk maybe navigate towards should only move once or something so 
			// we can put the log in the same place cause at the moment I'm just trying to jam it in
			
		}
	}
	
	// TODO: Return false or something if drone can't reach sensor
	private boolean navigateTowards(Point target) {
		
		while (true) {
			int bearing = nearestBearing(position, target);
			
			boolean moved = move(bearing);
			if (!moved) {
				move(bearingScan(bearing, target));
			}
			
			String w3wLocation = null;
			if (distanceBetween(position, target) <= SENSOR_READ_DISTANCE) {
				w3wLocation = sensors.get(target).getLocation();
			}
			
			// We actually need to use the read function somewhere and store the data
			
			flightLog.add(new LogData(timesMoved, lastPosition, bearing, position, w3wLocation));
			
		}
		
		
	}
	
	
	// This will return a bearing that does not collide with a building
	// TODO: Add lookahead
	// TODO: Rename the variables for the love of god
	private int bearingScan(int originalBearing, Point target) {
		
		Optional<Point> newClockwisePosition = Optional.empty();
		Optional<Point> newAnticlockwisePosition = Optional.empty();

		int backtrackBearing = mod360(lastBearing - 180);  // Bearing that would return the drone to where it just was
		
		int cwBearing = mod360(originalBearing + 10);
		while (cwBearing != backtrackBearing) {
			newClockwisePosition = testMove(cwBearing);
			if (newClockwisePosition.isPresent()) break;
			cwBearing = mod360(cwBearing + 10);
		}
		
		int acwBearing = mod360(originalBearing - 10);
		while (acwBearing != backtrackBearing) {
			newAnticlockwisePosition = testMove(acwBearing);
			if (newAnticlockwisePosition.isPresent()) break;
			acwBearing = mod360(cwBearing - 10);
		}
		
		if (newClockwisePosition.isEmpty() && newAnticlockwisePosition.isEmpty()) {
			throw new RuntimeException("We're stuck. Nice.");
		} else if (newClockwisePosition.isEmpty()) {
			return acwBearing;
		} else if (newAnticlockwisePosition.isEmpty()) {
			return cwBearing;
		} else {
			if (distanceBetween(newClockwisePosition.get(), target) > distanceBetween(newAnticlockwisePosition.get(), target)) {
				return acwBearing;
			} else {
				return cwBearing;
			}
		}
	}

		
	// Maybe some redundancy here
	// Just for safety
	
	private Optional<Point> testMove(int bearing) {
		
		var destination = moveDestination(position, MOVE_DISTANCE, bearing);
		
		if (nfzc.isMoveLegal(position, destination) && !outOfMoves()) {
			return Optional.ofNullable(destination);
		} else {
			return Optional.empty();
		}
	}
	
	private boolean move(int bearing) {
		
		var destination = moveDestination(position, MOVE_DISTANCE, bearing);
		
		if (nfzc.isMoveLegal(position, destination) && !outOfMoves()) {
			timesMoved += 1;
			lastPosition = position;
			lastBearing = bearing;
			position = destination;
			return true;
		} else {
			return false;
		}
	}
	
	private boolean outOfMoves() {
		return timesMoved >= MAX_MOVES;
	}
	
	private Point getPosition() {
		return position;
	}
	
	private int getLastBearing() {
		return lastBearing;
	}
	
	private Optional<String> readSensor(Point sensor) {
		if (distanceBetween(position, sensor) < SENSOR_READ_DISTANCE) {
			var data = sensors.get(sensor);
			
			if (data.getBattery() >= 10.0) {
				return Optional.of(data.getReading());
			} else {
				return Optional.empty();
			}
			
		} else {
			throw new RuntimeException("You numpty. Too far away from sensor.");
		}
	}
	
}
