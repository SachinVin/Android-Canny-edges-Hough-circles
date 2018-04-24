package com.example1.edge1;

import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.Intent;
import android.os.AsyncTask;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;
import android.widget.Toast;

import org.opencv.android.BaseLoaderCallback;
import org.opencv.android.CameraBridgeViewBase;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewFrame;
import org.opencv.android.CameraBridgeViewBase.CvCameraViewListener2;
import org.opencv.android.LoaderCallbackInterface;
import org.opencv.android.OpenCVLoader;
import org.opencv.core.Core;
import org.opencv.core.Mat;
import org.opencv.core.Point;
import org.opencv.core.Scalar;
import org.opencv.core.Size;
import org.opencv.imgproc.Imgproc;

import java.io.IOException;
import java.util.UUID;

public class CameraActivity extends Activity implements CvCameraViewListener2 {

	private static final String TAG = "OCVSample::Activity";

	public static final int VIEW_MODE_RGBA = 0;
	public static final int VIEW_MODE_CANNY = 2;
	public static final int VIEW_MODE_HOUGH = 3;

	private CameraBridgeViewBase mOpenCvCameraView;
	private TextView textView ;
	private TextView textAvg;

	private Mat mIntermediateMat;
	private Boolean calibrate=false;
	private Double xCalib =0.0;
	private Double yCalib =0.0;
    private Double xAvg =0.0;
    private Double yAvg =0.0;
	int CalibCount=0;
	int avgCount=0;

    static String address = null;
    private ProgressDialog progress;
    static BluetoothAdapter myBluetooth = null;
    private static BluetoothSocket btSocket = null;
    private static boolean isBtConnected = false;
    static final UUID myUUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

	public static int viewMode = VIEW_MODE_RGBA;



	private BaseLoaderCallback mLoaderCallback = new BaseLoaderCallback(this) {
		@Override
		public void onManagerConnected(int status) {
			switch (status) {
			case LoaderCallbackInterface.SUCCESS: {
				Log.i(TAG, "OpenCV loaded successfully");

				mOpenCvCameraView.enableView();

			}
				break;
			default: {
				super.onManagerConnected(status);
			}
				break;
			}
		}
	};

	public CameraActivity() {
		Log.i(TAG, "Instantiated new " + this.getClass());
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		Log.i(TAG, "called onCreate");
		super.onCreate(savedInstanceState);
		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
		setContentView(R.layout.camera_layout);

        // receive the address of the bluetooth devicerom previous activity
        Intent newint = getIntent();
        address = newint.getStringExtra(DeviceList.EXTRA_ADDRESS);

        // Check if selection of Bluetooth device was skipped
        if(!address.equals("SKIP")) new ConnectBT().execute();

		mOpenCvCameraView = (CameraBridgeViewBase) findViewById(R.id.camera_view);
		// Camera select
        // 0-Rear camera
        // 1-Front camera
		mOpenCvCameraView.setCameraIndex(1);
		mOpenCvCameraView.setCvCameraViewListener(this);
		textView = (TextView) findViewById(R.id.textView1);

	}

	@Override
	public void onPause() {
		super.onPause();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
	}

	@Override
	public void onResume() {
		super.onResume();
		OpenCVLoader.initAsync(OpenCVLoader.OPENCV_VERSION_2_4_3, this,
				mLoaderCallback);
	}

	public void onDestroy() {
		super.onDestroy();
		if (mOpenCvCameraView != null)
			mOpenCvCameraView.disableView();
		Disconnect();
	}

	public void onCameraViewStarted(int width, int height) {
		mIntermediateMat = new Mat();

	}

	public void onCameraViewStopped() {
		if (mIntermediateMat != null)
			mIntermediateMat.release();

		mIntermediateMat = null;
	}

