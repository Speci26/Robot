package robot.opencv;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;

import org.opencv.calib3d.Calib3d;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.MatOfPoint;
import org.opencv.core.MatOfPoint2f;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;
import org.opencv.imgproc.Moments;

import android.util.Log;
import robot.shapes.Ball;
import robot.shapes.Beacon;
import robot.shapes.Circle;
import robot.shapes.Square;

public class ImageProcessor {

	// TODO Check if offsets for homography are needed

	private static double mMinContourArea = 0.1; // Minimum contour area in
													// percent for contours
													// filtering

	private String TAG; // Tag for log-messages sent to logcat

	/**
	 * Constructor method.
	 * 
	 * @param TAG
	 *            Used for messages to logcat.
	 */
	public ImageProcessor(String TAG) {
		this.TAG = TAG;
	}

	/**
	 * Finds contours of the objects within the given grayImage.
	 * 
	 * @param grayImage
	 *            input image
	 * @return a list of contours
	 */
	public List<MatOfPoint> findContours(Mat grayImage) {
		List<MatOfPoint> mmContours = new ArrayList<MatOfPoint>();
		;
		Mat tempImage = new Mat();
		Mat mHierarchy = new Mat();

		try {
			List<MatOfPoint> contours = new ArrayList<MatOfPoint>();
			grayImage.copyTo(tempImage);
			Imgproc.findContours(tempImage, contours, mHierarchy,
					Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE);

			// Find max contour area
			double maxArea = 0;
			Iterator<MatOfPoint> each = contours.iterator();
			while (each.hasNext()) {
				MatOfPoint wrapper = each.next();
				double area = Imgproc.contourArea(wrapper);
				if (area > maxArea)
					maxArea = area;
			}

			// Filter contours by area and resize to fit the original image size
			each = contours.iterator();
			while (each.hasNext()) {
				MatOfPoint contour = each.next();
				if (Imgproc.contourArea(contour) > mMinContourArea * maxArea) {
					mmContours.add(contour);
				}
			}

		} catch (Exception e) {
		} finally {
			tempImage.release(); // free memory
		}
		return mmContours;
	}

	/**
	 * Calculates and returns homography matrix (with the help of a chessboard
	 * pattern).
	 * 
	 * @param mRgba
	 *            input image
	 * @return homography matrix
	 */
	public Mat getHomographyMatrix(Mat mRgba) {
		final Size mPatternSize = new Size(6, 9); // number of inner corners in
		// the used chessboard
		// pattern
		float x = -48.0f; // coordinates of first detected inner corner on
		// chessboard
		float y = 309.0f;
		float delta = 12.0f; // size of a single square edge in chessboard
		LinkedList<Point> PointList = new LinkedList<Point>();
		// Define real-world coordinates for given chessboard pattern:
		for (int i = 0; i < mPatternSize.height; i++) {
			y = 309.0f;
			for (int j = 0; j < mPatternSize.width; j++) {
				PointList.addLast(new Point(x, y));
				y += delta;
			}
			x += delta;
		}
		MatOfPoint2f RealWorldC = new MatOfPoint2f();
		RealWorldC.fromList(PointList);
		// Detect inner corners of chessboard pattern from image:
		Mat gray = new Mat();
		Imgproc.cvtColor(mRgba, gray, Imgproc.COLOR_RGBA2GRAY); // convert image
		// to grayscale
		MatOfPoint2f mCorners = new MatOfPoint2f();
		boolean mPatternWasFound = Calib3d.findChessboardCorners(gray,
				mPatternSize, mCorners);
		gray.release(); // free memory
		// Calculate homography:
		if (mPatternWasFound) {
			// Calib3d.drawChessboardCorners(mRgba, mPatternSize, mCorners,
			// mPatternWasFound); // for visualization
			return Calib3d.findHomography(mCorners, RealWorldC);
		} else
			return new Mat();
	}

