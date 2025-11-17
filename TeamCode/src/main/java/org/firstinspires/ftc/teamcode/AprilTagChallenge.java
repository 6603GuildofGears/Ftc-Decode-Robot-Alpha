package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.Range;
import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import com.qualcomm.robotcore.hardware.IMU;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D;
import java.util.List;

/**
 * TeleOp: AprilTag Challenge - performs different sequences for tags 21, 22, 23.
 * All actions are smooth and controlled, with resets if tag is lost for >20s.
 */
@TeleOp(name="AprilTag Challenge", group="TeleOp")
public class AprilTagChallenge extends LinearOpMode {
    // Hardware
    private DcMotor frontLeft, frontRight, backLeft, backRight;
    private Limelight3A limelight;
    private IMU imu;
    private boolean imuAvailable = false;
    private ElapsedTime runtime = new ElapsedTime();
    private double accumulatedHeading = 0.0; // Fallback heading when IMU unavailable

    // Tag IDs
    private static final int TAG_21 = 21;
    private static final int TAG_22 = 22;
    private static final int TAG_23 = 23;

    // Motion parameters
    private static final double TURN_GAIN = 0.025;
    private static final double MAX_TURN_SPEED = 0.35;
    private static final double MIN_TURN_POWER = 0.12;
    private static final double CENTER_TOLERANCE_DEG = 2.0;
    private static final double DRIVE_GAIN = 0.18; // Proportional gain for distance
    private static final double MAX_DRIVE_SPEED = 0.28;
    private static final double MIN_DRIVE_POWER = 0.12;
    private static final double MAX_TURN_SLEW_PER_SEC = 2.0;
    private static final double MAX_DRIVE_SLEW_PER_SEC = 2.0;

    // Target distances (inches)
    private static final double INCHES_PER_METER = 39.3701;
    private static final double TAG21_DIST = 36.0; // 3 feet
    private static final double TAG22_DIST = 36.0; // 3 feet
    private static final double TAG23_DIST = 48.0; // 4 feet

    // Tag lost timeout
    private static final double TAG_LOST_TIMEOUT = 20.0; // seconds

    // Smoothing state
    private double lastTurnPower = 0.0, lastDrivePower = 0.0;
    private long lastUpdateNanos = 0L;

    // State machine
    private enum ChallengeState {
        WAIT_FOR_TAG, CENTER_ON_TAG, PAUSE1, ACTION1, PAUSE2, ACTION2, PAUSE3, ACTION3, PAUSE4, ACTION4, COMPLETE, LOST
    }

