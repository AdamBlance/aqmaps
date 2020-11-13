package uk.ac.ed.inf.aqmaps;

import java.util.Optional;

import com.mapbox.geojson.Point;

import static uk.ac.ed.inf.aqmaps.PointUtils.moveDestination;
import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;

public class Drone {
	
	private Point position;
	private int timesMoved = 0;
	
	public static final double MOVE_DISTANCE = 0.0003;
	public static final double SENSOR_READ_DISTANCE = 0.0002;
	public static final int MAX_MOVES = 150;
	
	public Drone(Point startPosition) {
		position = startPosition;
	}

	public Optional<Point> move(int bearing) {
		if (outOfMoves()) {
			return Optional.empty();
		}
		var destination = moveDestination(position, MOVE_DISTANCE, bearing);
		position = destination;
		timesMoved += 1;
		return Optional.of(position);
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
			throw new IllegalStateException("Drone is too far away to read sensor.");
		}
	}
	
	public int getTimesMoved() {
		return timesMoved;
	}
	
	private boolean outOfMoves() {
		return timesMoved >= MAX_MOVES;
	}
	
}
