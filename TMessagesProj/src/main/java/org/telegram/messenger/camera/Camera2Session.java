package org.telegram.messenger.camera;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.ImageFormat;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.hardware.camera2.CameraCaptureSession;
import android.hardware.camera2.CameraCharacteristics;
import android.hardware.camera2.CameraDevice;
import android.hardware.camera2.CameraExtensionSession;
import android.hardware.camera2.CameraManager;
import android.hardware.camera2.CameraMetadata;
import android.hardware.camera2.CaptureFailure;
import android.hardware.camera2.CaptureRequest;
import android.hardware.camera2.CaptureResult;
import android.hardware.camera2.TotalCaptureResult;
import android.hardware.camera2.params.ExtensionSessionConfiguration;
import android.hardware.camera2.params.OutputConfiguration;
import android.hardware.camera2.params.SessionConfiguration;
import android.hardware.camera2.params.StreamConfigurationMap;
import android.media.Image;
import android.media.ImageReader;
import android.os.Build;
import android.os.Handler;
import android.os.HandlerThread;
import android.util.Log;
import android.util.Range;
import android.util.Size;
import android.util.SizeF;
import android.view.Surface;
import android.view.WindowManager;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;

import org.telegram.messenger.AndroidUtilities;
import org.telegram.messenger.ApplicationLoader;
import org.telegram.messenger.FileLog;
import org.telegram.messenger.video.RoundVideoEncodingPolicy;
import org.telegram.messenger.MessagesController;
import org.telegram.messenger.NanogramExperiments;
import org.telegram.messenger.nanogram.NanogramFeatures;
import org.telegram.messenger.UserConfig;
import org.telegram.messenger.Utilities;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Executor;

@TargetApi(Build.VERSION_CODES.LOLLIPOP)
public class Camera2Session {

    private boolean isError;
    private boolean isSuccess;
    private boolean isClosed;

    private final CameraManager cameraManager;
    private final boolean isFront;
    public final String cameraId;
    private CameraCharacteristics cameraCharacteristics;

    private HandlerThread thread;
    private Handler handler;

    private CameraDevice cameraDevice;
    private SurfaceTexture surfaceTexture;
    private CameraCaptureSession captureSession;
    private Surface surface;

    private final CameraDevice.StateCallback cameraStateCallback;
    private final CameraCaptureSession.StateCallback captureStateCallback;
    private CaptureRequest.Builder captureRequestBuilder;
    private Rect sensorSize;
    private float maxZoom = 1f;
    private float currentZoom = 1f;

    private final Size previewSize;

    private ImageReader imageReader;

    private long lastTime;

    private final boolean allowExtensions;
    private final NanogramCameraExtensions.Plan plan;
    private final Executor handlerExecutor = command -> handler.post(command);
    private CameraExtensionSession extensionSession;
    private int sessionGeneration;
    private boolean sessionStarting;
    private int requestedExtension = -1;
    private boolean extensionsBlocked;
    private Size normalStillSize;
    private Size activeStillSize;
    private Set<CaptureRequest.Key> extensionKeys;
    private Runnable pendingStart;
    private Runnable pendingRetry;
    private Utilities.Callback<Integer> captureProgressCallback;

    private static final String TAG_STILL = "nanogram_still";

    public static Camera2Session create(boolean front, int viewWidth, int viewHeight) {
        return create(front, viewWidth, viewHeight, 0, false);
    }

    public static Camera2Session create(boolean front, int viewWidth, int viewHeight, int fallbackSize) {
        return create(front, viewWidth, viewHeight, fallbackSize, false);
    }

    /** allowExtensions is only for the photo camera: it enables the full-size still reader and, with the Nanogram switch on, camera extensions. */
    public static Camera2Session create(boolean front, int viewWidth, int viewHeight, int fallbackSize, boolean allowExtensions) {
        final Context context = ApplicationLoader.applicationContext;
        final CameraManager cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);

