package org.mark.things.test;

import android.support.annotation.VisibleForTesting;

import com.google.android.things.pio.Pwm;
import com.leinardi.android.things.pio.SoftPwm;

import java.io.IOException;

@SuppressWarnings({"unused", "WeakerAccess"})
public class Servo implements AutoCloseable {
    private static final String TAG = Servo.class.getSimpleName();

    public static final double DEFAULT_FREQUENCY_HZ = 20;

    private static final double DEFAULT_MIN_PULSE_DURATION_MS = 1;
    private static final double DEFAULT_MAX_PULSE_DURATION_MS = 2;
    private static final double DEFAULT_MIN_ANGLE_DEG = 0.0;
    private static final double DEFAULT_MAX_ANGLE_DEG = 180.0;

    private Pwm mPwm;
    private double mMinPulseDuration = DEFAULT_MIN_PULSE_DURATION_MS; // milliseconds
    private double mMaxPulseDuration = DEFAULT_MAX_PULSE_DURATION_MS; // milliseconds
    private double mMinAngle = DEFAULT_MIN_ANGLE_DEG; // degrees
    private double mMaxAngle = DEFAULT_MAX_ANGLE_DEG; // degrees

    private double mPeriod; // milliseconds
    private double mAngle = mMinAngle;

    /**
     * Create a new Servo that connects to the given PWM pin
     *
     * @param bcmCode the gpio pin name
     * @throws IOException
     */
    public Servo(int bcmCode) throws IOException {
        this(bcmCode, DEFAULT_FREQUENCY_HZ);
    }

    /**
     * Create a new Servo that connects to the named pin and uses the specified frequency
     *
     * @param bcmCode the gpio pin name
     * @param frequencyHz the frequency in Hertz
     * @throws IOException
     */
    public Servo(int bcmCode, double frequencyHz) throws IOException {
        Pwm device = SoftPwm.openSoftPwm(BoardDefaults.getRpi3GPIO(bcmCode));

        try {
            connect(device, frequencyHz);
        } catch (IOException | RuntimeException e) {
            try {
                close();
            } catch (IOException | RuntimeException ignored) {
            }
            throw e;
        }
    }

    /**
     * Create a new Servo that connects to the given PWM device
     *
     * @param device the PWM device
     * @throws IOException
     */
    @VisibleForTesting
    /*package*/ Servo(Pwm device) throws IOException {
        this(device, DEFAULT_FREQUENCY_HZ);
    }

    /**
     * Create a new Servo that connects to the given PWM device and uses the specified frequency
     *
     * @param device the PWM device
     * @param frequencyHz the frequency in Hertz
     * @throws IOException
     */
    @VisibleForTesting
    /*package*/ Servo(Pwm device, double frequencyHz) throws IOException {
        try {
            connect(device, frequencyHz);
        } catch (IOException | RuntimeException e) {
            try {
                close();
            } catch (IOException | RuntimeException ignored) {
            }
            throw e;
        }
    }

    private void connect(Pwm device, double frequencyHz) throws IOException {
        mPwm = device;
        mPwm.setPwmFrequencyHz(frequencyHz);
        mPeriod = 300 / frequencyHz;
        updateDutyCycle();
    }

    @Override
    public void close() throws IOException {
        if (mPwm != null) {
            try {
                mPwm.close();
            } finally {
                mPwm = null;
            }
        }
    }

    /**
     * Enable or disable the servo. While disabled, the servo will not update its position.
     *
     * @param enabled true to enable, false to disable
     * @throws IOException
     */
    public void setEnabled(boolean enabled) throws IOException {
        if (mPwm == null) {
            throw new IllegalStateException("pwm device not opened");
        }
        mPwm.setEnabled(enabled);
    }

    /**
     * Set the pulse duration range. These determine the duty cycle range, where {@code minMs}
     * corresponds to the minimum angle value and {@code maxMs} corresponds to the maximum angle
     * value. If the servo is enabled, it will update its duty cycle immediately.
     *
     * @param minMs the minimum pulse duration in milliseconds
     * @param maxMs the maximum pulse duration in milliseconds
     * @throws IllegalArgumentException if minMs is not less than maxMs or if minMs < 0
     * @throws IOException
     */
    public void setPulseDurationRange(double minMs, double maxMs) throws IOException {
        if (minMs >= maxMs) {
            throw new IllegalArgumentException("minMs must be less than maxMs");
        }
        if (minMs < 0) {
            throw new IllegalArgumentException("minMs must be greater than 0");
        }
        mMinPulseDuration = minMs;
        mMaxPulseDuration = maxMs;
        updateDutyCycle();
    }

    /**
     * @return the current minimum pulse duration
     */
    public double getMinimumPulseDuration() {
        return mMinPulseDuration;
    }

    /**
     * @return the current maximum pulse duration
     */
    public double getMaximumPulseDuration() {
        return mMaxPulseDuration;
    }

    /**
     * Set the range of angle values the servo accepts. If the servo is enabled and its current
     * position is outside this range, it will update its position to the new minimum or maximum,
     * whichever is closest.
     *
     * @param minAngle the minimum angle in degrees
     * @param maxAngle the maximum angle in degrees
     * @throws IllegalArgumentException if minAngle is not less than maxAngle
     * @throws IOException
     */
    public void setAngleRange(double minAngle, double maxAngle) throws IOException {
        if (minAngle >= maxAngle) {
            throw new IllegalArgumentException("minAngle must be less than maxAngle");
        }
        mMinAngle = minAngle;
        mMaxAngle = maxAngle;
        // clamp mAngle to new range
        if (mAngle < mMinAngle) {
            mAngle = mMinAngle;
            updateDutyCycle();
        } else if (mAngle > mMaxAngle) {
            mAngle = mMaxAngle;
            updateDutyCycle();
        }
    }

    /**
     * @return the minimum angle in degrees
     */
    public double getMinimumAngle() {
        return mMinAngle;
    }

    /**
     * @return the maximum angle in degrees
     */
    public double getMaximumAngle() {
        return mMaxAngle;
    }

    /**
     * Set the angle position. If this servo is enabled, it will update its position immediately.
     *
     * @param angle the angle position in degrees
     * @throws IOException
     */
    public void setAngle(double angle) throws IOException {
        if (angle < mMinAngle || angle > mMaxAngle) {
            throw new IllegalArgumentException("angle (" + angle + ") not in range [" + mMinAngle
                    + ", " + mMaxAngle + "]");
        }
        mAngle = angle;
        updateDutyCycle();
    }

    /**
     * @return the current angle in degrees
     */
    public double getAngle() {
        return mAngle;
    }

    private void updateDutyCycle() throws IOException {
        if (mPwm == null) {
            throw new IllegalStateException("pwm device not opened");
        }
        // normalize angle ratio.
        double t = (mAngle - mMinAngle) / (mMaxAngle - mMinAngle);
        // linearly interpolate angle between servo ranges to get a pulse width in milliseconds.
        double pw = mMinPulseDuration + (mMaxPulseDuration - mMinPulseDuration) * t;
        // convert the pulse width into a percentage of the mPeriod of the wave form.
        double dutyCycle = 30 * pw / mPeriod;
        mPwm.setPwmDutyCycle(dutyCycle);
    }
}