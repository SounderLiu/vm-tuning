/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.power;

import java.util.LinkedList;
import java.util.List;
import java.util.Vector;

import org.cloudbus.cloudsim.CloudletScheduler;
import org.cloudbus.cloudsim.CloudletSchedulerDynamicWorkload;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.examples.power.Constants;
import org.cloudbus.cloudsim.util.MathUtil;

/**
 * The class of a VM that stores its CPU utilization history. The history is used by VM allocation
 * and selection policies.
 *
 * If you are using any algorithms, policies or workload included in the power package please cite
 * the following paper:
 *
 * Anton Beloglazov, and Rajkumar Buyya, "Optimal Online Deterministic Algorithms and Adaptive
 * Heuristics for Energy and Performance Efficient Dynamic Consolidation of Virtual Machines in
 * Cloud Data Centers", Concurrency and Computation: Practice and Experience (CCPE), Volume 24,
 * Issue 13, Pages: 1397-1420, John Wiley & Sons, Ltd, New York, USA, 2012
 *
 * @author Anton Beloglazov
 * @since CloudSim Toolkit 2.0
 */
public class PowerVm extends Vm {

    /** The Constant HISTORY_LENGTH. */
    public static final int HISTORY_LENGTH = 30;

    /** The utilization history. */
    private final List<Double> utilizationHistory = new LinkedList<Double>();

    /** The previous time. */
    private double previousTime;

    /** The scheduling interval. */
    private double schedulingInterval;

    ///////need to be updated////////
    private double cpuUsageTime;
    private double memUsageMean;
    private double netUsageMean;

    private Vector<Double> propensityVector;
    private Vector<Double> probabilityVector;
//    private int cloudletSize;
    private Vector<Integer> possibleWorkloadValueVec;
    private double applicationImpaction;
    private double workloadWeight;

    /////////////////////////////////////

    /**
     * Instantiates a new power vm.
     *
     * @param id the id
     * @param userId the user id
     * @param mips the mips
     * @param pesNumber the pes number
     * @param ram the ram
     * @param bw the bw
     * @param size the size
     * @param priority the priority
     * @param vmm the vmm
     * @param cloudletScheduler the cloudlet scheduler
     * @param schedulingInterval the scheduling interval
     */
    public PowerVm(
            final int id,
            final int userId,
            final double mips,
            final int pesNumber,
            final int ram,
            final long bw,
            final long size,
            final int priority,
            final String vmm,
            final CloudletScheduler cloudletScheduler,
            final double schedulingInterval) {
        super(id, userId, mips, pesNumber, ram, bw, size, vmm, cloudletScheduler);
        setSchedulingInterval(schedulingInterval);

        possibleWorkloadValueVec = new Vector<Integer>();
        probabilityVector = new Vector<Double>();
        propensityVector = new Vector<Double>();

        for(int i = Constants.WORKLOAD_DOWN; i < Constants.WORKLOAD_UP + 1; i++){
            possibleWorkloadValueVec.add(i);
        }

        for(int i =0 ; i < Constants.WORKLOAD_UP - Constants.WORKLOAD_DOWN + 1; i++ ){
            propensityVector.add(
                    Constants.SCALING_PARAMETER *
                            (Constants.WORKLOAD_UP - Constants.WORKLOAD_DOWN)/
                            2  /
                            ( Constants.WORKLOAD_UP - Constants.WORKLOAD_DOWN + 1)
            );

            probabilityVector.add((double)1/( Constants.WORKLOAD_UP - Constants.WORKLOAD_DOWN + 1));
        }
        setApplicationImpaction(1.0);

    }

    /**
     * Updates the processing of cloudlets running on this VM.
     *
     * @param currentTime current simulation time
     * @param mipsShare array with MIPS share of each Pe available to the scheduler
     *
     * @return time predicted completion time of the earliest finishing cloudlet, or 0 if there is
     * 		no next events
     *
     * @pre currentTime >= 0
     * @post $none
     */
    @Override
    public double updateVmProcessing(final double currentTime, final List<Double> mipsShare) {
        double time = super.updateVmProcessing(currentTime, mipsShare);
        if (currentTime > getPreviousTime() && (currentTime - 0.1) % getSchedulingInterval() == 0) {
            double utilization = getTotalUtilizationOfCpu(getCloudletScheduler().getPreviousTime());
            if (CloudSim.clock() != 0 || utilization != 0) {
                addUtilizationHistoryValue(utilization);
            }
            setPreviousTime(currentTime);
        }
        return time;
    }

