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

	
	// This is good for logging, probably less helpful for geojson map
	private List<String> flightLog = new ArrayList<>();
	
	private List<Sensor> sensors;
	
	// it would be interesting to know what to abbreviate, maybe look it up
	private static final double MOVE_DISTANCE = 0.0003;
	private static final double FLIGHT_COMPLETION_DISTANCE = 0.0003;
	public static final double SENSOR_READ_DISTANCE = 0.0002;
	private static final int MAX_MOVES = 150;
	
	public Drone(Point startPosition, List<Sensor> sensors, NoFlyZoneChecker nfzc) {
		position = startPosition;
		this.nfzc = nfzc;
		this.sensors = sensors;
	}
	
		
	// Maybe some redundancy here
	// Just for safety
	
	public Optional<Point> testMove(int bearing) {
		
		var destination = moveDestination(position, MOVE_DISTANCE, bearing);
		
		if (nfzc.isMoveLegal(position, destination) && !outOfMoves()) {
			return Optional.ofNullable(destination);
		} else {
			return Optional.empty();
		}
	}
	
	public boolean move(int bearing) {
				
		var destination = moveDestination(position, MOVE_DISTANCE, bearing);
		
		if (nfzc.isMoveLegal(position, destination) && !outOfMoves()) {
			timesMoved += 1;
			position = destination;
			return true;
		} else {
			return false;
		}
	}
	
	public boolean outOfMoves() {
		return timesMoved >= MAX_MOVES;
	}
	
	public int getTimesMoved() {
		return timesMoved;
	}
	
	public Point getPosition() {
		return position;
	}
	
	public List<Sensor> getSensors() {
		return sensors;
	}
	
	public Optional<Double> readSensor(Sensor sensor) {
		if (distanceBetween(position, sensor.getPoint()) < SENSOR_READ_DISTANCE) {
			
			if (sensor.getBattery() >= 10.0) {
				return Optional.of(sensor.getReading());
			} else {
				return Optional.empty();
			}
		} else {
			throw new RuntimeException("You numpty. Too far away from sensor.");
		}
	}
	
}
