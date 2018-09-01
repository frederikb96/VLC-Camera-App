package edmt.dev.androidcamera2api;

import android.Manifest;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.ImageFormat;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.support.annotation.NonNull;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.widget.Button;
import android.widget.Toast;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

public class MainActivity extends AppCompatActivity {

    //<editor-fold desc="Declaration">
    private TextureView textureView;

    //Check state orientation of output image
    private static final SparseIntArray ORIENTATIONS = new SparseIntArray();
    static{
        ORIENTATIONS.append(Surface.ROTATION_0,90);
        ORIENTATIONS.append(Surface.ROTATION_90,0);
        ORIENTATIONS.append(Surface.ROTATION_180,270);
        ORIENTATIONS.append(Surface.ROTATION_270,180);
    }

    //Camera variables
    private String cameraId;
    private CameraDevice cameraDevice;
    private CameraCaptureSession cameraCaptureSessions;
    private CaptureRequest.Builder captureRequestBuilder;
    private Size imageDimension;
    private ImageReader imageReader;
    private int width;
    private int height;

    //Save to FILE
    private static final int REQUEST_CAMERA_PERMISSION = 200;
    private boolean mFlashSupported;
    private Handler mBackgroundHandler;
    private HandlerThread mBackgroundThread;

    //Image processing
    private final ImageData imageData = new ImageData();
    public boolean recordingData;
    long startTime;
    long endTime;
    long middleTime =330;
    short test = 0;


    //Manual camera settings
    private Long expUpper;
    private Long expLower;
    private Integer senUpper;
    private Integer senLower;
    private Long fraUpper;
    private Long fraLower;

    //Intent
    public static final String EXTRA_MESSAGE = "com.example.androidCamera2API-master.MESSAGE";

    //Callback of camera device
    CameraDevice.StateCallback stateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(@NonNull CameraDevice camera) {
            cameraDevice = camera;
            setUpCamera();
        }

        @Override
        public void onDisconnected(@NonNull CameraDevice cameraDevice) {
            cameraDevice.close();
        }

