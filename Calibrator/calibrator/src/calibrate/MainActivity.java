package calibrate;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jp.ksksue.driver.serial.FTDriver;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.CvType;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Rect;
import org.opencv.core.Scalar;
import org.opencv.imgproc.Imgproc;

import calibrate.generated.R;
import robot.navigate.Position;
import robot.navigate.Robot;
import robot.opencv.ImageProcessor;
import robot.shapes.Ball;
import robot.shapes.Beacon;
import robot.shapes.BeaconSquareHolder;
import robot.shapes.Circle;
import robot.shapes.Square;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.hardware.usb.UsbManager;
import android.os.Bundle;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.MotionEvent;
import android.view.View;
import android.view.View.OnTouchListener;
import android.view.WindowManager;

import android.widget.Button;
import android.widget.EditText;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;
import android.widget.TextView;
import android.widget.Toast;

public class MainActivity extends Activity implements OnTouchListener,
		CvCameraViewListener2 {

	// TODO: Use LEDs to display connection to the robot.

	// TODO: Probably add a function that allows to turn robot by velocity (and
	// since depending on the angle different correctorfactors are needed, we
	// need to do this via a switch case)

	// TODO: Add comments for variables.

	// TODO: Resolve warnings in all xml-files.

	// TODO: target Position does not allow negative inputs in GUI

	// TODO: Ex3: beacons: Detection of multiple, multi-colored objects, finding
	// their bottom points, calculating and displaying their locations in the
	// robot's egocentric ground-plane coordinates, as well as their distances
	// to the robot, using a pre-calibrated homography matrix

	// TODO: Ex3: The robot is placed at an arbitrary location within a
	// rectangular workspace of roughly 2.5m by 2.5m in size, surrounded by 8
	// beacons spaced 125cm apart and placed around the setup. Update the robot
	// odometry based on these values.

	// TODO: Ex3: About 10 balls of known colors are placed at arbitrary
	// locations within the workspace. Find them and bring to target position.

	// TODO: don't use squareTest for Beacons (use it for balls)

	// GUI Elements
	private TextView textLog; // Textview on GUI which contains the robot's log
	private EditText editText1; // Textfield on GUI for entering x-coordinate of
								// target
	private EditText editText2; // Textfield on GUI for entering y-coordinate of
								// target
	private EditText editText3; // Textfield on GUI for entering theta-alignment
								// at target

	private TextView textView4;

	private SeekBar seekBar1;
	private SeekBar seekBar2;
	private SeekBar seekBar3;

	// General variables
	private static final String TAG = "RobotLog"; // Tag for log-messages sent
													// to logcat
	private static final int CV_FONT_HERSHEY_COMPLEX = 0;

	// Used Classes
	private Robot robot; // Used to control the robot.
	private ImageProcessor imageProcessor; // Used for image processing.

	// Imageprocessing specific variables
	private Mat homographyMatrix; // the homography matrix is used to map camera
									// pixels to the ground plane (relative to
									// the robot's cam)
	private int frameInterval = 0; // Helper variable which is needed to
									// calculate the ball centers only every
									// 15th frame.
	private int executionInterval = 15; // Every executionInterval frames, the
	// objects are drawn into the camera
	// frame
	private Mat mRgbaOutput; // Current image for output (circles etc. can be
								// added to this image); updated every
								// cameraframe
	private Mat mRgbaWork; // Current image for image processing (not to be
							// modified!); updated every cameraframe

	private Integer onTouchOption = 0; // 0 -> read CircleColors; 1 -> read
										// BeaconColors

	private List<Scalar> myColors = new ArrayList<Scalar>(); // Stores all
																	// currently
																	// recognized
																	// colors
																	// for balls
	private List<String> myColorNames = new ArrayList<String>();
	
	private Integer colorIndex = 0;

	private CameraBridgeViewBase mOpenCvCameraView; // interaction between
													// openCV and camera

	// TODO: write own method to update these lists
	List<Circle> circleList = new ArrayList<Circle>();

	List<Square> squareList = new ArrayList<Square>();
	List<Square> confirmedSquares = new ArrayList<Square>();

	List<Beacon> beaconList = new ArrayList<Beacon>();
	// Robot specific variables
	// TODO Use Position.java instead of targetX and targetY
	private double targetX = 100.0; // target's x-coordinate
	private double targetY = 100.0; // target's y-coordinate
	private int targetTheta = 45; // alignment at target point

	private int globalHue = 0;
	private int globalSaturation = 0;
	private int globalLightness = 0;

	/**
	 * Connects to the robot when app is started and initializes the position of
	 * the robot's bar.
	 * 
	 * @param savedInstanceState
	 */
	@Override
	protected void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.activity_main);
		// getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		seekBar1 = (SeekBar) findViewById(R.id.seekBar1);
		seekBar2 = (SeekBar) findViewById(R.id.seekBar2);
		seekBar3 = (SeekBar) findViewById(R.id.seekBar3);
		textView4 = (TextView) findViewById(R.id.textView4);

		seekBar1.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				globalHue = progress;
				sendColorRadius();
			}

			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			public void onStopTrackingTouch(SeekBar seekBar) {
				Toast.makeText(MainActivity.this, "Hue:" + globalHue,
						Toast.LENGTH_SHORT).show();
			}
		});

		seekBar2.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				globalSaturation = progress;
				sendColorRadius();
			}

			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			public void onStopTrackingTouch(SeekBar seekBar) {
				Toast.makeText(MainActivity.this,
						"Saturation:" + globalSaturation, Toast.LENGTH_SHORT)
						.show();
			}
		});

		seekBar3.setOnSeekBarChangeListener(new OnSeekBarChangeListener() {

			public void onProgressChanged(SeekBar seekBar, int progress,
					boolean fromUser) {
				globalLightness = progress;
				sendColorRadius();
			}

			public void onStartTrackingTouch(SeekBar seekBar) {

			}

			public void onStopTrackingTouch(SeekBar seekBar) {
				Toast.makeText(MainActivity.this,
						"Lightness:" + globalLightness, Toast.LENGTH_SHORT)
						.show();
			}
		});


		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.color_blob_detection_activity_surface_view);
		mOpenCvCameraView.setCvCameraViewListener(this);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		// initialize myBeaconColors & myColors
		myColors.add(new Scalar(147, 170, 100)); // blue
		myColors.add(new Scalar(40, 190, 150)); // yellow
		myColors.add(new Scalar(0, 190, 145)); // red
		myColors.add(new Scalar(75, 160, 120)); // green

		myColors.add(new Scalar(98, 160, 110)); // green
		myColors.add(new Scalar(250, 200, 165)); // red
		myColors.add(new Scalar(152, 200, 80)); // blue
		myColors.add(new Scalar(6, 190, 148)); // orange

		myColorNames.add("beacon: blue");
		myColorNames.add("beacon: yellow");
		myColorNames.add("beacon: red");
		myColorNames.add("beacon: green");
		myColorNames.add("circle: green");
		myColorNames.add("circle: red");
		myColorNames.add("circle: blue");
		myColorNames.add("circle: orange");
		
	}


	public void toggleStructuringElement(View v) {
		imageProcessor.useCircle = !imageProcessor.useCircle;

		Button p1_button = (Button)findViewById(R.id.button2);
		if (imageProcessor.useCircle) {
			p1_button.setText("Circle ON");
		} else
			p1_button.setText("Square ON");	
	}


	public void nextColor(View v) {
		if (colorIndex + 1 < myColors.size()) {
			colorIndex++;
		} else
			colorIndex = 0;
	}

	public void sendColorRadius() {
		imageProcessor.setColorRadius(globalHue, globalSaturation,
				globalLightness);
		textView4.setText("HSV: " + globalHue + " ; " + globalSaturation
				+ " ; " + globalLightness);
	}

	public void buttonMoveToGoalN3_onClick(View v) {
		robot.resetPosition();
		Thread t = new Thread() {

			@Override
			public void run() {
				robot.moveToGoalNaive3(150, 150, 45);
			};
		};

		t.start();
	}

	public void buttonFindSensorIDs_onClick(View v) {
		try {
			robot.findSensorIDs();
		} catch (Exception e) {
		}
	}

	public void buttonOneMeter_onClick(View v) {
		Thread t = new Thread() {

			@Override
			public void run() {
				robot.moveByDistance(100);
			};
		};

		t.start();
	}

	public void buttonOneMeterDriveByVel_onClick(View v) {
		Thread t = new Thread() {

			@Override
			public void run() {
				robot.moveByVelocity(100, false);
			};
		};

		t.start();
	}

	public void buttonOneMeterDriveByVelSlow_onClick(View v) {
		Thread t = new Thread() {

			@Override
			public void run() {
				robot.moveByVelocitySlow(100, false);
			};
		};

		t.start();
	}

	public void button360Deg_onClick(View v) {
		Thread t = new Thread() {

			@Override
			public void run() {
				for (int i = 0; i < 12; i++)
					robot.turnByDistance(30, 'r');
			};
		};

		t.start();
	}

	public void button360DegByVel_onClick(View v) {
		Thread t = new Thread() {

			@Override
			public void run() {
				robot.turnByVelocity(180, 'r');
				robot.turnByVelocity(180, 'r');
			};
		};

		t.start();
	}

	public void buttonMinus_onClick(View v) {
		robot.moveBar('-');
	}

	public void buttonPlus_onClick(View v) {
		robot.moveBar('+');
	}

	public void buttonSensor_onClick(View v) {
		Map<String, Integer> measurement = new HashMap<String, Integer>();
		measurement = robot.getDistance();
		for (Map.Entry<String, Integer> entry : measurement.entrySet()) {
			textLog.append((entry.getKey() + entry.getValue() + "\n"));
		}
	}

	public void buttonEightZero_onClick(View v) {
		robot.resetPosition();
		Thread t = new Thread() {

			@Override
			public void run() {
				robot.moveSquare(50, 'r', 0);
				robot.moveSquare(50, 'l', 0);
			};
		};
		t.start();
	}

	public void buttonEightOne_onClick(View v) {
		robot.resetPosition();
		Thread t = new Thread() {

			@Override
			public void run() {
				if (robot.moveSquare(50, 'r', 1)) {
					robot.moveSquare(50, 'l', 1);
				}
			};
		};
		t.start();
	}

	public void buttonEightTwo_onClick(View v) {
		robot.resetPosition();
		Thread t = new Thread() {

			@Override
			public void run() {
				robot.moveSquare(50, 'r', 2);
				robot.moveSquare(50, 'l', 2);
			};
		};
		t.start();
	}

	public void buttonReadTargetPoint(View v) {
		targetX = Integer.parseInt(editText1.getText().toString());
		targetY = Integer.parseInt(editText2.getText().toString());
		targetTheta = Integer.parseInt(editText3.getText().toString());
		robot.writeLog("new target at x: " + targetX + " y: " + targetY
				+ " theta: " + targetTheta);
	}

	public void buttonMLineDemo_onClick(View v) {
		robot.resetPosition();
		Thread t = new Thread() {

			@Override
			public void run() {
				int x = 200;
				int y = 200;
				int theta = 45;
				robot.turnByDistanceBalanced(90, 'r');
				robot.moveByVelocity(100, true);
				robot.turnByDistanceBalanced(135, 'l');
				robot.driveToIntersectionMLine(150, x, y);
				robot.robotSetLeds(0, 0);
				robot.robotSetLeds(127, 127);
				robot.robotSetLeds(0, 0);
				robot.robotSetLeds(127, 127);
				robot.robotSetLeds(0, 0);
				robot.moveToGoalNaive2(x, y, theta);
			};
		};
		t.start();
	}

	public void buttonmoveToGoalN2_onClick(View v) {
		robot.resetPosition();
		Thread t = new Thread() {

			@Override
			public void run() {
				robot.moveToGoalNaive2(250, 130, 45);
			};
		};
		t.start();
	}

	public void buttonDriveAndRead_onClick(View v) {
		robot.resetPosition();
		Thread t = new Thread() {

			@Override
			public void run() {
				robot.driveAndRead();
			};
		};

		t.start();
	}

	public void buttonTest_onClick(View v) {

		Thread t = new Thread() {

			@Override
			public void run() {
				// robot.riseBarUp();
				robot.updateGlobalPosition(findTwoBeacons(), homographyMatrix);
				// robot.moveToTarget(100, 100, 0);
			};
		};

		t.start();
	}

	public void buttonTest2_onClick(View v) {

		Thread t = new Thread() {

			@Override
			public void run() {
				robot.robotSetBar(0);
				// findTwoBeacons();
			};
		};

		t.start();
	}



	public void button3_onClick(View v) {
		Thread t = new Thread() {

			@Override
			public void run() {
				homographyMatrix = new Mat();
				int i = 0;
				while (homographyMatrix.empty()) {
					try {
						robot.writeLog(i++
								+ ". try: searching homography Matrix.");
						homographyMatrix = imageProcessor
								.getHomographyMatrix(mRgbaOutput);
						Thread.sleep(1000);
					} catch (InterruptedException e) {
						// do nothing
					}
				}
				robot.writeLog("Homography Matrix found.");
			};
		};

		t.start();
	}

	/**
	 * in the default case the OnTouch method adds a selected color to the
	 * circle list
	 * 
	 * this method allows you to switch the color target to the beacon list and
	 * vice versa
	 * 
	 * 
	 * @param v
	 */
	public void ButtontoggleInputColor(View v) {
		Thread t = new Thread() {

			@Override
			public void run() {
				if (onTouchOption == 0) {
					onTouchOption = 1; // read Beacon Color
					robot.writeLog("onTouch reads now beacon colors");
				} else {
					onTouchOption = 0; // read Circle Color
					robot.writeLog("onTouch reads now circle colors");
				}
			};
		};

		t.start();
	}


	/**
	 * Enables access to the camera and on touch events on the according view.
	 */
	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@SuppressLint("ClickableViewAccessibility")
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
			case LoaderCallbackInterface.SUCCESS: {
				mOpenCvCameraView.enableView();
				mOpenCvCameraView.setOnTouchListener(MainActivity.this);
			}
				break;
			default: {
				super.onManagerConnected(status);
			}
				break;
			}
		}
	};

	/**
	 * Closes camera view when application is closed or the system tries to free
	 * memory.
	 */
	public void onDestroy() {
		super.onDestroy();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
	}

	/**
	 * Initializes the camera view and associated image matrices with the given
	 * width and height.
	 * 
	 * @param width
	 *            width of the camera view (in pixels)
	 * @param height
	 *            height of the camera view (in pixels)
	 */
	public void onCameraViewStarted(int width, int height) {
		mRgbaWork = new Mat(height, width, CvType.CV_8UC4);
		mRgbaOutput = new Mat(height, width, CvType.CV_8UC4);
		imageProcessor = new ImageProcessor(TAG);
	}

	/**
	 * clears memory in case camera view is stopped
	 */
	public void onCameraViewStopped() {
		mRgbaWork.release();
		mRgbaOutput.release();
	}

	/**
	 * When camera view is touched, this method saves the color of the touched
	 * pixel (and averaged surrounding pixels). This color is then tracked for
	 * finding blobs in the view.
	 * 
	 * @param v
	 *            touched view
	 * @param event
	 *            object that is used to report movement (position etc.)
	 * @return false (no need for subsequent touch events)
	 */
	@SuppressLint("ClickableViewAccessibility")
	public boolean onTouch(View v, MotionEvent event) {
		return false;
	}

	/**
	 * Gets called every camera frame. Updates the global image matrices
	 * mRgbaOutput and mRgbaWork which are used for image processing and image
	 * display.
	 * 
	 * @param inputFrame
	 *            current camera frame
	 * @return the image matrix to display
	 */
	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		mRgbaOutput = inputFrame.rgba();
		mRgbaWork = inputFrame.rgba(); // TODO: does mRgbaWork refer to the same
										// image as mRgbaOutput? In that case,
										// either fix or remove this variable.

		// draw squares on CameraFrame

		Mat grayImg = new Mat();
		if (!myColors.isEmpty()) {
				grayImg = imageProcessor.filter(mRgbaWork, myColors.get(colorIndex));
			mRgbaOutput = grayImg;
		}
		return mRgbaOutput;
	}

	/**
	 * Disables camera view if Application switches into background.
	 */
	@Override
	public void onPause() {
		super.onPause();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
	}

	/**
	 * Enables camera view if Application resumes.
	 */
	@Override
	public void onResume() {
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this,
				mLoaderCallback);
	}

	/**
	 * Turns until two beacons are seen by the robot.
	 * 
	 * @return List of beacons.
	 */
	public List<Beacon> findTwoBeacons() {
		ImageProcessor imgProc = new ImageProcessor(TAG);
		List<Beacon> beacons = imgProc.findBeacons(confirmedSquares)
				.getBeaconList();
		int angle = 0;
		while (beacons.size() < 2 && angle < 360) {
			angle += 15;
			robot.turnByDistance(15, 'r');
			try {
				Thread.sleep(1000);
			} catch (InterruptedException e) {
				Log.e(TAG, e.getMessage());
			}
			beacons = imgProc.findBeacons(confirmedSquares).getBeaconList();
		}
		if (angle >= 360) {
			robot.moveByVelocity(30.0, false);
		}
		return beacons;
	}


	/**
	 * updates global position using beacons every ~15
	 */
	public void UpdateselfLocalization() {
		findTwoBeacons();
		robot.updateGlobalPosition(beaconList, homographyMatrix);
	}

}