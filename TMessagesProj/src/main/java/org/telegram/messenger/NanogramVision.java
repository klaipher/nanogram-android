package org.telegram.messenger;

import android.graphics.Bitmap;
import android.graphics.ImageFormat;
import android.os.SystemClock;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import com.google.android.gms.tasks.Tasks;
import com.google.mlkit.common.MlKitException;
import com.google.mlkit.vision.barcode.BarcodeScanner;
import com.google.mlkit.vision.barcode.BarcodeScannerOptions;
import com.google.mlkit.vision.barcode.BarcodeScanning;
import com.google.mlkit.vision.barcode.common.Barcode;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.face.Face;
import com.google.mlkit.vision.face.FaceDetection;
import com.google.mlkit.vision.face.FaceDetector;
import com.google.mlkit.vision.face.FaceDetectorOptions;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;

/**
 * Synchronous ML Kit barcode and face detection for background threads, replacing the retired Mobile Vision library.
 * A detector that cannot run (the unbundled model is missing, no Play services) reports isOperational() == false
 * so callers keep their ZXing or no-op fallback.
 */
public final class NanogramVision {

    private static final long TIMEOUT_SECONDS = 10;
    private static final long BACKOFF_MS = 30_000;
    private static volatile long unavailableUntil;

    private NanogramVision() {
    }

    /** A detector that failed during the call has no say: its caller falls back to ZXing for the same frame. */
    public static boolean useDetectorResult(boolean hasDetector, boolean operationalAfterDetect) {
        return hasDetector && operationalAfterDetect;
    }

    static boolean inBackoff(long now, long until) {
        return now < until;
    }

    private static boolean transientFailure(Throwable e) {
        return e instanceof TimeoutException
                || e instanceof ExecutionException && e.getCause() instanceof MlKitException && ((MlKitException) e.getCause()).getErrorCode() == MlKitException.UNAVAILABLE
                || e instanceof MlKitException && ((MlKitException) e).getErrorCode() == MlKitException.UNAVAILABLE;
    }

    /** Returns true when the failure is permanent for this detector; transient ones (model still downloading, timeout) pause ML Kit process-wide and log once per pause. */
    private static boolean onFailure(Throwable e) {
        if (!transientFailure(e)) {
            FileLog.e(e);
            return true;
        }
        final long now = SystemClock.elapsedRealtime();
        if (!inBackoff(now, unavailableUntil)) {
            FileLog.e(e);
        }
        unavailableUntil = now + BACKOFF_MS;
        return false;
    }

    private static boolean paused() {
        return inBackoff(SystemClock.elapsedRealtime(), unavailableUntil);
    }

    public static int normalizeRotation(int degrees) {
        int d = ((degrees % 360) + 360) % 360;
        return d % 90 == 0 ? d : 0;
    }

    public static final class Barcodes {
        private final BarcodeScanner scanner;
        private volatile boolean broken;

        public Barcodes(boolean qrOnly) {
            BarcodeScanner created = null;
            try {
                BarcodeScannerOptions.Builder options = new BarcodeScannerOptions.Builder();
                if (qrOnly) {
                    options.setBarcodeFormats(Barcode.FORMAT_QR_CODE);
                } else {
                    options.setBarcodeFormats(Barcode.FORMAT_ALL_FORMATS);
                }
                created = BarcodeScanning.getClient(options.build());
            } catch (Throwable e) {
                FileLog.e(e);
            }
            scanner = created;
            broken = created == null;
        }

        public boolean isOperational() {
            return !broken && !paused();
        }

        @NonNull
        public List<Barcode> detect(@NonNull Bitmap bitmap) {
            return run(InputImage.fromBitmap(bitmap, 0));
        }

        @NonNull
        public List<Barcode> detect(@NonNull byte[] nv21, int width, int height) {
            return run(InputImage.fromByteArray(nv21, width, height, 0, ImageFormat.NV21));
        }

        @NonNull
        private List<Barcode> run(InputImage image) {
            if (broken || scanner == null || paused()) {
                return Collections.emptyList();
            }
            try {
                List<Barcode> codes = Tasks.await(scanner.process(image), TIMEOUT_SECONDS, TimeUnit.SECONDS);
                return codes != null ? codes : Collections.emptyList();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return Collections.emptyList();
            } catch (Throwable e) {
                if (onFailure(e)) {
                    broken = true;
                }
                return Collections.emptyList();
            }
        }

        public void release() {
            if (scanner != null) {
                try {
                    scanner.close();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }
    }

    public static final class Faces {
        private final FaceDetector detector;
        private volatile boolean broken;

        public Faces(boolean accurate, boolean landmarks) {
            FaceDetector created = null;
            try {
                FaceDetectorOptions options = new FaceDetectorOptions.Builder()
                        .setPerformanceMode(accurate ? FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE : FaceDetectorOptions.PERFORMANCE_MODE_FAST)
                        .setLandmarkMode(landmarks ? FaceDetectorOptions.LANDMARK_MODE_ALL : FaceDetectorOptions.LANDMARK_MODE_NONE)
                        .build();
                created = FaceDetection.getClient(options);
            } catch (Throwable e) {
                FileLog.e(e);
            }
            detector = created;
            broken = created == null;
        }

        public boolean isOperational() {
            return !broken && !paused();
        }

        @Nullable
        public List<Face> detect(@NonNull Bitmap bitmap, int rotationDegrees) {
            if (broken || detector == null || paused()) {
                return null;
            }
            try {
                return Tasks.await(detector.process(InputImage.fromBitmap(bitmap, normalizeRotation(rotationDegrees))), TIMEOUT_SECONDS, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            } catch (Throwable e) {
                if (onFailure(e)) {
                    broken = true;
                }
                return null;
            }
        }

        public void release() {
            if (detector != null) {
                try {
                    detector.close();
                } catch (Throwable e) {
                    FileLog.e(e);
                }
            }
        }
    }
}
