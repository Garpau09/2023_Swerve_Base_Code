package frc.robot.subsystems;

import java.util.HashMap;

import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.CommandBase;
import edu.wpi.first.wpilibj2.command.SubsystemBase;

import frc.robot.Constants.ArmConstants;
import frc.robot.Constants.ArmConstants.ArmPoses;
import frc.robot.Mechanisms.ArmSegment;
import frc.robot.Mechanisms.Gripper;

public class ArmSubsystem extends SubsystemBase {

	// region properties

	public HashMap<ArmPoses, double[]> armStates = ArmConstants.kArmStates;

	/** used to track the state of the arm */
	ArmPoses targetArmState;

	/** controls the side of the robot the arm is on */
	private boolean isFront;

	// create arms
	private ArmSegment majorArm;
	private ArmSegment minorArm;

	// create gripper
	private Gripper gripper;

	// endregion

	public ArmSubsystem() {

		// this will cause the code to fail to run if the hashmap is not full
		for (ArmPoses pose : ArmPoses.values()) {
			try {
				double x = 0;
				x = x + armStates.get(pose)[0];
				x = x + armStates.get(pose)[1];
				x = x + armStates.get(pose)[2];
			} catch (Exception exception) {
				throw new IndexOutOfBoundsException(
						"NOT ALL ARM POSES HAVE A VALUE IN THE HASHMAP! THIS WILL RESLUT IN CRASHING IF NOT RESOLVED!");
			}
		}

		// region: def arms

		// major arm defs
		majorArm = new ArmSegment(
				ArmConstants.kRightMajorArmPort,
				ArmConstants.kLeftMajorArmPort,
				ArmConstants.kMajorArmTicks,
				false);

		majorArm.setPID(
				ArmConstants.kMajorArmGains.kP,
				ArmConstants.kMajorArmGains.kI,
				ArmConstants.kMajorArmGains.kD,
				ArmConstants.kMajorArmIzone);

		majorArm.setConstraints(ArmConstants.kMajorArmConstraints);
		majorArm.setMaxOutput(ArmConstants.kMajorSecondStagePIDOutputLimit);

		// minor arm defs
		minorArm = new ArmSegment(
				ArmConstants.kRightMinorArmPort,
				ArmConstants.kLeftMinorArmPort,
				ArmConstants.kMinorArmTicks,
				true);

		minorArm.setPID(
				ArmConstants.kMinorArmGains.kP,
				ArmConstants.kMinorArmGains.kI,
				ArmConstants.kMinorArmGains.kD,
				ArmConstants.kMinorArmIzone);

		minorArm.setConstraints(ArmConstants.kMinorArmConstraints);
		minorArm.setMaxOutput(ArmConstants.kMinorSecondStagePIDOutputLimit);
		// endregion

		gripper = new Gripper(ArmConstants.kLeftGripperPort, ArmConstants.kRightGripperPort);

		// the default state of the arms
		isFront = true;

		setSequencedArmState(ArmPoses.TUCKED);
	}

	@Override
	public void periodic() {
		// This method will be called once per scheduler run

		SmartDashboard.putString("ArmState", targetArmState.toString());

		SmartDashboard.putNumber("major target", majorArm.getTargetTheta());
		SmartDashboard.putNumber("minor target", minorArm.getTargetTheta());

		SmartDashboard.putNumber("major real theta: ", majorArm.getRealTheta());
		SmartDashboard.putNumber("minor real theta: ", minorArm.getRealTheta());

		SmartDashboard.putNumber("major left real theta", majorArm.getLeftRealTheta());
		SmartDashboard.putNumber("major right real theta", majorArm.getRightRealTheta());

		SmartDashboard.putNumber("major left real theta", majorArm.getLeftRealTheta());
		SmartDashboard.putNumber("major right real theta", majorArm.getRightRealTheta());

		SmartDashboard.putNumber("major power draw: ", majorArm.getPowerDraw());
		SmartDashboard.putNumber("minor power draw: ", minorArm.getPowerDraw());

		SmartDashboard.putBoolean("At target: ", getAtTarget(8));
		SmartDashboard.putBoolean("At target major", majorArm.getAtTarget(5));
		SmartDashboard.putBoolean("At target minor", minorArm.getAtTarget(5));

		SmartDashboard.putNumber("LeftMajorOutput", majorArm.getLeftMotorOutput());
		SmartDashboard.putNumber("RightMajorOutput", majorArm.getRightMotorOutput());

		updateOutputLimit();
	}