	public Mat onCameraFrame(CvCameraViewFrame inputFrame) {
		Mat rgba = inputFrame.rgba();
		Size sizeRgba = rgba.size();
		Core.flip(rgba, rgba, 1);

		Mat rgbaInnerWindow;

		int rows = (int) sizeRgba.height;
		int cols = (int) sizeRgba.width;

		int left = cols / 8;
		int top = rows / 8;

		int width = cols * 1 / 4;
		int height = rows * 3 / 4;



		switch (CameraActivity.viewMode) {

		case CameraActivity.VIEW_MODE_RGBA:
			break;

		case CameraActivity.VIEW_MODE_CANNY:
			rgbaInnerWindow = rgba
					.submat(top, top + height, left, left + width);
			Imgproc.cvtColor(rgbaInnerWindow,rgbaInnerWindow,Imgproc.COLOR_BGR2GRAY);
			Imgproc.Canny(rgbaInnerWindow, mIntermediateMat, 60, 70);
			Imgproc.cvtColor(mIntermediateMat, rgbaInnerWindow,
					Imgproc.COLOR_GRAY2BGRA, 4);

			rgbaInnerWindow.release();
			break;

		case CameraActivity.VIEW_MODE_HOUGH:
			rgbaInnerWindow = rgba
					.submat(top, top + height, left, left + width);
			Mat circles = rgbaInnerWindow.clone();
			Imgproc.GaussianBlur(rgbaInnerWindow, rgbaInnerWindow, new Size(5,
					5), 2, 2);
			Imgproc.cvtColor(rgbaInnerWindow,mIntermediateMat,Imgproc.COLOR_BGRA2GRAY);
			Imgproc.equalizeHist(mIntermediateMat,mIntermediateMat);
			//Core.inRange(mIntermediateMat,new Scalar(10,10,10),
			//		new Scalar(100,100,100),mIntermediateMat);
			Imgproc.HoughCircles(mIntermediateMat, circles,
					Imgproc.CV_HOUGH_GRADIENT, 1, 105, 100, 13,
					32, 45);
			Imgproc.cvtColor(mIntermediateMat, rgbaInnerWindow,
					Imgproc.COLOR_GRAY2BGRA, 4);
			// Draw a rectangle around the detection area
			Core.rectangle(rgba, new Point(left,top),new Point(left + width,top + height),
					new Scalar(255, 0, 0));
			// Intl'
			Point pt=new Point(0,0);

			for (int x = 0; x < circles.cols(); x++) {
				double vCircle[] = circles.get(0, x);
				if (vCircle == null)
					break;
				pt = new Point(Math.round(vCircle[0]),
						Math.round(vCircle[1]));
				int radius = (int) Math.round(vCircle[2]);
				Log.d("cv", pt + " radius " + radius);
				Core.circle(rgbaInnerWindow, pt, 3, new Scalar(0, 0, 255), 5);
				Core.circle(rgbaInnerWindow, pt, radius, new Scalar(255, 0, 0),	2);

				if(calibrate){
					calibrateEye(pt);
				}
			}
			// Draw the Calibrated position of the eye
			Point calibPt = new Point(xCalib, yCalib);

			Core.circle(rgbaInnerWindow, calibPt, 3, new Scalar(0, 255, 255), 5);
			Core.circle(rgbaInnerWindow, calibPt, 5, new Scalar(255, 255, 0), 2);

			//Current Average position of the Eye; To reduce noise.
			Point AvgPt = averageEye(pt);

			Core.circle(rgbaInnerWindow, AvgPt, 3, new Scalar(255,0 , 255), 5);
			Core.circle(rgbaInnerWindow, AvgPt, 5, new Scalar(0, 255, 255), 2);

			rgbaInnerWindow.release();
			break;

		}

		return rgba;
	}

    class ConnectBT extends AsyncTask<Void, Void, Void>  // Bluetooth backend
    {
        private boolean ConnectSuccess = true; //if it's here, it's almost connected

        @Override
        protected void onPreExecute() {
            progress = ProgressDialog.show(CameraActivity.this, "Connecting...", "Please wait!!!");  //show a progress dialog
        }

