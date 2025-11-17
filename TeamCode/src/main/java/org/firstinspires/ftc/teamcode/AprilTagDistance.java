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
import com.qualcomm.robotcore.util.ElapsedTime;

import com.qualcomm.hardware.limelightvision.Limelight3A;
import com.qualcomm.hardware.limelightvision.LLResult;
import com.qualcomm.hardware.limelightvision.LLResultTypes;
import org.firstinspires.ftc.robotcore.external.navigation.Pose3D;

import java.util.List;

/**
 * TeleOp: Display AprilTag distance and offset measurements.
 * Robot remains stationary while showing distance (forward) and lateral offset (left/right)
 * for any detected AprilTag in inches.
 */

@TeleOp(name="AprilTag Distance Display", group="TeleOp")
public class AprilTagDistance extends LinearOpMode {

    private Limelight3A limelight;
    private ElapsedTime runtime = new ElapsedTime();
    
    private static final double INCHES_PER_METER = 39.3701;

    @Override
    public void runOpMode() {
        telemetry.addData("Status", "Initializing...");
        telemetry.update();

        // Initialize the Limelight
        limelight = hardwareMap.get(Limelight3A.class, "limelight");
        limelight.setPollRateHz(100); // Poll 100 times per second
        limelight.pipelineSwitch(0);  // Switch to pipeline 0 (AprilTag detection)
        limelight.start(); // Start polling for data

        telemetry.addData("Status", "Ready - Robot will NOT move");
        telemetry.addData("Info", "Displays distance to AprilTags in inches");
        telemetry.update();

        // Wait for the game to start (driver presses START)
        waitForStart();
        runtime.reset();

        // Run until the end of the match (driver presses STOP)
        while (opModeIsActive()) {

            // Get the latest result from Limelight
            LLResult result = limelight.getLatestResult();

            telemetry.addData("Runtime", "%.1f sec", runtime.seconds());
            telemetry.addData("---", "---");

            // Check if we have valid data
            if (result != null && result.isValid()) {
                // Get AprilTag results
                List<LLResultTypes.FiducialResult> fiducials = result.getFiducialResults();

                if (fiducials != null && !fiducials.isEmpty()) {
                    telemetry.addData("AprilTags Detected", fiducials.size());
                    telemetry.addData("---", "---");

                    // Display info for each detected AprilTag
                    for (LLResultTypes.FiducialResult fiducial : fiducials) {
                        int tagId = fiducial.getFiducialId();
                        
                        // Get angular offset in degrees
                        double targetXDeg = fiducial.getTargetXDegrees(); // horizontal angle offset
                        double targetYDeg = fiducial.getTargetYDegrees(); // vertical angle offset

                        // Get 3D position (robot pose in tag space)
                        Pose3D robotPose = fiducial.getRobotPoseTargetSpace();
                        
                        telemetry.addData("Tag ID", tagId);
                        telemetry.addData("  Angle Offset", "Horiz: %.2f°, Vert: %.2f°", targetXDeg, targetYDeg);
                        
                        if (robotPose != null) {
                            // Convert meters to inches
                            double xInches = robotPose.getPosition().x * INCHES_PER_METER; // left(-)/right(+)
                            double yInches = robotPose.getPosition().y * INCHES_PER_METER; // down(-)/up(+)
                            double zInches = robotPose.getPosition().z * INCHES_PER_METER; // distance forward
                            
                            telemetry.addData("  Distance Forward", "%.2f inches", zInches);
                            telemetry.addData("  Lateral Offset", "%.2f inches %s", 
                                Math.abs(xInches), xInches > 0 ? "(RIGHT)" : xInches < 0 ? "(LEFT)" : "(CENTERED)");
                            telemetry.addData("  Vertical Offset", "%.2f inches %s", 
                                Math.abs(yInches), yInches > 0 ? "(ABOVE)" : yInches < 0 ? "(BELOW)" : "(LEVEL)");
                            telemetry.addData("  3D Position", "X:%.1f Y:%.1f Z:%.1f in", xInches, yInches, zInches);
                        } else {
                            telemetry.addData("  Position", "Not available");
                        }
                        telemetry.addData("---", "---");
                    }
                } else {
                    telemetry.addData("AprilTags", "None detected");
                }
            } else {
                telemetry.addData("Limelight", "No valid data");
            }

            telemetry.update();

            // Small delay to prevent overwhelming the system
            sleep(50);
        }

        // Stop the Limelight when done
        limelight.stop();
        
        telemetry.addData("Status", "OpMode Stopped");
        telemetry.update();
    }
}
