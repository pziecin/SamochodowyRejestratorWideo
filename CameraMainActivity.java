package pl.pziecina.carvideoregister;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.SurfaceTexture;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.MediaRecorder;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.support.v4.content.ContextCompat;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.util.SparseIntArray;
import android.view.Surface;
import android.view.TextureView;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckedTextView;
import android.widget.Chronometer;
import android.widget.ImageButton;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.math.BigInteger;
import java.nio.channels.FileChannel;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.Date;
import java.util.Formatter;
import java.util.concurrent.atomic.AtomicBoolean;

import static android.view.View.VISIBLE;

public class CameraMainActivity extends AppCompatActivity implements SensorEventListener {

    private static final String TAG = "CameraVideoRegister";
    private static final String BITRATETAG = "bitrate";
    private static final String BITRATEPROGRESSTAG = "bitrateprogress";
    private static final String TIMEINTERVALTAG = "timeinterval";
    private static final String TIMEINTERVALPROGRESSTAG = "timeintervalprogresstag";
    private static final String FOLDERSIZETAG = "foldersize";
    private static final String FOLDERSIZEPROGRESSTAG = "foldersizeprogresstag";
    private static final String ACCELEROMETERTAG = "accelerometertag";
    private static final String ACCELEROMETERPROGRESSTAG = "accelerometerprogresstag";
    private static final String ACCELEROMETERBOOLEAN = "accelerometerboolean";

    private static final int REQUEST_CAMERA_PERMISSION_RESULT = 0;
    private static final int REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT = 1;
    private boolean itsBeacuesIDontKnowHowToFixInvokingOnSurfaceTextureAvailableWhenOnPause = false;

    private static SparseIntArray ORIENTATIONS = new SparseIntArray();

    static {
        ORIENTATIONS.append(Surface.ROTATION_0, 270);
        ORIENTATIONS.append(Surface.ROTATION_90, 180);
        ORIENTATIONS.append(Surface.ROTATION_180, 90);
        ORIENTATIONS.append(Surface.ROTATION_270, 0);
    }


    // textura pobiera rozmiar i tu mozna dodac ustawienie obrazu z kamery
    private TextureView textureView;
    private TextureView.SurfaceTextureListener surfaceTextureListener = new TextureView.SurfaceTextureListener() {
        @Override
        public void onSurfaceTextureAvailable(SurfaceTexture surfaceTexture, int width, int height) {
            System.out.println("Surface takie parametry wchodza" + width + " : " + height);                               // do usuniecia
            if (itsBeacuesIDontKnowHowToFixInvokingOnSurfaceTextureAvailableWhenOnPause) {
                setupCamera(width, height);
                connectCamera();
            }
        }

        @Override
        public void onSurfaceTextureSizeChanged(SurfaceTexture surfaceTexture, int width, int height) {
        }

        @Override
        public boolean onSurfaceTextureDestroyed(SurfaceTexture surfaceTexture) {
            surfaceTexture.release();
            return false;
        }

        @Override
        public void onSurfaceTextureUpdated(SurfaceTexture surfaceTexture) {

        }
    };

