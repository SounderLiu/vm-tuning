/*
 * Title:        CloudSim Toolkit
 * Description:  CloudSim (Cloud Simulation) Toolkit for Modeling and Simulation of Clouds
 * Licence:      GPL - http://www.gnu.org/copyleft/gpl.html
 *
 * Copyright (c) 2009-2012, The University of Melbourne, Australia
 */

package org.cloudbus.cloudsim.power;

import org.apache.commons.math3.analysis.function.Constant;
import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.core.CloudSim;
import org.cloudbus.cloudsim.core.CloudSimTags;
import org.cloudbus.cloudsim.core.SimEvent;
import org.cloudbus.cloudsim.core.predicates.PredicateType;
import org.cloudbus.cloudsim.examples.power.Constants;
import org.cloudbus.cloudsim.lists.VmList;

import java.util.List;
import java.util.Random;

/**
 * A broker for the power package.
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
public class PowerDatacenterBroker extends DatacenterBroker {

    private double cloudletCreateLastProcessTime;
    private int cloudletCreateTimes;


    /**
     * Instantiates a new power datacenter broker.
     *
     * @param name the name
     * @throws Exception the exception
     */
    public PowerDatacenterBroker(String name) throws Exception {
        super(name);
        cloudletCreateLastProcessTime = 0.0;
        cloudletCreateTimes = 1;
    }

    /*
     * (non-Javadoc)
     * @see
     * org.cloudbus.cloudsim.DatacenterBroker#processVmCreate(org.cloudbus.cloudsim.core.SimEvent)
     */
    @Override
    protected void processVmCreate(SimEvent ev) {
        int[] data = (int[]) ev.getData();
        int result = data[2];

        if (result != CloudSimTags.TRUE) {
            int datacenterId = data[0];
            int vmId = data[1];
            System.out.println(CloudSim.clock() + ": " + getName() + ": Creation of VM #" + vmId
                    + " failed in Datacenter #" + datacenterId);
            System.exit(0);
        }
        super.processVmCreate(ev);
    }

    /**
     * Submit cloudlets to the created VMs.
     *
     * @pre $none
     * @post $none
     */
    @Override
    protected void submitCloudlets() {
        int vmIndex = 0;
        int vmSize = getVmsCreatedList().size();
        Random rdm = new Random(System.currentTimeMillis());
        for (Cloudlet cloudlet : getCloudletList()) {
            Vm vm;
            vm = VmList.getById(getVmsCreatedList(), Math.abs(rdm.nextInt())%vmSize);
            if (vm == null) { // vm was not created
                Log.printLine(CloudSim.clock() + ": " + getName() + ": Postponing execution of cloudlet "
                        + cloudlet.getCloudletId() + ": bount VM not available");
                continue;
            }
            if (vm.getCurrentRequestedBw() + cloudlet.getUtilizationOfBw(CloudSim.clock())>vm.getBw())
                continue;
            if (vm.getCurrentRequestedRam() + cloudlet.getUtilizationOfRam(CloudSim.clock())>vm.getRam())
                continue;
            if (vm.getCurrentRequestedTotalMips() + cloudlet.getUtilizationOfCpu(CloudSim.clock())>vm.getMips())
                continue;



            Log.printLine(CloudSim.clock() + ": " + getName() + ": Sending cloudlet "
                    + cloudlet.getCloudletId() + " to VM #" + vm.getId());
            cloudlet.setVmId(vm.getId());
            sendNow(getVmsToDatacentersMap().get(vm.getId()), CloudSimTags.CLOUDLET_SUBMIT, cloudlet);
            cloudletsSubmitted++;
            cloudletsSubmittedTotal ++;
            vmIndex = (vmIndex + 1) % getVmsCreatedList().size();
            getCloudletSubmittedList().add(cloudlet);
        }

        // remove submitted cloudlets from waiting list
        for (Cloudlet cloudlet : getCloudletSubmittedList()) {
            getCloudletList().remove(cloudlet);
        }
    }
    @Override
    protected void processOtherEvent(SimEvent ev) {
        Log.printLine(getName() + ".other Event");
        switch (ev.getTag()) {
            case CloudSimTags.CLOUDLET_CREATE:
                processCloudletCreate(this.getCloudletList(),this.getId(),Constants.CLOUDLET_NUM);
                break;
            default:
                if (ev == null) {
                    Log.printLine(getName() + ".processOtherEvent(): Error - an event is null.");
                }
        }
    }



    private int processCloudletCreate(List<Cloudlet> list, int brokerId, int cloudletNum){

        double currentTime = CloudSim.clock();
        // if some time passed since last processing
        if (currentTime > getCloudletCreateLastProcessTime()) {
            System.out.println(currentTime + ":Cloudlet Create call...");
            long fileSize = 3000;
            long outputSize = 3000;
            UtilizationModelRandom utilizationModelRandom = new UtilizationModelRandom();
//        UtilizationModelStochastic utilizationModelStochastic = new UtilizationModelStochastic();
            int cloudletID = Constants.CLOUDLET_NUM * this.cloudletCreateTimes;

            for (int i = 0; i < cloudletNum; i++) {
                Cloudlet cloudlet = null;

                try {
                    cloudlet = new Cloudlet(
                            cloudletID + i,
                            Constants.CLOUDLET_LENGTH,
                            Constants.CLOUDLET_PES,
                            fileSize,
                            outputSize,
                            utilizationModelRandom,
                            utilizationModelRandom,
                            utilizationModelRandom
                    );

                } catch (Exception e) {
                    e.printStackTrace();
                    System.exit(0);
                }
                cloudlet.setUserId(brokerId);
                list.add(cloudlet);
            }

        }
        this.cloudletCreateTimes ++;

        submitCloudlets();

        // schedules an event to the next time
        CloudSim.cancelAll(getId(), new PredicateType(CloudSimTags.CLOUDLET_CREATE));
        schedule(getId(), Constants.CLOUDLET_CREATE_INTERVAL, CloudSimTags.CLOUDLET_CREATE);

        setCloudletCreateLastProcessTime(currentTime);
        return 1;
    }

    public int getCloudletCreateTimes() {
        return cloudletCreateTimes;
    }

    public double getCloudletCreateLastProcessTime() {
        return cloudletCreateLastProcessTime;
    }

    public void setCloudletCreateLastProcessTime(double cloudletCreateLastProcessTime) {
        this.cloudletCreateLastProcessTime = cloudletCreateLastProcessTime;
    }
}