        float bestAspectRatio = 0;
        Size bestSize = null;
        String cameraId = null;
        try {
            String[] cameraIds = cameraManager.getCameraIdList();
            for (int i = 0; i < cameraIds.length; ++i) {
                final String id = cameraIds[i];
                CameraCharacteristics characteristics = cameraManager.getCameraCharacteristics(id);
                if (characteristics == null) continue;
                if (characteristics.get(CameraCharacteristics.LENS_FACING) != (front ? CameraCharacteristics.LENS_FACING_FRONT : CameraCharacteristics.LENS_FACING_BACK)) {
                    continue;
                }
                StreamConfigurationMap confMap = (StreamConfigurationMap) characteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                Size pixelSize = characteristics.get(CameraCharacteristics.SENSOR_INFO_PIXEL_ARRAY_SIZE);
                float cameraAspectRatio = pixelSize == null ? 0 : (float) pixelSize.getWidth() / pixelSize.getHeight();
                if ((viewWidth / (float) viewHeight >= 1f) != (cameraAspectRatio >= 1f)) {
                    cameraAspectRatio = 1f / cameraAspectRatio;
                }
                if (bestAspectRatio <= 0 || Math.abs((float) viewWidth / viewHeight - bestAspectRatio) > Math.abs((float) viewWidth / viewHeight - cameraAspectRatio)) {
                    if (confMap != null && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                        Size size = chooseOptimalSize(confMap.getOutputSizes(SurfaceTexture.class), viewWidth, viewHeight, false);
                        if (fallbackSize > 0 && size != null && !RoundVideoEncodingPolicy.cameraSizeAcceptable(viewWidth, size.getWidth(), size.getHeight())) {
                            size = chooseOptimalSize(confMap.getOutputSizes(SurfaceTexture.class), fallbackSize, fallbackSize, false);
                        }
                        if (size != null) {
                            bestAspectRatio = cameraAspectRatio;
                            cameraId = id;
                            bestSize = size;
                        }
                    }
                } else {

                }
            }
        } catch (Exception e) {
            FileLog.e(e);
        }

