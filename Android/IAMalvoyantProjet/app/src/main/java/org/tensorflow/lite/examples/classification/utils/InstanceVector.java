package org.tensorflow.lite.examples.classification.utils;


import java.io.Serializable;
import java.util.Arrays;
import java.util.Vector;

public class InstanceVector implements Serializable {

    private static final long serialVersionUID = -5802571941999463497L;

    private static final int VECTOR_SIZE = 1001;

    private String instanceName;
    private Vector<Double> vector;

    public InstanceVector(String name) {
        this.instanceName = name;
        Double array[] = new Double[VECTOR_SIZE];
        Arrays.fill(array, 0d);
        this.vector = new Vector<>(Arrays.asList(array));
    }

    public String getInstanceName() {
        return instanceName;
    }

    public void setInstanceName(String instanceName) {
        this.instanceName = instanceName;
    }

    public Vector<Double> getVector() {
        return vector;
    }

    public void setVectorValue(int index, Double value) {
        this.vector.setElementAt(value, index);
    }

}
