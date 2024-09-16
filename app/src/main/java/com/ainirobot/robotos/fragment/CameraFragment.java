package com.ainirobot.robotos.fragment;

import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.Manifest;
import android.content.Context;
import android.util.Base64;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import android.widget.Spinner;
import android.widget.ArrayAdapter;
import android.widget.Toast;
import android.widget.Button;
import android.widget.AdapterView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.ainirobot.robotos.R;

import android.content.pm.PackageManager;

import android.hardware.camera2.CameraAccessException;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraManager;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import com.google.common.util.concurrent.ListenableFuture;

import org.json.JSONException;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;


public class CameraFragment extends BaseFragment {

    private static final int CAMERA_REQUEST_CODE = 1001;

    private Spinner cameraSelectorSpinner;
    private Button captureButton;
    private PreviewView previewView;

    private ProcessCameraProvider cameraProvider;
    private Preview preview;
    private ImageCapture imageCapture;
    private ArrayList<String> cameraList = new ArrayList<>();
    private ArrayList<String> cameraIdList = new ArrayList<>(); // Holds camera IDs for later use
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private CameraSelector cameraSelector;

    private ExecutorService cameraExecutor;

    // Example Java array
    private static final String SERVER_URL = "http://192.168.8.148:8080"; // Replace with your server's URL

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
        captureButton = view.findViewById(R.id.captureButton);
        previewView = view.findViewById(R.id.previewView);

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

        // Capture image on button click
        captureButton.setOnClickListener(v -> takePhoto());

    }

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

        // Set up the preview use case to show the camera preview
        Preview preview = new Preview.Builder().build();

        // Use CameraSelector to select the camera by its lens facing direction
        cameraSelector = new CameraSelector.Builder()
                .requireLensFacing(getLensFacingFromCameraId(cameraId))
                .build();

        // Connect the preview to the preview view
        preview.setSurfaceProvider(previewView.getSurfaceProvider());

        // Build the imageCapture
        imageCapture = new ImageCapture.Builder().build();

        // Bind the camera to the lifecycle
        try {
            cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture);
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
//                Toast.makeText(requireContext(), msg, Toast.LENGTH_SHORT).show();
                uploadImage(photoFile);
            }

            @Override
            public void onError(@NonNull ImageCaptureException exception) {
                Log.e("CameraXApp", "Photo capture failed: " + exception.getMessage(), exception);
                Toast.makeText(requireContext(), "Capture failed: " + exception.getMessage(), Toast.LENGTH_SHORT).show();
            }
        });
    }

    // Method to encode the image file into a Base64 string
    private String encodeImageToBase64(File imageFile) throws IOException {
        FileInputStream fis = new FileInputStream(imageFile);
        Bitmap bitmap = BitmapFactory.decodeStream(fis);
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, baos); // Compress the bitmap into JPEG format
        byte[] imageBytes = baos.toByteArray();
        return Base64.encodeToString(imageBytes, Base64.DEFAULT); // Encode to Base64
    }

    private void uploadImage(File imageFile) {

        ExecutorService executor = Executors.newSingleThreadExecutor();
        JSONObject payload = new JSONObject();
        executor.execute(() -> {
            try {
                // Convert image to Base64 string
                String base64Image = encodeImageToBase64(imageFile);

                // Prepare JSON object
                JSONObject metaData = new JSONObject();
                try {
                    metaData.put("camera_id",  "camera_1");
                    metaData.put("waypoint_id", 0);
                    metaData.put("pose", null);
                } catch (JSONException e) {
                    e.printStackTrace();
                }

                try {
                    payload.put("metadata", metaData);
                    payload.put("image", base64Image);
                } catch (JSONException e) {
                    e.printStackTrace();
                }
                // Send JSON object to the server
                sendJsonToServer(payload);
                getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "Image and JSON sent via REST API", Toast.LENGTH_SHORT).show());

            } catch (IOException e) {
                Log.e("REST API", "Error encoding image or sending JSON: " + e.getMessage());
                Log.e("REST API", payload.toString());
                getActivity().runOnUiThread(() -> Toast.makeText(getActivity(), "Failed to send image and JSON", Toast.LENGTH_SHORT).show());
            }
        });
    }

    // Method to send JSON object to the server
    private void sendJsonToServer(JSONObject jsonObject) throws IOException {
        URL url = new URL(SERVER_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json; utf-8");
        conn.setRequestProperty("Accept", "application/json");
        conn.setRequestProperty("User-Agent", "Java HttpURLConnection");
        conn.setDoOutput(true);

        OutputStream os = conn.getOutputStream();
        byte[] input = jsonObject.toString().getBytes(StandardCharsets.UTF_8);
        os.write(input, 0, input.length);
        os.close();

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            Log.i("REST API", "Image successfully uploaded");
        } else {
            Log.e("REST API", "Failed to upload image, response code: " + responseCode);
        }
        conn.disconnect();
    }


    @Override
    public void onDestroy() {
        super.onDestroy();
        if (cameraExecutor != null) {
            cameraExecutor.shutdown();
        }
    }

}