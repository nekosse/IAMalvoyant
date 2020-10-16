package org.tensorflow.lite.examples.classification.utils;

import java.util.Vector;

public abstract class EuclidianCalculator {

    public static double euclidianDistance(Vector<Double> v1, Vector<Double> v2){
        if(v1.size() != v2.size())
            throw new IllegalArgumentException("Both vectors should have the same size");
        double sum = 0d;
        for(int i=0; i < v1.size(); i++){
            sum += Math.pow(v1.get(i) - v2.get(i) ,2);
        }
        return Math.sqrt(sum);
    }

}
