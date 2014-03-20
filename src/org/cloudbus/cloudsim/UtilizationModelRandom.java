package org.cloudbus.cloudsim;

import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.HashMap;
import java.util.Map;
import java.util.Random;


/**
 * Created by Sounder Liu on 14-1-24.
 */
public class UtilizationModelRandom implements UtilizationModel {


    /** The random generator. */
    private Random randomGenerator;

    /** The history. */
    private Map<Double, Double> history;

    /**
     * Instantiates a new utilization model stochastic.
     */
    public UtilizationModelRandom() {
        setHistory(new HashMap<Double, Double>());
        setRandomGenerator(new Random(System.currentTimeMillis()));
    }

    /**
     * Instantiates a new utilization model stochastic.
     *
     * @param seed the seed
     */
    public UtilizationModelRandom(long seed) {
        setHistory(new HashMap<Double, Double>());
        setRandomGenerator(new Random(seed));
    }

    /*
     * (non-Javadoc)
     * @see cloudsim.power.UtilizationModel#getUtilization(double)
     */
    @Override
    public double getUtilization(double time) {
        if (getHistory().containsKey(time)) {
            return getHistory().get(time);
        }

        double utilization = (double)getRandomGenerator().nextInt(100)/1000;
        getHistory().put(time, utilization);
        return utilization;
    }

    /**
     * Gets the history.
     *
     * @return the history
     */
    protected Map<Double, Double> getHistory() {
        return history;
    }

    /**
     * Sets the history.
     *
     * @param history the history
     */
    protected void setHistory(Map<Double, Double> history) {
        this.history = history;
    }

    /**
     * Save history.
     *
     * @param filename the filename
     * @throws Exception the exception
     */
    public void saveHistory(String filename) throws Exception {
        FileOutputStream fos = new FileOutputStream(filename);
        ObjectOutputStream oos = new ObjectOutputStream(fos);
        oos.writeObject(getHistory());
        oos.close();
    }

    /**
     * Load history.
     *
     * @param filename the filename
     * @throws Exception the exception
     */
    @SuppressWarnings("unchecked")
    public void loadHistory(String filename) throws Exception {
        FileInputStream fis = new FileInputStream(filename);
        ObjectInputStream ois = new ObjectInputStream(fis);
        setHistory((Map<Double, Double>) ois.readObject());
        ois.close();
    }

    /**
     * Sets the random generator.
     *
     * @param randomGenerator the new random generator
     */
    public void setRandomGenerator(Random randomGenerator) {
        this.randomGenerator = randomGenerator;
    }

    /**
     * Gets the random generator.
     *
     * @return the random generator
     */
    public Random getRandomGenerator() {
        return randomGenerator;
    }

}

