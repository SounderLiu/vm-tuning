package org.cloudbus.cloudsim.examples.power.planetlab;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.List;

import org.cloudbus.cloudsim.*;
import org.cloudbus.cloudsim.examples.power.Constants;

/**
 * A helper class for the running examples for the PlanetLab workload.
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
 * @since Jan 5, 2012
 */
public class TuningHelper {

    /**
     * Creates the cloudlet list planet lab.
     *
     * @param brokerId the broker id
     * @param cloudletNum the number of cloudlet
     * @return the list
     * @throws java.io.FileNotFoundException the file not found exception
     */
    public static List<Cloudlet> createCloudletListPlanetLab(int brokerId, int cloudletNum)
            throws FileNotFoundException {
        List<Cloudlet> list = new ArrayList<Cloudlet>();

        long fileSize = 3000;
        long outputSize = 3000;
        UtilizationModelRandom utilizationModelRandom = new UtilizationModelRandom();
//        UtilizationModelStochastic utilizationModelStochastic = new UtilizationModelStochastic();


        for (int i = 0; i < cloudletNum; i++) {
            Cloudlet cloudlet = null;
            try {
                cloudlet = new Cloudlet(
                        i,
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

        return list;
    }

}