    private CameraDevice cameraDevice;
    private CameraDevice.StateCallback cameraDeviceStateCallback = new CameraDevice.StateCallback() {
        @Override
        public void onOpened(CameraDevice camera) {
            cameraDevice = camera;
            mediaRecorder = new MediaRecorder();
            if (atomicIsRecording.get())/*(isRecording)*/ {
                try {
                    //createVideoFileName(false);
                    startRecordPreview();
                } catch (IOException e) {
                    e.printStackTrace();
                }
               // mediaRecorder.start();
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        chronometer.setBase(SystemClock.elapsedRealtime());
                        chronometer.setVisibility(VISIBLE);
                        chronometer.start();
                    }
                });
            } else {
                startPreview();
            }
        }

        @Override
        public void onDisconnected(CameraDevice camera) {
            camera.close();
            cameraDevice = null;
        }

        @Override
        public void onError(CameraDevice camera, int error) {
            camera.close();
            cameraDevice = null;
        }
    };

    private HandlerThread backgroundHandlerThread;
    private Handler backgroundHandler;
    private Handler sequenceSavingHandler;
    private Runnable sequenceSavingRunnable;
    private Handler accidentHandler;
    private Runnable accidentRunnable;


    private String cameraId;
    private Size previewSize;
    private Size videoSize;

    private MediaRecorder mediaRecorder;
    private Chronometer chronometer;
    private int totalRotation;

    private CaptureRequest.Builder captureRequestBuilder;

    private ImageButton recordImageButton;
    private ImageButton settingsButton;
    private SeekBar seekBarBitRate;
    private SeekBar seekBarTime;
    private SeekBar seekBarFolderSize;
    private SeekBar seekBarAcceleration;
    private TextView bitRateAct;
    private TextView timeAct;
    private TextView folderSizeAct;

    private TextView bitRateMin;
    private TextView bitRateMax;
    private TextView bitRateLabel;
    private TextView timeSeqMin;
    private TextView intervalTimeLabel;
    private TextView timeSeqMax;
    private TextView folderSizeMin;
    private TextView folderSizeLabel;
    private TextView folderSizeMax;
    private TextView accelerationView;
    private TextView minAccel;
    private TextView maxAccel;
    private TextView currentAccel;
    private TextView accelLabel;
    private View settingsView;
    private CheckedTextView accelerometerCheckBox;

    private SharedPreferences sharedPreferences;

    private int bitRate = 3000000;
    private int maxTime = 90;
    private long maxFolderCapacity = 100000000;
    private float accelerationBreak = 10;

    private AtomicBoolean isClickedSettings = new AtomicBoolean();
    private boolean isClicked = false;
    private AtomicBoolean atomicIsRecording = new AtomicBoolean();

    private File videoFolder;
    private File videoFolderAccidents;
    private String videoFileName;

    private void findingViewsById(){
        chronometer = findViewById(R.id.chronometer);
        textureView = findViewById(R.id.textureView);
        recordImageButton = findViewById(R.id.videoButton);
        seekBarBitRate = findViewById(R.id.seekBarBitRate);
        seekBarTime = findViewById(R.id.seekBarTime);
        seekBarFolderSize = findViewById(R.id.seekBarFolderSize);
        seekBarAcceleration = findViewById(R.id.seekBarAcceleration);
        settingsButton = findViewById(R.id.settings);
        bitRateAct = findViewById(R.id.bitRateAct);
        timeAct = findViewById(R.id.timeAct);
        folderSizeAct = findViewById(R.id.folderSizeAct);

        accelerationView = findViewById(R.id.accelerationView);
        bitRateMin = findViewById(R.id.bitRateMin);
        bitRateMax = findViewById(R.id.bitRateMax);
        bitRateLabel = findViewById(R.id.bitRateLabel);
        timeSeqMin = findViewById(R.id.timeSeqMin);
        timeSeqMax = findViewById(R.id.timeSeqMax);
        intervalTimeLabel = findViewById(R.id.intervalTimeLabel);
        folderSizeMin = findViewById(R.id.folderSizeMin);
        folderSizeLabel = findViewById(R.id.folderSizeLabel);
        folderSizeMax = findViewById(R.id.folderSizeMax);
        currentAccel = findViewById(R.id.currentAccel);
        maxAccel = findViewById(R.id.maxAccel);
        minAccel = findViewById(R.id.minAccel);
        accelLabel = findViewById(R.id.accelLabel);
        accelerometerCheckBox = findViewById(R.id.accelerometer);
        settingsView = findViewById(R.id.settingsView);
    }

    private void showSettings() {
        if(checkWriteStoragePermission()) {
            bitRateAct.setVisibility(View.VISIBLE);
            bitRateMax.setVisibility(View.VISIBLE);
            bitRateMin.setVisibility(View.VISIBLE);
            bitRateLabel.setVisibility(View.VISIBLE);
            seekBarBitRate.setVisibility(View.VISIBLE);
            timeSeqMin.setVisibility(View.VISIBLE);
            timeSeqMax.setVisibility(View.VISIBLE);
            timeAct.setVisibility(View.VISIBLE);
            intervalTimeLabel.setVisibility(View.VISIBLE);
            seekBarTime.setVisibility(View.VISIBLE);
            folderSizeMin.setVisibility(View.VISIBLE);
            folderSizeMax.setText(formatByte(getMaxFolderSpace(videoFolder)));
            folderSizeMax.setVisibility(View.VISIBLE);
            folderSizeLabel.setVisibility(View.VISIBLE);
            seekBarFolderSize.setVisibility(View.VISIBLE);
            accelerometerCheckBox.setVisibility(View.VISIBLE);
            folderSizeAct.setVisibility(View.VISIBLE);
            settingsView.setVisibility(View.VISIBLE);
            minAccel.setVisibility(VISIBLE);
            maxAccel.setVisibility(VISIBLE);
            currentAccel.setVisibility(VISIBLE);
            accelLabel.setVisibility(VISIBLE);
            seekBarAcceleration.setVisibility(VISIBLE);

            seekBarBitRate.setProgress(sharedPreferences.getInt(BITRATEPROGRESSTAG, 56));
            seekBarTime.setProgress(sharedPreferences.getInt(TIMEINTERVALPROGRESSTAG, 13));
            seekBarFolderSize.setProgress(sharedPreferences.getInt(FOLDERSIZEPROGRESSTAG, 10));
            seekBarAcceleration.setProgress(sharedPreferences.getInt(ACCELEROMETERPROGRESSTAG,32));
            bitRateAct.setText(formatBit(sharedPreferences.getInt(BITRATETAG, bitRate)));
            timeAct.setText(Integer.toString(sharedPreferences.getInt(TIMEINTERVALTAG, maxTime)) + " s");
            folderSizeAct.setText(formatByte(sharedPreferences.getLong(FOLDERSIZETAG, maxFolderCapacity)));
            currentAccel.setText(String.format("%.2f",sharedPreferences.getFloat(ACCELEROMETERTAG, accelerationBreak)) + " g");
        }
    }

    private void hideSettings(){
        bitRateAct.setVisibility(View.INVISIBLE);
        bitRateMax.setVisibility(View.INVISIBLE);
        bitRateMin.setVisibility(View.INVISIBLE);
        bitRateLabel.setVisibility(View.INVISIBLE);
        seekBarBitRate.setVisibility(View.INVISIBLE);
        timeSeqMin.setVisibility(View.INVISIBLE);
        timeSeqMax.setVisibility(View.INVISIBLE);
        timeAct.setVisibility(View.INVISIBLE);
        intervalTimeLabel.setVisibility(View.INVISIBLE);
        seekBarTime.setVisibility(View.INVISIBLE);
        folderSizeMin.setVisibility(View.INVISIBLE);
        folderSizeMax.setVisibility(View.INVISIBLE);
        folderSizeLabel.setVisibility(View.INVISIBLE);
        folderSizeAct.setVisibility(View.INVISIBLE);
        seekBarFolderSize.setVisibility(View.INVISIBLE);
        accelerometerCheckBox.setVisibility(View.INVISIBLE);
        settingsView.setVisibility(View.INVISIBLE);
        minAccel.setVisibility(View.INVISIBLE);
        maxAccel.setVisibility(View.INVISIBLE);
        currentAccel.setVisibility(View.INVISIBLE);
        accelLabel.setVisibility(View.INVISIBLE);
        seekBarAcceleration.setVisibility(View.INVISIBLE);
    }

    private void setButtonListeners(){
        settingsButton.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v){
                if(isClickedSettings.compareAndSet(false,true))
                    showSettings();
                else if(isClickedSettings.compareAndSet(true,false))
                    hideSettings();

                seekBarBitRate.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if(progress == 0){
                            bitRate = 500000;
                        }else {
                            double bitRateDouble = 500000 + (progress - 1) * 45454.55;
                            bitRate = (int) Math.round(bitRateDouble);
                        }
                        bitRateAct.setText(formatBit(bitRate));
                        sharedPreferences.edit().putInt(BITRATETAG, bitRate).putInt(BITRATEPROGRESSTAG, seekBar.getProgress()).apply();
                    }
                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                seekBarTime.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if(progress == 0){
                            maxTime = 20;
                        }else {
                            double maxTimeDouble = 20 + (progress - 1) * 5.858586;
                            maxTime = (int) Math.round(maxTimeDouble);
                        }
                        timeAct.setText(Integer.toString(maxTime) + " s");
                        sharedPreferences.edit().putInt(TIMEINTERVALTAG, maxTime).putInt(TIMEINTERVALPROGRESSTAG, seekBar.getProgress()).apply();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                seekBarFolderSize.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if(progress == 0){
                            maxFolderCapacity = 400000000;
                        }else {
                            long maxTimeLong = 400000000 + (Long.valueOf(progress) - 1) * getFolderSizeStep();
                            maxFolderCapacity = maxTimeLong;

                        }
                        folderSizeAct.setText(formatByte(maxFolderCapacity));
                        sharedPreferences.edit().putLong(FOLDERSIZETAG, maxFolderCapacity).putInt(FOLDERSIZEPROGRESSTAG, seekBar.getProgress()).apply();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {
                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {
                    }
                });
                seekBarAcceleration.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
                    @Override
                    public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                        if(progress == 0){
                            accelerationBreak = 4;
                        }else {
                            float accel = 5 + (progress - 1) * 0.161f;
                            accelerationBreak = accel;

                        }
                        currentAccel.setText(String.format("%.2f",accelerationBreak) + " g");
                        sharedPreferences.edit().putFloat(ACCELEROMETERTAG, accelerationBreak).putInt(ACCELEROMETERPROGRESSTAG, seekBar.getProgress()).apply();
                    }

                    @Override
                    public void onStartTrackingTouch(SeekBar seekBar) {

                    }

                    @Override
                    public void onStopTrackingTouch(SeekBar seekBar) {

                    }
                });
            }
        });

        accelerometerCheckBox.setOnClickListener(new View.OnClickListener(){
            @Override
            public void onClick(View v) {
                if(accelerometerCheckBox.isChecked()) {
                    accelerationView.setVisibility(VISIBLE);
                    accelerometerCheckBox.setChecked(false);
                    sharedPreferences.edit().putBoolean(ACCELEROMETERBOOLEAN,true).apply();
                    accelerometerCheckBox.setTextColor(getResources().getColor(R.color.turnOn));
                    accelerometerCheckBox.setText("Acceleration ON");
                }
                else {
                    accelerationView.setVisibility(View.INVISIBLE);
                    accelerometerCheckBox.setChecked(true);
                    sharedPreferences.edit().putBoolean(ACCELEROMETERBOOLEAN,false).apply();
                    accelerometerCheckBox.setText("Acceleration OFF");
                    accelerometerCheckBox.setTextColor(getResources().getColor(R.color.turnOff));
                }
            }
        });


        recordImageButton.setOnClickListener(new View.OnClickListener() {
            private long lastClickTime = 0;
            @Override
            public void onClick(View v) {
                if (SystemClock.elapsedRealtime() - lastClickTime < 1000){
                    return;
                }
                lastClickTime = SystemClock.elapsedRealtime();

                if(checkWriteStoragePermission()) {
                    if (isClicked){
                        stopSequenceSavingThread();
                        if (!(atomicIsRecording.get())) {
                            stopThreadForAccident();
                        }
                        chronometer.stop();
                        chronometer.setVisibility(View.INVISIBLE);

                        recordImageButton.setImageResource(R.mipmap.record);
                        settingsButton.setVisibility(View.VISIBLE);


                        startPreview();

                        mediaRecorder.stop();
                        mediaRecorder.reset();


                        atomicIsRecording.set(false);
                        isClicked = false;
                    } else {
                            startSequenceSavingThread();

                            try {
                                createVideoFileName();
                                startRecordPreview();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }

                            mediaRecorder.start();
                            chronometer.setBase(SystemClock.elapsedRealtime());
                            chronometer.setVisibility(VISIBLE);
                            chronometer.start();
                            recordImageButton.setImageResource(R.mipmap.stoprecord);
                            settingsButton.setVisibility(View.INVISIBLE);
                            hideSettings();
                            atomicIsRecording.set(true);
                            isClicked = true;
                    }
                }
            }
        });

    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_HARDWARE_ACCELERATED);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        sensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
        accelerometer = sensorManager.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);
        currentAcceleration = SensorManager.GRAVITY_EARTH;
        lastAcceleration = SensorManager.GRAVITY_EARTH;

        sharedPreferences = getSharedPreferences("SRWPreference", Context.MODE_PRIVATE);


        System.out.println("onCreate");                                  // usunąć

        setContentView(R.layout.activity_camera_main);

        findingViewsById();

        setButtonListeners();

        boolean accelerometer = sharedPreferences.getBoolean(ACCELEROMETERBOOLEAN, false);
        if (accelerometer){
            accelerometerCheckBox.setTextColor(getResources().getColor(R.color.turnOn));
            accelerometerCheckBox.setText("Acceleration ON");
            accelerometerCheckBox.setChecked(accelerometer);
            accelerationView.setVisibility(VISIBLE);
        }
    }

    @Override
    protected void onStart() {
        super.onStart();

        System.out.println("onStart");
    }

    @Override
    protected void onResume() {
        super.onResume();

        System.out.println("onResume");

        createVideoFolder();

        startBackgroundThread();

        sensorManager.registerListener(this, accelerometer, SensorManager.SENSOR_DELAY_NORMAL);

        if (textureView.isAvailable()) {
            setupCamera(textureView.getWidth(), textureView.getHeight());
            connectCamera();
        } else {
            itsBeacuesIDontKnowHowToFixInvokingOnSurfaceTextureAvailableWhenOnPause = true;
            textureView.setSurfaceTextureListener(surfaceTextureListener);
        }
    }

    @Override
    protected void onPause() {
        System.out.println("onPause");

        if (SystemClock.elapsedRealtime() - activeThreadTime < 5000){
            atomicIsRecording.set(true);
        }

        if (atomicIsRecording.compareAndSet(true,false)) {
            stopSequenceSavingThread();

            chronometer.stop();
            chronometer.setVisibility(View.INVISIBLE);
            recordImageButton.setImageResource(R.mipmap.record);

            mediaRecorder.stop();
            mediaRecorder.reset();

            isClicked = false;
        }

        itsBeacuesIDontKnowHowToFixInvokingOnSurfaceTextureAvailableWhenOnPause = false;
        closeCamera();

        sensorManager.unregisterListener(this);
        stopBackgroundThread();

        super.onPause();
    }

    @Override
    protected void onStop() {
        System.out.println("onStop");
        super.onStop();
    }

    @Override
    protected void onRestart() {
        super.onRestart();

        System.out.println("onRestart");
    }

    @Override
    protected void onDestroy() {
        System.out.println("onDestroy");

        super.onDestroy();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQUEST_CAMERA_PERMISSION_RESULT) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED && grantResults[1] == PackageManager.PERMISSION_GRANTED) {
            }
            else if(grantResults[0] != PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this,"Aplikacja wymaga dostępu do kamery", Toast.LENGTH_SHORT).show();
            }else if(grantResults[1] != PackageManager.PERMISSION_GRANTED){
                Toast.makeText(this,"Aplikacja wymaga dostępu do mikrofonu", Toast.LENGTH_SHORT).show();
            }
        }
        if (requestCode == REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT) {
            if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this,
                        "Zgoda na zapisywanie przydzielona", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this,
                        "Aplikacja wymaga zapisywania plików", Toast.LENGTH_SHORT).show();
            }
        }
    }



    // To jest po to zeby usunac ten gorny label.

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {   // if ( ekran wlaczony)
        super.onWindowFocusChanged(hasFocus);
        View decorView = getWindow().getDecorView();
        if (hasFocus) {
            decorView.setSystemUiVisibility(View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                    | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                    | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
                    | View.SYSTEM_UI_FLAG_FULLSCREEN
                    | View.SYSTEM_UI_FLAG_HIDE_NAVIGATION);
        }
    }

    private void setupCamera(int width, int height) {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            String localCameraId = cameraManager.getCameraIdList()[0];
            CameraCharacteristics cameraCharacteristics = cameraManager.getCameraCharacteristics(localCameraId);

            StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);

            int deviceOrientation = getWindowManager().getDefaultDisplay().getRotation();
            totalRotation = sensorToDeviceRotation(cameraCharacteristics, deviceOrientation);
            boolean swapRotation = totalRotation == 0 || totalRotation == 180;
            int rotatedWidth = width;
            int rotatedHeight = height;
            if (swapRotation) {
                rotatedWidth = height;
                rotatedHeight = width;
            }

            System.out.println("Klasa dziwne strasznie: " + rotatedHeight + " rH " + rotatedWidth + " rW " + map.getOutputSizes(MediaRecorder.class)[0].toString());

            videoSize = chooseOptimalSize(map.getOutputSizes(MediaRecorder.class), rotatedWidth, rotatedHeight);

            previewSize = chooseOptimalSize(map.getOutputSizes(SurfaceTexture.class), rotatedWidth, rotatedHeight);

            cameraId = localCameraId;
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void connectCamera() {
        CameraManager cameraManager = (CameraManager) getSystemService(Context.CAMERA_SERVICE);
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (checkSelfPermission(Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED && checkSelfPermission(Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                    cameraManager.openCamera(cameraId, cameraDeviceStateCallback, backgroundHandler);
                }else{
                    requestPermissions(new String[]{Manifest.permission.CAMERA, Manifest.permission.RECORD_AUDIO}, REQUEST_CAMERA_PERMISSION_RESULT);
                }
            }else {
                cameraManager.openCamera(cameraId, cameraDeviceStateCallback, backgroundHandler);
            }
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startRecordPreview() throws IOException {


        setupMediaRecorder();

        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);

        Surface recordSurface = mediaRecorder.getSurface();

        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_RECORD);
            captureRequestBuilder.addTarget(previewSurface);
            captureRequestBuilder.addTarget(recordSurface);

            cameraDevice.createCaptureSession(Arrays.asList(previewSurface, recordSurface), new CameraCaptureSession.StateCallback() {
                @Override
                public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                    try {
                        cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                    } catch (CameraAccessException e) {
                        e.printStackTrace();
                    }
                }

                @Override
                public void onConfigureFailed(CameraCaptureSession session) {
                    Log.d(TAG, "onConfigureFailed: startRecord");
                }
            }, null);

        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void startPreview() {
        SurfaceTexture surfaceTexture = textureView.getSurfaceTexture();
        surfaceTexture.setDefaultBufferSize(previewSize.getWidth(), previewSize.getHeight());
        Surface previewSurface = new Surface(surfaceTexture);


        try {
            captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            captureRequestBuilder.addTarget(previewSurface);


            cameraDevice.createCaptureSession(Arrays.asList(previewSurface),
                    new CameraCaptureSession.StateCallback() {
                        @Override
                        public void onConfigured(CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "onConfigured: startPreview");
                            try {
                                cameraCaptureSession.setRepeatingRequest(captureRequestBuilder.build(), null, backgroundHandler);
                            } catch (CameraAccessException e) {
                                e.printStackTrace();
                            }
                        }

                        @Override
                        public void onConfigureFailed(CameraCaptureSession cameraCaptureSession) {
                            Log.d(TAG, "onConfigureFailed: startPreview");
                        }
                    }, null);
        } catch (CameraAccessException e) {
            e.printStackTrace();
        }
    }

    private void closeCamera() {
        if (cameraDevice != null) {
            cameraDevice.close();
            cameraDevice = null;
        }
        if (mediaRecorder != null) {
            mediaRecorder.release();
            mediaRecorder = null;
        }
    }

    private synchronized void startSequenceSavingThread() {
        sequenceSavingHandler = new Handler(backgroundHandlerThread.getLooper());

        sequenceSavingHandler.postDelayed(sequenceSavingRunnable = new Runnable() {
            public void run() {
                if (atomicIsRecording.compareAndSet(true,false)){

                    mediaRecorder.stop();
                    mediaRecorder.reset();

                    try {
                       createVideoFileName();
                       startRecordPreview();
                    } catch (IOException e) {
                        e.printStackTrace();
                    }
                    mediaRecorder.start();
                    atomicIsRecording.lazySet(true);
                }
                sequenceSavingHandler.postDelayed(this, maxTime * 1000);
            }
        }, maxTime * 1000);
    }


    private void stopSequenceSavingThread() {
        sequenceSavingHandler.removeCallbacksAndMessages(null);
        sequenceSavingHandler = null;
    }

    private synchronized void startBackgroundThread() {
        backgroundHandlerThread = new HandlerThread("SRW");
        backgroundHandlerThread.start();
        backgroundHandler = new Handler(backgroundHandlerThread.getLooper());
    }

    private void stopBackgroundThread() {
        backgroundHandlerThread.quitSafely();
        try {
            backgroundHandlerThread.join();
            backgroundHandlerThread = null;
            backgroundHandler = null;
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    //Nad tym trzeba jeszcze popracować

    private static int sensorToDeviceRotation(CameraCharacteristics cameraCharacteristics, int rotation) {
        int sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
        rotation = ORIENTATIONS.get(rotation);
        return (rotation + sensorOrientation) % 360;
    }

    private static Size chooseOptimalSize(Size[] choices, int width, int height) {
        for (Size option : choices) {
            if (option.getWidth() == option.getHeight() * height / width
                    && option.getWidth() <= width && option.getHeight() <= height) {
                return option;
            } else if (option.getHeight() == option.getWidth() * height / width
                    && option.getWidth() <= width && option.getHeight() <= height) {
                return option;
            }
        }
        return choices[0];
    }  // dorobić obsługę gdy nie bedzie 16:9 lub 9:16

    private void createVideoFolder() {
        File videoFile = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MOVIES);
        videoFolder = new File(videoFile, "SRW");
        if (!videoFolder.exists()) {
            videoFolder.mkdirs();
        }

        videoFolderAccidents = new File(videoFile, "SRWAccidents");
        if (!videoFolderAccidents.exists()) {
            videoFolderAccidents.mkdirs();
        }
    }

    File videoFile;

    private void createVideoFileName() throws IOException {
        int i = 0;
        while(isSpaceForNewFiles()){
            if(deleteOldestFileInFolder(videoFolder));
            i++;
        }
        System.out.println("ILE RAZY" + i);
            String timestamp = new SimpleDateFormat("yyyyMMdd_HHmmss").format(new Date());
            String prepend = "VIDEO_" + timestamp + "_";
            videoFile = File.createTempFile(prepend, ".mp4", videoFolder);
            videoFileName = videoFile.getAbsolutePath();
            System.out.println(videoFileName);
    }

    private boolean checkWriteStoragePermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED)
                return true;
            else {
                if (shouldShowRequestPermissionRationale(Manifest.permission.WRITE_EXTERNAL_STORAGE))
                    Toast.makeText(this, "app needs to be able to save videos", Toast.LENGTH_SHORT).show();
                requestPermissions(new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_EXTERNAL_STORAGE_PERMISSION_RESULT);
                return false;
            }
        } else
            return true;
    }


    private void setupMediaRecorder() throws IOException {
        mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE);
        mediaRecorder.setAudioSource(MediaRecorder.AudioSource.CAMCORDER);
        mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
        mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264);
        mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.HE_AAC);
        mediaRecorder.setAudioEncodingBitRate(48000);
        mediaRecorder.setAudioChannels(2);
        mediaRecorder.setOutputFile(videoFileName);
        mediaRecorder.setVideoSize(videoSize.getWidth(), videoSize.getHeight());
        mediaRecorder.setVideoFrameRate(30);
        mediaRecorder.setVideoEncodingBitRate(bitRate);
        mediaRecorder.setOrientationHint(totalRotation);
        mediaRecorder.prepare();
    }


    //-------------------------------------------------------------------------------------------------Sensor
    private SensorManager sensorManager;
    private Sensor accelerometer;

    private float currentAcceleration;
    private float lastAcceleration;

    @Override
    public void onAccuracyChanged(Sensor sensor, int accuracy) {
        System.out.println("ACCURACY " + accuracy);

    }

    private void threadForAccident() {
        accidentHandler = new Handler(backgroundHandlerThread.getLooper());

        accidentHandler.postDelayed(accidentRunnable = new Runnable() {
            public void run() {
                    Log.d("THREAD ACCIDENT", "AKCELEROMETR DZIALA");

                    mediaRecorder.stop();
                    mediaRecorder.reset();

                    try {
                        moveFile(videoFile, videoFolderAccidents);
                    }catch(IOException ioe){
                        ioe.printStackTrace();
                    }

                    aktywny = false;

                    try {
                        createVideoFileName();
                        startRecordPreview();
                    } catch (IOException ioe) {
                        ioe.printStackTrace();
                    }
                    mediaRecorder.start();
                    atomicIsRecording.lazySet(true);
            }
        }, 5000);
    }

    public void stopThreadForAccident(){
        accidentHandler.removeCallbacksAndMessages(null);
        accidentHandler = null;
    }



    private boolean aktywny = true;

    private float acceleration = 0.00f;

    private long activeThreadTime = 0;



    @Override
    public void onSensorChanged(SensorEvent event) {
        if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {

            float x = event.values[0];
            float y = event.values[1];
            float z = event.values[2];

            float alfa = 0.9f;

            lastAcceleration = currentAcceleration;
            currentAcceleration = (float) Math.sqrt(x * x + y * y + z * z);
            float delta = currentAcceleration - lastAcceleration;

            acceleration = acceleration * alfa + delta;

            accelerationView.setText(Float.toString(acceleration));
            if (acceleration > accelerationBreak) {
                accelerationView.setTextColor(Color.GREEN);
                if (atomicIsRecording.compareAndSet(true,false) && aktywny) {
                    activeThreadTime = SystemClock.elapsedRealtime();
                    threadForAccident();
                }
            }else if (acceleration < 0.1 && acceleration > -0.1) {
                aktywny = true;
            } else if (acceleration < -accelerationBreak) {
                accelerationView.setTextColor(Color.RED);
            }else {
                accelerationView.setTextColor(Color.WHITE);
            }
        }
    }

    private void moveFile(File file, File dir) throws IOException {
        File newFile = new File(dir, file.getName());
        FileChannel outputChannel = null;
        FileChannel inputChannel = null;
        try {
            outputChannel = new FileOutputStream(newFile).getChannel();
            inputChannel = new FileInputStream(file).getChannel();
            inputChannel.transferTo(0, inputChannel.size(), outputChannel);
            inputChannel.close();
            file.delete();
        } finally {
            if (inputChannel != null) inputChannel.close();
            if (outputChannel != null) outputChannel.close();
        }

    }

    private long getMaxFolderSpace(File folder){
        return folder.getUsableSpace() + getFolderSize(folder);
    }

    private long getFolderSizeStep(){
        return (getMaxFolderSpace(videoFolder) - 400000000)/(99);
    }

    private boolean deleteOldestFileInFolder(File folder){
        File[] files = folder.listFiles();
        if(files[0].exists()) {
            long minLastModified = files[0].lastModified();
            int minValueToDelete = 0;

            for (int i = 1; i < files.length; i++) {
                if (files[i].lastModified() < minLastModified) {
                    minLastModified = files[i].lastModified();
                    minValueToDelete = i;
                }
            }
            files[minValueToDelete].delete();
            return true;
        }else{
            return false;
        }
    }

    private boolean isSpaceForNewFiles(){
        return getFolderSize(videoFolder) + bitRate * maxTime / 8 * 5/4 > maxFolderCapacity;
    }

    private long getFolderSize(File folder) {
        long length = 0;
        File[] files = folder.listFiles();

        int count = files.length;
        if(count == 0)
            return 0;
        for (int i = 0; i < count; i++) {
            if (files[i].isFile()) {
                length += files[i].length();
            } else {
                length += getFolderSize(files[i]);
            }
        }
        return length;
    }


    private String formatBit(int value){
        if(value < 1000)
            return Integer.toString(value) + " bit";
        else
            return Integer.toString(value/1000) + " kbit/s";
    }

    private String formatByte(long value){
        if(value < 1000)
            return Long.toString(value) + " byte";
        else if(value < 1000*1000)
            return Long.toString(value/1000) + " KB";
        else if(value < 1000*1000*1000)
            return Long.toString(value/(1000*1000)) + " MB";
        else {
            long megabits = value / (1000 * 1000);
            return String.format("%.2f", megabits * 0.001) + " GB";
        }
    }
}