package upv.remoteCar;



import android.content.Context;
import android.content.Intent;

import android.graphics.Bitmap;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;
import android.hardware.*;


import java.io.*;
import java.util.Scanner;
import java.util.UUID;

import android.app.Activity;
import android.bluetooth.*;
import android.os.Bundle;
import android.os.Vibrator;
import android.text.Layout;
import android.view.*;
import android.view.WindowManager.LayoutParams;
import android.view.animation.Animation;
import android.view.animation.RotateAnimation;
import android.widget.*;

//import android.view.WindowManager.LayoutParams;

public class RemoteCarActivity extends Activity  implements SensorEventListener {

	private BluetoothSocket btSocket = null;
	private OutputStream out;
	private Scanner in;

	// ACCELEROMETER
	private SensorManager mSensorManager;
	private float  values[] = {((float)0.0), ((float)0.0), ((float)0.0)};
	// ACCELEROMETER

	// OPTIONS
	private byte hornPressed = 0;
	private byte afLights = 0;

	private int RecLength = 0;
	private int RecMax = 100000;
	private int replayProgress = 0;
	private byte record[][] = new byte[RecMax][5];

	private boolean engineON = false; 
	private boolean recording = false;
	private boolean replaying = false;
	// OPTIONS

	// DATA
	private byte sync = (byte)250;
	private byte forward;
	private byte speed;
	private byte steer;
	private byte klaxon;
	private byte lights;
	// DATA