    @Override
    public void runOpMode() {
        // Hardware init
        frontLeft  = hardwareMap.get(DcMotor.class, "frontLeft");
        frontRight = hardwareMap.get(DcMotor.class, "frontRight");
        backLeft   = hardwareMap.get(DcMotor.class, "backLeft");
        backRight  = hardwareMap.get(DcMotor.class, "backRight");
        frontLeft.setDirection(DcMotor.Direction.REVERSE);
        backLeft.setDirection(DcMotor.Direction.FORWARD);
        frontRight.setDirection(DcMotor.Direction.FORWARD);
        backRight.setDirection(DcMotor.Direction.FORWARD);
        frontLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        frontRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        backLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        backRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        // IMU init (BHI260AP on newer Control Hubs)
        try {
            imu = hardwareMap.get(IMU.class, "imu");
            imu.resetYaw();
            imuAvailable = true;
            telemetry.addLine("IMU (BHI260AP) initialized successfully");
        } catch (Exception e) {
            imuAvailable = false;
            telemetry.addLine("IMU not available - using fallback heading");
            telemetry.addData("IMU Error", e.getMessage());
        }

        limelight = hardwareMap.get(Limelight3A.class, "limelight");
        limelight.setPollRateHz(100);
        limelight.pipelineSwitch(0);
        limelight.start();

        telemetry.addLine("AprilTag Challenge Ready");
        telemetry.update();
        waitForStart();
        runtime.reset();

        int lastTagId = -1;
        double lastTagSeenTime = runtime.seconds();
        ChallengeState state = ChallengeState.WAIT_FOR_TAG;
        int currentTagId = -1;
        double stateStartTime = runtime.seconds();
        double actionStartHeading = 0;
        double actionStartX = 0;
        double actionStartY = 0;
        double actionTarget = 0;

        while (opModeIsActive()) {
            // Get tag info
            LLResult result = limelight.getLatestResult();
            boolean hasTag = false;
            int seenTagId = -1;
            double tagX = 0, tagY = 0, tagZ = 0;
            double tagXDeg = 0, tagYDeg = 0;
            if (result != null && result.isValid()) {
                List<LLResultTypes.FiducialResult> fiducials = result.getFiducialResults();
                if (fiducials != null && !fiducials.isEmpty()) {
                    for (LLResultTypes.FiducialResult f : fiducials) {
                        int id = f.getFiducialId();
                        if (id == TAG_21 || id == TAG_22 || id == TAG_23) {
                            hasTag = true;
                            seenTagId = id;
                            tagXDeg = f.getTargetXDegrees(); // degrees, horizontal offset
                            tagYDeg = f.getTargetYDegrees(); // degrees, vertical offset
                            // Get 3D position (robot pose in tag space)
                            Pose3D robotPose = f.getRobotPoseTargetSpace();
                            if (robotPose != null) {
                                tagX = robotPose.getPosition().x; // meters, left/right
                                tagY = robotPose.getPosition().y; // meters, up/down
                                tagZ = robotPose.getPosition().z; // meters, forward (distance)
                            }
                            break;
                        }
                    }
                }
            }

            // Tag lost logic
            if (hasTag) {
                lastTagSeenTime = runtime.seconds();
                lastTagId = seenTagId;
            }
            boolean tagLostTooLong = (runtime.seconds() - lastTagSeenTime) > TAG_LOST_TIMEOUT;

            // State machine
            if (tagLostTooLong) {
                state = ChallengeState.LOST;
            }

            switch (state) {
                case WAIT_FOR_TAG:
                    if (hasTag) {
                        currentTagId = seenTagId;
                        state = ChallengeState.CENTER_ON_TAG;
                        stateStartTime = runtime.seconds();
                    }
                    break;
                case CENTER_ON_TAG: {
                    double targetDist = (currentTagId == TAG_21) ? TAG21_DIST : (currentTagId == TAG_22) ? TAG22_DIST : TAG23_DIST;
                    boolean centered = centerOnTag(tagXDeg, tagZ, targetDist, telemetry);
                    if (centered) {
                        state = ChallengeState.PAUSE1;
                        stateStartTime = runtime.seconds();
                    }
                    break;
                }
                case PAUSE1:
                    stopAll();
                    if (runtime.seconds() - stateStartTime > 2.0) {
                        state = ChallengeState.ACTION1;
                        stateStartTime = runtime.seconds();
                        actionStartHeading = getHeading();
                        actionStartX = tagX;
                        actionStartY = tagZ;
                    }
                    break;
                case ACTION1:
                    if (currentTagId == TAG_21) {
                        // 360 spin
                        boolean done = spinToHeading(actionStartHeading, 360, telemetry);
                        if (done) { state = ChallengeState.PAUSE2; stateStartTime = runtime.seconds(); }
                    } else if (currentTagId == TAG_22) {
                        // Move back 1 foot
                        boolean done = moveInches(-12, telemetry);
                        if (done) { state = ChallengeState.PAUSE2; stateStartTime = runtime.seconds(); }
                    } else if (currentTagId == TAG_23) {
                        // Turn right 15 deg
                        boolean done = spinToHeading(actionStartHeading, 15, telemetry);
                        if (done) { state = ChallengeState.PAUSE2; stateStartTime = runtime.seconds(); }
                    }
                    break;
                case PAUSE2:
                    stopAll();
                    if (runtime.seconds() - stateStartTime > 2.0) {
                        state = ChallengeState.ACTION2;
                        stateStartTime = runtime.seconds();
                        actionStartHeading = getHeading();
                        actionStartX = tagX;
                        actionStartY = tagZ;
                    }
                    break;
                case ACTION2:
                    if (currentTagId == TAG_21) {
                        // Move right 1 foot
                        boolean done = strafeInches(12, telemetry);
                        if (done) { state = ChallengeState.PAUSE3; stateStartTime = runtime.seconds(); }
                    } else if (currentTagId == TAG_22) {
                        // Move forward 2 feet
                        boolean done = moveInches(24, telemetry);
                        if (done) { state = ChallengeState.PAUSE3; stateStartTime = runtime.seconds(); }
                    } else if (currentTagId == TAG_23) {
                        // Turn left 30 deg
                        boolean done = spinToHeading(actionStartHeading, -30, telemetry);
                        if (done) { state = ChallengeState.PAUSE3; stateStartTime = runtime.seconds(); }
                    }
                    break;
                case PAUSE3:
                    stopAll();
                    if (runtime.seconds() - stateStartTime > 2.0) {
                        state = ChallengeState.ACTION3;
                        stateStartTime = runtime.seconds();
                        actionStartHeading = getHeading();
                        actionStartX = tagX;
                        actionStartY = tagZ;
                    }
                    break;
                case ACTION3:
                    if (currentTagId == TAG_21) {
                        // Move left 2 feet
                        boolean done = strafeInches(-24, telemetry);
                        if (done) { state = ChallengeState.PAUSE4; stateStartTime = runtime.seconds(); }
                    } else if (currentTagId == TAG_22) {
                        // Move back 1 foot
                        boolean done = moveInches(-12, telemetry);
                        if (done) { state = ChallengeState.PAUSE4; stateStartTime = runtime.seconds(); }
                    } else if (currentTagId == TAG_23) {
                        // Turn right 15 deg
                        boolean done = spinToHeading(actionStartHeading, 15, telemetry);
                        if (done) { state = ChallengeState.PAUSE4; stateStartTime = runtime.seconds(); }
                    }
                    break;
                case PAUSE4:
                    stopAll();
                    if (runtime.seconds() - stateStartTime > 2.0) {
                        state = ChallengeState.ACTION4;
                        stateStartTime = runtime.seconds();
                        actionStartHeading = getHeading();
                        actionStartX = tagX;
                        actionStartY = tagZ;
                    }
                    break;
                case ACTION4:
                    if (currentTagId == TAG_21) {
                        // Move right 1 foot (center on tag)
                        boolean done = strafeInches(12, telemetry);
                        if (done) { state = ChallengeState.COMPLETE; stateStartTime = runtime.seconds(); }
                    } else if (currentTagId == TAG_22) {
                        // 360 spin
                        boolean done = spinToHeading(actionStartHeading, 360, telemetry);
                        if (done) { state = ChallengeState.COMPLETE; stateStartTime = runtime.seconds(); }
                    } else if (currentTagId == TAG_23) {
                        // Center on tag at 4 feet
                        boolean centered = centerOnTag(tagXDeg, tagZ, TAG23_DIST, telemetry);
                        if (centered) { state = ChallengeState.COMPLETE; stateStartTime = runtime.seconds(); }
                    }
                    break;
                case COMPLETE:
                    stopAll();
                    telemetry.addLine("Challenge complete! Waiting for new tag...");
                    if (hasTag && seenTagId != currentTagId) {
                        state = ChallengeState.WAIT_FOR_TAG;
                        stateStartTime = runtime.seconds();
                    }
                    break;
                case LOST:
                default:
                    stopAll();
                    telemetry.addLine("Tag lost for >20s. Waiting for new tag...");
                    if (hasTag) {
                        state = ChallengeState.WAIT_FOR_TAG;
                        stateStartTime = runtime.seconds();
                    }
                    break;
            }

            telemetry.addData("State", state);
            telemetry.addData("CurrentTagId", currentTagId);
            telemetry.addData("TagSeen", hasTag ? seenTagId : -1);
            telemetry.update();
            sleep(20);
        }
        stopAll();
        limelight.stop();
    }

