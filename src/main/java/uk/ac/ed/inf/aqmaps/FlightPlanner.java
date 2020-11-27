package uk.ac.ed.inf.aqmaps;

import java.util.ArrayList;
import java.util.List;

import com.mapbox.geojson.Point;

import static uk.ac.ed.inf.aqmaps.PointUtils.distanceBetween;


public class FlightPlanner {
	
	public static List<Waypoint> greedyPath(Point start, List<Waypoint> waypoints) {		
		var dronePath = new ArrayList<Waypoint>();
		var unvisited = new ArrayList<Waypoint>(waypoints);
		var currentPoint = start;
		
		for (int i = 0; i < waypoints.size(); i++) {
			var closest = closestSensor(currentPoint, unvisited);
			currentPoint = closest.getPoint();
			dronePath.add(closest);
			unvisited.remove(closest);
		}
		return dronePath;
	}
	
	public static List<Waypoint> twoOptPath(Point start, List<Waypoint> waypoints) {
		var startWaypoint = new Waypoint(start, true);
		
		List<Waypoint> path = new ArrayList<>();
		path.add(startWaypoint);
		path.addAll(greedyPath(start, waypoints));
		path.add(startWaypoint);
				
		boolean improved = true;
		while (improved) {
			improved = false;
			outerloop:
			for (int i = 1; i < path.size() - 1; i++) {
				for (int j = i+1; j < path.size() - 1; j++) {
					double diff = pathDifference(path, i, j);
					if (diff < 0) {
						path = modifiedPath(path, i, j);
						improved = true;					
						break outerloop;
					}
				}
			}
		}
		return path.subList(1, path.size() - 1);
	}
	
	private static double pathDifference(List<Waypoint> path, int start, int end) {
		
		var beforeStartP = path.get(start - 1).getPoint();
		var startP = path.get(start).getPoint();	
		var endP = path.get(end).getPoint();
		var afterEndP = path.get(end + 1).getPoint();
		
		double lengthBefore = distanceBetween(beforeStartP, startP) + distanceBetween(endP, afterEndP);
		double lengthAfter = distanceBetween(beforeStartP, endP) + distanceBetween(startP, afterEndP);
				
		return lengthAfter - lengthBefore;
	}
	
	private static List<Waypoint> modifiedPath(List<Waypoint> path, int s, int e) {
		var output = new ArrayList<>(path);
		for (int i = s; i <= e; i++) {
			output.set(i, path.get(e-(i-s)));
		}
		return output;
	}
	
	// Just wanted to try java streams here, obviously this isn't the most efficient
	// I would have done an argmax type thing but java doesn't have a proper pair datatype
	private static Waypoint closestSensor(Point point, List<Waypoint> sensors) {
		return sensors.stream()
				.min((Waypoint a, Waypoint b) -> Double.compare(distanceBetween(point, a.getPoint()), distanceBetween(point, b.getPoint())))
				.get();
	}
	
}
