/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package org.wso2.carbon.ml.core.spark.summary;

import java.io.Serializable;
import java.util.Arrays;
import java.util.List;
import org.wso2.carbon.ml.commons.constants.MLConstants;
import org.wso2.carbon.ml.commons.domain.ModelSummary;

/**
 *
 * @author Thush
 */
public class DeeplearningModelSummary implements ModelSummary, Serializable{
    
    private double error;
    private List<PredictedVsActual> predictedVsActuals;
    private List<TestResultDataPoint> testResultDataPointsSample;
    private List<FeatureImportance> featureImportance;
    private String algorithm;
    private String[] features;
    private double modelAccuracy;
    private double meanSquaredError;

        public String getAlgorithm() {
        return algorithm;
    }

    public void setAlgorithm(String algorithm) {
        this.algorithm = algorithm;
    }

    /**
     * @return Returns classification error
     */
    public double getError() {
        return error;
    }

    /**
     * @param error Sets classification error
     */
    public void setError(double error) {
        this.error = error;
    }

    /**
     * @return Returns predicted vs. actual labels
     */
    public List<PredictedVsActual> getPredictedVsActuals() {
        return predictedVsActuals;
    }

    /**
     * @param predictedVsActuals Sets predicted vs. actual labels
     */
    public void setPredictedVsActuals(List<PredictedVsActual> predictedVsActuals) {
        this.predictedVsActuals = predictedVsActuals;
    }

    /**
     * @return Returns a list of features with predicted vs. actual values
     */
    public List<TestResultDataPoint> getTestResultDataPointsSample() {
        return testResultDataPointsSample;
    }

    /**
     * @param testResultDataPointsSample Sets features with predicted vs. actual values
     */
    public void setTestResultDataPointsSample(List<TestResultDataPoint> testResultDataPointsSample) {
        this.testResultDataPointsSample = testResultDataPointsSample;
    }

    @Override
    public String getModelSummaryType() {
        return MLConstants.DEEPLEARNING_MODEL_SUMMARY;
    }

    
    /**
     * @return Weights of each of the feature
     */
    public List<FeatureImportance> getFeatureImportance() {
        return featureImportance;
    }

    /**
     * @param featureImportance Weights of each of the feature
     */
    public void setFeatureImportance(List<FeatureImportance> featureImportance) {
        this.featureImportance = featureImportance;
    }

    /**
     * @param features Array of names of the features
     */
    public void setFeatures(String[] features) {
        if (features == null) {
            this.features = new String[0];
        } else {
            this.features = Arrays.copyOf(features, features.length);
        }
    }

    /**
     * @return Returns model accuracy
     */
    public double getModelAccuracy() {
        return modelAccuracy;
    }

    /**
     * @param modelAccuracy Sets model accuracy
     */
    public void setModelAccuracy(double modelAccuracy) {
        this.modelAccuracy = modelAccuracy;
    }

    /**
     * @return Returns mean squared error
     */
    public double getMeanSquaredError() {
        return meanSquaredError;
    }

    /**
     * @param meanSquaredError Sets mean squared error
     */
    public void setMeanSquaredError(double meanSquaredError) {
        this.meanSquaredError = meanSquaredError;
    }

    @Override
    public String[] getFeatures() {
        return features;
    }

}