/* Copyright (c) 2021 FIRST. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without modification,
 * are permitted (subject to the limitations in the disclaimer below) provided that
 * the following conditions are met:
 *
 * Redistributions of source code must retain the above copyright notice, this list
 * of conditions and the following disclaimer.
 *
 * Redistribution in binary form must reproduce the above copyright notice, this
 * list of conditions and the following disclaimer in the documentation and/or
 * other materials provided with the distribution.
 *
 * Neither the name of FIRST nor the names of its contributors may be used to endorse or
 * promote products derived from this software without specific prior written permission.
 *
 * NO EXPRESS OR IMPLIED LICENSES TO ANY PARTY'S PATENT RIGHTS ARE GRANTED BY THIS
 * LICENSE. THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO,
 * THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.firstinspires.ftc.teamcode;

import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.Range;

import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;

import java.util.List;

/**
 * TeleOp: Uses the Limelight camera to detect AprilTag 21 and rotate to keep it centered.
 * The robot rotates left/right to keep the tag horizontally centered in the camera view.
 * It does NOT drive forward; it only rotates in place while the user has this TeleOp running.
 */

@TeleOp(name="AprilTag Center Tag 21", group="TeleOp")
public class AprilTagFollower extends LinearOpMode {

    // Declare OpMode members
    private ElapsedTime runtime = new ElapsedTime();
    private DcMotor frontLeft = null;
    private DcMotor frontRight = null;
    private DcMotor backLeft = null;
    private DcMotor backRight = null;
    private Limelight3A limelight;

    // AprilTag following parameters
    private static final int TARGET_TAG_ID = 21;
    private static final int SHY_TAG_ID_1 = 22;
    private static final int SHY_TAG_ID_2 = 23;
    private static final double TURN_GAIN = 0.025;       // Slightly lower gain to reduce aggressiveness
    private static final double MAX_TURN_SPEED = 0.35;   // Lower max turn to avoid dramatic movements
    private static final double MIN_TURN_POWER = 0.12;   // Minimum power to overcome motor friction
    private static final double CENTER_TOLERANCE = 2.0;  // Degrees - how close to center is "centered"
    // Forward drive parameters (for approaching tag 21)
    private static final double DRIVE_SPEED = 0.25;      // Slow approach speed
    private static final double MIN_DRIVE_POWER = 0.12;  // Minimum drive power to overcome friction
    private static final double MAX_DRIVE_SLEW_PER_SEC = 2.0; // Slew rate for drive smoothing
    // Slew-rate limiting to smooth sudden changes in turn power (units: power per second)
    private static final double MAX_TURN_SLEW_PER_SEC = 2.0; // e.g., change by at most 0.04 per 20ms

    // Smoothing state
    private double lastTurnPower = 0.0;
    private double lastDrivePower = 0.0;
    private long lastUpdateNanos = 0L;

    @Override
    public void runOpMode() {
        telemetry.addData("Status", "Initializing...");
        telemetry.update();

        // Initialize the hardware variables
        frontLeft  = hardwareMap.get(DcMotor.class, "frontLeft");
        frontRight = hardwareMap.get(DcMotor.class, "frontRight");
        backLeft  = hardwareMap.get(DcMotor.class, "backLeft");
        backRight = hardwareMap.get(DcMotor.class, "backRight");

        // Set motor directions
        frontLeft.setDirection(DcMotor.Direction.REVERSE);
        backLeft.setDirection(DcMotor.Direction.FORWARD);
        frontRight.setDirection(DcMotor.Direction.FORWARD);
        backRight.setDirection(DcMotor.Direction.FORWARD);

        // Set motors to brake when power is zero
        frontLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        frontRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        backLeft.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);
        backRight.setZeroPowerBehavior(DcMotor.ZeroPowerBehavior.BRAKE);

        // Initialize the Limelight
        limelight = hardwareMap.get(Limelight3A.class, "limelight");
        limelight.setPollRateHz(100); // Poll 100 times per second
        limelight.pipelineSwitch(0);  // Switch to pipeline 0 (AprilTag detection)
        limelight.start(); // Start polling for data