	// region Commands

	public CommandBase UnsequencedArmPoseCommand(final ArmPoses state) {
		return runOnce(() -> {
			setUnsequencedArmState(state);
		});
	}

	public CommandBase SequencedArmPoseCommand(final ArmPoses state) {
		return runOnce(() -> {
			setSequencedArmState(state);
		});
	}

	/** Toggles the dominant side of the robot */
	public void ToggleSide() {
		isFront = !isFront;
		majorArm.setSign((isFront) ? 1 : -1);
		minorArm.setSign((isFront) ? 1 : -1);
		majorArm.setReference();
		minorArm.setReference();
	}

	public CommandBase toggleArmMotors() {
		return runOnce(() -> {
			minorArm.toggleMotors();
			majorArm.toggleMotors();
		});
	}

	public CommandBase zeroArms() {
		return runOnce(() -> {
			minorArm.resetZeros();
			majorArm.resetZeros();
		});
	}

	// endregion

	// region Setters

	/**
	 * Sets the height of the arm
	 * 
	 * @param state can be (LOW_SCORE, MID_SCORE, HIGH_SCORE,
	 *              LOW_INTAKE, MID_INTAKE, HIGH_INTAKE)
	 */
	public void setUnsequencedArmState(ArmPoses state) {
		minorArm.setMaxOutput(armStates.get(targetArmState)[2]);

		setTargetArmState(state);
	}

	public void setSequencedArmState(ArmPoses state) {
		if (state != ArmPoses.TUCKED) {
			minorArm.setMaxOutput(ArmConstants.kMinorFirstStagePIDOutputLimit);
			majorArm.setMaxOutput(ArmConstants.kMajorSecondStagePIDOutputLimit);
		} else {
			minorArm.setMaxOutput(ArmConstants.kMinorSecondStagePIDOutputLimit);
			majorArm.setMaxOutput(ArmConstants.kMajorFirstStagePIDOutputLimit);
		}
		setTargetArmState(state);
	}

	/**
	 * @param targetArmState the targetArmState to set
	 */
	public void setTargetArmState(ArmPoses state) {
		targetArmState = state;

		if (state == ArmPoses.TUCKED) {
			gripper.closeGriper();
		} else {
			// gripper.openGriper();
		}

		// gets the angle values from the hashmap
		majorArm.setTargetTheta(armStates.get(targetArmState)[0]);
		minorArm.setTargetTheta(armStates.get(targetArmState)[1]);

		majorArm.setReference();
		minorArm.setReference();
	}

	public void updateOutputLimit() {
		if (majorArm.getAtTarget(10) || minorArm.getAtTarget(10)) {
			minorArm.setMaxOutput(ArmConstants.kMinorSecondStagePIDOutputLimit);
			majorArm.setMaxOutput(ArmConstants.kMajorSecondStagePIDOutputLimit);
		}
	}

	// endregion

	// region getters

	/**
	 * Used to get the target height of the arm as an enum
	 * 
	 * @return armState: can be (LOW_SCORE, MID_SCORE, HIGH_SCORE, LOW_INTAKE,
	 *         MID_INTAKE, HIGH_INTAKE)
	 */
	public ArmPoses getArmState() {
		return targetArmState;
	}

	/** ruturns true if the target dominant side of the robot is front */
	public boolean getIsFront() {
		return isFront;
	}

	public void openGriper() {
		gripper.openGriper();
	}

	public void closeGriper() {
		gripper.closeGriper();
	}

	public boolean getAtTarget(double deadBand) {

		if (majorArm.getAtTarget(deadBand) && minorArm.getAtTarget(deadBand)) {
			return true;
		}
		return false;
	}

	public double[] getTargetTheta() {
		return new double[] { majorArm.getTargetTheta(), minorArm.getTargetTheta() };
	}

	// endregion

}
