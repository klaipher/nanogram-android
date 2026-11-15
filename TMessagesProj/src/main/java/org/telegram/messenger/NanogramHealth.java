package org.telegram.messenger;

import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.BatteryManager;
import android.os.Build;
import android.os.PowerManager;
import android.os.PowerMonitor;
import android.os.PowerMonitorReadings;
import android.os.SystemClock;
import android.os.health.HealthStats;
import android.os.health.ProcessHealthStats;
import android.os.health.SystemHealthManager;
import android.os.health.TimerStat;
import android.os.health.UidHealthStats;

import androidx.annotation.RequiresApi;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

/**
 * CPU, wakelock, network and energy numbers of Nanogram's own uid (SystemHealthManager) and, on Android 15 with
 * on-device power monitors, whole-phone energy. Snapshots run on one background thread: at app start, when a
 * performance experiment switch changes, when the Performance experiments screen opens and when diagnostics are built.
 * Counts and durations only; no wakelock or job names, no other app's data.
 */
public final class NanogramHealth {

    private static final long ENERGY_TIMEOUT_MS = 2000;

    private static final ExecutorService executor = Executors.newSingleThreadExecutor(r -> {
        Thread thread = new Thread(r, "nanogram-health");
        thread.setDaemon(true);
        return thread;
    });

    private static final ConcurrentHashMap<String, NanogramHealthMath.Sample> bases = new ConcurrentHashMap<>();
    private static volatile NanogramHealthMath.Sample start;
    private static volatile NanogramHealthMath.Sample latest;
    // Supported monitors never change while the process lives; null until asked once.
    private static List<PowerMonitor> monitors;

    private NanogramHealth() {
    }

    public static void onAppStart(Context context) {
        executor.execute(() -> {
            NanogramHealthMath.Sample sample = take(context);
            start = sample;
            latest = sample;
        });
    }

    public static void onSwitchChanged(String experimentId) {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        executor.execute(() -> {
            NanogramHealthMath.Sample sample = take(context);
            bases.put(experimentId, sample);
            latest = sample;
        });
    }

    /** Takes a fresh reading and then runs onDone (on the health thread) so a screen can redraw with it. */
    public static void refreshAsync(Runnable onDone) {
        final Context context = ApplicationLoader.applicationContext;
        if (context == null) {
            return;
        }
        executor.execute(() -> {
            latest = take(context);
            if (onDone != null) {
                onDone.run();
            }
        });
    }

    /** Fresh reading for the zip; call from a background thread. Falls back to the last one on timeout. */
    public static NanogramHealthMath.Sample refreshBlocking(Context context) {
        Future<NanogramHealthMath.Sample> future = executor.submit(() -> {
            NanogramHealthMath.Sample sample = take(context);
            latest = sample;
            return sample;
        });
        try {
            return future.get(5, TimeUnit.SECONDS);
        } catch (Throwable e) {
            return latest;
        }
    }

    public static String experimentText(String experimentId) {
        NanogramHealthMath.Sample now = latest;
        NanogramHealthMath.Sample base = bases.get(experimentId);
        if (base == null) {
            base = start;
        }
        return NanogramHealthMath.experimentText(base, now);
    }

    public static String fullReport(Context context) {
        NanogramHealthMath.Sample now = refreshBlocking(context);
        if (now == null) {
            return "Health and energy: no reading yet\n";
        }
        return NanogramHealthMath.fullReport(start, now, new TreeMap<>(bases), Build.VERSION.SDK_INT);
    }

