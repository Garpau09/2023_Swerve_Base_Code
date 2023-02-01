// Copyright (c) FIRST and other WPILib contributors.
// Open Source Software; you can modify and/or share it under the terms of
// the WPILib BSD license file in the root directory of this project.

package frc.robot.subsystems;

import com.ctre.phoenix.sensors.AbsoluteSensorRange;
import com.ctre.phoenix.sensors.CANCoder;
import com.ctre.phoenix.sensors.CANCoderStatusFrame;
import com.ctre.phoenix.sensors.SensorInitializationStrategy;
import com.revrobotics.CANSparkMax;
import com.revrobotics.CANSparkMax.IdleMode;
import com.revrobotics.CANSparkMaxLowLevel.MotorType;
import com.revrobotics.SparkMaxRelativeEncoder.Type;

import edu.wpi.first.math.controller.PIDController;
import edu.wpi.first.math.controller.ProfiledPIDController;
import edu.wpi.first.math.controller.SimpleMotorFeedforward;
import edu.wpi.first.math.geometry.Rotation2d;
import edu.wpi.first.math.kinematics.SwerveModulePosition;
import edu.wpi.first.math.kinematics.SwerveModuleState;
import edu.wpi.first.math.trajectory.TrapezoidProfile;
import edu.wpi.first.wpilibj.smartdashboard.SmartDashboard;
import edu.wpi.first.wpilibj2.command.SubsystemBase;
import frc.robot.Constants.DriveConstants;
import frc.robot.Constants.ModuleConstants;

public class SwerveModule extends SubsystemBase {
	/** Creates a new SwerveModule. */

	private final CANSparkMax m_driveMotor;
	private final CANSparkMax m_turningMotor;

	private final CANCoder m_turnEncoder;

	private final PIDController m_drivePIDController = new PIDController(
			DashboardSubsystem.PIDConstants.getDrive_kP(),
			DriveConstants.kModuleDriveControllerI,
			DashboardSubsystem.PIDConstants.getDrive_kD());

	private final ProfiledPIDController m_turningPIDController = new ProfiledPIDController(
			DashboardSubsystem.PIDConstants.getTurn_kP(),
			DriveConstants.kModuleTurningControllerI,
			DashboardSubsystem.PIDConstants.getTurn_kD(),
			new TrapezoidProfile.Constraints(
					DriveConstants.kMaxModuleAngularSpeedRadiansPerSecond,
					DriveConstants.kMaxModuleAngularAccelerationRadiansPerSecondSquared));

	SimpleMotorFeedforward driveFeedForward = new SimpleMotorFeedforward(
			DriveConstants.ksVolts, DriveConstants.kvVoltSecondsPerMeter);

	SimpleMotorFeedforward turnFeedForward = new SimpleMotorFeedforward(
			DriveConstants.ksTurning, DriveConstants.kvTurning);

	public SwerveModule(
			int driveMotorChannel,
			int turningMotorChannel,
			int turningEncoderPorts,
			double angleZero) {

		// Initialize the motors
		m_driveMotor = new CANSparkMax(driveMotorChannel, MotorType.kBrushless);
		m_turningMotor = new CANSparkMax(turningMotorChannel, MotorType.kBrushless);

		m_turningMotor.restoreFactoryDefaults();
		m_driveMotor.restoreFactoryDefaults();

		// Configure current limits for motors - prevents disabling/brownouts
		// TODO: Check if current limit works/is necessary
		m_driveMotor.setSecondaryCurrentLimit(30);
		m_driveMotor.setSmartCurrentLimit(30);
		m_driveMotor.setIdleMode(IdleMode.kBrake);
		m_turningMotor.setIdleMode(IdleMode.kBrake);
		m_turningMotor.setSmartCurrentLimit(30);

		// Configure the encoders for both motors
		// CANcoder defaults range 0 to 360. WPILib swerve module has angles from -180
		// to 180
		// Changed range to accomodate this issue

		this.m_turnEncoder = new CANCoder(turningEncoderPorts);
		this.m_turnEncoder.configFactoryDefault();
		this.m_turnEncoder.configSensorInitializationStrategy(SensorInitializationStrategy.BootToAbsolutePosition);
		this.m_turnEncoder.configMagnetOffset(-1 * angleZero);
		this.m_turnEncoder.configAbsoluteSensorRange(AbsoluteSensorRange.Signed_PlusMinus180);

		this.m_turnEncoder.setStatusFramePeriod(CANCoderStatusFrame.SensorData, 10, 100);

		// Set turning PID output to allow the swerve modules to treat the min/max as
		// continuous
		// Optimizes how the modules chooses to go to a desired position
		m_turningPIDController.enableContinuousInput(-Math.PI, Math.PI);

	}