	/**
	 * Filters the input image for the given color and opens the image (thus
	 * reducing noice).
	 * 
	 * @param rgbaImage
	 *            image to filter
	 * @param hsvColor
	 *            color to filter for
	 * @return filtered image in grayscale
	 */
	public Mat filter(Mat rgbaImage, Scalar hsvColor) {
		Scalar mmLowerBound = new Scalar(0);
		Scalar mmUpperBound = new Scalar(0);
		Mat mmPyrDownMat = new Mat();
		Mat mmHsvMat = new Mat();
		Mat mmMask = new Mat();
		Mat mmDilatedMask = new Mat();

		try {

			Scalar mmColorRadius = new Scalar(30, 70, 70, 0); // Color radius
																// for range
																// checking in
																// HSV color
																// space
			double minH = (hsvColor.val[0] >= mmColorRadius.val[0]) ? hsvColor.val[0]
					- mmColorRadius.val[0]
					: 0;
			double maxH = (hsvColor.val[0] + mmColorRadius.val[0] <= 255) ? hsvColor.val[0]
					+ mmColorRadius.val[0]
					: 255;

			mmLowerBound.val[0] = minH;
			mmUpperBound.val[0] = maxH;

			mmLowerBound.val[1] = hsvColor.val[1] - mmColorRadius.val[1];
			mmUpperBound.val[1] = hsvColor.val[1] + mmColorRadius.val[1];

			mmLowerBound.val[2] = hsvColor.val[2] - mmColorRadius.val[2];
			mmUpperBound.val[2] = hsvColor.val[2] + mmColorRadius.val[2];

			mmLowerBound.val[3] = 0;
			mmUpperBound.val[3] = 255;

			Imgproc.pyrDown(rgbaImage, mmPyrDownMat);
			Imgproc.pyrDown(mmPyrDownMat, mmPyrDownMat);

			Imgproc.cvtColor(mmPyrDownMat, mmHsvMat, Imgproc.COLOR_RGB2HSV_FULL);

			Core.inRange(mmHsvMat, mmLowerBound, mmUpperBound, mmMask);
			// Mat element =
			// Imgproc.getStructuringElement(Imgproc.MORPH_ELLIPSE,
			// new Size(10, 10));
			Mat element = Imgproc.getStructuringElement(Imgproc.MORPH_RECT,
					new Size(15, 15));
			Imgproc.dilate(mmMask, mmDilatedMask, element);
			Imgproc.erode(mmDilatedMask, mmDilatedMask, element);

			Imgproc.resize(mmDilatedMask, mmDilatedMask, rgbaImage.size());

		} catch (Exception e) {
		} finally {
			mmPyrDownMat.release();
			mmHsvMat.release();
			mmMask.release();
		}

		return mmDilatedMask;
	}

	/**
	 * computes center point of given contour
	 * 
	 * @param contours
	 * @return Point (representing center point)
	 */
	public Point computeCenterPt(MatOfPoint contours) {

		Moments mu = new Moments();
		mu = Imgproc.moments(contours, false);
		double x = mu.get_m10() / mu.get_m00();
		double y = mu.get_m01() / mu.get_m00();

		Point ptCenter = new Point(x, y);
		return ptCenter;
	}

	/**
	 * Calculates the distance between two points.
	 * 
	 * @param p1
	 *            first point
	 * @param p2
	 *            second point
	 * @return distance in pixels
	 */
	public double distPointToPoint(Point p1, Point p2) {
		return Math.sqrt(Math.pow(p2.x - p1.x, 2) + Math.pow(p2.y - p1.y, 2));
	}

	/**
	 * measures distance from every point to center and computes average radius
	 * 
	 * @param contours
	 *            list of contours
	 * @param center
	 *            center of the given contours
	 * @return radius in pixels
	 */
	public double computeRadius(MatOfPoint contours, Point center) {
		center = computeCenterPt(contours);
		double distToCenter = 0;
		List<Point> pts = contours.toList();
		for (Point p : pts) {
			distToCenter += distPointToPoint(p, center);
		}
		distToCenter = distToCenter / pts.size();

		return distToCenter;
	}

