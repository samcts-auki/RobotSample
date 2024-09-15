package com.ainirobot.robotos.fragment;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import android.os.Bundle;
import android.os.RemoteException;
import android.util.Log;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.Spinner;
import android.widget.Toast;
import android.widget.LinearLayout;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.ainirobot.coreservice.client.Definition;
import com.ainirobot.coreservice.client.RobotApi;
import com.ainirobot.coreservice.client.listener.ActionListener;
import com.ainirobot.robotos.LogTools;
import com.ainirobot.robotos.R;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class PhotoTaskFragment extends BaseFragment {

    private static final int CAMERA_REQUEST_CODE = 1001;

    private Spinner cameraSelectorSpinner;
    private ProcessCameraProvider cameraProvider;
    private ImageCapture imageCapture;
    private ArrayList<String> cameraList = new ArrayList<>();
    private ArrayList<String> cameraIdList = new ArrayList<>(); // Holds camera IDs for later use
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private CameraSelector cameraSelector;
    private ExecutorService cameraExecutor;

    public static Fragment newInstance() {
        return new PhotoTaskFragment();
    }

    // Example Java array
    String[] poseList = {"item1", "item2"};

    @Override
    public View onCreateView(Context context) {
        View root = mInflater.inflate(R.layout.fragment_photo_task, null, false);

        // Request Permissions
        requestPermissions(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
        }, 1);

        // Get the LinearLayout where buttons will be added
        LinearLayout buttonContainer = root.findViewById(R.id.button_container);

        List<com.ainirobot.coreservice.client.actionbean.Pose> placeList =  RobotApi.getInstance().getPlaceList();

        // Dynamically create buttons based on the array
        for (com.ainirobot.coreservice.client.actionbean.Pose item : placeList) {
            Button button = new Button(getActivity());
            button.setText(item.getName());

            // Set an OnClickListener to each button
            button.setOnClickListener(v -> handleButtonClick(item.getName()));

            // Add the button to the container
            buttonContainer.addView(button);
        }
        return root;
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // Request camera permission
        if (ContextCompat.checkSelfPermission(requireContext(), Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(requireActivity(), new String[]{Manifest.permission.CAMERA}, CAMERA_REQUEST_CODE);
        }

        // Initialize UI elements
        cameraSelectorSpinner = view.findViewById(R.id.camera_selector_spinner);

        // Camera Executor for background tasks
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Get the camera provider instance
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());

        getAvailableCameras();

        // Listen for spinner selections
        cameraSelectorSpinner.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {
                String selectedCameraId = cameraIdList.get(position);
                switchCamera(selectedCameraId); // Start the selected camera by its ID
            }

            @Override
            public void onNothingSelected(AdapterView<?> parent) {
                // Do nothing
            }
        });

        // Bind the camera lifecycle to this fragment
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                if (!cameraIdList.isEmpty()) {
                    // Start the first camera by default
                    switchCamera(cameraIdList.get(0));
                }
            } catch (ExecutionException | InterruptedException e) {
                e.printStackTrace();
            }
        }, ContextCompat.getMainExecutor(requireContext()));


    }
