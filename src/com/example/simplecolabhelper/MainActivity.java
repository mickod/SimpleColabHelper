package com.example.simplecolabhelper;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.ServerSocket;
import java.net.Socket;
import java.text.DecimalFormat;
import java.util.Locale;

import android.net.wifi.WifiInfo;
import android.net.wifi.WifiManager;
import android.os.Bundle;
import android.os.Environment;
import android.app.Activity;
import android.content.Context;
import android.graphics.drawable.AnimationDrawable;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;

public class MainActivity extends Activity {
	
	ServerSocket serverSocket;
	TextView stateTextView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        
        //Set the view
        setContentView(R.layout.activity_main);
        
        //Find the local IP address and display it
        try {
        	WifiManager wifiMan = (WifiManager) this.getSystemService(Context.WIFI_SERVICE);
        	WifiInfo wifiInf = wifiMan.getConnectionInfo();
        	int ipAddress = wifiInf.getIpAddress();
        	String hostaddr = String.format(Locale.US, "%d.%d.%d.%d", (ipAddress & 0xff),(ipAddress >> 8 & 0xff),(ipAddress >> 16 & 0xff),(ipAddress >> 24 & 0xff));
	        TextView ipTextView = (TextView)findViewById(R.id.ip_address);
	        ipTextView.setText(hostaddr);
	        Log.d("MainActivity onCreate","IP address: " + hostaddr);
		} catch (Exception e) {
			//log the error
			Log.d("MainActivity onCreate","error getting the IP address");
			e.printStackTrace();
		}
        
        //Display the state as starting server
        stateTextView = (TextView)findViewById(R.id.state);
        stateTextView.setText(R.string.state_starting_server);
        
        //Make the log view area scrollable
        TextView logView = (TextView)findViewById(R.id.log_scroll_text_view);
        logView.setMovementMethod(new ScrollingMovementMethod());
        
        //Set the status box to green
        ImageView statusBox = (ImageView) findViewById(R.id.status_box);
        statusBox.setBackgroundResource(R.drawable.green_box);
        
        //Start an asynch task to wait for requests over the socket
        //Based on modified version of approach outlined:
        //http://android-er.blogspot.hk/2014/08/bi-directional-communication-between.html
        Thread socketServerThread = new Thread(new SocketServerThread(this.getBaseContext()));
        socketServerThread.start(); 
        Log.d("MainActivity onCreate","Server started");
        
        //Keep the screen on
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    protected void onDestroy() {
    	super.onDestroy();

     	//Close any open sockets
		if (serverSocket != null) {
			 try {
				 serverSocket.close();
			 } catch (IOException e) {
				 //Log error
				 Log.d("MainActivity onDestroy","error trying to close sockets");
			 }
		 }
	}
    
    private class SocketServerThread extends Thread {
    	//This Class represents a Socket Server. The server handles requests for number file computations

    	static final int SocketServerPORT = 8080;
    	private Context ctx;
    	
    	//Contructor
    	public SocketServerThread(Context context) {
    		this.ctx = context;
    	}