	// TODO Needed?
	// TODO If so, comment
	private Mat drawBalls(Mat mRgbaWithBalls, List<Ball> foundBalls) {

		int counter = 0;
		for (Ball ball : foundBalls) {

			// Just for highlighting
			Scalar color = null;
			Scalar red = new Scalar(20);
			Scalar black = new Scalar(128);
			if (counter % 2 == 0) {
				color = red;
			} else {
				color = black;
			}

			Point center = ball.getBallCenterCameraFrame();
			int rad = (int) ball.getRadius();
			Core.circle(mRgbaWithBalls, center, 10, new Scalar(20), -1);
			Core.circle(mRgbaWithBalls, center, (int) rad, color, 5);
			Point lowestPoint = new Point(center.x, center.y + rad);
			Core.circle(mRgbaWithBalls, lowestPoint, 10, color, 5);
			// Imgproc.drawContours(img, ballArea, -1, color, -1);

			counter++;
		}

		return mRgbaWithBalls;
	}

	/**
	 * Finds the centers of all circles on a given image matrix..
	 * 
	 * @param mRgbaWork
	 *            The image to find circles in.
	 * @param myColors
	 *            a list of colors which should be processed
	 * 
	 * @return a list of centers of circles that are currently present on the
	 *         camera frame
	 */
	public List<Point> findCirclesOnCamera(Mat mRgbaWork, List<Scalar> myColors) {
		List<Point> circleCenters = new ArrayList<Point>();
		for (Scalar hsvColor : myColors) {
			Mat grayImg;
			do {
				grayImg = filter(mRgbaWork, hsvColor);
			} while (grayImg.empty());
			List<MatOfPoint> contours = findContours(grayImg);

			for (MatOfPoint area : contours) {

				Point center = computeCenterPt(area);

				circleCenters.add(center);
			}
			grayImg.release();
		}

		return circleCenters;
	}

	// TODO Add comment
	public List<Circle> findCirclesOnCamera2(Mat mRgbaWork,
			List<Scalar> myColors) {
		List<Circle> circlesList = new ArrayList<Circle>();
		for (Scalar hsvColor : myColors) {
			Mat grayImg;
			do {
				grayImg = filter(mRgbaWork, hsvColor);
			} while (grayImg.empty());
			List<MatOfPoint> contours = findContours(grayImg);

			for (MatOfPoint area : contours) {

				Point center = computeCenterPt(area);

				Double radius = computeRadius(area, center);

				Circle foundCircle = new Circle(center, radius);

				circlesList.add(foundCircle);
			}
			grayImg.release();
		}
		return circlesList;
	}

	// TODO Add comment
	public List<Square> findSquaresOnCamera(Mat mRgbaWork, List<Scalar> myColors) {
		List<Square> squareList = new ArrayList<Square>();
		int i = 0;
		for (Scalar hsvColor : myColors) {
			i++;
			Mat grayImg = new Mat();
			do {
				grayImg = filter(mRgbaWork, hsvColor);
			} while (grayImg.empty());

			List<MatOfPoint> contours = findContours(grayImg);

			for (MatOfPoint area : contours) {

				Point center = computeCenterPt(area);
				double[] squareSize = squareSize(area, center);

				Point lowerEdgeLeft = computeLowerEdgeLeft(center, squareSize);

				Square foundSquare = new Square(center, squareSize[0],
						lowerEdgeLeft, i);

				squareList.add(foundSquare);

				// squareSize[0] -> halfWidth
				// squareSize[1] -> halfHeight

			}

		}
		Log.i(TAG, "(findSquaresOnCamera) Found squares: " + squareList.size());
		return squareList;
	}

