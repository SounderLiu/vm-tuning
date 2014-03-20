/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.power;

import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Vector;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.predicates.PredicateType;
import org.cloudbus.cloudsim.examples.power.Constants;

/**
 * PowerDatacenter is a class that enables simulation of power-aware data centers.
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
public class PowerDatacenter extends Datacenter {

    /** The power. */
    private double power;

    /** The disable migrations. */
    private boolean disableMigrations;

    /** The cloudlet submited. */
    private double cloudletSubmitted;

    /** The migration count. */
    private int migrationCount;

    private double localSchedulingInterval;

    private double globalScheulingInterval;

    private double localLastProcessTime;
    private double globalLastProcessTime;

    private boolean tuningInited;

    /**
     * Instantiates a new datacenter.
     *
     * @param name the name
     * @param characteristics the res config
     * @param schedulingInterval the scheduling interval
    //     * @param utilizationBound the utilization bound
     * @param vmAllocationPolicy the vm provisioner
     * @param storageList the storage list
     * @throws Exception the exception
     */
    public PowerDatacenter(
            String name,
            DatacenterCharacteristics characteristics,
            VmAllocationPolicy vmAllocationPolicy,
            List<Storage> storageList,
            double schedulingInterval) throws Exception {
        super(name, characteristics, vmAllocationPolicy, storageList, schedulingInterval);

        setPower(0.0);
        setDisableMigrations(false);
        setCloudletSubmitted(-1);
        setMigrationCount(0);
        setGlobalScheulingInterval(Constants.GLOBAL_INTERVIAL);
        setLocalSchedulingInterval(Constants.LOCAL_INTERVIAL);
        setLocalLastProcessTime(0.0);
        setGlobalLastProcessTime(0.0);

        tuningInited = false;



    }

    /**
     * Updates processing of each cloudlet running in this PowerDatacenter. It is necessary because
     * Hosts and VirtualMachines are simple objects, not entities. So, they don't receive events and
     * updating cloudlets inside them must be called from the outside.
     *
     * @pre $none
     * @post $none
     */
    @Override
    protected void updateCloudletProcessing() {
        if (!tuningInited){
            CloudSim.cancelAll(getId(), new PredicateType(CloudSimTags.VM_GLOBAL_TUNING));
            schedule(getId(), getGlobalScheulingInterval(), CloudSimTags.VM_GLOBAL_TUNING);

            CloudSim.cancelAll(getId(), new PredicateType(CloudSimTags.VM_LOCAL_TUNING));
            schedule(getId(), getLocalSchedulingInterval(), CloudSimTags.VM_LOCAL_TUNING);

            tuningInited = true;
        }

        if (getCloudletSubmitted() == -1 || getCloudletSubmitted() == CloudSim.clock()) {
            CloudSim.cancelAll(getId(), new PredicateType(CloudSimTags.VM_DATACENTER_EVENT));
            schedule(getId(), getSchedulingInterval(), CloudSimTags.VM_DATACENTER_EVENT);

            return;
        }
        double currentTime = CloudSim.clock();

        // if some time passed since last processing
        if (currentTime > getLastProcessTime()) {
            System.out.print(currentTime + " ");

            double minTime = updateCloudetProcessingWithoutSchedulingFutureEventsForce();

            if (!isDisableMigrations()) {
                List<Map<String, Object>> migrationMap = getVmAllocationPolicy().optimizeAllocation(
                        getVmList());

                if (migrationMap != null) {
                    for (Map<String, Object> migrate : migrationMap) {
                        Vm vm = (Vm) migrate.get("vm");
                        PowerHost targetHost = (PowerHost) migrate.get("host");
                        PowerHost oldHost = (PowerHost) vm.getHost();

                        if (oldHost == null) {
                            Log.formatLine(
                                    "%.2f: Migration of VM #%d to Host #%d is started",
                                    currentTime,
                                    vm.getId(),
                                    targetHost.getId());
                        } else {
                            Log.formatLine(
                                    "%.2f: Migration of VM #%d from Host #%d to Host #%d is started",
                                    currentTime,
                                    vm.getId(),
                                    oldHost.getId(),
                                    targetHost.getId());
                        }

                        if(targetHost.addMigratingInVm(vm)){
                            incrementMigrationCount();
                            /** VM migration delay = RAM / bandwidth **/
                            // we use BW / 2 to model BW available for migration purposes, the other
                            // half of BW is for VM communication
                            // around 16 seconds for 1024 MB using 1 Gbit/s network
                            send(
                                    getId(),
                                    vm.getRam() / ((double) targetHost.getBw() / (2 * 8000)),
                                    CloudSimTags.VM_MIGRATE,
                                    migrate);
                        }


                    }
                }
            }

            // schedules an event to the next time
            if (minTime != Double.MAX_VALUE) {
                CloudSim.cancelAll(getId(), new PredicateType(CloudSimTags.VM_DATACENTER_EVENT));
                send(getId(), getSchedulingInterval(), CloudSimTags.VM_DATACENTER_EVENT);
            }

            setLastProcessTime(currentTime);
        }
    }

    /**
     * Update cloudet processing without scheduling future events.
     *
     * @return the double
     */
    protected double updateCloudetProcessingWithoutSchedulingFutureEvents() {
        if (CloudSim.clock() > getLastProcessTime()) {
            return updateCloudetProcessingWithoutSchedulingFutureEventsForce();
        }
        return 0;
    }

    /**
     * Update cloudet processing without scheduling future events.
     *
     * @return the double
     */
    protected double updateCloudetProcessingWithoutSchedulingFutureEventsForce() {
        double currentTime = CloudSim.clock();
        double minTime = Double.MAX_VALUE;
        double timeDiff = currentTime - getLastProcessTime();
        double timeFrameDatacenterEnergy = 0.0;

        Log.printLine("\n\n--------------------------------------------------------------\n\n");
        Log.formatLine("New resource usage for the time frame starting at %.2f:", currentTime);

        for (PowerHost host : this.<PowerHost> getHostList()) {
            Log.printLine();

            double time = host.updateVmsProcessing(currentTime); // inform VMs to update processing
            if (time < minTime) {
                minTime = time;
            }

            Log.formatLine(
                    "%.2f: [Host #%d] utilization is %.2f%%",
                    currentTime,
                    host.getId(),
                    host.getUtilizationOfCpu() * 100);
        }

        if (timeDiff > 0) {
            Log.formatLine(
                    "\nEnergy consumption for the last time frame from %.2f to %.2f:",
                    getLastProcessTime(),
                    currentTime);

            for (PowerHost host : this.<PowerHost> getHostList()) {
                double previousUtilizationOfCpu = host.getPreviousUtilizationOfCpu();
                double utilizationOfCpu = host.getUtilizationOfCpu();
                double timeFrameHostEnergy = host.getEnergyLinearInterpolation(
                        previousUtilizationOfCpu,
                        utilizationOfCpu,
                        timeDiff);
                timeFrameDatacenterEnergy += timeFrameHostEnergy;

                Log.printLine();
                Log.formatLine(
                        "%.2f: [Host #%d] utilization at %.2f was %.2f%%, now is %.2f%%",
                        currentTime,
                        host.getId(),
                        getLastProcessTime(),
                        previousUtilizationOfCpu * 100,
                        utilizationOfCpu * 100);
                Log.formatLine(
                        "%.2f: [Host #%d] energy is %.2f W*sec",
                        currentTime,
                        host.getId(),
                        timeFrameHostEnergy);
            }

            Log.formatLine(
                    "\n%.2f: Data center's energy is %.2f W*sec\n",
                    currentTime,
                    timeFrameDatacenterEnergy);
        }

        setPower(getPower() + timeFrameDatacenterEnergy);

        checkCloudletCompletion();

//        /** Remove completed VMs **/
//        for (PowerHost host : this.<PowerHost> getHostList()) {
//            for (Vm vm : host.getCompletedVms()) {
//                getVmAllocationPolicy().deallocateHostForVm(vm);
//                getVmList().remove(vm);
//                Log.printLine("VM #" + vm.getId() + " has been deallocated from host #" + host.getId());
//            }
//        }

        Log.printLine();

        setLastProcessTime(currentTime);
        return minTime;
    }

    /*
     * (non-Javadoc)
     * @see org.cloudbus.cloudsim.Datacenter#processVmMigrate(org.cloudbus.cloudsim.core.SimEvent,
     * boolean)
     */
    @Override
    protected void processVmMigrate(SimEvent ev, boolean ack) {
        updateCloudetProcessingWithoutSchedulingFutureEvents();
        super.processVmMigrate(ev, ack);
        SimEvent event = CloudSim.findFirstDeferred(getId(), new PredicateType(CloudSimTags.VM_MIGRATE));
        if (event == null || event.eventTime() > CloudSim.clock()) {
            updateCloudetProcessingWithoutSchedulingFutureEventsForce();
        }
    }

    /*
     * (non-Javadoc)
     * @see cloudsim.Datacenter#processCloudletSubmit(cloudsim.core.SimEvent, boolean)
     */
    @Override
    protected void processCloudletSubmit(SimEvent ev, boolean ack) {
        super.processCloudletSubmit(ev, ack);
        setCloudletSubmitted(CloudSim.clock());
    }

    @Override
    protected void processOtherEvent(SimEvent ev) {
        Log.printLine(getName() + ".other Event");
        switch (ev.getTag()) {
            case CloudSimTags.VM_LOCAL_TUNING:
                processLocalTuning();
                break;

            default:
                if (ev == null) {
                    Log.printLine(getName() + ".processOtherEvent(): Error - an event is null.");
                }
        }
    }


    private int processLocalTuning(){
        double currentTime = CloudSim.clock();
        // if some time passed since last processing
        if (currentTime > getLocalLastProcessTime()) {
            System.out.println(currentTime + ": Local tuning call...");

            //update propensity
            for(Host host : this.getHostList()){
                PowerHost powerHost = (PowerHost) host;
//                System.out.println("=======" + host.getId() + "=======");
                double vmWorkloadWeightTotal = 0.0;
                for(Vm vm : powerHost.getVmList()){
                    PowerVm powerVm = (PowerVm) vm;


                    updatePropensity(powerVm.getPropensityVector(), powerVm.getProbabilityVector(), powerVm.getPossibleWorkloadValueVec(), powerVm.getCloudletSize());

                    //get the max propensity
                    double maxValue =-1.0;
                    int maxValueIndex = 0;
                    int counter = 0;
                    for(Double propensity : powerVm.getPropensityVector()){

                        if (propensity > maxValue){
                            maxValue = propensity;
                            maxValueIndex = counter;
                        }
                        counter ++;
                    }
                    //update vm's LastWorkloadValue
//                    powerVm.setLastWorkloadValue( powerVm.getPossibleWorkloadValueVec().get(maxValueIndex) );
                    powerVm.setWorkloadWeight(powerVm.getApplicationImpaction() * powerVm.getPossibleWorkloadValueVec().get(maxValueIndex));
                    vmWorkloadWeightTotal += powerVm.getWorkloadWeight();
                }
                //update vm property
                for(Vm vm: powerHost.getVmList()){
                    PowerVm powerVm = (PowerVm) vm;
                    double weight = 0;
                    weight = powerVm.getWorkloadWeight() / vmWorkloadWeightTotal;

                    if(weight ==1.0){
                        powerVm.setRam((int)(powerHost.getRam() * weight *0.95 *0.5));
                        powerVm.setMips((int)(powerHost.getTotalMips() * weight *0.95*0.5));
                        powerVm.setBw((int)(powerHost.getBw() * weight *0.95*0.5));
                    }
                    else if(weight ==0.0){
                        powerVm.setRam((int)(powerHost.getRam() * 0.01 *0.5));
                        powerVm.setMips((int)(powerHost.getTotalMips() *  0.01*0.5));
                        powerVm.setBw((int)(powerHost.getBw() *  0.01*0.5));
                    }
                    else{
                        powerVm.setRam((int)(powerHost.getRam() * weight*0.5));
                        powerVm.setMips((int)(powerHost.getTotalMips() * weight*0.5));
                        powerVm.setBw((int)(powerHost.getBw() * weight*0.5 ));
                    }

                }
            }
        }

        // schedules an event to the next time
        CloudSim.cancelAll(getId(), new PredicateType(CloudSimTags.VM_LOCAL_TUNING));
        schedule(getId(), getLocalSchedulingInterval(), CloudSimTags.VM_LOCAL_TUNING);

        setLocalLastProcessTime(currentTime);
        return 1;
    }

    private int updatePropensity(Vector<Double>propensityVec, Vector<Double>probabilityVec, Vector<Integer> valueVec, double workloadLastValue ){
        double newSum = 0;
        for(int index = 0; index<propensityVec.size(); index++){
            Double propensityNew = propensityVec.get(index) + EFunction(propensityVec, index, valueVec.get(index), workloadLastValue,valueVec.size(),0.05);
            propensityVec.set(index,propensityNew);
            newSum += propensityNew;
        }
        for(int index = 0; index<propensityVec.size(); index++){
            Double probability = propensityVec.get(index)/newSum;
            probabilityVec.set(index,probability);
        }
        return 1;
    }

    private double getAccuracy(Vector<Double>propensityVec, int index){
        long q = Math.round(propensityVec.get(index));
        switch((int)q){
            case 0:
                return 0;
            case 1:
                return 1.00 - 0.82 ;

            case 2:
                return 1.95 - 1.64  ;

            case 3:
                return 2.85- 2.46  ;

            case 4:
                return 3.70 - 3.28  ;

            case 5:
                return 4.50 - 4.10 ;

            case 6:
                return 5.25 - 4.92 ;

            case 7:
                return 5.95 - 5.74 ;

            case 8 :
                return 6.60 - 6.56 ;

            case 9:
                return 7.20 - 7.38  ;

            case 10:
                return 7.75 - 8.20  ;

            case 11:
                return 8.25 - 9.02  ;

            case 12 :
                return     8.70 - 9.84;

            case 13:
                return 9.10 - 10.66  ;

            case 14:
                return 9.45 - 11.48  ;

            case 15:
                return 9.75 - 12.30  ;

            case 16 :
                return  10.00 - 13.12 ;

            case 17 :
                return 10.25 - 13.94  ;

            case 18:
                return 10.35 - 14.76  ;

            case 19 :
                return 10.45 - 15.58  ;

            case 20 :
                return 10.50 - 16.40  ;

            case 21:
                return 10.50 - 21.22  ;

            default:
                System.out.println("#### Out of range...");
                return 1;
        }
    }

    private double EFunction(Vector<Double>propensityVec,int index, double k, double k_last, int K, double e){
        double experience = 0;
        if (k == k_last){
            experience = getAccuracy(propensityVec,index) * (1 - e);
        }
        else{
            experience = propensityVec.get(index) * e /(K -1);
        }
        return experience;
    }

    /**
     * Gets the power.
     *
     * @return the power
     */
    public double getPower() {
        return power;
    }

    /**
     * Sets the power.
     *
     * @param power the new power
     */
    protected void setPower(double power) {
        this.power = power;
    }

    /**
     * Checks if PowerDatacenter is in migration.
     *
     * @return true, if PowerDatacenter is in migration
     */
    protected boolean isInMigration() {
        boolean result = false;
        for (Vm vm : getVmList()) {
            if (vm.isInMigration()) {
                result = true;
                break;
            }
        }
        return result;
    }

    /**
     * Checks if is disable migrations.
     *
     * @return true, if is disable migrations
     */
    public boolean isDisableMigrations() {
        return disableMigrations;
    }

    /**
     * Sets the disable migrations.
     *
     * @param disableMigrations the new disable migrations
     */
    public void setDisableMigrations(boolean disableMigrations) {
        this.disableMigrations = disableMigrations;
    }

    /**
     * Checks if is cloudlet submited.
     *
     * @return true, if is cloudlet submited
     */
    protected double getCloudletSubmitted() {
        return cloudletSubmitted;
    }

    /**
     * Sets the cloudlet submited.
     *
     * @param cloudletSubmitted the new cloudlet submited
     */
    protected void setCloudletSubmitted(double cloudletSubmitted) {
        this.cloudletSubmitted = cloudletSubmitted;
    }

    /**
     * Gets the migration count.
     *
     * @return the migration count
     */
    public int getMigrationCount() {
        return migrationCount;
    }

    /**
     * Sets the migration count.
     *
     * @param migrationCount the new migration count
     */
    protected void setMigrationCount(int migrationCount) {
        this.migrationCount = migrationCount;
    }

    /**
     * Increment migration count.
     */
    protected void incrementMigrationCount() {
        setMigrationCount(getMigrationCount() + 1);
    }


    public double getLocalSchedulingInterval() {
        return localSchedulingInterval;
    }

    public double getGlobalScheulingInterval() {
        return globalScheulingInterval;
    }

    public void setLocalSchedulingInterval(double localSchedulingInterval) {
        this.localSchedulingInterval = localSchedulingInterval;
    }

    public void setGlobalScheulingInterval(double globalScheulingInterval) {
        this.globalScheulingInterval = globalScheulingInterval;
    }

    public double getLocalLastProcessTime() {
        return localLastProcessTime;
    }

    public void setLocalLastProcessTime(double localLastProcessTime) {
        this.localLastProcessTime = localLastProcessTime;
    }

    public double getGlobalLastProcessTime() {
        return globalLastProcessTime;
    }

    public void setGlobalLastProcessTime(double globalLastProcessTime) {
        this.globalLastProcessTime = globalLastProcessTime;
    }
}