	void enableBluetooth(BluetoothAdapter localBT)
	{
		final int REQUEST_ENABLE_BT = 1;

		if (!localBT.isEnabled()) {
			Intent enableBtIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
			startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT);
		}
	}



	private BluetoothSocket setUpBluetooth() {

		final UUID MY_UUID = 
				UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");

		BluetoothSocket bS;

		BluetoothAdapter localBT = BluetoothAdapter.getDefaultAdapter();

		enableBluetooth(localBT); //ask for enabling bluetooth

		BluetoothAdapter mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

		BluetoothDevice remoteBT = mBluetoothAdapter.getRemoteDevice("00:19:5D:EE:A1:28");//BTcar
		//BluetoothDevice remoteBT = mBluetoothAdapter.getRemoteDevice("E0:F8:47:1A:B6:1B");//Pau

		try {
			bS = remoteBT.createRfcommSocketToServiceRecord(MY_UUID);
		} catch (IOException e) { return null;}

		mBluetoothAdapter.cancelDiscovery();

		try {
			bS.connect();
		} catch (IOException e) {try {bS.close();return null;} catch (IOException e2) {}}

		try {
			out = bS.getOutputStream();
			in = new Scanner(bS.getInputStream());
		}catch (Exception e){}

		return bS;

	}



	private void readData() {
		//final ImageView gdot = (ImageView) findViewById(R.id.gDot);
		int x=0,y=0,z=0;
		int gAcc = 250;
		try {
			Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
			//System.out.println("entro en el readData");
			if(in.hasNextInt()){
				if(in.nextInt() == 5000){
					x = in.nextInt();
					y = in.nextInt();
					//z = in.nextInt();
				} 
			} else return;
			if(x>120) v.vibrate(75);
			System.out.println(x);
			//final int xOK = (-(x*94)/gAcc)+200;
			//final int yOK = (-(y*94)/gAcc)+190;

			//System.out.println(xOK + " " +yOK);

			/*	Runnable r = new Runnable(){
				public void run(){
				//	gdot.layout(xOK, yOK, xOK+15, yOK+15);			}
			};
			runOnUiThread(r);*/
		}catch(Exception e){
			System.out.println("POLLA" + e);
		}
	}


	/*
	 * the "values" variable is the accelerometer data without treatment. Here we adjust it.
	 * */
	private void sendData(float data[]) {
		try{
			if(!replaying && engineON){
				forward = (byte)forward(data[0]);
				speed = (byte)speed(data[0]);
				steer = (byte)dir(data[1]);
				klaxon = (byte)hornPressed;
				lights = (byte)afLights;


				out.write(sync); //SYNC	

				out.write(forward); //forwards or backwards
				out.write(speed); //speed
				out.write(steer); //steering

				out.write(klaxon); //klaxon
				out.write(lights); //antiFog Lights

				out.flush();
				//System.out.println(forward + " "  + speed);
				if(recording){
					record[RecLength][0]=forward;
					record[RecLength][1]=speed;
					record[RecLength][2]=steer;
					record[RecLength][3]=klaxon;
					record[RecLength][4]=lights;
					RecLength++;
				}
			}
			else if(replaying){
				if(replayProgress++ < RecLength){
					out.write(sync);
					out.write(record[replayProgress][0]);
					out.write(record[replayProgress][1]);
					out.write(record[replayProgress][2]);
					out.write(record[replayProgress][3]);
					out.write(record[replayProgress][4]);
				}
				else{
					stopReplayRemote();
				}
			}
			Thread.sleep(100);
		} catch (Exception e) {System.out.println("SHIT SENDING.");}
	}

	private char forward (float v) {
		if(v <= 6.0) return 0;
		return 1;
	}
	private char speed (float v) {

		if(v > 4.0 && v < 6.0) {
			updateVelocimeter(0.0f);
			return 0;
		}

		if(v > 5.0) v-= 5.0f;
		else v = 5.0f -v;

		if(v < 0.0) v = 0.0f;
		if(v > 5.0) v = 5.0f;

		updateVelocimeter(v);
		return (char)(50+(v*38));
	}
	private char dir (float v) { 
		v += 5.0f;
		if(v > 10.0) v =10.0f;
		if(v < 0.0) v = 0.0f;

		updateVolante((v-5.0f)*18.0f);
		return (char)((70-(v*7))+90.0);
	}

	float oldPos= 0.0f;
	public void updateVolante(final float v)
	{
		try{
		final View engine = (View) findViewById(R.id.button1);
		Runnable polla = new Runnable(){
			public void run(){
				engine.setRotation(v);
			}
		};
		runOnUiThread(polla);
		}
		catch(Exception e)
		{
			System.out.println(e);
		}
	}
	
	public void updateVelocimeter(float v)
	{
		final int value = (int)((v/5.0)*13.0) -2;
		final ImageView img = (ImageView) findViewById(R.id.velocimeter);
		Runnable polla = new Runnable(){
			public void run(){
				switch (value)
				{
				case 0:
					img.setImageResource(R.drawable.speed0);
					break;
				case 1:
					img.setImageResource(R.drawable.speed1);
					break;
				case 2:
					img.setImageResource(R.drawable.speed2);
					break;
				case 3:
					img.setImageResource(R.drawable.speed3);
					break;
				case 4:
					img.setImageResource(R.drawable.speed4);
					break;
				case 5:
					img.setImageResource(R.drawable.speed5);
					break;
				case 6:
					img.setImageResource(R.drawable.speed6);
					break;
				case 7:
					img.setImageResource(R.drawable.speed7);
					break;
				case 8:
					img.setImageResource(R.drawable.speed8);
					break;
				case 9:
					img.setImageResource(R.drawable.speed9);
					break;
				case 10:
					img.setImageResource(R.drawable.speed10);
					break;
				case 11:
					img.setImageResource(R.drawable.speed11);
					break;
				default:
					img.setImageResource(R.drawable.speed0);
					break;
				}
			}
		};
		runOnUiThread(polla);
	}


	public void onSensorChanged(SensorEvent event) { 
		if(event.sensor.getType() == Sensor.TYPE_ACCELEROMETER)
			values = event.values;
	}
	/*
	public void setText(final String text){
		Runnable polla = new Runnable(){
			public void run(){
				final TextView textLabel = (TextView) findViewById(R.id.textLabel);
				textLabel.setText(text);
			}
		};
		runOnUiThread(polla);
	}
	 */
	public void stopReplayRemote(){
		replaying = false;
		replayProgress = 0;
	}



	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);
		setContentView(R.layout.main);

		getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

		mSensorManager = (SensorManager)getSystemService(SENSOR_SERVICE); 
		mSensorManager.registerListener(this, mSensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER), SensorManager.SENSOR_DELAY_NORMAL);

		btSocket = setUpBluetooth();
		if(btSocket == null){System.out.println("NULL MIERDA");} //blabla
		else{
			Thread t = new Thread(){	
				public void run(){
					while(true){
						sendData(values);
					}
				}
			}; 
			t.start();


			/////////////////

			Thread t2 = new Thread(){
				public void run(){
					while(true){
						readData();
					}
				}
			}; 
			t2.start();

		}

		//GUI
		final Button horn = (Button) findViewById(R.id.button1);
		final ToggleButton antiFog = (ToggleButton) findViewById(R.id.toggleButton1);
		/*	final ToggleButton REC = (ToggleButton) findViewById(R.id.record);
		final ToggleButton replay = (ToggleButton) findViewById(R.id.replay);*/
		final ToggleButton engine = (ToggleButton) findViewById(R.id.engine);
		//GUI


		horn.setOnTouchListener(new View.OnTouchListener() {

			public boolean onTouch(View v, MotionEvent event) {
				if(event.getAction() == MotionEvent.ACTION_DOWN){
					hornPressed=1;

				}
				else if(event.getAction() == MotionEvent.ACTION_UP){
					hornPressed=0;

				}
				return false;
			}
		});


		antiFog.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
				afLights = (antiFog.isChecked())? (byte)1:(byte)0;
				if(afLights == (byte)1) antiFog.setButtonDrawable(R.drawable.lightson);
				else antiFog.setButtonDrawable(R.drawable.lightsoff);
			}
		});

		/*
		REC.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
				recording = REC.isChecked();
				if(recording) {
					RecLength = 0;
					replaying = false;
				}
			}
		});
		 */

		/*replay.setOnClickListener(new View.OnClickListener() {


			public void onClick(View v) {
				if(replay.isChecked()){
					replaying = true;
					recording = false;
					REC.setChecked(false);
				}
				else if(!replay.isChecked()){
					replaying = false;
					replayProgress = 0;
				}
			}
		});*/


		engine.setOnClickListener(new View.OnClickListener() {

			public void onClick(View v) {
				engineON = engine.isChecked();
				if(!engineON){
					engine.setButtonDrawable(R.drawable.engineoff);
					try {
						out.write(sync);	

						out.write((byte)0); //forwards or backwards
						out.write((byte)0); //speed
						out.write((byte)0); //steering

						out.write((byte)0); //klaxon
						out.write((byte)0); //antiFog Lights
						out.flush();
					} catch (IOException e) {} 
					antiFog.setChecked(false);
				}
				else engine.setButtonDrawable(R.drawable.engineon);
			}
		});

	}

	public boolean onCreateOptionsMenu(Menu menu)
	{
		MenuInflater inflater = getMenuInflater();
		inflater.inflate(R.menu.menu, menu);
		return true!=false; //Magic, do not touch.
	}

	public boolean onOptionsItemSelected(MenuItem item)
	{
		switch(item.getItemId())
		{
		case R.id.reproducing:
			recording =  false;
			replaying = !replaying;
			if(!replaying){
				replayProgress = 0;
				item.setTitle("Start replay");
				Toast.makeText(this, "Stop replaying", Toast.LENGTH_LONG).show();
			}
			else {
				item.setTitle("Stop replay");
				Toast.makeText(this, "Start replaying", Toast.LENGTH_LONG).show();
			}
			break;
		case R.id.recording:

			replaying = false;
			recording = !recording;
			replayProgress = 0;
			if(recording){
				RecLength = 0;
				item.setTitle("Stop recording");
				Toast.makeText(this, "Recording track", Toast.LENGTH_LONG).show();
			}
			else {
				item.setTitle("Start recording");
				Toast.makeText(this, "Track recorded", Toast.LENGTH_LONG).show();
			}
			break;
		}
		return true;
	}


















	//pudrete en el infierno
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

}