	// TODO Add comment
	private Point computeLowerEdgeLeft(Point center, double[] squareSize) {
		Double halfWidth = squareSize[0];
		Double halfHeight = squareSize[1];

		Log.i(TAG, "halfWidth:" + halfWidth + " halfHeight:" + halfHeight);
		Point lowestEdgeLeft = new Point(center.x - halfWidth, center.y
				+ halfHeight);

		return lowestEdgeLeft;
	}

	// TODO needed?
	// if so, add description
	public Scalar getColorSalar(Mat mRgbaWork, Point pt) {
		double[] color = mRgbaWork.get((int) pt.x, (int) pt.y);
		return new Scalar(color);
	}

	// TODO update Description
	/**
	 * compares alignment of all squares in global squareCenter-list and tries
	 * to find stacked squares if two squares are stacked the method deletes one
	 * of them and extends the first to the size of both
	 */
	public List<Beacon> findBeacon(List<Square> squareList) {
		List<Beacon> beaconList = new ArrayList<Beacon>();
		Double TOLx = 40.0;
		Double TOLy = 20.0;
		if (squareList.size() > 0) {
			for (int i = 0; i < squareList.size() - 1; i++) {
				for (int j = 1; j < squareList.size(); j++) {
					// it's not possible to write compare method in
					// point-class...
					Square squareA = squareList.get(i);
					Square squareB = squareList.get(j);
					if (compare2PtbyX(squareA.getLowPt(), squareB.getLowPt()) <= TOLx
							&& compare2PtbyY(squareA.getLowPt(),
									squareB.getLowPt()) <= TOLy) {
						Point newLowerLeftEdge = new Point();
						Point newCenterPt = new Point();
						Double newHalfHeight = (squareA.getCenter().y + squareB
								.getCenter().y) / 2.0;
						if (squareA.getCenter().y > squareB.getCenter().y) {
							// squareA is above squareB
							newCenterPt = new Point(squareA.getCenter().x,
									squareA.getCenter().y
											- (2 * squareA.getHalfHeight()));
							newLowerLeftEdge = new Point(
									squareA.getLowerLeftEdge().x,
									squareA.getLowerLeftEdge().y
											- (2 * squareA.getHalfHeight()));
							Beacon newBeacon = new Beacon(newCenterPt,
									newHalfHeight, newLowerLeftEdge,
									squareB.getColorID(), squareA.getColorID());
							if (!beaconList.contains(newBeacon)) {
								beaconList.add(newBeacon);
							}
						} else {
							// squareB is above squareA
							newCenterPt = new Point(squareB.getCenter().x,
									squareB.getCenter().y
											- (2 * squareB.getHalfHeight()));
							newLowerLeftEdge = new Point(
									squareB.getLowerLeftEdge().x,
									squareB.getLowerLeftEdge().y
											- (2 * squareB.getHalfHeight()));
							Beacon newBeacon = new Beacon(newCenterPt,
									newHalfHeight, newLowerLeftEdge,
									squareA.getColorID(), squareB.getColorID());
							if (!beaconList.contains(newBeacon)) {
								beaconList.add(newBeacon);
							}
						}
						// overwrite/extend one square to the size of both
						// squares and
						// remove the second square form the list
					}
				}
			}
		}
		return beaconList;
	}

