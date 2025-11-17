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

import com.qualcomm.robotcore.eventloop.opmode.LinearOpMode;
import com.qualcomm.robotcore.eventloop.opmode.TeleOp;
import com.qualcomm.robotcore.hardware.DcMotor;
import com.qualcomm.robotcore.util.ElapsedTime;
import com.qualcomm.robotcore.util.Range;

import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D;
import org.firstinspires.ftc.robotcore.external.navigation.Position;
import org.firstinspires.ftc.robotcore.external.navigation.YawPitchRollAngles;
import org.firstinspires.ftc.robotcore.external.navigation.AngleUnit;
import org.firstinspires.ftc.robotcore.external.navigation.DistanceUnit;

import java.util.List;

/**
 * TeleOp mode for controlling the robot with a Logitech gamepad.
 * Uses tank drive with left stick for forward/backward and right stick for turning.
 * Displays robot field position when AprilTags 20 or 24 are detected.
 */

@TeleOp(name="Robot TeleOp", group="TeleOp")
public class RobotTeleOp extends LinearOpMode {

    // Declare OpMode members
    private ElapsedTime runtime = new ElapsedTime();
    private DcMotor frontLeft = null;
    private DcMotor frontRight = null;
    private DcMotor backLeft = null;
    private DcMotor backRight = null;
    private Limelight3A limelight;

    // AprilTag field positions (in inches) - adjust these to match your field setup
    // Format: {x, y, heading_degrees}
    private static final double[] TAG_20_FIELD_POS = {0.0, 0.0, 0.0};    // Example: origin, facing forward
    private static final double[] TAG_24_FIELD_POS = {144.0, 72.0, 90.0}; // Example: 12ft right, 6ft forward, facing right
    private static final double INCHES_PER_METER = 39.3701;

    @Override
    public void runOpMode() {
        telemetry.addData("Status", "Initializing...");
        telemetry.update();

        // Initialize the hardware variables
        frontLeft  = hardwareMap.get(DcMotor.class, "frontLeft");
        frontRight = hardwareMap.get(DcMotor.class, "frontRight");
        backLeft   = hardwareMap.get(DcMotor.class, "backLeft");
        backRight  = hardwareMap.get(DcMotor.class, "backRight");

        // Set motor directions (same as AprilTagFollower)
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
        limelight.setPollRateHz(100);
        limelight.pipelineSwitch(0);
        limelight.start();

        telemetry.addData("Status", "Ready to start");
        telemetry.addData("Controls", "Left stick: drive, Right stick: turn");
        telemetry.update();

        // Wait for the game to start (driver presses START)
        waitForStart();
        runtime.reset();

        // Run until the end of the match (driver presses STOP)
        while (opModeIsActive()) {

            // Get AprilTag data from Limelight
            LLResult result = limelight.getLatestResult();
            boolean hasLocalization = false;
            double robotFieldX = 0, robotFieldY = 0, robotFieldHeading = 0;
            int detectedTagId = -1;

            if (result != null && result.isValid()) {
                List<LLResultTypes.FiducialResult> fiducials = result.getFiducialResults();
                if (fiducials != null && !fiducials.isEmpty()) {
                    for (LLResultTypes.FiducialResult fiducial : fiducials) {
                        int tagId = fiducial.getFiducialId();
                        if (tagId == 20 || tagId == 24) {
                            // Get robot pose relative to the tag
                            Pose3D robotPoseTagSpace = fiducial.getRobotPoseTargetSpace();
                            if (robotPoseTagSpace != null) {
                                // Convert robot position from tag space to field space
                                double[] tagFieldPos = (tagId == 20) ? TAG_20_FIELD_POS : TAG_24_FIELD_POS;
                                double[] robotPos = calculateRobotFieldPosition(
                                    robotPoseTagSpace,
                                    tagFieldPos[0], tagFieldPos[1], tagFieldPos[2]
                                );
                                robotFieldX = robotPos[0];
                                robotFieldY = robotPos[1];
                                robotFieldHeading = robotPos[2];
                                detectedTagId = tagId;
                                hasLocalization = true;
                                break;
                            }
                        }
                    }
                }
            }

            // POV Mode uses left stick to go forward/backward, and right stick to turn
            // Note: pushing stick forward gives negative values
            double drive = -gamepad1.left_stick_y;  // Forward/backward
            double turn  =  gamepad1.right_stick_x; // Left/right turn

            // Tank drive: left and right motor powers
            double leftPower  = Range.clip(drive + turn, -1.0, 1.0);
            double rightPower = Range.clip(drive - turn, -1.0, 1.0);

            // Send calculated power to wheels
            frontLeft.setPower(leftPower);
            backLeft.setPower(leftPower);
            frontRight.setPower(rightPower);
            backRight.setPower(rightPower);

            // Show the elapsed game time and wheel power
            telemetry.addData("Status", "Running");
            telemetry.addData("Runtime", "%.1f sec", runtime.seconds());
            telemetry.addData("Motors", "left (%.2f), right (%.2f)", leftPower, rightPower);
            telemetry.addData("Sticks", "drive (%.2f), turn (%.2f)", drive, turn);
            telemetry.addData("---", "---");
            
            if (hasLocalization) {
                telemetry.addData("Localization", "Tag %d detected", detectedTagId);
                telemetry.addData("Robot Field Pos", "X: %.1f\" Y: %.1f\"", robotFieldX, robotFieldY);
                telemetry.addData("Robot Heading", "%.1f degrees", robotFieldHeading);
            } else {
                telemetry.addData("Localization", "No tags 20/24 visible");
            }
            
            telemetry.update();
        }

        // Stop all motors when OpMode ends
        frontLeft.setPower(0);
        frontRight.setPower(0);
        backLeft.setPower(0);
        backRight.setPower(0);
        limelight.stop();

        telemetry.addData("Status", "Stopped");
        telemetry.update();
    }

