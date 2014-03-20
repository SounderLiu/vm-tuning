package org.cloudbus.cloudsim.power;

import java.util.*;
import org.cloudbus.cloudsim.Host;
import org.cloudbus.cloudsim.Log;
import org.cloudbus.cloudsim.Vm;
import org.cloudbus.cloudsim.examples.power.Constants;
import org.cloudbus.cloudsim.examples.power.planetlab.PlanetLabConstants;
import org.cloudbus.cloudsim.util.ExecutionTimeMeasurer;

/**
 * Created by Sounder Liu on 14-1-17.
 */

public class PowerVmAllocationPolicyMigrationGlobalTuning extends
        PowerVmAllocationPolicyMigrationAbstract {

    Vector<Vector<Double>> cpuCost;
    Vector<Vector<Double>> memCost;
    Vector<Vector<Double>> netCost;
    Vector<Vector<Double>> totalCost;

    public PowerVmAllocationPolicyMigrationGlobalTuning(
            List<? extends Host> hostList,
            PowerVmSelectionPolicy vmSelectionPolicy ) {
        super(hostList, vmSelectionPolicy);

        cpuCost = null;
        memCost = null;
        netCost = null;
        totalCost = null;
    }

    @Override
    protected boolean isHostOverUtilized(PowerHost host) { return false; }

    @Override
    public List<Map<String, Object>> optimizeAllocation(List<? extends Vm> vmList) {
        ExecutionTimeMeasurer.start("optimizeAllocationTotal");

//        saveAllocation();

        ExecutionTimeMeasurer.start("optimizeAllocationCalculateCost");
        cpuCost = calCPU();
        memCost = calMem();
        netCost = calNet();
        totalCost = calCost(cpuCost, memCost, netCost);
        getExecutionTimeHistoryVmReallocation().add(
                ExecutionTimeMeasurer.end("optimizeAllocationCalculateCost"));


        ExecutionTimeMeasurer.start("optimizeAllocationVmReallocation");
        List<Map<String, Object>> migrationMap = getMigratedMap();
        getExecutionTimeHistoryVmReallocation().add(
                ExecutionTimeMeasurer.end("optimizeAllocationVmReallocation"));

        Log.printLine();


//        restoreAllocation();

        getExecutionTimeHistoryTotal().add(ExecutionTimeMeasurer.end("optimizeAllocationTotal"));

        return migrationMap;
    }

    protected List<Map<String, Object>> getMigratedMap(){
        double max_cost_diff = 0.0;
        int fromHost = -1;
        int toHost = -1;
        int peekVM = -1;

        List<Map<String, Object>> migrationMap = new LinkedList<Map<String, Object>>();

        Vector<Vector<Double>> cost = calCost(calCPU(), calMem(), calNet());

        int hostCount = 0;
        for(Host host : this.getHostList()){
            int vmCount = 0;
            for(Vm vm: host.getVmList()){
                double current_cost = cost.get(hostCount).get(vmCount);
                int newHostCount = 0;
                for(Host newHost : this.getHostList()){
                    double target_cost = 0.0;
                    target_cost = calNewCost(hostCount, newHostCount, vmCount);
                    double cost_diff = current_cost - target_cost;
                    if (cost_diff > max_cost_diff){
                        max_cost_diff = cost_diff;
                        fromHost = hostCount;
                        toHost = newHostCount;
                        peekVM = vmCount;
                    }

                    newHostCount++;
                }
                vmCount++;

            }
            hostCount ++;
        }
        if(max_cost_diff > Constants.MIGRATION_THR){
            Map<String, Object> migrate = new HashMap<String, Object>();
            migrate.put("vm",this.getHostList().get(fromHost).getVmList().get(peekVM) );
            migrate.put("host", this.getHostList().get(toHost));
            migrationMap.add(migrate);
        }


        return  migrationMap;
    }


    private double calNewCost(int fromHostID, int  toHostID, int peekVMID){
        Host fromHost = this.getHostList().get(fromHostID);
        Host toHost = this.getHostList().get(toHostID);
        Vm peekVm = fromHost.getVmList().get(peekVMID);

        double cpuCost = 0.0;
        double memCost =0.0 ;
        double netCost = 0.0;



        double cpuSecond = 0.0;
        double memSecond = 0.0;
        double netSecond = 0.0;

        for(int counter = 0; counter < toHost.getVmList().size(); counter ++){
            cpuSecond += this.cpuCost.get(toHostID).get(counter);
            memSecond += this.memCost.get(toHostID).get(counter);
            netSecond += this.netCost.get(toHostID).get(counter);

        }
        double cpuFirst = (fromHost.getTotalMips()/toHost.getTotalMips())* this.cpuCost.get(fromHostID).get(peekVMID) + cpuSecond;


                cpuCost = Math.pow(PlanetLabConstants.NUMBER_OF_HOSTS, cpuFirst)  -
                Math.pow(PlanetLabConstants.NUMBER_OF_HOSTS, cpuSecond);

        memCost = Math.pow(PlanetLabConstants.NUMBER_OF_HOSTS, this.memCost.get(fromHostID).get(peekVMID)/toHost.getRam())  -
                Math.pow(PlanetLabConstants.NUMBER_OF_HOSTS, memSecond/toHost.getRam());

        netCost = Math.pow(PlanetLabConstants.NUMBER_OF_HOSTS, this.netCost.get(fromHostID).get(peekVMID)/toHost.getBw())  -
                Math.pow(PlanetLabConstants.NUMBER_OF_HOSTS, netSecond/toHost.getBw());


        return cpuCost + memCost + netCost ;
    }


    private Vector<Vector<Double>> calCost( Vector<Vector<Double> > cpuUsage,
                                            Vector<Vector<Double> > memUsage,
                                            Vector<Vector<Double> > netUsage){
        Vector<Vector<Double> > totalCost = new Vector<Vector<Double>>();


        int i = 0;
        for(Host host : this.getHostList()){
            PowerHost powerHost = (PowerHost) host;
            Vector<Double> hostCost = new Vector<Double>();
            int j = 0;

            for(Vm vm : powerHost.getVmList()){
                double cpuFirst = 0.0;
                double cpuSecond = 0.0;
                double memFirst = 0.0;
                double memSecond = 0.0;
                double netFirst = 0.0;
                double netSecond = 0.0;

                for(int counter = 0; counter < powerHost.getVmList().size(); counter ++){
                    if(counter == j)
                    {
                        cpuFirst = cpuUsage.get(i).get(counter);
                        memFirst = memUsage.get(i).get(counter);
                        netFirst = netUsage.get(i).get(counter);
                    }
                    else{
                        cpuSecond += cpuUsage.get(i).get(counter);
                        memSecond += memUsage.get(i).get(counter);
                        netSecond += netUsage.get(i).get(counter);
                    }
                }

                Double cpuCost = Math.pow(PlanetLabConstants.NUMBER_OF_HOSTS, cpuFirst)  -
                        Math.pow(PlanetLabConstants.NUMBER_OF_HOSTS, cpuSecond);

                Double memCost = Math.pow(PlanetLabConstants.NUMBER_OF_HOSTS, memFirst/powerHost.getRam())  -
                        Math.pow(PlanetLabConstants.NUMBER_OF_HOSTS, memSecond/powerHost.getRam());

                Double netCost = Math.pow(PlanetLabConstants.NUMBER_OF_HOSTS, netFirst/powerHost.getBw())  -
                        Math.pow(PlanetLabConstants.NUMBER_OF_HOSTS, netSecond/powerHost.getBw());

                hostCost.add(cpuCost + memCost + netCost);
                j++;
            }
            totalCost.add(hostCost);
            i++;
        }
        return totalCost;
    }

    private Vector<Vector<Double>> calCPU(){
        Vector<Vector<Double> > cpuUsage = new Vector<Vector<Double>>();
        for(Host host : this.getHostList()){
            PowerHost powerHost = (PowerHost) host;
            Vector <Double> hostCPU = new Vector<Double>();
            for(Vm vm : powerHost.getVmList()){
                PowerVm powerVm = (PowerVm) vm;

                Double cpu = powerVm.getCpuUsageTime()/powerVm.getMips()/(Constants.GLOBAL_INTERVIAL * Constants.HOST_PES[Constants.HOST_TUNING_TYPE]);
                hostCPU.add(cpu);
            }
            cpuUsage.add(hostCPU);
        }
        return cpuUsage;
    }

    private Vector<Vector<Double>> calMem(){
        Vector<Vector<Double> > memUsage = new Vector<Vector<Double>>();
        for(Host host : this.getHostList()){
            PowerHost powerHost = (PowerHost) host;
            Vector <Double> hostMem = new Vector<Double>();
            for(Vm vm : powerHost.getVmList()){
                PowerVm powerVm = (PowerVm) vm;
                Double mem =  (double)powerVm.getCurrentRequestedRam();


                hostMem.add(mem);
            }
            memUsage.add(hostMem);
        }
        return memUsage;
    }

    private Vector<Vector<Double>> calNet(){
        Vector<Vector<Double> > netUsage = new Vector<Vector<Double>>();
        for(Host host : this.getHostList()){
            PowerHost powerHost = (PowerHost) host;
            Vector <Double> hostNet = new Vector<Double>();
            for(Vm vm : powerHost.getVmList()){
                PowerVm powerVm = (PowerVm) vm;
                Double net =  (double)powerVm.getCurrentRequestedBw();
                hostNet.add(net);
            }
            netUsage.add(hostNet);
        }
        return netUsage;
    }
}