        telemetry.addData("Status", "Ready to start");
        telemetry.addData("Target", "AprilTag ID %d", TARGET_TAG_ID);
        telemetry.update();

        // Wait for the game to start (driver presses START)
        waitForStart();
        runtime.reset();

        // Run until the end of the match (driver presses STOP)
        while (opModeIsActive()) {

            // Get the latest result from Limelight
            LLResult result = limelight.getLatestResult();

            boolean hasTag = false;
            int seenTagId = -1;
            double targetX = 0; // Horizontal offset in degrees for the chosen tag

            // Check if we have valid data
            if (result != null && result.isValid()) {
                // Get AprilTag results
                List<LLResultTypes.FiducialResult> fiducials = result.getFiducialResults();

                if (fiducials != null && !fiducials.isEmpty()) {
                    // Prefer target tag 21. If not present, react to 22/23 as "shy" (turn away).
                    LLResultTypes.FiducialResult choice = null;
                    for (LLResultTypes.FiducialResult f : fiducials) {
                        if (f.getFiducialId() == TARGET_TAG_ID) { choice = f; break; }
                    }
                    if (choice == null) {
                        for (LLResultTypes.FiducialResult f : fiducials) {
                            if (f.getFiducialId() == SHY_TAG_ID_1 || f.getFiducialId() == SHY_TAG_ID_2) { choice = f; break; }
                        }
                    }
                    if (choice != null) {
                        hasTag = true;
                        seenTagId = choice.getFiducialId();
                        targetX = choice.getTargetXDegrees();
                        telemetry.addData("Status", "Tag %d FOUND", seenTagId);
                        telemetry.addData("Horizontal Offset", "%.2f degrees", targetX);
                    }
                }
            }

            // Control logic - turn to center the tag and drive forward if tag 21
            double turnPower = 0;
            double drivePower = 0;

            if (hasTag) {
                // Determine behavior: normal (center) for tag 21; shy (turn away) for 22/23
                boolean shy = (seenTagId == SHY_TAG_ID_1 || seenTagId == SHY_TAG_ID_2);

                // Compute base turn from horizontal offset. Negative targetX => tag left.
                // For correct physical direction with current motor mapping, use negative sign.
                double desiredTurn = (-targetX) * TURN_GAIN;     // normal behavior = center the tag
                if (shy) desiredTurn = -desiredTurn;              // shy behavior = turn away from the tag

                // If close to centered and not shy, stop turning and drive forward (tag 21 only)
                if (!shy && Math.abs(targetX) < CENTER_TOLERANCE) {
                    desiredTurn = 0;
                    if (seenTagId == TARGET_TAG_ID) {
                        drivePower = DRIVE_SPEED; // Drive toward tag 21
                        telemetry.addData("Action", "CENTERED - Approaching tag!");
                    } else {
                        telemetry.addData("Action", "CENTERED!");
                    }
                } else {
                    if (shy) {
                        telemetry.addData("Action", targetX < 0 ? "Shy: Turning RIGHT away" : "Shy: Turning LEFT away");
                    } else {
                        telemetry.addData("Action", targetX < 0 ? "Turning LEFT to center" : "Turning RIGHT to center");
                    }
                }

                // Clip to max turn speed
                desiredTurn = Range.clip(desiredTurn, -MAX_TURN_SPEED, MAX_TURN_SPEED);

                // Slew-rate limit to smooth sudden changes
                long now = System.nanoTime();
                if (lastUpdateNanos == 0L) lastUpdateNanos = now; // initialize
                double dtSec = Math.max(1e-3, (now - lastUpdateNanos) / 1.0e9);
                double maxStep = MAX_TURN_SLEW_PER_SEC * dtSec;
                double delta = desiredTurn - lastTurnPower;
                if (delta > maxStep) delta = maxStep;
                if (delta < -maxStep) delta = -maxStep;
                turnPower = lastTurnPower + delta;
                lastTurnPower = turnPower;
                lastUpdateNanos = now;

                // Apply minimum power threshold (dead-zone)
                if (Math.abs(turnPower) > 0 && Math.abs(turnPower) < MIN_TURN_POWER) {
                    turnPower = Math.signum(turnPower) * MIN_TURN_POWER;
                }

                // Smooth drive power changes
                double deltaDrive = drivePower - lastDrivePower;
                double maxDriveStep = MAX_DRIVE_SLEW_PER_SEC * dtSec;
                if (deltaDrive > maxDriveStep) deltaDrive = maxDriveStep;
                if (deltaDrive < -maxDriveStep) deltaDrive = -maxDriveStep;
                drivePower = lastDrivePower + deltaDrive;
                lastDrivePower = drivePower;

                // Apply minimum drive power threshold
                if (Math.abs(drivePower) > 0 && Math.abs(drivePower) < MIN_DRIVE_POWER) {
                    drivePower = Math.signum(drivePower) * MIN_DRIVE_POWER;
                }

            } else {
                // Tag is not visible
                // Gently slew back to 0 to avoid sudden stop jerk
                long now = System.nanoTime();
                if (lastUpdateNanos == 0L) lastUpdateNanos = now;
                double dtSec = Math.max(1e-3, (now - lastUpdateNanos) / 1.0e9);
                double maxStep = MAX_TURN_SLEW_PER_SEC * dtSec;
                double deltaToZero = -lastTurnPower;
                if (deltaToZero > maxStep) deltaToZero = maxStep;
                if (deltaToZero < -maxStep) deltaToZero = -maxStep;
                turnPower = lastTurnPower + deltaToZero;
                lastTurnPower = turnPower;
                lastUpdateNanos = now;

                // When coasting to stop, snap to zero if below threshold to avoid stall
                if (Math.abs(turnPower) < MIN_TURN_POWER) {
                    turnPower = 0;
                    lastTurnPower = 0;
                }

                // Also coast drive power to zero
                double deltaDriveToZero = -lastDrivePower;
                double maxDriveStep = MAX_DRIVE_SLEW_PER_SEC * dtSec;
                if (deltaDriveToZero > maxDriveStep) deltaDriveToZero = maxDriveStep;
                if (deltaDriveToZero < -maxDriveStep) deltaDriveToZero = -maxDriveStep;
                drivePower = lastDrivePower + deltaDriveToZero;
                lastDrivePower = drivePower;

                if (Math.abs(drivePower) < MIN_DRIVE_POWER) {
                    drivePower = 0;
                    lastDrivePower = 0;
                }

                telemetry.addData("Status", "Searching for Tag %d...", TARGET_TAG_ID);
                telemetry.addData("Action", (Math.abs(turnPower) > 1e-3 || Math.abs(drivePower) > 1e-3) ? "Coasting to stop" : "Stopped");
            }

            // Send power to wheels (tank drive: combine forward motion and rotation)
            // drivePower is positive forward; turnPower rotates (negative = left side backward)
            double leftPower = drivePower - turnPower;
            double rightPower = drivePower + turnPower;
            frontLeft.setPower(leftPower);
            backLeft.setPower(leftPower);
            frontRight.setPower(rightPower);
            backRight.setPower(rightPower);

            telemetry.addData("Motor Power", "L: %.2f, R: %.2f", leftPower, rightPower);
            telemetry.addData("Drive Power", "%.3f", drivePower);
            telemetry.addData("Turn Power", "%.3f", turnPower);
            telemetry.addData("Runtime", "%.1f sec", runtime.seconds());
            telemetry.update();

            // Small delay to prevent overwhelming the system
            sleep(20);
        }

        // Stop all motion
        stopMotors();
        limelight.stop();
        
        telemetry.addData("Status", "OpMode Stopped");
        telemetry.update();
    }

    /**
     * Stop all motors
     */
    private void stopMotors() {
        frontLeft.setPower(0);
        frontRight.setPower(0);
        backLeft.setPower(0);
        backRight.setPower(0);
    }
}