        @Override
        protected Void doInBackground(Void... devices) //while the progress dialog is shown, the connection is done in background
        {
            try {
                if (btSocket == null || !isBtConnected) {
                    myBluetooth = BluetoothAdapter.getDefaultAdapter();//get the mobile bluetooth device
                    BluetoothDevice device = myBluetooth.getRemoteDevice(address);//connects to the device's address and checks if it's available
                    btSocket = device.createInsecureRfcommSocketToServiceRecord(myUUID);//create a RF COMM (SPP) connection
                    BluetoothAdapter.getDefaultAdapter().cancelDiscovery();
                    btSocket.connect();//start connection
                }
            } catch (IOException e) {
                ConnectSuccess = false;//if the try failed, you can check the exception here
                Log.e("Connection" , String.valueOf(e));
            }
            return null;
        }

        @Override
        protected void onPostExecute(Void result) //after the doInBackground, it checks if everything went fine
        {
            super.onPostExecute(result);

            if (!ConnectSuccess) {
                msg("onPostExecute: Connection Failed. Is it a SPP Bluetooth? Try again.");
                finish();
            } else {
                Log.i("Connection","Connection established");
                msg("Connected");
                isBtConnected = true;
            }
            progress.dismiss();
        }
    }

	private void Send(String s)
	{
		if (btSocket!=null && !address.equals("SKIP"))
		{
			try
			{
				btSocket.getOutputStream().write(s.getBytes());
			}
			catch (IOException e)
			{
				Log.e("DataSending", String.valueOf(e));
				msg("Error Sending data");
			}
		}
	}

    private void msg(String s) {
	    // Make writing a Toast easier
        Toast.makeText(getApplicationContext(), s, Toast.LENGTH_LONG).show();
    }

    private void Disconnect()
    {
        if (btSocket!=null) //If the btSocket is busy
        {
            try
            {
                btSocket.close(); //close connection
            }
            catch (IOException e)
            {
                Log.w("Disconnect", String.valueOf(e));
            }
        }
        finish(); //return to the first layout
    }

	public Point averageEye(Point point){
		//Calculate exponentially weighted average
		Double samples = 20d;

		if(!point.equals(new Point(0,0))) {
			xAvg -= xAvg / samples;
			yAvg -= yAvg / samples;

			xAvg += point.x / samples;
			yAvg += point.y / samples;

			avgCount++;

			if(avgCount > samples){
				sendDir(xAvg,yAvg);
				avgCount = 0;
			}
		}
		Log.i("Average point", String.valueOf(point));
        return new Point(xAvg,yAvg);
    }
	public void calibrateEye(Point point){
		//Find the average position of the eye or n samples
		Double samples = 60d;

		xCalib += point.x/samples;
		yCalib += point.y/samples;

		CalibCount++;

		textView.setText(""+ (60-CalibCount));
		if(CalibCount>samples) {
			calibrate = false;
			CalibCount = 0;
			Log.i(TAG,"Calibrated x & y:"+ xCalib +","+ yCalib);
		}
	}

	//send directions to the microcontroller
	public void sendDir(Double x , Double y){
		Double xDiff= xCalib-x;
		Double yDiff= yCalib-y;

		int error = 10;

		if(xDiff>error && (yDiff<error && yDiff>-error) ){
			Send("f");
		}
		else if(xDiff<-error && (yDiff<error && yDiff>-error) ){
			Send("b");
		}
		else if(yDiff>error && (xDiff<error && xDiff>-error) ){
			Send("l");
		}
		else if(yDiff<error && (xDiff<error && xDiff>-error) ){
			Send("r");
		}
		else{
			Send("s");
		}
		//Send(String.valueOf(new Point(xDiff,yDiff)));

	}

	public void Canny(View view) {
		if (viewMode != VIEW_MODE_CANNY) {
			viewMode = VIEW_MODE_CANNY;
		} else {
			viewMode = VIEW_MODE_RGBA;
		}
	}

	public void Hough(View view) {
		if (viewMode != VIEW_MODE_HOUGH) {
			viewMode = VIEW_MODE_HOUGH;
		} else {
			viewMode = VIEW_MODE_RGBA;
		}
	}

	public void calibrateBtn(View view){
		xCalib =0d;
		yCalib =0d;
		calibrate = true;
        viewMode = VIEW_MODE_HOUGH;
	}

}