    // --- Helper methods ---
    // Center on tag at a given distance (inches)
    private boolean centerOnTag(double tagXDeg, double tagZ, double targetDistIn, org.firstinspires.ftc.robotcore.external.Telemetry telemetry) {
        double targetDistM = targetDistIn / INCHES_PER_METER;
        double distError = tagZ - targetDistM;
        double drive = Range.clip(distError * DRIVE_GAIN, -MAX_DRIVE_SPEED, MAX_DRIVE_SPEED);
        if (Math.abs(drive) > 0 && Math.abs(drive) < MIN_DRIVE_POWER) drive = Math.signum(drive) * MIN_DRIVE_POWER;
        double turn = Range.clip(-tagXDeg * TURN_GAIN, -MAX_TURN_SPEED, MAX_TURN_SPEED);
        if (Math.abs(turn) > 0 && Math.abs(turn) < MIN_TURN_POWER) turn = Math.signum(turn) * MIN_TURN_POWER;
        // Slew-rate limit (optional, can be added for extra smoothness)
        setDrivePower(drive, turn);
        telemetry.addData("CenterOnTag", "distErr=%.2f in, drive=%.2f, turn=%.2f", distError*INCHES_PER_METER, drive, turn);
        return Math.abs(distError*INCHES_PER_METER) < 1.5 && Math.abs(tagXDeg) < CENTER_TOLERANCE_DEG;
    }