        @Override
        public void onError(@NonNull CameraDevice cameraDevice, int i) {
            cameraDevice.close();
            cameraDevice=null;
        }
    };

    //Listener for texture surface
    TextureView.SurfaceTextureListener textureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int i, int i1) {
            openCamera();
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int i, int i1) {

        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };
    //</editor-fold>

    //<editor-fold desc="Activity creator">
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        textureView = (TextureView)findViewById(R.id.textureView);
        //From Java 1.4 , you can use keyword 'assert' to check expression true or false
        assert textureView != null;
        textureView.setSurfaceTextureListener(textureListener);
        Button btnCapture = (Button) findViewById(R.id.btnCapture);
        btnCapture.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                recordingData = !recordingData;
                if(recordingData) {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Image recording has stated!", Toast.LENGTH_SHORT).show();
                        }
                    });
                } else {
                    runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            Toast.makeText(MainActivity.this, "Image recording has stopped!", Toast.LENGTH_SHORT).show();
                        }
                    });
                }
            }
        });
    }
    //</editor-fold>

    //<editor-fold desc="Main methods">
    /**
     * Creates the camera preview
     */
    private void createCameraPreview() {
        try{
            //This is to create the surface of the preview texture field
            SurfaceTexture texture = textureView.getSurfaceTexture();
            assert  texture != null;
            texture.setDefaultBufferSize(imageDimension.getWidth(),imageDimension.getHeight());
            //The surface of the preview texture
            Surface surface = new Surface(texture);
            //This is to create the surface to capture the image to the reader
            Surface imageSurface = imageReader.getSurface();

            List<Surface> outputSurface = new ArrayList<>(2);
            outputSurface.add(imageSurface);
            outputSurface.add(surface);

            //Set up the capture Builder with settings
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.set(CaptureRequest.CONTROL_AE_MODE,CaptureRequest.CONTROL_AE_MODE_OFF);
            captureRequestBuilder.set(CaptureRequest.SENSOR_EXPOSURE_TIME, expLower);
            captureRequestBuilder.set(CaptureRequest.SENSOR_SENSITIVITY,senUpper);
            captureRequestBuilder.set(CaptureRequest.SENSOR_FRAME_DURATION,fraUpper);

            //Add target to Builder - both the texture field and the reader
            captureRequestBuilder.addTarget(surface);
            captureRequestBuilder.addTarget(imageSurface);

            cameraDevice.createCaptureSession(outputSurface, new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(@NonNull CameraCaptureSession cameraCaptureSession) {
                    if(cameraDevice == null)
                        return;
                    cameraCaptureSessions = cameraCaptureSession;
                    updatePreview();
                }

                @Override
                public void onConfigureFailed(@NonNull CameraCaptureSession cameraCaptureSession) {
                    Toast.makeText(MainActivity.this, "Changed", Toast.LENGTH_SHORT).show();
                }
            },null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    /**
     * Updates the camera preview, or more it starts capturing the frames and paths them to the surfaces of the session
     */
    private void updatePreview() {
        if(cameraDevice == null)
            Toast.makeText(this, "Error", Toast.LENGTH_SHORT).show();
        //Doesn't do anything,don't know why but I took it out, just to be sure, as it should not set the mode back to auto
        //captureRequestBuilder.set(CaptureRequest.CONTROL_MODE,CaptureRequest.CONTROL_MODE_AUTO);
        try{
            //This says that it should repeatedly capture frames with the settings set
            cameraCaptureSessions.setRepeatingRequest(captureRequestBuilder.build(),null,mBackgroundHandler);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }


    /**
     * Opens the camera, first initialization
     */
    private void openCamera() {
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try{
            //Get Camera ID
            cameraId = manager.getCameraIdList()[0];
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);


            //Stuff with permission and things I don't understand
            StreamConfigurationMap map = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
            assert map != null;
            imageDimension = map.getOutputSizes(SurfaceTexture.class)[0];
            //Check real time permission if run higher API 23
            if(ActivityCompat.checkSelfPermission(this, Manifest.permission.CAMERA) != PackageManager.PERMISSION_GRANTED)
            {
                ActivityCompat.requestPermissions(this,new String[]{
                        Manifest.permission.CAMERA,
                        Manifest.permission.WRITE_EXTERNAL_STORAGE
                },REQUEST_CAMERA_PERMISSION);
                return;
            }

            //This calls the setUpCamera method to set up the camera parameters
            manager.openCamera(cameraId,stateCallback,null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void setUpCamera() {
        CameraManager manager = (CameraManager)getSystemService(Context.CAMERA_SERVICE);
        try{
            CameraCharacteristics characteristics = manager.getCameraCharacteristics(cameraId);
            StreamConfigurationMap configs = characteristics.get(
                    CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            //Set size of image reader
            Size[] yuvSizes = null;
            if(characteristics != null)
                yuvSizes = characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)
                        .getOutputSizes(ImageFormat.YUV_420_888);

            //Capture image with custom size
            width = 640;
            height = 480;
            //Size is from 0(biggest) to length-1(smallest)
            if(yuvSizes != null && yuvSizes.length > 0)
            {
                width = yuvSizes[0].getWidth();
                height = yuvSizes[0].getHeight();
            }
            //Set up image reader with custom size and format, image buffer set to 5, recommended for vlc
            imageReader = ImageReader.newInstance(width,height,ImageFormat.YUV_420_888,5);
            recordingData = false;

            //<editor-fold desc="Listener of image reader">
            final ImageReader.OnImageAvailableListener readerListener = new ImageReader.OnImageAvailableListener() {
                //If image is passed to surface by capturing, the image is available in the reader and this method is called
                @Override
                public void onImageAvailable(ImageReader imageReader) {
                    //Get image from image reader
                    Image image = imageReader.acquireNextImage();

                    if (recordingData && a == 0) {
                        a=1;
                        startTime = System.nanoTime();
                        //Set up the data which stores the data of the image plane
                        byte[] data = new byte[image.getWidth() * image.getHeight()];
                        //Get y plane of image and path buffer to data
                        image.getPlanes()[0].getBuffer().get(data);
                        image.close();
                        try {
                            ThreadManager.getInstance().getmDecoderThreadPool().execute(new RunnableImage(data.clone()));
                        } catch (Exception e) {
                            e.printStackTrace();
                        }

                        endTime = System.nanoTime();
                        middleTime = (middleTime + (endTime - startTime) / 100000) / 2;
                    }else{
                        image.close();
                    }
                }
            };
            //</editor-fold>

            //Image reader is set to image reader listener
            imageReader.setOnImageAvailableListener(readerListener,mBackgroundHandler);

            //Sets the manual exposure values
            /*
            Range expRange  = characteristics.get(CameraCharacteristics.SENSOR_INFO_EXPOSURE_TIME_RANGE);
            expUpper = (Long) expRange.getUpper();
            expLower = (Long) expRange.getLower();

            Range senRange = characteristics.get(CameraCharacteristics.SENSOR_INFO_SENSITIVITY_RANGE);
            senUpper = (Integer) senRange.getUpper();
            senLower = (Integer) senRange.getLower();
            */

            expLower = (Long) (long) (1000000000/6000);    //22000 to 100000000
            senUpper = (Integer) (int) 4000;               //64 to 1600 //but higher somehow possible
            fraUpper = (Long) (long) 1000000000/30;


            createCameraPreview();

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }

    }
    //</editor-fold>

    //<editor-fold desc="Sub methods">
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
       if(requestCode == REQUEST_CAMERA_PERMISSION)
       {
           if(grantResults[0] != PackageManager.PERMISSION_GRANTED)
           {
               Toast.makeText(this, "You can't use camera without permission", Toast.LENGTH_SHORT).show();
                finish();
           }
       }
    }

    @Override
    protected void onResume() {
        super.onResume();
        startBackgroundThread();
        if(textureView.isAvailable())
            openCamera();
        else
            textureView.setSurfaceTextureListener(textureListener);
    }

    @Override
    protected void onPause() {
        stopBackgroundThread();
        super.onPause();
    }

    private void stopBackgroundThread() {
        mBackgroundThread.quitSafely();
        try{
            mBackgroundThread.join();
            mBackgroundThread= null;
            mBackgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    private void startBackgroundThread() {
        mBackgroundThread = new HandlerThread("Camera Background");
        mBackgroundThread.setPriority(Thread.MAX_PRIORITY);
        mBackgroundThread.start();
        mBackgroundHandler = new Handler(mBackgroundThread.getLooper());
    }
    //</editor-fold>

    private int a =0;

    //<editor-fold desc="Threads">
    private class ThreadSaveData extends Thread {

        public void run() {
            try {
                Thread.currentThread().sleep(1000);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            synchronized (imageData) {
                try {



                    //set up the file path
                    File file = new File(Environment.getExternalStorageDirectory()+"/yuv/picture_"+imageData.dataTest.get(0).length+"_"+0+"_YData.csv");
                    //Stream of text file
                    FileWriter fileWriter = null;
                    try{
                        fileWriter = new FileWriter(file);

                        int[] data1Dim = imageData.dataTest.get(0);

                        int[] zeros = new int[100];
                        int[] ones = new int[100];

                        boolean lastHigh = false;   //cares about possible that one low and high again to stay in a row
                        int counterHigh=0;        //counts how many highs in a row
                        int endHigh = -1;         //saves end pixel of a high
                        int startHigh;            //saves start pixel of a high




                        //<editor-fold desc="Algorithm">

                        for (int i = 0; i<imageData.dataTest.get(0).length; i++) {
                            if(data1Dim[i]>=1) {   //high point recognized
                                lastHigh = true;
                                counterHigh++;
                            } else if(lastHigh) {   //this low but last was high
                                lastHigh=false;
                                counterHigh++;
                            } else if(counterHigh != 0) {   //two times low after some highs
                                counterHigh--;  //counter adjust two last high pixel

                                if(5<=counterHigh && counterHigh<=80) {
                                    if(counterHigh<100) {
                                        ones[counterHigh]++;
                                    } else {
                                        ones[99]++;
                                    }

                                    startHigh = i - 1 - counterHigh;  //set new start of this normal high
                                    //Only if start bit called
                                    if(endHigh!=-1) {   //only do more if it was not the first high
                                        if(startHigh-endHigh < 100) {
                                            zeros[startHigh-endHigh]++;
                                        } else {
                                            zeros[99]++;
                                        }

                                    }
                                    endHigh = i - 2;  // a normal high and was processed and now set the end

                                }

                                counterHigh = 0;
                            }
                            //if no high has been - nothing happens in loop and go further in data
                        }


                        for(int i = 0; i<ones.length;i++) {
                            fileWriter.write(Integer.toString(ones[i]) + ",");
                            fileWriter.write(Integer.toString(zeros[i]) + "\n");
                        }



                    }finally {
                        if(fileWriter != null)
                            fileWriter.close();
                    }






                    imageData.dataTest.clear();
                    imageData.dataStream.clear();
                    imageData.lastFrameCaptured=false;
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }
            runOnUiThread(new Runnable() {
                @Override
                public void run() {
                    Toast.makeText(MainActivity.this, "Saved the images!", Toast.LENGTH_SHORT).show();
                }
            });
        }
    }

    public class ImageData {
        public List<int[]> dataTest = new ArrayList<>();
        public List<Byte> dataStream = new ArrayList<>();
        public boolean lastFrameCaptured;

        ImageData() {
            lastFrameCaptured = false;
        }

    }

    private class RunnableImage implements Runnable {
        //Initialization
        byte[] data;

        RunnableImage(byte[] data) {
            this.data = data;
        }

        @Override
        public void run() {
            //Initialization
            byte[] dataPlanes = this.data;
            int width = 4032;
            int height = 3024;
            double resizeWidth = width/4032;
            double resizeHeight = height/3024;

            //<editor-fold desc="ROI">
            //Variables
            int upROI = -1;     //data array starts top right corner and first columns than rows, ends left bottom
            int lowROI = -1;    //0 is first position, 1 is second
            int rightROI = -1;
            int leftROI = -1;
            int rightUpROIBuffer = -1;
            int borderROIBuffer = -1;
            ArrayList<Integer> rightROIList = new ArrayList<>();
            ArrayList<Integer> leftROIList = new ArrayList<>();
            int highestInRow = 0;
            int lowestInRow = 250;
            int byteToIntBuffer;
            int counterInterval = 0;
            boolean firstDistinguished = false;
            int counterStripes = 0;
            boolean stripesOccurred = false;
            //Constants
            int STEP_ROI_ROW = 25;
            int STEP_ROI_PIXEL = 8;         //min low is 8
            int DISTINGUISH_VALUE = 80;     //from 0 to 255
            int INTERVAL_OF_STRIPES = 65;   //in pixels, 70 as longest time without change is 0.6 low with around these pixels
            int COUNT_OF_STRIPES = 12;  //depends on bits per sequence, at least a sequence per row; COUNT_OF_STRIPES dark/bright stripes per row

            //<editor-fold desc="ROI Detection">
            //Loops
            for(int i=0; i<width; i=i+STEP_ROI_ROW) {
                //i is offset of Row
                for(int n=0;n<height; n=n+STEP_ROI_PIXEL) {
                    // n*width + i is pixel
                    byteToIntBuffer = (dataPlanes[i+n*width] & 0xff);
                    counterInterval++;
                    if(byteToIntBuffer>highestInRow) {
                        highestInRow = byteToIntBuffer;
                    }
                    if(byteToIntBuffer<lowestInRow) {
                        lowestInRow = byteToIntBuffer;
                    }
                    if(highestInRow-lowestInRow > DISTINGUISH_VALUE) {  //Check if bright an dark stripes can be distinguished
                        borderROIBuffer = i+n*width;
                        if (!firstDistinguished) {
                            rightUpROIBuffer = borderROIBuffer;
                            firstDistinguished = true;
                        }
                        counterStripes++;       //counter++ stripes
                        if(counterStripes>=COUNT_OF_STRIPES) {
                            stripesOccurred = true;
                        }

                        //Reset interval values to start new interval
                        highestInRow = 0;
                        lowestInRow = 250;
                        counterInterval = 0;
                    }
                    //Check the interval
                    if(counterInterval>=INTERVAL_OF_STRIPES/STEP_ROI_PIXEL) {   //Check if interval ended
                        //Reset interval values if ended
                        counterStripes = 0;
                        highestInRow = 0;
                        lowestInRow = 250;
                        counterInterval = 0;
                    }
                }
                //Stuff before next Row starts
                if (stripesOccurred) { //check if enough stripes per row
                    //Set the left and low ROI Border
                    lowROI = borderROIBuffer%width;
                    leftROIList.add(borderROIBuffer/width);         //Add value to left array list
                    //Set right ROI of buffer of right and up
                    rightROIList.add(rightUpROIBuffer/width);       //Add value to right buffer
                    if(upROI == -1) {   //Do only ones at first up
                        upROI = rightUpROIBuffer%width;
                    }
                }
                //Reset highest and lowest and reset row
                highestInRow = 0;
                lowestInRow = 250;
                counterInterval = 0;
                firstDistinguished = false;
                stripesOccurred = false;
                counterStripes = 0;
            }
            lowROI++;   //To include the last line
            //Set Borders out of list
            if (!rightROIList.isEmpty()) {
                rightROI=0;
                for (int sumOfLine : rightROIList) {
                    rightROI+=sumOfLine;
                }
                rightROI/=rightROIList.size();
            }
            if (!leftROIList.isEmpty()) {
                leftROI=0;
                for (int sumOfLine : leftROIList) {
                    leftROI+=sumOfLine;
                }
                leftROI/=leftROIList.size();
            }
            //Now leftROI and rightROI are set
            //</editor-fold>
            //</editor-fold>

            //Check if ROI found otherwise discard frame
            if(!(rightROI == -1 || leftROI == -1 || upROI == -1|| lowROI== -1)) {
                //New dimensions of array
                int widthROI = lowROI-upROI;
                int heightROI = leftROI-rightROI;

                //<editor-fold desc="1 dim array">
                //1 dim array by calculating mean of column
                //Variables
                int[] data1Dim = new int[heightROI];
                int sumOfLine = 0;
                int counterSamples = 0;
                int counterLines=0;

                //Constants
                int STEP_1DIM_ROW = 25;

                for(int i=rightROI; i<leftROI; i++) {
                    for(int n=upROI; n<lowROI; n=n+STEP_1DIM_ROW) {
                        sumOfLine += (dataPlanes[i*width+n] & 0xff);
                        counterSamples++;
                    }
                    data1Dim[counterLines] = sumOfLine/counterSamples;
                    counterLines++;
                    sumOfLine = 0;
                    counterSamples = 0;
                }
                //</editor-fold>


                //<editor-fold desc="Thresholding">
                //Constants
                int THRESH_STEP = 8;
                int THRESH_INTERVAL = 65;

                //Variables
                int highestThresh = 0;
                int lowestThresh = 250;
                ArrayList<Integer> threshValues = new ArrayList<>(heightROI/THRESH_INTERVAL);
                int counterThreshSteps = 0;

                for(int i=0; i<heightROI;i+=THRESH_STEP) {  //<= THRESH_STEP values not considered
                    if(data1Dim[i]>highestThresh) {
                        highestThresh = data1Dim[i];
                    }
                    if(data1Dim[i]<lowestThresh) {
                        lowestThresh = data1Dim[i];
                    }
                    counterThreshSteps++;
                    if(counterThreshSteps>=THRESH_INTERVAL/THRESH_STEP) {
                        threshValues.add((highestThresh+lowestThresh)/2);
                        highestThresh = 0;
                        lowestThresh = 250;
                        counterThreshSteps = 0;
                    }
                }
                //</editor-fold>

                //Beta will be implemented in decoding for faster processing
                //<editor-fold desc="Downsampling">
                //Variables
                int counterThreshIntervals = 0;
                int threshValueBuffer;
                threshValueBuffer = threshValues.get(counterThreshIntervals);

                for(int i=0;i<heightROI;i++) {
                    if(data1Dim[i]>threshValueBuffer){
                        data1Dim[i] = 1;
                    } else {
                        data1Dim[i] = 0;
                    }
                    if(i>=THRESH_INTERVAL && threshValues.size() > counterThreshIntervals + 1) {
                        counterThreshIntervals++;
                        threshValueBuffer = threshValues.get(counterThreshIntervals);
                    }
                }
                //</editor-fold>


                //<editor-fold desc="Decoding algorithm">
                //Variables
                byte data6Bit = 0;         //The encoded data in 6bit
                byte[] data4Bit = new byte[12];         //the byte array where to save the 4 bit data bytes decoded form the 6 bit data; 1 block number 1 byte repeated
                byte dataByteBuffer;        //buffer
                int bytePart = 0;           //checks if already first 6bit captured of the 12
                int counterBytes = 0;      //counts the bytes
                int counterBits = 0;       //counter of captured bits
                boolean lastHigh = false;   //cares about possible that one low and high again to stay in a row
                int counterHigh=0;        //counts how many highs in a row
                int endHigh = -1;         //saves end pixel of a high
                int startHigh;            //saves start pixel of a high
                int lastBit = -1;          //-1 nothing, 0 zero last, 1 one last, 2 start bit
                boolean error = false;

                //Constants
                int LEVEL_ZERO = 70;
                int START_BIT_MIN = 36;
                int START_BIT_MAX = 40;
                int INTERVAL_LEVEL;
                int INTERVAL_RENEW;

                //<editor-fold desc="Algorithm">

                for (int i = 0; i<heightROI; i++) {
                    if(error) {
                        error = false;  //reset error flag
                        data6Bit = 0;    //the current buffered data is reset
                        data4Bit[counterBytes] = 0; //the already saved data at this position is reset
                        if(bytePart!=0) {   //if first part of data has already been saved so not part 0 anymore
                            data4Bit[counterBytes-1] = 0;   //than reset las data
                            counterBytes--; //and change counter again
                        }
                        bytePart = 0;   //set part to zero again
                        counterBits = 0;    //counter of captured bits is reset
                        lastBit = -1;   //the last bit is not available any longer
                    }

                    if(data1Dim[i]>=1) {   //high point recognized
                        lastHigh = true;
                        counterHigh++;
                    } else if(lastHigh) {   //this low but last was high
                        lastHigh=false;
                        counterHigh++;
                    } else if(counterHigh != 0) {   //two times low after some highs
                        counterHigh--;  //counter adjust two last high pixel
                        if(36<=counterHigh && counterHigh<=40) {    //check if high was startBit without low parts
                            lastBit = 2;
                            endHigh = i - 2;
                        } else if(13<=counterHigh && counterHigh<=23) { //check if it was a normal high
                            startHigh = i - 1 - counterHigh;  //set new start of this normal high
                            //Only if start bit called
                            if(endHigh!=-1) {   //only do more if it was not the first high
                                if(2 <= startHigh-endHigh && startHigh-endHigh <= 8) {  //check if two start highs
                                    //start bit
                                    lastBit = 2;
                                } else if (lastBit!=-1) {                               //Check if start bit called ones
                                    if(10 <= startHigh-endHigh && startHigh-endHigh <= 20){  //check if 0.2 in between to highs
                                        //0,2
                                        if(lastBit == 2 || lastBit == 0) {
                                            //its a 1
                                            data6Bit = (byte) ((1 << (5-counterBits) | data6Bit));
                                            counterBits++;
                                            lastBit = 1;
                                        } else {
                                            //error not possible to have this bit followed by this lows
                                            error = true;
                                            Log.d("DataTest", "Error last Bit at 0.2; and at pixel: "+i);
                                        }
                                    } else if(29 <= startHigh-endHigh && startHigh-endHigh <= 40){  //check if 0.4 in between to highs
                                        //0,4
                                        if(lastBit == 2 || lastBit == 0) {
                                            //its a 0
                                            counterBits++;
                                            lastBit = 0;
                                        } else {
                                            //its a 1
                                            data6Bit = (byte) ((1 << (5-counterBits) | data6Bit));
                                            counterBits++;
                                            lastBit = 1;
                                        }
                                    } else if(44 <= startHigh-endHigh && startHigh-endHigh <= 60){  //check if 0.6 in between to highs
                                        //0,6
                                        if(lastBit == 1) {
                                            //its a 0
                                            counterBits++;
                                            lastBit = 0;
                                        } else {
                                            //error
                                            Log.d("DataTest", "Error last Bit at 0.6; and at pixel: "+i);
                                            error = true;
                                        }
                                    } else {    //some else number of lows in between two highs => sequence is interrupted
                                        // error
                                        Log.d("DataTest", "Error strange number of lows; and at pixel: "+i);
                                        error = true;
                                    }

                                    if(counterBits==6 && bytePart==0) {    //first 6 bit to 4bit
                                        if ((dataByteBuffer = decode4Bit6Bit(data6Bit)) != -1) {
                                            data4Bit[counterBytes] = dataByteBuffer;
                                            counterBits = 0;    //reset the counter of how many bits
                                            data6Bit = 0;    //reset the data buffer
                                            bytePart = 1;           //set to new part
                                            counterBytes++; //set counterBytes higher...
                                        } else {
                                            error = true;
                                        }
                                    } else if(counterBits==6 && bytePart == 1) {    //first 6 bit to 4bit
                                        if ((dataByteBuffer = decode4Bit6Bit(data6Bit)) != -1) {
                                            data4Bit[counterBytes] = (byte) (dataByteBuffer << 4);
                                            counterBits = 0;
                                            data6Bit = 0;
                                            bytePart = 2;
                                        } else {
                                            error = true;
                                        }
                                    } else if(counterBits == 6) { //last 6 bit to last 4 bit
                                        if ((dataByteBuffer = decode4Bit6Bit(data6Bit)) != -1) {
                                            data4Bit[counterBytes] = (byte) (dataByteBuffer | data4Bit[counterBytes]);
                                            counterBytes++; //to get new bytes of data
                                            bytePart = 0; //to care about the if case in the error handling
                                        }
                                        //reset to capture new byte resp. error if = -1
                                        error = true;
                                    }
                                }
                            }
                            endHigh = i - 2;  // a normal high and was processed and now set the end
                        } else if(counterHigh>=13){
                            //1. error as sequence is interrupted - too many high values
                            Log.d("DataTest", "Error to many high; highs: "+counterHigh+"; and at pixel: "+i);
                            error = true;
                            endHigh = -1;   //not a normal high so set back last high value
                        }
                        //2. just some strange highs (small ones maybe only) in between highs does not matter

                        //after end of high processed go to 0 again
                        counterHigh = 0;
                    }
                    //if no high has been - nothing happens in loop and go further in data
                }
                //</editor-fold>

                //</editor-fold>

                synchronized (imageData) {
                    if (!imageData.lastFrameCaptured) { //stops still executing threads from interacting during proceeding the final message


                        imageData.dataTest.add(data1Dim);  //add data to be saved
                        ThreadSaveData threadSaveData = new ThreadSaveData();
                        threadSaveData.start();


                        Log.d("Image","Thread processed picture: "+Thread.currentThread().getName() +";  And it was the frame: "+test);
                        for(int n=0;data4Bit[n]!=0 && data4Bit[n+1]!=0;n+=2) {   //check if at least one byte of frame readable, than process this byte
                            while(imageData.dataStream.size()<data4Bit[n]) {
                                imageData.dataStream.add((byte) 0);
                            }
                            imageData.dataStream.set(data4Bit[n]-1,data4Bit[n+1]);
                            if (imageData.dataStream.size()==3 && imageData.dataStream.get(0)!=0 && imageData.dataStream.get(1)!=0 && imageData.dataStream.get(2)!=0) {  //my condition to stop
                                Log.d("TimeCheck", "End and time in middle: " + middleTime);
                                runOnUiThread(new Runnable() {
                                    @Override
                                    public void run() {
                                        Toast.makeText(MainActivity.this, "Message is captured, saving started!", Toast.LENGTH_SHORT).show();
                                    }
                                });
                                recordingData = false;  //stop recording in image reader
                                imageData.dataTest.add(data1Dim);  //add data to be saved
                                imageData.lastFrameCaptured = true; //stop still executing threads from writing more data
                                //start new activity to display output
                                Intent intent = new Intent(MainActivity.this, DisplayMessageActivity.class);
                                //get all data to the message string
                                String message = "";
                                for(int i=0; i<imageData.dataStream.size();i++) {
                                    message = message + String.valueOf((char) (byte) imageData.dataStream.get(i));
                                }
                                intent.putExtra(EXTRA_MESSAGE,message);
                                startActivity(intent);
                                //New Thread to handle saving
                                //ThreadSaveData threadSaveData = new ThreadSaveData();
                                threadSaveData.start();
                                break;  //break from loop as enough bytes captured
                            }
                        }
                    } else {
                        Log.d("Image","Thread didn't save data, as after saving was done: "+Thread.currentThread().getName());
                    }
                }
            }


        }
        private byte decode4Bit6Bit(byte dataCoded) {
            byte data = -1;
            switch (dataCoded) {
                case 14:
                    data = 0;
                    break;
                case 13:
                    data = 1;
                    break;
                case 19:
                    data = 2;
                    break;
                case 22:
                    data = 3;
                    break;
                case 21:
                    data = 4;
                    break;
                case 35:
                    data = 5;
                    break;
                case 38:
                    data = 6;
                    break;
                case 37:
                    data = 7;
                    break;
                case 25:
                    data = 8;
                    break;
                case 26:
                    data = 9;
                    break;
                case 28:
                    data = 10;
                    break;
                case 49:
                    data = 11;
                    break;
                case 50:
                    data = 12;
                    break;
                case 41:
                    data = 13;
                    break;
                case 42:
                    data = 14;
                    break;
                case 44:
                    data = 15;
                    break;
            }
            return data;    //returns -1 if error
        }
    }
    //</editor-fold>
}