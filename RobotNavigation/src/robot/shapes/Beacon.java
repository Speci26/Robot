package robot.shapes;

import org.opencv.core.Point;

public class Beacon extends Square {
	
	private int upperColorID;

	public Beacon(Point center, Double halfHeight, Point lowerEdgeLeft,
			int lowerColorID, int upperColorID) {
		super(center, halfHeight, lowerEdgeLeft, lowerColorID);
		this.upperColorID = upperColorID;
	}
	
	public Beacon(Point center, Point lowPt, Point lowerEdgeLeft,
			int lowerColorID, int upperColorID) {
		super(center,lowPt, lowerEdgeLeft, lowerColorID);
		this.upperColorID = upperColorID;
	}
	
	public int getColorComb() {
		return upperColorID*10 + getColorID();
	}

	
}