    	@Override
    	public void run() {
    		Socket socket = null;
    		DataInputStream inputFileDIS = null;
		    BufferedOutputStream socketBOS = null;
		    DataOutputStream socketDOS = null;


		    while (true) {
	    		try {
	    			serverSocket = new ServerSocket(SocketServerPORT);
	    			MainActivity.this.runOnUiThread(new Runnable() {
	    				@Override
		    			public void run() {
		    				stateTextView.setText(R.string.state_server_started);
		    			}
	    			});
	    			loglnToScreen("State: " + getResources().getString(R.string.state_server_started));
	
	    			while (true) {
	    				//Accept connections from clients sending number files for computation
	    				Log.d("MainActivity SocketServerThread Run","Waiting for connection");
	        			MainActivity.this.runOnUiThread(new Runnable() {
	        	    	    @Override
	    	    			public void run() {
	        	    	    	stateTextView.setText(R.string.state_waiting_for_connection);
	        	    	    	
	        	    	    	//Set the status box to solid green
	        	    	    	ImageView statusBox = (ImageView) findViewById(R.id.status_box);
	        	    	        statusBox.setBackgroundResource(R.drawable.green_box);
	        	    	        
	        	    	        //Set the progress message to blank
	        	    	    	TextView progressMessageTextView = (TextView) (TextView)findViewById(R.id.progress_textview);
	        	    	    	progressMessageTextView.setText("");
	    	    			}
	        			});
	        			loglnToScreen("State: " + getResources().getString(R.string.state_waiting_for_connection));
	    				socket = serverSocket.accept();
	 
	    				//Handle the message - read the data in and store the numbers locally 
	        			MainActivity.this.runOnUiThread(new Runnable() {
	        	    	    @Override
	    	    			public void run() {
	        	    	    	stateTextView.setText(R.string.state_connection_received);
	    	    			}
	        			});
	        			loglnToScreen("State: " + getResources().getString(R.string.state_connection_received));					    
						
		    			MainActivity.this.runOnUiThread(new Runnable() {
		    				@Override
	    	    			public void run() {
	    	    	    		stateTextView.setText(R.string.state_receiving_file);
	    	    	    		
	        	    	    	//Set the status box to blinking yellow
	        	    	    	ImageView statusBox = (ImageView) findViewById(R.id.status_box);
	        	    	        statusBox.setBackgroundResource(R.drawable.status_box_yellow_animation);
	        	    	        AnimationDrawable frameAnimation = (AnimationDrawable) statusBox
	        	    	                .getBackground();
	        	    	        frameAnimation.start();
	    	    			}
	        			});
		    			logToScreen(">>> State: " + getResources().getString(R.string.state_receiving_file) + " ");
					    		
					    //The first part of the message should be the length of the file being transfered - read it first and
					    //then write from the second byte onwards to the buffer. For this case we are ensuring our files are shorter 
		    			//max int so the full file can be read into an array in memory with no need to create a file.
					    boolean reportCount = true;
	    				inputFileDIS = new DataInputStream(socket.getInputStream());
						int bufferSize = socket.getReceiveBufferSize();
						Log.d("MainActivity SocketServerThread Run","Receive buffer size: " + bufferSize);
					    long fileSize = inputFileDIS.readLong();
					    byte[] bytes = new byte[bufferSize];
					    ByteArrayOutputStream numbersOS = new ByteArrayOutputStream();
					    Log.d("MainActivity onCreate","Numbers incoming fileSize: " + fileSize);
					    
					    //The next part of the message should be the number of iterations for the compute loop
					    int iterationCount = inputFileDIS.readInt();
					    
					    //Now read in the rest of the file up to the final byte indicated by the size
					    long totalCount = 0;
					    int thisReadCount = 0;
					    while (totalCount < fileSize && (thisReadCount = inputFileDIS.read(bytes)) != -1) {
					    	totalCount += thisReadCount;
					    	if (reportCount) {
					    		Log.d("MainActivity SocketServerThread Run","Total Bytes read: " + totalCount);
					    		logToScreen(".");
					    	}
					    	numbersOS.write(bytes, 0, thisReadCount);
					    }
					    //Write the final buffer read in - this is necessary as thisReadCount will be set to -1 
					    //when the end of stream id detected even when it has read in some bytes while detecting the end
					    //of stream
					    numbersOS.write(bytes);
					    Log.d("MainActivity SocketServerThread Run","numbers file received");
					    Log.d("MainActivity SocketServerThread Run","totalCount: " + totalCount);
					    Log.d("MainActivity SocketServerThread Run","thisReadCount: " + thisReadCount);
					    logToScreen("\n");
					    String fileSizeString = new DecimalFormat("0.00").format(totalCount/1000000.0);
					    loglnToScreen("Total Bytes read: " + totalCount + " (" + fileSizeString + "MB)");
					    numbersOS.flush();
					    numbersOS.close();
					    //inputFileDIS.close();
	
					    //Update the status display
		    			MainActivity.this.runOnUiThread(new Runnable() {
		    	    	    @Override
	    	    			public void run() {
	    	    	    		stateTextView.setText(R.string.state_computing_result);
	    	    	    		
	        	    	    	//Set the status box to blinking orange
	        	    	    	ImageView statusBox = (ImageView) findViewById(R.id.status_box);
	        	    	        statusBox.setBackgroundResource(R.drawable.status_box_orange_animation);
	        	    	        AnimationDrawable frameAnimation = (AnimationDrawable) statusBox
	        	    	                .getBackground();
	        	    	        frameAnimation.start();
	    	    			}
		        		});
		    			loglnToScreen("State: " + getResources().getString(R.string.state_computing_result));
		    			
		    			//Do the computation
		    			ByteArrayInputStream numbersIS = new ByteArrayInputStream(numbersOS.toByteArray());
		    		    double result = 0.0;
		    		    int computeIterations = iterationCount;
		    			try {
		    	    	
		    		    	//Do the computation
		    		    	int numbersArraySize = safeLongToInt(fileSize);
		    		    	Log.d("SimpleComputeTask","doInBackground numberfile length:" + fileSize);
		    		    	Log.d("SimpleComputeTask","doInBackground numberfile size:" + numbersArraySize);
		    		    	
		    		    	for(int i=0; i<numbersArraySize; i++) {
		    		    		//Report progress every 100 loops
		    		    		if( i % 50 == 0 ){
		    		    			Log.d("SimpleComputeTask","doInBackground reporting progess i:" + i);
		    		    			updateProgress(i);
		    		    		}
		    		    		
		    		    		
		    		    		byte thisByte[] = new byte[1];
		    		    		int readResult = numbersIS.read(thisByte);
		    		    		Log.d("SimpleComputeTask","doInBackground i" + i + "thisByte[0]: " + thisByte[0]);
		    		    		if(readResult < 1) {
		    		    			Log.d("SimpleComputeTask","doInBackground error reading numbers file");
		    		    			return;
		    		    		}
		    		    		double interim = 0;
		    		    		for(int j=0; j<computeIterations; j++){
		    		    			//Log.d("SimpleComputeTask","doInBackground j: " + j);
		    		    			interim = Math.pow(thisByte[0], 2);
		    		    		}
		    		    		result = (result + interim) * 0.99999999;
		    		    	}
		    			} catch (FileNotFoundException e) {
		    				Log.d("SimpleComputeTask","doInBackground numbersFile: FileNotFoundException");
		    			} catch (IOException e) {
		    				Log.d("SimpleComputeTask","doInBackground numbersFile: IOException");
		    			}
		    			
				    	
				    	//Update status to show sending the result back
		    			final double thisResult = result;
		    			MainActivity.this.runOnUiThread(new Runnable() {
		    				@Override
	    	    			public void run() {
	    	    	    		stateTextView.setText(R.string.state_sending_result);
	    	    	    		
	        	    	    	//Set the status box to blinking green
	        	    	    	ImageView statusBox = (ImageView) findViewById(R.id.status_box);
	        	    	        statusBox.setBackgroundResource(R.drawable.status_box_green_animation);
	        	    	        AnimationDrawable frameAnimation = (AnimationDrawable) statusBox
	        	    	                .getBackground();
	        	    	        frameAnimation.start();
	        	    	        
	        	    	        //Log the result
	    					    String resultString = new DecimalFormat("0.000000").format(thisResult);
	    					    loglnToScreen("Result: " + resultString);
	    	    			}
	        			});
		    			loglnToScreen("State: " + getResources().getString(R.string.state_sending_result));
		    			
		    			//Send the result back over the socket
		    			//First send the file size
		    			Log.d("MainActivity SocketServerThread Run","Sending result back");
		    			loglnToScreen("Creating BufferedOutputStream");
					    socketBOS = new BufferedOutputStream(socket.getOutputStream());
					    loglnToScreen("Creating DataOutputStream");
					    socketDOS = new DataOutputStream(socketBOS);
					    loglnToScreen("Writing double result");
					    socketDOS.writeDouble(result);
	
					    //Tidy up streams
					    Log.d("MainActivity SocketServerThread Run","Tidying up");
					    loglnToScreen("Tidying up");
					    socketDOS.flush();
					    socketDOS.close();
					    socketBOS.close();
					    
		    			MainActivity.this.runOnUiThread(new Runnable() {
		    				@Override
			    			public void run() {
			    				stateTextView.setText(R.string.state_sent_result);
			    			}
		    			});
		    			loglnToScreen("State: " + getResources().getString(R.string.state_sent_result));
			    	    
			    	    //Close socket
					    socket.close();
	    			}
	    	   } catch (IOException e) {
		    	   //Log error
	    		   Log.d("MainActivity SocketServerThread Run","error in socket server");
		    	   e.printStackTrace();
	    	   } finally {
	    		   //Tidy up...
	    		   try {	    			   
		    		   if (socket != null) {
		    			   socket.close();
		    		   }
		    		   if (inputFileDIS != null) {
		    			   inputFileDIS.close();
		    		   }
		    		   if (socketBOS != null) {
		    			   socketBOS.close();
		    		   } 
		    		   if (socketDOS != null) {
		    			   socketDOS.close();
		    		   } 
				   } catch (IOException e) {
					   Log.d("MainActivity SocketServerThread Run","error tidying up");
					   e.printStackTrace();
				   }
	    	   }
	    	}  
	    }
    	