        if (cameraId == null || bestSize == null) {
            return null;
        }
        NanogramCameraExtensions.Plan plan = null;
        if (allowExtensions && NanogramFeatures.enhancedPhotos()) {
            try {
                final StreamConfigurationMap confMap = cameraManager.getCameraCharacteristics(cameraId).get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                final List<int[]> normal = new ArrayList<>();
                if (confMap != null) {
                    for (Size s : confMap.getOutputSizes(SurfaceTexture.class)) {
                        normal.add(new int[]{s.getWidth(), s.getHeight()});
                    }
                }
                plan = NanogramCameraExtensions.plan(cameraId, NanogramFeatures.enhancedPhotoMode(), viewWidth, viewHeight, normal);
                if (plan != null) {
                    bestSize = new Size(plan.previewWidth, plan.previewHeight);
                }
            } catch (Exception e) {
                FileLog.e(e);
                plan = null;
            }
        }
        return new Camera2Session(context, front, cameraId, bestSize, allowExtensions, plan);
    }

    private Camera2Session(Context context, boolean isFront, String cameraId, Size size, boolean allowExtensions, NanogramCameraExtensions.Plan plan) {
        this.allowExtensions = allowExtensions;
        this.plan = plan;
        thread = new HandlerThread("tg_camera2");
        thread.start();
        handler = new Handler(thread.getLooper());

        cameraStateCallback = new CameraDevice.StateCallback() {
            @Override
            public void onOpened(@NonNull CameraDevice camera) {
                Camera2Session.this.cameraDevice = camera;
                Camera2Session.this.lastTime = System.currentTimeMillis();
                FileLog.d("Camera2Session camera #" + cameraId + " opened");
                checkOpen();
            }

            @Override
            public void onDisconnected(@NonNull CameraDevice camera) {
                Camera2Session.this.cameraDevice = camera;
                FileLog.d("Camera2Session camera #" + cameraId + " disconnected");
            }

            @Override
            public void onError(@NonNull CameraDevice camera, int error) {
                Camera2Session.this.cameraDevice = camera;
                FileLog.e("Camera2Session camera #" + cameraId + " received " + error + " error");
                AndroidUtilities.runOnUIThread(() -> {
                    isError = true;
                });
            }
        };

        captureStateCallback = new CameraCaptureSession.StateCallback() {
            @Override
            public void onConfigured(@NonNull CameraCaptureSession session) {
                captureSession = session;
                sessionStarting = false;
                FileLog.e("Camera2Session camera #" + cameraId + " capture session configured");
                Camera2Session.this.lastTime = System.currentTimeMillis();
                try {
                    if (allowExtensions && (requestedExtension >= 0 || pendingStart != null)) {
                        captureSession = null;
                        session.close();
                        return;
                    }
                    updateCaptureRequest();
                    notifyReady();
                    runPendingRetry();
                } catch (Exception e) {
                    FileLog.e(e);
                }
            }

            @Override
            public void onClosed(@NonNull CameraCaptureSession session) {
                runPendingStart();
            }

            @Override
            public void onConfigureFailed(@NonNull CameraCaptureSession session) {
                sessionStarting = false;
                captureSession = session;
                FileLog.e("Camera2Session camera #" + cameraId + " capture session failed to configure");
                AndroidUtilities.runOnUIThread(() -> {
                    isError = true;
                });
            }
        };

        this.isFront = isFront;
        this.cameraId = cameraId;
        this.previewSize = size;
        this.lastTime = System.currentTimeMillis();
        if (!allowExtensions) {
            this.imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 1);
        }
        cameraManager = (CameraManager) context.getSystemService(Context.CAMERA_SERVICE);
        try {
            cameraCharacteristics = cameraManager.getCameraCharacteristics(cameraId);
            if (allowExtensions) {
                final StreamConfigurationMap map = cameraCharacteristics.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP);
                final List<int[]> jpeg = new ArrayList<>();
                if (map != null) {
                    for (Size s : map.getOutputSizes(ImageFormat.JPEG)) {
                        jpeg.add(new int[]{s.getWidth(), s.getHeight()});
                    }
                }
                final int[] still = NanogramCameraExtensions.pickStillSize(jpeg, (float) size.getWidth() / size.getHeight(), NanogramCameraExtensions.MAX_STILL_PIXELS);
                normalStillSize = still != null ? new Size(still[0], still[1]) : size;
                ensureStillReader(normalStillSize);
            }
            sensorSize = cameraCharacteristics.get(CameraCharacteristics.SENSOR_INFO_ACTIVE_ARRAY_SIZE);
            final Float value = cameraCharacteristics.get(CameraCharacteristics.SCALER_AVAILABLE_MAX_DIGITAL_ZOOM);
            maxZoom = (value == null || value < 1f) ? 1f : value;
            cameraManager.openCamera(cameraId, cameraStateCallback, handler);
        } catch (Exception e) {
            FileLog.e(e);
            AndroidUtilities.runOnUIThread(() -> {
                isError = true;
            });
        }
    }

    private Runnable doneCallback;
    public void whenDone(Runnable doneCallback) {
        if (isInitiated()) {
            doneCallback.run();
            this.doneCallback = null;
        } else {
            this.doneCallback = doneCallback;
        }
    }

    public void open(SurfaceTexture surfaceTexture) {
        handler.post(() -> {
            this.surfaceTexture = surfaceTexture;
            if (surfaceTexture != null) {
                surfaceTexture.setDefaultBufferSize(getPreviewWidth(), getPreviewHeight());
            }
            checkOpen();
        });
    }

    private boolean opened = false;
    private void checkOpen() {
        if (opened) return;
        if (surfaceTexture == null || cameraDevice == null) return;
        opened = true;

        surface = new Surface(surfaceTexture);

        startSession();
    }

    private void notifyReady() {
        AndroidUtilities.runOnUIThread(() -> {
            isSuccess = true;
            if (doneCallback != null) {
                doneCallback.run();
                doneCallback = null;
            }
        });
    }

    private void runPendingStart() {
        final Runnable r = pendingStart;
        pendingStart = null;
        if (r != null && !isClosed) {
            r.run();
        }
    }

    private void runPendingRetry() {
        final Runnable r = pendingRetry;
        pendingRetry = null;
        if (r != null && !isClosed) {
            r.run();
        }
    }

    private void ensureStillReader(Size size) {
        if (imageReader != null && activeStillSize != null && activeStillSize.equals(size)) {
            return;
        }
        if (imageReader != null) {
            imageReader.close();
        }
        activeStillSize = size;
        imageReader = ImageReader.newInstance(size.getWidth(), size.getHeight(), ImageFormat.JPEG, 2);
    }

    private int desiredExtension() {
        if (!allowExtensions || plan == null || extensionsBlocked || recordingVideo || scanningBarcode || flashing) return -1;
        if (!NanogramFeatures.enhancedPhotos()) return -1;
        if (NanogramCameraExtensions.isBlacklisted(cameraId, plan.extension)) return -1;
        return plan.extension;
    }

    /** Handler thread. Opens the extension session when wanted, otherwise the normal one. */
    private void startSession() {
        if (cameraDevice == null || isClosed) return;
        final int ext = desiredExtension();
        if (ext >= 0 && startExtensionSession(ext)) return;
        startNormalSession();
    }

    private void startNormalSession() {
        try {
            requestedExtension = -1;
            sessionGeneration++;
            sessionStarting = true;
            if (allowExtensions) {
                ensureStillReader(normalStillSize);
                final List<OutputConfiguration> outputs = new ArrayList<>();
                outputs.add(new OutputConfiguration(surface));
                outputs.add(new OutputConfiguration(imageReader.getSurface()));
                cameraDevice.createCaptureSession(new SessionConfiguration(SessionConfiguration.SESSION_REGULAR, outputs, handlerExecutor, captureStateCallback));
            } else {
                ArrayList<Surface> surfaces = new ArrayList<>();
                surfaces.add(surface);
                surfaces.add(imageReader.getSurface());
                cameraDevice.createCaptureSession(surfaces, captureStateCallback, null);
            }
        } catch (Exception e) {
            FileLog.e(e);
            sessionStarting = false;
            AndroidUtilities.runOnUIThread(() -> {
                isError = true;
            });
        }
    }

    private boolean startExtensionSession(int ext) {
        try {
            final float aspect = (float) previewSize.getWidth() / previewSize.getHeight();
            final int[] still = NanogramCameraExtensions.pickStillSize(plan.jpegSizes, aspect, NanogramCameraExtensions.MAX_STILL_PIXELS);
            if (still == null) {
                NanogramCameraExtensions.blacklist(cameraId, ext);
                return false;
            }
            ensureStillReader(new Size(still[0], still[1]));
            extensionKeys = null;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                extensionKeys = cameraManager.getCameraExtensionCharacteristics(cameraId).getAvailableCaptureRequestKeys(ext);
            }
            final List<OutputConfiguration> outputs = new ArrayList<>();
            outputs.add(new OutputConfiguration(surface));
            outputs.add(new OutputConfiguration(imageReader.getSurface()));
            requestedExtension = ext;
            sessionStarting = true;
            cameraDevice.createExtensionSession(new ExtensionSessionConfiguration(ext, outputs, handlerExecutor, new ExtensionStateCallback(++sessionGeneration, ext)));
            return true;
        } catch (Exception e) {
            FileLog.e("Camera2Session extension " + NanogramCameraExtensions.name(ext) + " failed to start", e);
            requestedExtension = -1;
            sessionStarting = false;
            NanogramExperiments.count("cameraExtensionFailed");
            NanogramCameraExtensions.blacklist(cameraId, ext);
            return false;
        }
    }

    /** Handler thread. Closes the current session (one at a time) and starts the right one from its onClosed. */
    private void restartSession() {
        if (!opened || cameraDevice == null || isClosed) return;
        if (extensionSession != null) {
            final CameraExtensionSession old = extensionSession;
            extensionSession = null;
            pendingStart = this::startSession;
            closeExtension(old);
        } else if (captureSession != null) {
            final CameraCaptureSession old = captureSession;
            captureSession = null;
            pendingStart = this::startSession;
            old.close();
        } else if (sessionStarting) {
            pendingStart = this::startSession;
        } else if (pendingStart == null) {
            startSession();
        }
    }

    private void closeExtension(CameraExtensionSession session) {
        try {
            session.close();
        } catch (Exception e) {
            FileLog.e(e);
            runPendingStart();
        }
    }

    private void onModeStateChanged() {
        if (!allowExtensions || plan == null) {
            updateCaptureRequest();
            return;
        }
        handler.post(() -> {
            if (isClosed || !opened) return;
            if ((desiredExtension() >= 0) != (requestedExtension >= 0)) {
                restartSession();
            } else {
                updateCaptureRequest();
            }
        });
    }

    /** Photo camera only: the dual camera runs two sessions, so extensions step aside. */
    public void setExtensionsBlocked(boolean blocked) {
        if (extensionsBlocked != blocked) {
            extensionsBlocked = blocked;
            if (plan != null) {
                onModeStateChanged();
            }
        }
    }

    public void setCaptureProgressCallback(Utilities.Callback<Integer> callback) {
        captureProgressCallback = callback;
    }

    private class ExtensionStateCallback extends CameraExtensionSession.StateCallback {
        private final int generation;
        private final int extension;

        ExtensionStateCallback(int generation, int extension) {
            this.generation = generation;
            this.extension = extension;
        }

        @Override
        public void onConfigured(@NonNull CameraExtensionSession session) {
            if (generation != sessionGeneration) {
                closeExtension(session);
                return;
            }
            sessionStarting = false;
            if (isClosed || requestedExtension < 0 || pendingStart != null) {
                closeExtension(session);
                return;
            }
            extensionSession = session;
            lastTime = System.currentTimeMillis();
            NanogramExperiments.count("cameraExtensionOpened");
            FileLog.d("Camera2Session camera #" + cameraId + " extension " + NanogramCameraExtensions.name(extension) + " configured" + latencyInfo());
            updateExtensionRequest();
            notifyReady();
            runPendingRetry();
        }

        @Override
        public void onConfigureFailed(@NonNull CameraExtensionSession session) {
            if (generation != sessionGeneration) {
                return;
            }
            sessionStarting = false;
            FileLog.e("Camera2Session camera #" + cameraId + " extension session failed to configure");
            NanogramExperiments.count("cameraExtensionFailed");
            NanogramCameraExtensions.blacklist(cameraId, extension);
            extensionSession = null;
            if (!isClosed) {
                if (pendingStart != null) {
                    runPendingStart();
                } else {
                    startNormalSession();
                }
            }
        }

        @Override
        public void onClosed(@NonNull CameraExtensionSession session) {
            if (generation == sessionGeneration) {
                runPendingStart();
            }
        }
    }

    private String latencyInfo() {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && activeStillSize != null) {
                final android.util.Range<Long> range = cameraManager.getCameraExtensionCharacteristics(cameraId).getEstimatedCaptureLatencyRangeMillis(requestedExtension, activeStillSize, ImageFormat.JPEG);
                if (range != null) {
                    return ", latency " + range;
                }
            }
        } catch (Exception ignore) {
        }
        return "";
    }

    private class ExtensionCaptureCallback extends CameraExtensionSession.ExtensionCaptureCallback {
        private final File file;
        private final Utilities.Callback<Integer> whenDone;
        private final boolean retried;
        private boolean failedHandled;

        ExtensionCaptureCallback(File file, Utilities.Callback<Integer> whenDone, boolean retried) {
            this.file = file;
            this.whenDone = whenDone;
            this.retried = retried;
        }

        @Override
        public void onCaptureFailed(@NonNull CameraExtensionSession session, @NonNull CaptureRequest request) {
            handleFailed(request);
        }

        @Override
        @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
        public void onCaptureFailed(@NonNull CameraExtensionSession session, @NonNull CaptureRequest request, int failure) {
            handleFailed(request);
        }

        private void handleFailed(CaptureRequest request) {
            if (!TAG_STILL.equals(request.getTag()) || failedHandled) return;
            failedHandled = true;
            FileLog.e("Camera2Session camera #" + cameraId + " extension capture failed");
            NanogramExperiments.count("cameraExtensionFailed");
            if (requestedExtension >= 0) {
                NanogramCameraExtensions.blacklist(cameraId, requestedExtension);
            }
            if (!retried && !isClosed) {
                pendingRetry = () -> {
                    if (!shoot(file, whenDone, true)) {
                        FileLog.e("Camera2Session camera #" + cameraId + " extension capture retry could not start");
                    }
                };
                restartSession();
            }
        }

        @Override
        public void onCaptureSequenceAborted(@NonNull CameraExtensionSession session, int sequenceId) {
            if (retried || isClosed || failedHandled) return;
            failedHandled = true;
            pendingRetry = () -> {
                if (!shoot(file, whenDone, true)) {
                    FileLog.e("Camera2Session camera #" + cameraId + " extension capture retry could not start");
                }
            };
            if (extensionSession == session) {
                restartSession();
            }
        }

        @Override
        @RequiresApi(Build.VERSION_CODES.UPSIDE_DOWN_CAKE)
        public void onCaptureProcessProgressed(@NonNull CameraExtensionSession session, @NonNull CaptureRequest request, int progress) {
            final Utilities.Callback<Integer> callback = captureProgressCallback;
            if (callback != null) {
                AndroidUtilities.runOnUIThread(() -> callback.run(progress));
            }
        }
    }

    private boolean extensionKeyAvailable(CaptureRequest.Key<?> key) {
        return extensionKeys == null || extensionKeys.contains(key);
    }

    private void updateExtensionRequest() {
        final CameraExtensionSession session = extensionSession;
        if (cameraDevice == null || surface == null || session == null) return;
        try {
            final CaptureRequest.Builder builder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_PREVIEW);
            if (extensionKeys != null && extensionKeys.contains(CaptureRequest.CONTROL_ZOOM_RATIO) && Math.abs(currentZoom - 1f) >= 0.01f) {
                builder.set(CaptureRequest.CONTROL_ZOOM_RATIO, currentZoom);
            }
            builder.addTarget(surface);
            session.setRepeatingRequest(builder.build(), handlerExecutor, new CameraExtensionSession.ExtensionCaptureCallback() {});
        } catch (Exception e) {
            FileLog.e("Camera2Session extension setRepeatingRequest error", e);
        }
    }

    public boolean isInitiated() {
        return !isError && isSuccess && !isClosed;
    }

    public int getDisplayOrientation() {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return 0;
            }
            int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }

            Integer sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            int displayOrientation;
            if (isFront) {
                displayOrientation = (sensorOrientation + degrees) % 360;
                displayOrientation = (360 - displayOrientation) % 360; // compensate the mirror
            } else { // back-facing
                displayOrientation = (sensorOrientation - degrees + 360) % 360;
            }
            return displayOrientation;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    private int getJpegOrientation() {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return 0;
            }
            int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            int degrees = 0;
            switch (rotation) {
                case Surface.ROTATION_0:
                    degrees = 0;
                    break;
                case Surface.ROTATION_90:
                    degrees = 90;
                    break;
                case Surface.ROTATION_180:
                    degrees = 180;
                    break;
                case Surface.ROTATION_270:
                    degrees = 270;
                    break;
            }

            Integer sensorOrientation = cameraCharacteristics.get(CameraCharacteristics.SENSOR_ORIENTATION);
            int jpegOrientation;
            if (isFront) {
                jpegOrientation = (sensorOrientation + degrees) % 360;
                jpegOrientation = (360 - jpegOrientation) % 360; // compensate the mirror
            } else { // back-facing
                jpegOrientation = (sensorOrientation - degrees + 360) % 360;
            }
            return jpegOrientation;
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    public int getWorldAngle() {
        int displayOrientation = getDisplayOrientation();
        int jpegOrientation = getJpegOrientation();
        int diffOrientation = jpegOrientation - displayOrientation;
        if (diffOrientation < 0) {
            diffOrientation += 360;
        }
        return diffOrientation;
    }

    /**
     * Rotation the round video recorder applies to its GL quad on top of the SurfaceTexture
     * transform, which only makes the frame upright for the device's natural orientation.
     * Counter-clockwise degrees equal to the display rotation. {@link #getWorldAngle()} stays
     * as the attach camera (CameraView) expects it.
     */
    public int getRoundVideoRotationAngle() {
        try {
            Context context = ApplicationLoader.applicationContext;
            if (context == null) {
                return 0;
            }
            int rotation = ((WindowManager) context.getSystemService(Context.WINDOW_SERVICE)).getDefaultDisplay().getRotation();
            switch (rotation) {
                case Surface.ROTATION_90:
                    return 90;
                case Surface.ROTATION_180:
                    return 180;
                case Surface.ROTATION_270:
                    return 270;
                default:
                    return 0;
            }
        } catch (Exception e) {
            FileLog.e(e);
        }
        return 0;
    }

    public int getCurrentOrientation() {
        return getJpegOrientation();
    }

    private final Rect cropRegion = new Rect();
    public void setZoom(float value) {
        if (!isInitiated()) return;
        if (extensionSession != null) {
            currentZoom = Utilities.clamp(value, maxZoom, 1f);
            updateExtensionRequest();
            return;
        }
        if (captureRequestBuilder == null || cameraDevice == null || sensorSize == null) return;

        currentZoom = Utilities.clamp(value, maxZoom, 1f);
        updateCaptureRequest();

        try {
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, handler);
        } catch (Exception e) {
            FileLog.e(e);
        }
    }

    private boolean flashing;
    public void setFlash(boolean flash) {
        if (flashing != flash) {
            flashing = flash;
            onModeStateChanged();
        }
    }
    public boolean getFlash() {
        return flashing;
    }

    public float getZoom() {
        return currentZoom;
    }

    public float getMaxZoom() {
        return maxZoom;
    }

    public float getMinZoom() {
        // TODO: support wide zoom camera switching
        return 1f;
    }

    public int getPreviewWidth() {
        return previewSize.getWidth();
    }

    public int getPreviewHeight() {
        return previewSize.getHeight();
    }

    public void destroy(boolean async) {
        destroy(async, null);
    }

    public void destroy(boolean async, Runnable afterCallback) {
        isClosed = true;
        if (async) {
            handler.post(() -> {
                if (extensionSession != null) {
                    closeExtension(extensionSession);
                    extensionSession = null;
                }
                if (captureSession != null) {
                    captureSession.close();
                    captureSession = null;
                }
                if (cameraDevice != null) {
                    cameraDevice.close();
                    cameraDevice = null;
                }
                if (imageReader != null) {
                    imageReader.close();
                    imageReader = null;
                }
                thread.quitSafely();
                AndroidUtilities.runOnUIThread(() -> {
                    try {
                        thread.join();
                    } catch (Exception e) {
                        FileLog.e(e);
                    }
                    if (afterCallback != null) {
                        afterCallback.run();
                    }
                });
            });
        } else {
            if (extensionSession != null) {
                closeExtension(extensionSession);
                extensionSession = null;
            }
            if (captureSession != null) {
                captureSession.close();
                captureSession = null;
            }
            if (cameraDevice != null) {
                cameraDevice.close();
                cameraDevice = null;
            }
            if (imageReader != null) {
                imageReader.close();
                imageReader = null;
            }
            thread.quitSafely();
            try {
                thread.join();
            } catch (Exception e) {
                FileLog.e(e);
            }
            if (afterCallback != null) {
                AndroidUtilities.runOnUIThread(afterCallback);
            }
        }
    }

    private boolean recordingVideo;
    public void setRecordingVideo(boolean recording) {
        if (recordingVideo != recording) {
            recordingVideo = recording;
            onModeStateChanged();
        }
    }

    private boolean scanningBarcode;
    public void setScanningBarcode(boolean scanning) {
        if (scanningBarcode != scanning) {
            scanningBarcode = scanning;
            onModeStateChanged();
        }
    }

    private boolean nightMode;
    public void setNightMode(boolean enable) {
        if (nightMode != enable) {
            nightMode = enable;
            updateCaptureRequest();
        }
    }

    private void updateCaptureRequest() {
        if (extensionSession != null) {
            updateExtensionRequest();
            return;
        }
        if (cameraDevice == null || surface == null || captureSession == null) return;
        try {
            int template;
            if (recordingVideo) {
                template = CameraDevice.TEMPLATE_RECORD;
            } else if (scanningBarcode) {
                template = CameraDevice.TEMPLATE_STILL_CAPTURE;
            } else {
                template = CameraDevice.TEMPLATE_PREVIEW;
            }
            captureRequestBuilder = cameraDevice.createCaptureRequest(template);

            if (scanningBarcode) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_BARCODE);
            } else if (nightMode) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, isFront ? CameraMetadata.CONTROL_SCENE_MODE_NIGHT_PORTRAIT : CameraMetadata.CONTROL_SCENE_MODE_NIGHT);
            }

            captureRequestBuilder.set(CaptureRequest.FLASH_MODE, flashing ? (recordingVideo ? CaptureRequest.FLASH_MODE_TORCH : CaptureRequest.FLASH_MODE_SINGLE) : CaptureRequest.FLASH_MODE_OFF);

            if (recordingVideo) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_AE_TARGET_FPS_RANGE, new Range<Integer>(30, 60));
                captureRequestBuilder.set(CaptureRequest.CONTROL_CAPTURE_INTENT, CaptureRequest.CONTROL_CAPTURE_INTENT_VIDEO_RECORD);
                if (cameraCharacteristics != null && NanogramCameraSupport.contains(cameraCharacteristics.get(CameraCharacteristics.CONTROL_AVAILABLE_VIDEO_STABILIZATION_MODES), CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON)) {
                    captureRequestBuilder.set(CaptureRequest.CONTROL_VIDEO_STABILIZATION_MODE, CameraMetadata.CONTROL_VIDEO_STABILIZATION_MODE_ON);
                }
            }

            if (sensorSize != null && Math.abs(currentZoom - 1f) >= 0.01f) {
                final int centerX = sensorSize.width() / 2;
                final int centerY = sensorSize.height() / 2;
                final int deltaX = (int) ((0.5f * sensorSize.width()) / currentZoom);
                final int deltaY = (int) ((0.5f * sensorSize.height()) / currentZoom);
                cropRegion.set(
                        centerX - deltaX,
                        centerY - deltaY,
                        centerX + deltaX,
                        centerY + deltaY
                );
                captureRequestBuilder.set(CaptureRequest.SCALER_CROP_REGION, cropRegion);
            }

            captureRequestBuilder.addTarget(surface);
            captureSession.setRepeatingRequest(captureRequestBuilder.build(), null, handler);
        } catch (Exception e) {
            FileLog.e("Camera2Sessions setRepeatingRequest error in updateCaptureRequest", e);
        }
    }

    public boolean takePicture(final File file, Utilities.Callback<Integer> whenDone) {
        return shoot(file, whenDone, false);
    }

    private boolean shoot(final File file, Utilities.Callback<Integer> whenDone, boolean retried) {
        final CameraExtensionSession extension = extensionSession;
        if (cameraDevice == null || imageReader == null || (captureSession == null && extension == null)) return false;
        try {
            CaptureRequest.Builder captureRequestBuilder = cameraDevice.createCaptureRequest(CameraDevice.TEMPLATE_STILL_CAPTURE);
            final int orientation = getJpegOrientation();
            if (extension == null || extensionKeyAvailable(CaptureRequest.JPEG_ORIENTATION)) {
                captureRequestBuilder.set(CaptureRequest.JPEG_ORIENTATION, orientation);
            }
            imageReader.setOnImageAvailableListener(new ImageReader.OnImageAvailableListener() {
                @Override
                public void onImageAvailable(ImageReader reader) {
                    Image image = null;
                    try {
                        image = reader.acquireNextImage();
                        if (image == null) {
                            return;
                        }
                        ByteBuffer buffer = image.getPlanes()[0].getBuffer();
                        byte[] bytes = new byte[buffer.remaining()];
                        buffer.get(bytes);
                        try (FileOutputStream output = new FileOutputStream(file)) {
                            output.write(bytes);
                        }
                    } catch (Exception e) {
                        FileLog.e(e);
                    } finally {
                        if (image != null) {
                            image.close();
                        }
                    }

                    AndroidUtilities.runOnUIThread(() -> {
                        if (whenDone != null) {
                            whenDone.run(orientation);
                        }
                    });
                }
            }, handler);
            captureRequestBuilder.addTarget(imageReader.getSurface());
            if (extension != null) {
                if (extensionKeyAvailable(CaptureRequest.CONTROL_ZOOM_RATIO) && extensionKeys != null && Math.abs(currentZoom - 1f) >= 0.01f) {
                    captureRequestBuilder.set(CaptureRequest.CONTROL_ZOOM_RATIO, currentZoom);
                }
                captureRequestBuilder.setTag(TAG_STILL);
                extension.capture(captureRequestBuilder.build(), handlerExecutor, new ExtensionCaptureCallback(file, whenDone, retried));
                return true;
            }
            if (scanningBarcode) {
                captureRequestBuilder.set(CaptureRequest.CONTROL_SCENE_MODE, CameraMetadata.CONTROL_SCENE_MODE_BARCODE);
            }
            captureSession.capture(captureRequestBuilder.build(), new CameraCaptureSession.CaptureCallback() {}, null);
            return true;
        } catch (Exception e) {
            FileLog.e("Camera2Sessions takePicture error", e);
            return false;
        }
    }


    public static Size chooseOptimalSize(Size[] choices, int width, int height, boolean notBigger) {
        List<Size> bigEnoughWithAspectRatio = new ArrayList<>(choices.length);
        List<Size> bigEnough = new ArrayList<>(choices.length);
        int w = width;
        int h = height;
        for (int a = 0; a < choices.length; a++) {
            Size option = choices[a];
            if (notBigger && (option.getHeight() > height || option.getWidth() > width)) {
                continue;
            }
            if (option.getHeight() == option.getWidth() * h / w && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnoughWithAspectRatio.add(option);
            } else if (option.getHeight() * option.getWidth() <= width * height * 4 && option.getWidth() >= width && option.getHeight() >= height) {
                bigEnough.add(option);
            }
        }
        if (bigEnoughWithAspectRatio.size() > 0) {
            return Collections.min(bigEnoughWithAspectRatio, new CompareSizesByArea());
        } else if (bigEnough.size() > 0) {
            return Collections.min(bigEnough, new CompareSizesByArea());
        } else {
            return Collections.max(Arrays.asList(choices), new CompareSizesByArea());
        }
    }
    static class CompareSizesByArea implements Comparator<Size> {
        @Override
        public int compare(Size lhs, Size rhs) {
            return Long.signum((long) lhs.getWidth() * lhs.getHeight() - (long) rhs.getWidth() * rhs.getHeight());
        }
    }

}