    /**
     * Gets the utilization MAD in MIPS.
     *
     * @return the utilization mean in MIPS
     */
    public double getUtilizationMad() {
        double mad = 0;
        if (!getUtilizationHistory().isEmpty()) {
            int n = HISTORY_LENGTH;
            if (HISTORY_LENGTH > getUtilizationHistory().size()) {
                n = getUtilizationHistory().size();
            }
            double median = MathUtil.median(getUtilizationHistory());
            double[] deviationSum = new double[n];
            for (int i = 0; i < n; i++) {
                deviationSum[i] = Math.abs(median - getUtilizationHistory().get(i));
            }
            mad = MathUtil.median(deviationSum);
        }
        return mad;
    }

    /**
     * Gets the utilization mean in percents.
     *
     * @return the utilization mean in MIPS
     */
    public double getUtilizationMean() {
        double mean = 0;
        if (!getUtilizationHistory().isEmpty()) {
            int n = HISTORY_LENGTH;
            if (HISTORY_LENGTH > getUtilizationHistory().size()) {
                n = getUtilizationHistory().size();
            }
            for (int i = 0; i < n; i++) {
                mean += getUtilizationHistory().get(i);
            }
            mean /= n;
        }
        return mean * getMips();
    }

    /**
     * Gets the utilization variance in MIPS.
     *
     * @return the utilization variance in MIPS
     */
    public double getUtilizationVariance() {
        double mean = getUtilizationMean();
        double variance = 0;
        if (!getUtilizationHistory().isEmpty()) {
            int n = HISTORY_LENGTH;
            if (HISTORY_LENGTH > getUtilizationHistory().size()) {
                n = getUtilizationHistory().size();
            }
            for (int i = 0; i < n; i++) {
                double tmp = getUtilizationHistory().get(i) * getMips() - mean;
                variance += tmp * tmp;
            }
            variance /= n;
        }
        return variance;
    }

    /**
     * Adds the utilization history value.
     *
     * @param utilization the utilization
     */
    public void addUtilizationHistoryValue(final double utilization) {
        getUtilizationHistory().add(0, utilization);
        if (getUtilizationHistory().size() > HISTORY_LENGTH) {
            getUtilizationHistory().remove(HISTORY_LENGTH);
        }
    }

    /**
     * Gets the utilization history.
     *
     * @return the utilization history
     */
    protected List<Double> getUtilizationHistory() {
        return utilizationHistory;
    }

    /**
     * Gets the previous time.
     *
     * @return the previous time
     */
    public double getPreviousTime() {
        return previousTime;
    }

    /**
     * Sets the previous time.
     *
     * @param previousTime the new previous time
     */
    public void setPreviousTime(final double previousTime) {
        this.previousTime = previousTime;
    }

    /**
     * Gets the scheduling interval.
     *
     * @return the schedulingInterval
     */
    public double getSchedulingInterval() {
        return schedulingInterval;
    }

    /**
     * Sets the scheduling interval.
     *
     * @param schedulingInterval the schedulingInterval to set
     */
    protected void setSchedulingInterval(final double schedulingInterval) {
        this.schedulingInterval = schedulingInterval;
    }

    public double getCpuUsageTime() {
//        this.getCurrentRequestedTotalMips();
        return this.getCurrentRequestedTotalMips();
    }

    public void setCpuUsageTime(double cpuUsageTime) {
        this.cpuUsageTime = cpuUsageTime;
    }

    public double getMemUsageMean() {
        return memUsageMean;
    }

    public void setMemUsageMean(double memUsageMean) {
        this.memUsageMean = memUsageMean;
    }

    public double getNetUsageMean() {
        return netUsageMean;
    }

    public void setNetUsageMean(double netUsageMean) {
        this.netUsageMean = netUsageMean;
    }

    public Vector<Double> getPropensityVector() {
        return propensityVector;
    }

    public void setPropensityVector(Vector<Double> propensityVector) {
        this.propensityVector = propensityVector;
    }

    public int getCloudletSize() {
        return ((CloudletSchedulerDynamicWorkload)this.getCloudletScheduler()).getExecCloudletNum();
    }

//    public void setCloudletSize(int cloudletSize) {
//        this.cloudletSize = cloudletSize;
//    }

    public Vector<Integer> getPossibleWorkloadValueVec() {
        return possibleWorkloadValueVec;
    }


    public double getApplicationImpaction() {
        return applicationImpaction;
    }

    public void setApplicationImpaction(double applicationImpaction) {
        this.applicationImpaction = applicationImpaction;
    }

    public double getWorkloadWeight() {
        return workloadWeight;
    }

    public void setWorkloadWeight(double workloadWeight) {
        this.workloadWeight = workloadWeight;
    }

    public Vector<Double> getProbabilityVector() {
        return probabilityVector;
    }

    public void setProbabilityVector(Vector<Double> probabilityVector) {
        this.probabilityVector = probabilityVector;
    }
}