    	public void updateProgress(int loopCount) {
    		//Display the loop count
    		Log.d("MainACtivity","updateProgress");
    		
    		final int numLoops = loopCount;
			MainActivity.this.runOnUiThread(new Runnable() {
				@Override
    			public void run() {
					TextView progressMessageTextView = (TextView) (TextView)findViewById(R.id.progress_textview);
		        	String vidFileSizeString = new DecimalFormat("0").format(numLoops);
		        	progressMessageTextView.setText(vidFileSizeString);
    			}
			});
    		
        	
    		
    	}
    }
    
    private void logToScreen(final String logText) {
    	//Method to log to the scrollable text view on the screen with no newline
    	
		MainActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
		        //Display the state as starting server
		        TextView logView = (TextView)findViewById(R.id.log_scroll_text_view);
		        logView.append(logText);
			}
		});
    }
    
    private void loglnToScreen(final String logText) {
    	//Method to log to the scrollable text view on the screen wiht newline
    	
		MainActivity.this.runOnUiThread(new Runnable() {
			@Override
			public void run() {
		        //Display the state as starting server
		        TextView logView = (TextView)findViewById(R.id.log_scroll_text_view);
		        logView.append(">>> " + logText + "\n");
			}
		});
    	
    }
	
    private static int safeLongToInt(long l) {
    	//See: http://stackoverflow.com/a/1590842/334402
        if (l < Integer.MIN_VALUE || l > Integer.MAX_VALUE) {
            throw new IllegalArgumentException
                (l + " cannot be cast to int without changing its value.");
        }
        return (int) l;
    }
}

