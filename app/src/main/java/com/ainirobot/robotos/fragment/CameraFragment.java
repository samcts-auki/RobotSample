package com.ainirobot.robotos.fragment;

import android.Manifest;
import android.content.Context;
//import android.hardware.Camera;
import android.hardware.display.DisplayManager;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.Button;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ainirobot.robotos.R;

import android.content.pm.PackageManager;

import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class CameraFragment extends BaseFragment {

    private static final int CAMERA_REQUEST_CODE = 1001;

    private Spinner cameraSelectorSpinner;
    private Button startButton, stopButton, captureButton;
    private PreviewView previewView;
    private ProcessCameraProvider cameraProvider;
    private Preview preview;
    private ImageCapture imageCapture;
    private List<String> availableCameras = new ArrayList<>();
    private List<CameraSelector> cameraSelectors = new ArrayList<>();
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private ExecutorService cameraExecutor;

    public static Fragment newInstance() {
        return new CameraFragment();
    }

    @Override
    public View onCreateView(Context context) {
        View root = mInflater.inflate(R.layout.fragment_camera_layout, null, false);

        requestPermissions(new String[]{
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO,
                Manifest.permission.WRITE_EXTERNAL_STORAGE,
                Manifest.permission.READ_EXTERNAL_STORAGE,
        }, 1);
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
        startButton = view.findViewById(R.id.start_button);
        stopButton = view.findViewById(R.id.stop_button);
        captureButton = view.findViewById(R.id.captureButton);
        previewView = view.findViewById(R.id.previewView);

        // Camera Executor for background tasks
        cameraExecutor = Executors.newSingleThreadExecutor();

        // Get the camera provider instance
        cameraProviderFuture = ProcessCameraProvider.getInstance(requireContext());
        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                setupCameraSelector();
            } catch (ExecutionException | InterruptedException e) {
                Log.e("CameraXApp", "Error retrieving camera provider", e);
            }
        }, ContextCompat.getMainExecutor(requireContext()));

        // Handle the Start button
        startButton.setOnClickListener(v -> startCamera());

        // Handle the Stop button
        stopButton.setOnClickListener(v -> stopCamera());

        // Capture image on button click
        captureButton.setOnClickListener(v -> takePhoto());

    }

    private void setupCameraSelector() {
        // Retrieve all available camera infos and populate the spinner
        for (CameraSelector cameraSelector : getAvailableCameras()) {
            availableCameras.add(cameraSelector.toString());
            cameraSelectors.add(cameraSelector);
        }

        // Set up the Spinner with the available camera options
        ArrayAdapter<String> adapter = new ArrayAdapter<>(requireContext(), android.R.layout.simple_spinner_item, availableCameras);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        cameraSelectorSpinner.setAdapter(adapter);
    }

    private List<CameraSelector> getAvailableCameras() {
        // Populate available camera selectors (e.g., front, back, or more specific)
        List<CameraSelector> selectors = new ArrayList<>();
        selectors.add(CameraSelector.DEFAULT_BACK_CAMERA);
        selectors.add(CameraSelector.DEFAULT_FRONT_CAMERA);

        // You can add more custom selectors if your device has more than two cameras
        // Add your custom CameraSelector logic here, if needed
        return selectors;
    }

    private void startCamera() {
        if (cameraProvider == null) return;

        // Get the selected camera index
        int selectedCameraIndex = cameraSelectorSpinner.getSelectedItemPosition();
        CameraSelector cameraSelector = cameraSelectors.get(selectedCameraIndex);

        // Build the camera preview
        preview = new Preview.Builder().build();

        // Attach preview to PreviewView
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Build the imageCapture
        imageCapture = new ImageCapture.Builder().build();

        // Bind the camera to the lifecycle
        cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
    }

    private void stopCamera() {
        // Unbind all camera use cases
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
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