	// TODO update Description
	/**
	 * compares alignment of all squares in global squareCenter-list and tries
	 * to find stacked squares if two squares are stacked the method deletes one
	 * of them and extends the first to the size of both
	 */
	public List<Beacon> findBeaconOrdered(List<Square> squareList) {
		Collections.sort(squareList);
		Log.i(TAG, "squareList: " + squareList.toString());
		List<Beacon> beaconList = new ArrayList<Beacon>();
		Double TOLx = 60.0;
		Double TOLy = 60.0;
		for (int i = 0; i < squareList.size() - 1; i++) {
			for (int j = i+1; j < squareList.size(); j++) {
				Integer squareFoundBelow = 0;
				Square squareA = squareList.get(i);
				Square squareB = squareList.get(j);
				if (compare2PtbyX(squareA.getCenter(), squareB.getCenter()) <= TOLx
						&& compare2PtbyY(squareA.getCenter(),
								squareB.getCenter()) <= TOLy) { // TODO Take
																// lower point
																// of upper
																// object and
																// upper point
																// of lower
																// object;
																// decrease TOLy
					Point newCenterPt;
					Point newLowerLeftEdge;
					if (squareA.getCenter().y < squareB.getCenter().y) { // TODO
																			// Check
																			// if
																			// list
																			// is
																			// sorted;
																			// not
																			// needed
																			// then
						squareFoundBelow++;
						// squareA is above squareB
						newCenterPt = new Point(squareA.getCenter().x,
								squareA.getLowPt().y);
						newLowerLeftEdge = new Point(
								squareA.getLowerLeftEdge().x,
								squareA.getLowerLeftEdge().y
										+ (2 * squareA.getHalfHeight()));
						Beacon newBeacon = new Beacon(newCenterPt,
								squareB.getLowPt(), newLowerLeftEdge,
								squareB.getColorID(), squareA.getColorID());
						if (!beaconList.contains(newBeacon)
								&& squareFoundBelow < 2) {
							beaconList.add(newBeacon);
						}
					} else {
						squareFoundBelow++;
						// squareB is above squareA
						newCenterPt = new Point(squareB.getCenter().x,
								squareB.getLowPt().y);
						newLowerLeftEdge = new Point(
								squareB.getLowerLeftEdge().x,
								squareB.getLowerLeftEdge().y
										+ (2 * squareB.getHalfHeight()));
						Beacon newBeacon = new Beacon(newCenterPt,
								squareA.getLowPt(), newLowerLeftEdge,
								squareA.getColorID(), squareB.getColorID());
						if (!beaconList.contains(newBeacon)
								&& squareFoundBelow < 2) {
							beaconList.add(newBeacon);
						}
					}
					// overwrite/extend one square to the size of both
					// squares and
					// remove the second square form the list
				}
			}
		}
		return beaconList;
	}

	// TODO update description
	/**
	 * computes the difference of 2 points along the x-axis
	 * 
	 * @param one
	 *            point
	 * @param another
	 *            point
	 * @return absolute difference value
	 */
	public Double compare2PtbyX(Point a, Point b) {
		return Math.abs(a.x - b.x);
	}

	// TODO update description
	/**
	 * like compare2PtbyX, but only the y-axis
	 * 
	 * @param center
	 * @param center2
	 * @return
	 */
	private Double compare2PtbyY(Point a, Point b) {
		return Math.abs(a.y - b.y);
	}

	// TODO: Rename and finalize (see TODOs within method)
	public double[] squareSize(MatOfPoint contours, Point center) {
		Double width = 0.0, height = 0.0;
		int countH = 0, countW = 0, count = 0;
		List<Point> pts = contours.toList();
		for (Point p : pts) {
			width += Math.abs(p.x - center.x);
			height += Math.abs(p.y - center.y);
			count++;
		}

		width = width / count;
		height = height / count;

		Double halfHeight = 0.0, halfWidth = 0.0;
		Double borderLeft = center.x - width;
		Double borderRight = center.x + width;
		Double borderTop = center.y + height;
		Double borderBottom = center.y - height;

		for (Point p : pts) {
			if (borderLeft <= p.x && p.x <= borderRight) {
				halfHeight += Math.abs(p.y - center.y);
				countH++;
			}
			if (borderBottom <= p.y && p.y <= borderTop) {
				halfWidth += Math.abs(p.x - center.x);
				countW++;
			}
		}
		double[] squareSize = new double[2];
		squareSize[0] = halfWidth / countW;
		squareSize[1] = halfHeight / countH;
		return squareSize;
	}

}