	// Returns headings of the module
	public double getModuleHeading() {
		double m_turning = this.m_turnEncoder.getAbsolutePosition();
		return m_turning;
	}

	// Returns the current state of the module

	// Returns current position of the modules
	public SwerveModulePosition getPosition() {

		double m_moduleAngleRadians = Math.toRadians(m_turnEncoder.getAbsolutePosition());

		double m_distanceMeters = m_driveMotor.getEncoder(Type.kHallSensor, 42).getPosition()
				* ModuleConstants.kdriveGearRatio
				* ModuleConstants.kwheelCircumference;

		// Return SwerveModulePosition
		// @param double distanceMeters
		// @param Rotation2d angleRadians

		return new SwerveModulePosition(m_distanceMeters, new Rotation2d(m_moduleAngleRadians));
	}

	public void setDesiredState(SwerveModuleState desiredState) {

		double m_speedMetersPerSecond = m_driveMotor.getEncoder().getVelocity()
				* ModuleConstants.kdriveGearRatio
				* ModuleConstants.kwheelCircumference
				* (1 / 60); // 1/Minutes to 1/seconds

		double m_moduleAngleRadians = Math.toRadians(m_turnEncoder.getAbsolutePosition());

		// Optimize the reference state to avoid spinning further than 90 degrees
		// to desired state
		SwerveModuleState state = SwerveModuleState.optimize(desiredState, new Rotation2d(m_moduleAngleRadians));

		// Calculate the drive and turn motor outputs using PID and feedforward
		final double driveOutput = m_drivePIDController.calculate(m_speedMetersPerSecond, state.speedMetersPerSecond)
				+ driveFeedForward.calculate(state.speedMetersPerSecond);

		final var turnOutput = m_turningPIDController.calculate(m_moduleAngleRadians, state.angle.getRadians())
				+ turnFeedForward.calculate(m_turningPIDController.getSetpoint().velocity);

		// Set the motor voltages
		m_driveMotor.setVoltage(driveOutput);
		m_turningMotor.setVoltage(turnOutput);

	}

	public double getTurnOutput(SwerveModuleState desiredState) {

		double m_moduleAngleRadians = Math.toRadians(m_turnEncoder.getAbsolutePosition());

		// Optimize the reference state to avoid spinning further than 90 degrees
		// to desired state
		SwerveModuleState state = SwerveModuleState.optimize(desiredState, new Rotation2d(m_moduleAngleRadians));

		final var turnOutput = m_turningPIDController.calculate(m_moduleAngleRadians, state.angle.getRadians())
				+ turnFeedForward.calculate(m_turningPIDController.getSetpoint().velocity);

		return turnOutput;

	}

	public double getDriveOutput(SwerveModuleState desiredState) {

		double m_speedMetersPerSecond = m_driveMotor.getEncoder(Type.kHallSensor, 42).getVelocity()
				* ModuleConstants.kdriveGearRatio
				* ModuleConstants.kwheelCircumference
				* (1 / 60); // 1/Minutes to 1/seconds

		double m_moduleAngleRadians = Math.toRadians(m_turnEncoder.getAbsolutePosition());

		// Optimize the reference state to avoid spinning further than 90 degrees
		// to desired state
		SwerveModuleState state = SwerveModuleState.optimize(desiredState, new Rotation2d(m_moduleAngleRadians));

		// Calculate the drive and turn motor outputs using PID and feedforward
		final double driveOutput = m_drivePIDController.calculate(m_speedMetersPerSecond, state.speedMetersPerSecond)
				+ driveFeedForward.calculate(state.speedMetersPerSecond);

		return driveOutput;

	}

	public void resetEncoders() {
		m_turnEncoder.setPosition(0);
		m_driveMotor.getEncoder().setPosition(0);
	}

	public double getEncoderHeading() {
		return this.m_turnEncoder.getAbsolutePosition();
	}

	@Override
	public void periodic() {
		SmartDashboard.putNumber("Turn kP???", DashboardSubsystem.PIDConstants.getTurn_kP());
		// This method will be called once per scheduler run
	}
}