    // Move forward/backward a given number of inches (relative, not field-centric)
    private boolean moveInches(double inches, org.firstinspires.ftc.robotcore.external.Telemetry telemetry) {
        double power = Range.clip(inches > 0 ? MAX_DRIVE_SPEED : -MAX_DRIVE_SPEED, -MAX_DRIVE_SPEED, MAX_DRIVE_SPEED);
        setDrivePower(power, 0);
        telemetry.addData("MoveInches", "target=%.1f", inches);
        // For demo: just run for a fixed time (1 foot = ~0.5s at 0.25 power)
        sleep((long)(Math.abs(inches) * 40));
        setDrivePower(0, 0);
        return true;
    }

    // Strafe right/left a given number of inches (positive=right, negative=left)
    private boolean strafeInches(double inches, org.firstinspires.ftc.robotcore.external.Telemetry telemetry) {
        // For tank drive, simulate with turn+drive (not true strafe)
        // For mecanum, you would set left/right motors differently
        // Here, just run left/right motors in opposite directions for a short time
        double power = Range.clip(inches > 0 ? MAX_DRIVE_SPEED : -MAX_DRIVE_SPEED, -MAX_DRIVE_SPEED, MAX_DRIVE_SPEED);
        frontLeft.setPower(-power);
        backLeft.setPower(power);
        frontRight.setPower(power);
        backRight.setPower(-power);
        telemetry.addData("StrafeInches", "target=%.1f", inches);
        sleep((long)(Math.abs(inches) * 40));
        setDrivePower(0, 0);
        return true;
    }

    // Spin in place by a given number of degrees (relative to start heading)
    private boolean spinToHeading(double startHeading, double deltaDeg, org.firstinspires.ftc.robotcore.external.Telemetry telemetry) {
        double targetHeading = startHeading + deltaDeg;
        double currentHeading = getHeading();
        double error = targetHeading - currentHeading;
        double turn = Range.clip(error * 0.012, -MAX_TURN_SPEED, MAX_TURN_SPEED);
        setDrivePower(0, turn);
        
        // Update accumulated heading if IMU not available (approximate)
        if (!imuAvailable) {
            accumulatedHeading += turn * 0.5; // Rough estimate of heading change per cycle
        }
        
        telemetry.addData("SpinToHeading", "target=%.1f, curr=%.1f, err=%.1f", targetHeading, currentHeading, error);
        telemetry.addData("IMU", imuAvailable ? "Active" : "Fallback mode");
        if (Math.abs(error) < 3.0) {
            setDrivePower(0, 0);
            return true;
        }
        return false;
    }

    // Set drive and turn power (tank drive)
    private void setDrivePower(double drive, double turn) {
        double left = drive - turn;
        double right = drive + turn;
        frontLeft.setPower(left);
        backLeft.setPower(left);
        frontRight.setPower(right);
        backRight.setPower(right);
    }

    // Stop all motors
    private void stopAll() {
        setDrivePower(0, 0);
    }

    // IMU heading (yaw, in degrees, -180 to 180)
    private double getHeading() {
        if (imuAvailable) {
            YawPitchRollAngles angles = imu.getRobotYawPitchRollAngles();
            return angles.getYaw(AngleUnit.DEGREES);
        } else {
            // Fallback: use accumulated heading (not as accurate, but works)
            return accumulatedHeading;
        }
    }
}