//  Robot Stuff
    // Method to handle button click and use array element as input
    private void handleButtonClick(String input) {
        // For demonstration, we just show a Toast message
        Toast.makeText(getActivity(), "Button clicked: " + input, Toast.LENGTH_SHORT).show();

        // You can call other functions here with `input` as a parameter
        // Example: someFunction(input);
        RobotApi.getInstance().startNavigation(0, input, 1.5, 10 * 1000, mNavigationListener);
    }

    private static ActionListener mNavigationListener = new ActionListener() {

        @Override
        public void onResult(int status, String response) throws RemoteException {

            switch (status) {
                case Definition.RESULT_OK:
                    if ("true".equals(response)) {
                        LogTools.info("startNavigation result: " + status + "(Navigation success)" + " message: " + response);
                        LogTools.info("startNavigation result: " + status + "(导航成功)" + " message: " + response);
                    } else {
                        LogTools.info("startNavigation result: " + status + "(Navigation failed)" + " message: " + response);
                        LogTools.info("startNavigation result: " + status + "(导航失败)" + " message: " + response);
                    }
                    break;
                default:
                    break;
            }
        }
        @Override
        public void onError(int errorCode, String errorString) throws RemoteException {
            switch (errorCode) {
                case Definition.ERROR_NOT_ESTIMATE:
                    LogTools.info("onError result: " + errorCode + "(not estimate)" + " message: " + errorString);
                    LogTools.info("onError result: " + errorCode + "(当前未定位)" + " message: " + errorString);
                    break;
                case Definition.ERROR_IN_DESTINATION:
                    LogTools.info("onError result: " + errorCode + "(in destination, no action)" + " message: " + errorString);
                    LogTools.info("onError result: " + errorCode + "(当前机器人已经在目的地范围内)" + " message: " + errorString);
                    break;
                case Definition.ERROR_DESTINATION_NOT_EXIST:
                    LogTools.info("onError result: " + errorCode + "(destination not exist)" + " message: " + errorString);
                    LogTools.info("onError result: " + errorCode + "(导航目的地不存在)" + " message: " + errorString);
                    break;
                case Definition.ERROR_DESTINATION_CAN_NOT_ARRAIVE:
                    LogTools.info("onError result: " + errorCode + "(avoid timeout, can not arrive)" + " message: " + errorString);
                    LogTools.info("onError result: " + errorCode + "(避障超时，目的地不能到达，超时时间通过参数设置)" + " message: " + errorString);
                    break;
                case Definition.ACTION_RESPONSE_ALREADY_RUN:
                    LogTools.info("onError result: " + errorCode + "(already started, please stop first)" + " message: " + errorString);
                    LogTools.info("onError result: " + errorCode + "(当前接口已经调用，请先停止，才能再次调用)" + " message: " + errorString);
                    break;
                case Definition.ACTION_RESPONSE_REQUEST_RES_ERROR:
                    LogTools.info("onError result: " + errorCode + "(wheels are busy for other actions, please stop first)" + " message: " + errorString);
                    LogTools.info("onError result: " + errorCode + "(已经有需要控制底盘的接口调用，请先停止，才能继续调用)" + " message: " + errorString);
                    break;
                default:
                    break;
            }
        }

        @Override
        public void onStatusUpdate(int status, String data) throws RemoteException {
            switch (status) {
                case Definition.STATUS_NAVI_AVOID:
                    LogTools.info("onStatusUpdate result: " + status + "(can not avoid obstacles)" + " message: " + data);
                    LogTools.info("onStatusUpdate result: " + status + "(当前路线已经被障碍物堵死)" + " message: " + data);
                    break;
                case Definition.STATUS_NAVI_AVOID_END:
                    LogTools.info("onStatusUpdate result: " + status + "(Obstacle removed)" + " message: " + data);
                    LogTools.info("onStatusUpdate result: " + status + "(障碍物已移除)" + " message: " + data);
                    break;
                default:
                    break;
            }
        }
    };


//    Camera Stuff
private void getAvailableCameras() {
    CameraManager cameraManager = (CameraManager) requireActivity().getSystemService(Context.CAMERA_SERVICE);

    try {
        // Get the list of camera IDs
        String[] cameraIds = cameraManager.getCameraIdList();

        for (String cameraId : cameraIds) {
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);

            // Check if the camera is front or rear
            int lensFacing = characteristics.get(CameraCharacteristics.LENS_FACING);
            String cameraName = "";

            if (lensFacing == CameraCharacteristics.LENS_FACING_BACK) {
                cameraName = "Rear Camera (" + cameraId + ")";
            } else if (lensFacing == CameraCharacteristics.LENS_FACING_FRONT) {
                cameraName = "Front Camera (" + cameraId + ")";
            } else {
                cameraName = "External Camera (" + cameraId + ")";
            }

            // Add the camera name to the list
            cameraList.add(cameraName);
            cameraIdList.add(cameraId); // Store the camera ID for later use
        }

        // Populate the spinner with the camera names
        ArrayAdapter<String> spinnerAdapter = new ArrayAdapter<>(getActivity(), android.R.layout.simple_spinner_item, cameraList);
        spinnerAdapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cameraSelectorSpinner.setAdapter(spinnerAdapter);

    } catch (CameraAccessException e) {
        e.printStackTrace();
    }
}

    private void switchCamera(String cameraId) {
        if (cameraProvider != null) {
            // Unbind any existing use cases before rebinding
            cameraProvider.unbindAll();
        }

        // Use CameraSelector to select the camera by its lens facing direction
        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(getLensFacingFromCameraId(cameraId))
                .build();

        // Connect the imagecapture
        imageCapture = new ImageCapture.Builder().build();

        // Bind the camera to the lifecycle
        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, imageCapture);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    // Helper method to map cameraId to CameraSelector (lensFacing)
    private int getLensFacingFromCameraId(String cameraId) {
        try {
            CameraManager cameraManager = (CameraManager) requireActivity().getSystemService(Context.CAMERA_SERVICE);
            CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(cameraId);
            return characteristics.get(CameraCharacteristics.LENS_FACING);
        } catch (CameraAccessException e) {
            e.printStackTrace();
            return CameraCharacteristics.LENS_FACING_BACK; // Default to rear camera
        }
    }



    private void takePhoto() {
        if (imageCapture == null) {
            Toast.makeText(requireContext(), "Camera is not ready", Toast.LENGTH_SHORT).show();
            return;
        }

        File photoFile = new File(requireContext().getExternalFilesDir(null), "photo_" + System.currentTimeMillis() + ".jpg");

        ImageCapture.OutputFileOptions outputOptions = new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, cameraExecutor, new ImageCapture.OnImageSavedCallback() {
            @Override
            public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                String msg = "Photo captured: " + photoFile.getAbsolutePath();
                Log.d("CameraXApp", msg);
                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("CameraXApp", "Photo capture failed: " + exception.getMessage(), exception);
                Toast.makeText(requireContext(), "Capture failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }



    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

}