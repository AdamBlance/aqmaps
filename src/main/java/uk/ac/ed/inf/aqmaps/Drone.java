package uk.ac.ed.inf.aqmaps;

import java.util.Optional;

import com.mapbox.geojson.Point;

import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;
import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;

public class Drone {
	
	private Point position;
	private int timesMoved = 0;
	
	private static final double MOVE_DISTANCE = 0.0003;
	private static final double SENSOR_READ_DISTANCE = 0.0002;
	private static final int MAX_MOVES = 150;
	
	public Drone(Point startPosition) {
		position = startPosition;
	}
	
	public boolean move(int bearing) {
		if (outOfMoves()) {
			return false;
		}
		var destination = moveDestination(position, MOVE_DISTANCE, bearing);
		position = destination;
		timesMoved += 1;
		return true;
	}
	
	public Point getPosition() {
		return position;
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
	
	public int getTimesMoved() {
		return timesMoved;
	}
	
	public double getMoveDistance() {
		return MOVE_DISTANCE;
	}
	
	public double getSensorReadDistance() {
		return SENSOR_READ_DISTANCE;
	}
	
	private boolean outOfMoves() {
		return timesMoved >= MAX_MOVES;
	}
	
}
