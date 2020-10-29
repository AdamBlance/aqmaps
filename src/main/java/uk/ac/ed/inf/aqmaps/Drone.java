package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Optional;

import org.eclipse.jdt.annotation.Nullable;

import com.mapbox.geojson.Point;

import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;
import static uk.ac.ed.inf.aqmaps.PointUtils.nearestBearing;
import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;
import static uk.ac.ed.inf.aqmaps.PointUtils.mod360;


public class Drone {
	
	private Point position;
	
	private NoFlyZoneChecker nfzc;
	
	private int timesMoved = 0;
	private int lastBearing = -1;  // This might break if the first move hits a wall
	private Point lastPosition;
	
	// This is good for logging, probably less helpful for geojson map
	private List<String> flightLog = new ArrayList<>();
	
	// This will be the one we give to the geojson thing
	private List<Point> path = new ArrayList<>();
	
	// The geojson needs:
		// ordered list of points to draw
		// list of sensors and their visited status
	
	private List<Sensor> sensors;
	private HashMap<Sensor, SensorReport> sensorReports = new HashMap<>();
	
	// it would be interesting to know what to abbreviate, maybe look it up
	private static final double MOVE_DISTANCE = 0.0003;
	private static final double FLIGHT_COMPLETION_DISTANCE = 0.0003;
	private static final double SENSOR_READ_DISTANCE = 0.0002;
	private static final int MAX_MOVES = 150;
	
	public Drone(Point startPosition, List<Sensor> sensors, NoFlyZoneChecker nfzc) {
		position = startPosition;
		path.add(position);
		this.nfzc = nfzc;

		this.sensors = sensors;
		for (var s : sensors) {
			sensorReports.put(s, new SensorReport(false, false));
		}
	}
	
	
	// navigate towards could just move one thing at a time
	// if it moves normally, return something
	// if it arrives within range of sensor, return something different
	
	
	// TODO: Write common terminology somewhere and standardise it
	public void followPath(List<Sensor> path) {
		var startPosition = position;
		for (Sensor target : path) {
			navigateTowards(target);
			var reading = readSensor(target);
			var report = sensorReports.get(target);
			if (reading.isPresent()) {
				report.setValid(true);
			}
			report.setVisited(true);
		}
		navigateTowards(Sensor.dummySensor(startPosition));  // Return home
	}
	
	// TODO: Return false or something if drone can't reach sensor
	private boolean navigateTowards(Sensor sensor) {
		
		boolean arrived = false;
		while (!arrived) {
			
			if (outOfMoves()) throw new RuntimeException("Out of moves");
			
			var sensorPoint = sensor.getPoint();
			
			int bearing = nearestBearing(position, sensorPoint);
			
			boolean moved = move(bearing);
			if (!moved) {
				move(bearingScan(bearing, sensorPoint));
			}
			
			String w3wLocation = null;
			if (distanceBetween(position, sensorPoint) <= SENSOR_READ_DISTANCE) {
				var sensorLocation = sensor.getLocation();
				if (!sensorLocation.equals("dummy")) {
					w3wLocation = sensorLocation;
				}
				arrived = true;
			}
			logMove(w3wLocation);	
		}
		return true;
	}
	
	private void logMove(@Nullable String w3wLocation) {
		var log = String.format("%d,%f,%f,%d,%f,%f,%s",
				timesMoved,
				lastPosition.longitude(),
				lastPosition.latitude(),
				lastBearing,
				position.longitude(),
				position.latitude(),
				w3wLocation == null ? "null" : w3wLocation);
		flightLog.add(log);
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
			acwBearing = mod360(acwBearing - 10);
		}
		
		
		if (newClockwisePosition.isEmpty() && newAnticlockwisePosition.isEmpty()) {
			throw new RuntimeException("We're stuck. Nice.");
		} else if (newClockwisePosition.isEmpty()) {
			return acwBearing;
		} else if (newAnticlockwisePosition.isEmpty()) {
			return cwBearing;
		} else {
			var clockD = distanceBetween(newClockwisePosition.get(), target);
			var aclockD = distanceBetween(newAnticlockwisePosition.get(), target); 
			
			System.out.println(cwBearing);
			System.out.println(clockD);
			System.out.println(acwBearing);
			System.out.println(aclockD);
			
			if (clockD > aclockD) {
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
			path.add(position);
			return true;
		} else {
			return false;
		}
	}
	
	private boolean outOfMoves() {
		return timesMoved >= MAX_MOVES;
	}
	
	public List<Point> getPath() {
		return path;
	}
	
	public List<String> getLog() {
		return flightLog;
	}
	
	public HashMap<Sensor, SensorReport> getReports() {
		return sensorReports;
	}
	
	private Optional<Double> readSensor(Sensor sensor) {
		if (distanceBetween(position, sensor.getPoint()) < SENSOR_READ_DISTANCE) {
			
			if (sensor.getBattery() >= 10.0) {
				return Optional.of(Double.parseDouble(sensor.getReading()));
			} else {
				return Optional.empty();
			}
		} else {
			throw new RuntimeException("You numpty. Too far away from sensor.");
		}
	}
	
}