    /**
     * Calculate robot position on the field from AprilTag detection
     * @param robotPoseTagSpace Robot pose relative to the tag
     * @param tagFieldX Tag X position on field (inches)
     * @param tagFieldY Tag Y position on field (inches)
     * @param tagFieldHeading Tag heading on field (degrees)
     * @return [robotFieldX, robotFieldY, robotFieldHeading]
     */
    private double[] calculateRobotFieldPosition(Pose3D robotPoseTagSpace, 
                                                  double tagFieldX, 
                                                  double tagFieldY, 
                                                  double tagFieldHeading) {
        // Get robot position relative to tag (in inches)
        double robotX = robotPoseTagSpace.getPosition().x * INCHES_PER_METER;
        double robotY = robotPoseTagSpace.getPosition().y * INCHES_PER_METER;
        double robotZ = robotPoseTagSpace.getPosition().z * INCHES_PER_METER;
        
        // Get robot orientation relative to tag
        YawPitchRollAngles angles = robotPoseTagSpace.getOrientation();
        double robotYaw = angles.getYaw(AngleUnit.DEGREES);
        
        // Transform robot position from tag space to field space
        double tagHeadingRad = Math.toRadians(tagFieldHeading);
        double cosTheta = Math.cos(tagHeadingRad);
        double sinTheta = Math.sin(tagHeadingRad);
        
        // Robot position in field coordinates
        // Tag faces forward (Z-axis), X is right, Y is up
        // Transform: rotate robot position by tag heading, then add tag position
        double robotFieldX = tagFieldX + (robotZ * cosTheta - robotX * sinTheta);
        double robotFieldY = tagFieldY + (robotZ * sinTheta + robotX * cosTheta);
        double robotFieldHeading = tagFieldHeading + robotYaw;
        
        // Normalize heading to -180 to 180
        while (robotFieldHeading > 180) robotFieldHeading -= 360;
        while (robotFieldHeading < -180) robotFieldHeading += 360;
        
        return new double[]{robotFieldX, robotFieldY, robotFieldHeading};
    }
}