    private static NanogramHealthMath.Sample take(Context context) {
        long elapsed = SystemClock.elapsedRealtime();
        TreeMap<String, Long> counters = new TreeMap<>();
        try {
            SystemHealthManager manager = (SystemHealthManager) context.getSystemService(Context.SYSTEM_HEALTH_SERVICE);
            if (manager != null) {
                readCounters(manager.takeMyUidSnapshot(), counters);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        TreeMap<String, Long> energy = new TreeMap<>();
        boolean energySupported = false;
        long[] energyElapsed = {elapsed};
        if (Build.VERSION.SDK_INT >= NanogramHealthMath.POWER_MONITOR_API) {
            try {
                energySupported = readEnergy(context, energy, energyElapsed);
            } catch (Throwable e) {
                FileLog.e(e);
            }
        }
        return new NanogramHealthMath.Sample(elapsed, counters, energy, energySupported, readBattery(context), energyElapsed[0]);
    }

    private static void readCounters(HealthStats stats, Map<String, Long> out) {
        if (stats == null) {
            return;
        }
        measurement(stats, UidHealthStats.MEASUREMENT_REALTIME_BATTERY_MS, NanogramHealthMath.BATTERY_REALTIME_MS, out);
        measurement(stats, UidHealthStats.MEASUREMENT_USER_CPU_TIME_MS, NanogramHealthMath.CPU_USER, out);
        measurement(stats, UidHealthStats.MEASUREMENT_SYSTEM_CPU_TIME_MS, NanogramHealthMath.CPU_SYSTEM, out);
        timer(stats, UidHealthStats.TIMER_PROCESS_STATE_TOP_MS, null, NanogramHealthMath.STATE_TOP, out);
        timer(stats, UidHealthStats.TIMER_PROCESS_STATE_FOREGROUND_SERVICE_MS, null, NanogramHealthMath.STATE_FG_SERVICE, out);
        timer(stats, UidHealthStats.TIMER_PROCESS_STATE_TOP_SLEEPING_MS, null, NanogramHealthMath.STATE_TOP_SLEEPING, out);
        timer(stats, UidHealthStats.TIMER_PROCESS_STATE_FOREGROUND_MS, null, NanogramHealthMath.STATE_FOREGROUND, out);
        timer(stats, UidHealthStats.TIMER_PROCESS_STATE_BACKGROUND_MS, null, NanogramHealthMath.STATE_BACKGROUND, out);
        timer(stats, UidHealthStats.TIMER_PROCESS_STATE_CACHED_MS, null, NanogramHealthMath.STATE_CACHED, out);
        timer(stats, UidHealthStats.TIMER_MOBILE_RADIO_ACTIVE, NanogramHealthMath.RADIO_ACTIVE_COUNT, NanogramHealthMath.RADIO_ACTIVE_MS, out);
        timers(stats, UidHealthStats.TIMERS_WAKELOCKS_PARTIAL, NanogramHealthMath.WAKELOCK_PARTIAL_COUNT, NanogramHealthMath.WAKELOCK_PARTIAL_MS, out);
        timers(stats, UidHealthStats.TIMERS_WAKELOCKS_FULL, NanogramHealthMath.WAKELOCK_FULL_COUNT, NanogramHealthMath.WAKELOCK_FULL_MS, out);
        timers(stats, UidHealthStats.TIMERS_WAKELOCKS_WINDOW, NanogramHealthMath.WAKELOCK_WINDOW_COUNT, NanogramHealthMath.WAKELOCK_WINDOW_MS, out);
        timers(stats, UidHealthStats.TIMERS_JOBS, NanogramHealthMath.JOB_COUNT, NanogramHealthMath.JOB_MS, out);
        timers(stats, UidHealthStats.TIMERS_SYNCS, NanogramHealthMath.SYNC_COUNT, NanogramHealthMath.SYNC_MS, out);
        measurement(stats, UidHealthStats.MEASUREMENT_MOBILE_RX_BYTES, NanogramHealthMath.MOBILE_RX_BYTES, out);
        measurement(stats, UidHealthStats.MEASUREMENT_MOBILE_TX_BYTES, NanogramHealthMath.MOBILE_TX_BYTES, out);
        measurement(stats, UidHealthStats.MEASUREMENT_MOBILE_RX_PACKETS, NanogramHealthMath.MOBILE_RX_PACKETS, out);
        measurement(stats, UidHealthStats.MEASUREMENT_MOBILE_TX_PACKETS, NanogramHealthMath.MOBILE_TX_PACKETS, out);
        measurement(stats, UidHealthStats.MEASUREMENT_WIFI_RX_BYTES, NanogramHealthMath.WIFI_RX_BYTES, out);
        measurement(stats, UidHealthStats.MEASUREMENT_WIFI_TX_BYTES, NanogramHealthMath.WIFI_TX_BYTES, out);
        measurement(stats, UidHealthStats.MEASUREMENT_WIFI_RX_PACKETS, NanogramHealthMath.WIFI_RX_PACKETS, out);
        measurement(stats, UidHealthStats.MEASUREMENT_WIFI_TX_PACKETS, NanogramHealthMath.WIFI_TX_PACKETS, out);
        if (stats.hasStats(UidHealthStats.STATS_PROCESSES)) {
            long foreground = 0;
            long starts = 0;
            for (HealthStats process : stats.getStats(UidHealthStats.STATS_PROCESSES).values()) {
                if (process.hasMeasurement(ProcessHealthStats.MEASUREMENT_FOREGROUND_MS)) {
                    foreground += process.getMeasurement(ProcessHealthStats.MEASUREMENT_FOREGROUND_MS);
                }
                if (process.hasMeasurement(ProcessHealthStats.MEASUREMENT_STARTS_COUNT)) {
                    starts += process.getMeasurement(ProcessHealthStats.MEASUREMENT_STARTS_COUNT);
                }
            }
            out.put(NanogramHealthMath.PROCESS_FOREGROUND_MS, foreground);
            out.put(NanogramHealthMath.PROCESS_STARTS, starts);
        }
    }

    private static void measurement(HealthStats stats, int key, String name, Map<String, Long> out) {
        if (stats.hasMeasurement(key)) {
            out.put(name, stats.getMeasurement(key));
        }
    }

    private static void timer(HealthStats stats, int key, String countName, String timeName, Map<String, Long> out) {
        if (stats.hasTimer(key)) {
            if (countName != null) {
                out.put(countName, (long) stats.getTimerCount(key));
            }
            out.put(timeName, stats.getTimerTime(key));
        }
    }

    // Names of wakelocks, jobs and syncs are not kept: only the sums.
    private static void timers(HealthStats stats, int key, String countName, String timeName, Map<String, Long> out) {
        if (stats.hasTimers(key)) {
            long count = 0;
            long time = 0;
            for (TimerStat timer : stats.getTimers(key).values()) {
                count += timer.getCount();
                time += timer.getTime();
            }
            out.put(countName, count);
            out.put(timeName, time);
        }
    }

    @RequiresApi(Build.VERSION_CODES.VANILLA_ICE_CREAM)
    private static boolean readEnergy(Context context, Map<String, Long> out, long[] elapsedOut) throws InterruptedException {
        SystemHealthManager manager = (SystemHealthManager) context.getSystemService(Context.SYSTEM_HEALTH_SERVICE);
        if (manager == null) {
            return false;
        }
        if (monitors == null) {
            final CountDownLatch supported = new CountDownLatch(1);
            final AtomicReference<List<PowerMonitor>> holder = new AtomicReference<>();
            manager.getSupportedPowerMonitors(Runnable::run, list -> {
                holder.set(list);
                supported.countDown();
            });
            if (!supported.await(ENERGY_TIMEOUT_MS, TimeUnit.MILLISECONDS)) {
                return false;
            }
            List<PowerMonitor> list = holder.get();
            monitors = list == null ? new ArrayList<>() : new ArrayList<>(list);
        }
        if (!NanogramHealthMath.powerMonitorsUsable(Build.VERSION.SDK_INT, monitors.size())) {
            return false;
        }
        final CountDownLatch done = new CountDownLatch(1);
        final PowerMonitorReadings[] readings = new PowerMonitorReadings[1];
        manager.getPowerMonitorReadings(monitors, Runnable::run, new android.os.OutcomeReceiver<PowerMonitorReadings, RuntimeException>() {
            @Override
            public void onResult(PowerMonitorReadings result) {
                readings[0] = result;
                done.countDown();
            }

            @Override
            public void onError(RuntimeException error) {
                done.countDown();
            }
        });
        if (!done.await(ENERGY_TIMEOUT_MS, TimeUnit.MILLISECONDS) || readings[0] == null) {
            return true;
        }
        long stamp = 0;
        for (PowerMonitor monitor : monitors) {
            long uws = readings[0].getConsumedEnergy(monitor);
            if (uws != PowerMonitorReadings.ENERGY_UNAVAILABLE) {
                String prefix = monitor.getType() == PowerMonitor.POWER_MONITOR_TYPE_MEASUREMENT ? NanogramHealthMath.RAIL_PREFIX : NanogramHealthMath.CONSUMER_PREFIX;
                out.put(prefix + monitor.getName(), uws);
                stamp = Math.max(stamp, readings[0].getTimestampMillis(monitor));
            }
        }
        if (stamp > 0) {
            elapsedOut[0] = stamp;
        }
        return true;
    }

    private static NanogramHealthMath.Battery readBattery(Context context) {
        int percent = -1;
        boolean charging = false;
        int temperature = NanogramHealthMath.Battery.UNKNOWN;
        long current = Long.MIN_VALUE;
        long chargeCounter = Long.MIN_VALUE;
        long energyCounter = Long.MIN_VALUE;
        int thermalStatus = -1;
        float headroom = Float.NaN;
        try {
            Intent sticky = context.registerReceiver(null, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
            if (sticky != null) {
                int level = sticky.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
                int scale = sticky.getIntExtra(BatteryManager.EXTRA_SCALE, -1);
                if (level >= 0 && scale > 0) {
                    percent = Math.round(level * 100f / scale);
                }
                int status = sticky.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
                charging = status == BatteryManager.BATTERY_STATUS_CHARGING || status == BatteryManager.BATTERY_STATUS_FULL;
                if (sticky.hasExtra(BatteryManager.EXTRA_TEMPERATURE)) {
                    temperature = sticky.getIntExtra(BatteryManager.EXTRA_TEMPERATURE, 0);
                }
            }
            BatteryManager battery = (BatteryManager) context.getSystemService(Context.BATTERY_SERVICE);
            if (battery != null) {
                current = battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW);
                chargeCounter = battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_CHARGE_COUNTER);
                energyCounter = battery.getLongProperty(BatteryManager.BATTERY_PROPERTY_ENERGY_COUNTER);
            }
            PowerManager power = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (power != null) {
                thermalStatus = power.getCurrentThermalStatus();
                headroom = power.getThermalHeadroom(10);
            }
        } catch (Throwable e) {
            FileLog.e(e);
        }
        return new NanogramHealthMath.Battery(percent, charging, temperature, current, chargeCounter, energyCounter, thermalStatus, headroom);
    }
}
