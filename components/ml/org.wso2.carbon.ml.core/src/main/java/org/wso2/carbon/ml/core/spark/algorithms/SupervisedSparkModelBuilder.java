/*
 * Copyright (c) 2014-2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * WSO2 Inc. licenses this file to you under the Apache License,
 * Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

package org.wso2.carbon.ml.core.spark.algorithms;

import org.apache.spark.api.java.JavaPairRDD;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.mllib.classification.LogisticRegressionModel;
import org.apache.spark.mllib.classification.NaiveBayesModel;
import org.apache.spark.mllib.classification.SVMModel;
import org.apache.spark.mllib.evaluation.MulticlassMetrics;
import org.apache.spark.mllib.evaluation.RegressionMetrics;
import org.apache.spark.mllib.linalg.Vector;
import org.apache.spark.mllib.regression.LabeledPoint;
import org.apache.spark.mllib.regression.LassoModel;
import org.apache.spark.mllib.regression.LinearRegressionModel;
import org.apache.spark.mllib.regression.RidgeRegressionModel;
import org.apache.spark.mllib.tree.model.DecisionTreeModel;
import org.apache.spark.mllib.tree.model.RandomForestModel;
import org.apache.spark.rdd.RDD;
import org.wso2.carbon.ml.commons.constants.MLConstants;
import org.wso2.carbon.ml.commons.constants.MLConstants.SUPERVISED_ALGORITHM;
import org.wso2.carbon.ml.commons.domain.*;
import org.wso2.carbon.ml.core.exceptions.AlgorithmNameException;
import org.wso2.carbon.ml.core.exceptions.MLModelBuilderException;
import org.wso2.carbon.ml.core.factories.AlgorithmType;
import org.wso2.carbon.ml.core.interfaces.MLModelBuilder;
import org.wso2.carbon.ml.core.internal.MLModelConfigurationContext;
import org.wso2.carbon.ml.core.spark.MulticlassConfusionMatrix;
import org.wso2.carbon.ml.core.spark.models.MLClassificationModel;
import org.wso2.carbon.ml.core.spark.models.MLDecisionTreeModel;
import org.wso2.carbon.ml.core.spark.models.MLGeneralizedLinearModel;
import org.wso2.carbon.ml.core.spark.models.MLRandomForestModel;
import org.wso2.carbon.ml.core.spark.summary.ClassClassificationAndRegressionModelSummary;
import org.wso2.carbon.ml.core.spark.summary.FeatureImportance;
import org.wso2.carbon.ml.core.spark.summary.ProbabilisticClassificationModelSummary;
import org.wso2.carbon.ml.core.spark.transformations.*;
import org.wso2.carbon.ml.core.utils.MLCoreServiceValueHolder;
import org.wso2.carbon.ml.core.utils.MLUtils;
import org.wso2.carbon.ml.database.DatabaseService;
import org.wso2.carbon.ml.database.exceptions.DatabaseHandlerException;
import scala.Tuple2;

import java.text.DecimalFormat;
import java.util.*;

/**
 * Build supervised models supported by Spark.
 */
public class SupervisedSparkModelBuilder extends MLModelBuilder {

    public SupervisedSparkModelBuilder(MLModelConfigurationContext context) {
        super(context);
    }


    public JavaRDD<LabeledPoint> preProcess() throws MLModelBuilderException {
        JavaRDD<String> lines = null;
        try {
            MLModelConfigurationContext context = getContext();
            HeaderFilter headerFilter = new HeaderFilter.Builder().init(context).build();
            LineToTokens lineToTokens = new LineToTokens.Builder().init(context).build();
            DiscardedRowsFilter discardedRowsFilter = new DiscardedRowsFilter.Builder().init(context).build();
            RemoveDiscardedFeatures removeDiscardedFeatures = new RemoveDiscardedFeatures.Builder().init(context).build();
            BasicEncoder basicEncoder = new BasicEncoder.Builder().init(context).build();
            MeanImputation meanImputation = new MeanImputation.Builder().init(context).build();
            StringArrayToDoubleArray stringArrayToDoubleArray = new StringArrayToDoubleArray.Builder().build();
            DoubleArrayToLabeledPoint doubleArrayToLabeledPoint = new DoubleArrayToLabeledPoint.Builder().build();

            lines = context.getLines().cache();
            return lines.filter(headerFilter).map(lineToTokens).filter(discardedRowsFilter)
                    .map(removeDiscardedFeatures).map(basicEncoder).map(meanImputation).map(stringArrayToDoubleArray)
                    .map(doubleArrayToLabeledPoint);
        } finally {
            if (lines != null) {
                lines.unpersist();
            }
        }
    }

    /**
     * Build a supervised model.
     */


    public MLModel build() throws MLModelBuilderException {
        MLModelConfigurationContext context = getContext();
        JavaSparkContext sparkContext = null;
        DatabaseService databaseService = MLCoreServiceValueHolder.getInstance().getDatabaseService();
        MLModel mlModel = new MLModel();
        try {
            sparkContext = context.getSparkContext();
            Workflow workflow = context.getFacts();
            long modelId = context.getModelId();

            // Verify validity of response variable
            String typeOfResponseVariable = getTypeOfResponseVariable(workflow.getResponseVariable(),
                    workflow.getFeatures());

            if (typeOfResponseVariable == null) {
                throw new MLModelBuilderException("Type of response variable cannot be null for supervised learning "
                        + "algorithms.");
            }

            // Stops model building if a categorical attribute is used with numerical prediction
            if (workflow.getAlgorithmClass().equals(AlgorithmType.NUMERICAL_PREDICTION.getValue())
                    && typeOfResponseVariable.equals(FeatureType.CATEGORICAL)) {
                throw new MLModelBuilderException("Categorical attribute " + workflow.getResponseVariable()
                        + " cannot be used as the response variable of the Numerical Prediction algorithm: "
                        + workflow.getAlgorithmName());
            }

            // generate train and test datasets by converting tokens to labeled points
            int responseIndex = context.getResponseIndex();
            SortedMap<Integer, String> includedFeatures = MLUtils.getIncludedFeaturesAfterReordering(workflow,
                    context.getNewToOldIndicesList(), responseIndex);

            // gets the pre-processed dataset
            JavaRDD<LabeledPoint> labeledPoints = preProcess().cache();

            JavaRDD<LabeledPoint>[] dataSplit = labeledPoints.randomSplit(
                    new double[]{workflow.getTrainDataFraction(), 1 - workflow.getTrainDataFraction()},
                    MLConstants.RANDOM_SEED);

            // remove from cache
            labeledPoints.unpersist();

            JavaRDD<LabeledPoint> trainingData = dataSplit[0].cache();
            JavaRDD<LabeledPoint> testingData = dataSplit[1];
            // create a deployable MLModel object
            mlModel.setAlgorithmName(workflow.getAlgorithmName());
            mlModel.setAlgorithmClass(workflow.getAlgorithmClass());
            mlModel.setFeatures(workflow.getIncludedFeatures());
            mlModel.setResponseVariable(workflow.getResponseVariable());
            mlModel.setEncodings(context.getEncodings());
            mlModel.setNewToOldIndicesList(context.getNewToOldIndicesList());
            mlModel.setResponseIndex(responseIndex);

            ModelSummary summaryModel = null;
            Map<Integer, Integer> categoricalFeatureInfo;

            // build a machine learning model according to user selected algorithm
            SUPERVISED_ALGORITHM supervisedAlgorithm = SUPERVISED_ALGORITHM.valueOf(workflow.getAlgorithmName());
            switch (supervisedAlgorithm) {
                case LOGISTIC_REGRESSION:
                    summaryModel = buildLogisticRegressionModel(sparkContext, modelId, trainingData, testingData, workflow,
                            mlModel, includedFeatures, true);
                    break;
                case LOGISTIC_REGRESSION_LBFGS:
                    summaryModel = buildLogisticRegressionModel(sparkContext, modelId, trainingData, testingData, workflow,
                            mlModel, includedFeatures, false);
                    break;
                case DECISION_TREE:
                    categoricalFeatureInfo = getCategoricalFeatureInfo(context.getEncodings());
                    summaryModel = buildDecisionTreeModel(sparkContext, modelId, trainingData, testingData, workflow,
                            mlModel, includedFeatures, categoricalFeatureInfo);
                    break;
                case RANDOM_FOREST_CLASSIFICATION:
                    categoricalFeatureInfo = getCategoricalFeatureInfo(context.getEncodings());
                    summaryModel = buildRandomForestClassificationModel(sparkContext, modelId, trainingData, testingData, workflow,
                            mlModel, includedFeatures, categoricalFeatureInfo);
                    break;
                case SVM:
                    summaryModel = buildSVMModel(sparkContext, modelId, trainingData, testingData, workflow, mlModel,
                            includedFeatures);
                    break;
                case NAIVE_BAYES:
                    summaryModel = buildNaiveBayesModel(sparkContext, modelId, trainingData, testingData, workflow,
                            mlModel, includedFeatures);
                    break;
                case LINEAR_REGRESSION:
                    summaryModel = buildLinearRegressionModel(sparkContext, modelId, trainingData, testingData, workflow,
                            mlModel, includedFeatures);
                    break;
                case RIDGE_REGRESSION:
                    summaryModel = buildRidgeRegressionModel(sparkContext, modelId, trainingData, testingData, workflow,
                            mlModel, includedFeatures);
                    break;
                case LASSO_REGRESSION:
                    summaryModel = buildLassoRegressionModel(sparkContext, modelId, trainingData, testingData, workflow,
                            mlModel, includedFeatures);
                    break;
                case RANDOM_FOREST_REGRESSION:
                    categoricalFeatureInfo = getCategoricalFeatureInfo(context.getEncodings());
                    summaryModel = buildRandomForestRegressionModel(sparkContext, modelId, trainingData, testingData, workflow,
                            mlModel, includedFeatures, categoricalFeatureInfo);
                    break;
                case STACKING:
                    summaryModel = buildStackingModel(context, sparkContext, modelId, trainingData, testingData, workflow,
                            mlModel, includedFeatures);
                    break;
                case BAGGING:
                    summaryModel = buildBaggingModel(context, sparkContext, modelId, trainingData, testingData, workflow,
                            mlModel, includedFeatures);
                    break;

                default:
                    throw new AlgorithmNameException("Incorrect algorithm name: " + supervisedAlgorithm.toString());
            }

            // persist model summary
            databaseService.updateModelSummary(modelId, summaryModel);
            return mlModel;
        } catch (DatabaseHandlerException e) {
            throw new MLModelBuilderException("An error occurred while building supervised machine learning model: "
                    + e.getMessage(), e);
        }
    }

    private String getTypeOfResponseVariable(String responseVariable, List<Feature> features) {
        String type = null;
        for (Feature feature : features) {
            if (feature.getName().equals(responseVariable)) {
                type = feature.getType();
            }
        }
        return type;
    }

    private Map<Integer, Integer> getCategoricalFeatureInfo(List<Map<String, Integer>> encodings) {
        Map<Integer, Integer> info = new HashMap<Integer, Integer>();
        // skip the response variable which is at last
        for (int i = 0; i < encodings.size() - 1; i++) {
            if (encodings.get(i).size() > 0) {
                info.put(i, encodings.get(i).size());
            }
        }
        return info;
    }

    /**
     * This method builds a logistic regression model
     *
     * @param sparkContext JavaSparkContext initialized with the application
     * @param modelID      Model ID
     * @param trainingData Training data as a JavaRDD of LabeledPoints
     * @param testingData  Testing data as a JavaRDD of LabeledPoints
     * @param workflow     Machine learning workflow
     * @param mlModel      Deployable machine learning model
     * @param isSGD        Whether the algorithm is Logistic regression with SGD
     * @throws MLModelBuilderException
     */
    private ModelSummary buildLogisticRegressionModel(JavaSparkContext sparkContext, long modelID,
                                                      JavaRDD<LabeledPoint> trainingData, JavaRDD<LabeledPoint> testingData, Workflow workflow, MLModel mlModel,
                                                      SortedMap<Integer, String> includedFeatures, boolean isSGD) throws MLModelBuilderException {
        try {
            LogisticRegression logisticRegression = new LogisticRegression();
            Map<String, String> hyperParameters = workflow.getHyperParameters();
            LogisticRegressionModel logisticRegressionModel;
            String algorithmName;

            int noOfClasses = getNoOfClasses(mlModel);

            if (isSGD) {
                algorithmName = SUPERVISED_ALGORITHM.LOGISTIC_REGRESSION.toString();

                if (noOfClasses > 2) {
                    throw new MLModelBuilderException("A binary classification algorithm cannot have more than "
                            + "two distinct values in response variable.");
                }

                logisticRegressionModel = logisticRegression.trainWithSGD(trainingData,
                        Double.parseDouble(hyperParameters.get(MLConstants.LEARNING_RATE)),
                        Integer.parseInt(hyperParameters.get(MLConstants.ITERATIONS)),
                        hyperParameters.get(MLConstants.REGULARIZATION_TYPE),
                        Double.parseDouble(hyperParameters.get(MLConstants.REGULARIZATION_PARAMETER)),
                        Double.parseDouble(hyperParameters.get(MLConstants.SGD_DATA_FRACTION)));
            } else {
                algorithmName = SUPERVISED_ALGORITHM.LOGISTIC_REGRESSION_LBFGS.toString();
                logisticRegressionModel = logisticRegression.trainWithLBFGS(trainingData,
                        hyperParameters.get(MLConstants.REGULARIZATION_TYPE), noOfClasses);
            }

            // remove from cache
            trainingData.unpersist();
            // add test data to cache
            testingData.cache();

            Vector weights = logisticRegressionModel.weights();
            if (!isValidWeights(weights)) {
                throw new MLModelBuilderException("Weights of the model generated are null or infinity. [Weights] "
                        + vectorToString(weights));
            }

            // getting scores and labels without clearing threshold to get confusion matrix
            JavaRDD<Tuple2<Object, Object>> scoresAndLabelsThresholded = logisticRegression.test(
                    logisticRegressionModel, testingData);
            MulticlassMetrics multiclassMetrics = new MulticlassMetrics(JavaRDD.toRDD(scoresAndLabelsThresholded));
            MulticlassConfusionMatrix multiclassConfusionMatrix = getMulticlassConfusionMatrix(multiclassMetrics,
                    mlModel);

            // clearing the threshold value to get a probability as the output of the prediction
            logisticRegressionModel.clearThreshold();
            JavaRDD<Tuple2<Object, Object>> scoresAndLabels = logisticRegression.test(logisticRegressionModel,
                    testingData);
            ProbabilisticClassificationModelSummary probabilisticClassificationModelSummary = SparkModelUtils
                    .generateProbabilisticClassificationModelSummary(sparkContext, testingData, scoresAndLabels);
            mlModel.setModel(new MLClassificationModel(logisticRegressionModel));

            // remove from cache
            testingData.unpersist();

            List<FeatureImportance> featureWeights = getFeatureWeights(includedFeatures, logisticRegressionModel
                    .weights().toArray());
            probabilisticClassificationModelSummary.setFeatures(includedFeatures.values().toArray(new String[0]));
            probabilisticClassificationModelSummary.setFeatureImportance(featureWeights);
            probabilisticClassificationModelSummary.setAlgorithm(algorithmName);

            probabilisticClassificationModelSummary.setMulticlassConfusionMatrix(multiclassConfusionMatrix);
            Double modelAccuracy = getModelAccuracy(multiclassMetrics);
            probabilisticClassificationModelSummary.setModelAccuracy(modelAccuracy);
            probabilisticClassificationModelSummary.setDatasetVersion(workflow.getDatasetVersion());

            return probabilisticClassificationModelSummary;
        } catch (Exception e) {
            throw new MLModelBuilderException("An error occurred while building logistic regression model: "
                    + e.getMessage(), e);
        }
    }

    private int getNoOfClasses(MLModel mlModel) {
        if (mlModel.getEncodings() == null) {
            return -1;
        }
        int responseIndex = mlModel.getEncodings().size() - 1;
        return mlModel.getEncodings().get(responseIndex) != null ? mlModel.getEncodings().get(responseIndex).size()
                : -1;

    }

    /**
     * This method builds a decision tree model
     *
     * @param sparkContext JavaSparkContext initialized with the application
     * @param modelID      Model ID
     * @param trainingData Training data as a JavaRDD of LabeledPoints
     * @param testingData  Testing data as a JavaRDD of LabeledPoints
     * @param workflow     Machine learning workflow
     * @param mlModel      Deployable machine learning model
     * @throws MLModelBuilderException
     */
    private ModelSummary buildDecisionTreeModel(JavaSparkContext sparkContext, long modelID,
                                                JavaRDD<LabeledPoint> trainingData, JavaRDD<LabeledPoint> testingData, Workflow workflow, MLModel mlModel,
                                                SortedMap<Integer, String> includedFeatures, Map<Integer, Integer> categoricalFeatureInfo)
            throws MLModelBuilderException {
        try {
            Map<String, String> hyperParameters = workflow.getHyperParameters();
            DecisionTree decisionTree = new DecisionTree();
            DecisionTreeModel decisionTreeModel = decisionTree.train(trainingData, getNoOfClasses(mlModel),
                    categoricalFeatureInfo, hyperParameters.get(MLConstants.IMPURITY),
                    Integer.parseInt(hyperParameters.get(MLConstants.MAX_DEPTH)),
                    Integer.parseInt(hyperParameters.get(MLConstants.MAX_BINS)));

            // remove from cache
            trainingData.unpersist();
            // add test data to cache
            testingData.cache();

            JavaPairRDD<Double, Double> predictionsAndLabels = decisionTree.test(decisionTreeModel, testingData)
                    .cache();
            ClassClassificationAndRegressionModelSummary classClassificationAndRegressionModelSummary = SparkModelUtils
                    .getClassClassificationModelSummary(sparkContext, testingData, predictionsAndLabels);

            // remove from cache
            testingData.unpersist();

            mlModel.setModel(new MLDecisionTreeModel(decisionTreeModel));

            classClassificationAndRegressionModelSummary.setFeatures(includedFeatures.values().toArray(new String[0]));
            classClassificationAndRegressionModelSummary.setAlgorithm(SUPERVISED_ALGORITHM.DECISION_TREE.toString());

            MulticlassMetrics multiclassMetrics = getMulticlassMetrics(sparkContext, predictionsAndLabels);

            predictionsAndLabels.unpersist();

            classClassificationAndRegressionModelSummary.setMulticlassConfusionMatrix(getMulticlassConfusionMatrix(
                    multiclassMetrics, mlModel));
            Double modelAccuracy = getModelAccuracy(multiclassMetrics);

            classClassificationAndRegressionModelSummary.setModelAccuracy(modelAccuracy);
            classClassificationAndRegressionModelSummary.setDatasetVersion(workflow.getDatasetVersion());

            return classClassificationAndRegressionModelSummary;
        } catch (Exception e) {
            throw new MLModelBuilderException(
                    "An error occurred while building decision tree model: " + e.getMessage(), e);
        }

    }

    private ModelSummary buildRandomForestClassificationModel(JavaSparkContext sparkContext, long modelID,
                                                              JavaRDD<LabeledPoint> trainingData, JavaRDD<LabeledPoint> testingData, Workflow workflow, MLModel mlModel,
                                                              SortedMap<Integer, String> includedFeatures, Map<Integer, Integer> categoricalFeatureInfo)
            throws MLModelBuilderException {
        try {
            Map<String, String> hyperParameters = workflow.getHyperParameters();
            RandomForestClassifier randomForestClassifier = new RandomForestClassifier();
            final RandomForestModel randomForestModel = randomForestClassifier.train(trainingData, getNoOfClasses(mlModel),
                    categoricalFeatureInfo, Integer.parseInt(hyperParameters.get(MLConstants.NUM_TREES)),
                    hyperParameters.get(MLConstants.FEATURE_SUBSET_STRATEGY),
                    hyperParameters.get(MLConstants.IMPURITY),
                    Integer.parseInt(hyperParameters.get(MLConstants.MAX_DEPTH)),
                    Integer.parseInt(hyperParameters.get(MLConstants.MAX_BINS)),
                    Integer.parseInt(hyperParameters.get(MLConstants.SEED)));

            // remove from cache
            trainingData.unpersist();
            // add test data to cache
            testingData.cache();

            JavaPairRDD<Double, Double> predictionsAndLabels = randomForestClassifier.test(randomForestModel, testingData).cache();
            ClassClassificationAndRegressionModelSummary classClassificationAndRegressionModelSummary = SparkModelUtils
                    .getClassClassificationModelSummary(sparkContext, testingData, predictionsAndLabels);

            // remove from cache
            testingData.unpersist();

            mlModel.setModel(new MLRandomForestModel(randomForestModel));

            classClassificationAndRegressionModelSummary.setFeatures(includedFeatures.values().toArray(new String[0]));
            classClassificationAndRegressionModelSummary.setAlgorithm(SUPERVISED_ALGORITHM.RANDOM_FOREST_CLASSIFICATION.toString());

            MulticlassMetrics multiclassMetrics = getMulticlassMetrics(sparkContext, predictionsAndLabels);

            predictionsAndLabels.unpersist();

            classClassificationAndRegressionModelSummary.setMulticlassConfusionMatrix(getMulticlassConfusionMatrix(
                    multiclassMetrics, mlModel));
            Double modelAccuracy = getModelAccuracy(multiclassMetrics);
            classClassificationAndRegressionModelSummary.setModelAccuracy(modelAccuracy);
            classClassificationAndRegressionModelSummary.setDatasetVersion(workflow.getDatasetVersion());

            return classClassificationAndRegressionModelSummary;
        } catch (Exception e) {
            throw new MLModelBuilderException("An error occurred while building random forest classification model: "
                    + e.getMessage(), e);
        }

    }

    private ModelSummary buildRandomForestRegressionModel(JavaSparkContext sparkContext, long modelID,
                                                          JavaRDD<LabeledPoint> trainingData, JavaRDD<LabeledPoint> testingData, Workflow workflow, MLModel mlModel,
                                                          SortedMap<Integer, String> includedFeatures, Map<Integer, Integer> categoricalFeatureInfo)
            throws MLModelBuilderException {
        try {
            Map<String, String> hyperParameters = workflow.getHyperParameters();
            RandomForestRregression randomForestRegression = new RandomForestRregression();
            final RandomForestModel randomForestModel = randomForestRegression.train(trainingData,
                    categoricalFeatureInfo, Integer.parseInt(hyperParameters.get(MLConstants.NUM_TREES)),
                    hyperParameters.get(MLConstants.FEATURE_SUBSET_STRATEGY), hyperParameters.get(MLConstants.IMPURITY),
                    Integer.parseInt(hyperParameters.get(MLConstants.MAX_DEPTH)),
                    Integer.parseInt(hyperParameters.get(MLConstants.MAX_BINS)),
                    Integer.parseInt(hyperParameters.get(MLConstants.SEED)));

            // remove from cache
            trainingData.unpersist();
            // add test data to cache
            testingData.cache();

            JavaPairRDD<Double, Double> predictionsAndLabels = randomForestRegression.test(randomForestModel, testingData).cache();
            ClassClassificationAndRegressionModelSummary regressionModelSummary = SparkModelUtils
                    .getClassClassificationModelSummary(sparkContext, testingData, predictionsAndLabels);

            // remove from cache
            testingData.unpersist();

            mlModel.setModel(new MLRandomForestModel(randomForestModel));

            regressionModelSummary.setFeatures(includedFeatures.values().toArray(new String[0]));
            regressionModelSummary.setAlgorithm(SUPERVISED_ALGORITHM.RANDOM_FOREST_REGRESSION.toString());

            RegressionMetrics regressionMetrics = getRegressionMetrics(sparkContext, predictionsAndLabels);

            predictionsAndLabels.unpersist();

            Double meanSquaredError = regressionMetrics.meanSquaredError();
            regressionModelSummary.setMeanSquaredError(meanSquaredError);
            regressionModelSummary.setDatasetVersion(workflow.getDatasetVersion());

            return regressionModelSummary;
        } catch (Exception e) {
            throw new MLModelBuilderException("An error occurred while building random forest regression model: "
                    + e.getMessage(), e);
        }

    }

    /**
     * This method builds a support vector machine (SVM) model
     *
     * @param sparkContext JavaSparkContext initialized with the application
     * @param modelID      Model ID
     * @param trainingData Training data as a JavaRDD of LabeledPoints
     * @param testingData  Testing data as a JavaRDD of LabeledPoints
     * @param workflow     Machine learning workflow
     * @param mlModel      Deployable machine learning model
     * @throws MLModelBuilderException
     */
    private ModelSummary buildSVMModel(JavaSparkContext sparkContext, long modelID, JavaRDD<LabeledPoint> trainingData,
                                       JavaRDD<LabeledPoint> testingData, Workflow workflow, MLModel mlModel,
                                       SortedMap<Integer, String> includedFeatures) throws MLModelBuilderException {

        if (getNoOfClasses(mlModel) > 2) {
            throw new MLModelBuilderException("A binary classification algorithm cannot have more than "
                    + "two distinct values in response variable.");
        }

        try {
            SVM svm = new SVM();
            Map<String, String> hyperParameters = workflow.getHyperParameters();
            SVMModel svmModel = svm.train(trainingData, Integer.parseInt(hyperParameters.get(MLConstants.ITERATIONS)),
                    hyperParameters.get(MLConstants.REGULARIZATION_TYPE),
                    Double.parseDouble(hyperParameters.get(MLConstants.REGULARIZATION_PARAMETER)),
                    Double.parseDouble(hyperParameters.get(MLConstants.LEARNING_RATE)),
                    Double.parseDouble(hyperParameters.get(MLConstants.SGD_DATA_FRACTION)));

            // remove from cache
            trainingData.unpersist();
            // add test data to cache
            testingData.cache();

            Vector weights = svmModel.weights();
            if (!isValidWeights(weights)) {
                throw new MLModelBuilderException("Weights of the model generated are null or infinity. [Weights] "
                        + vectorToString(weights));
            }

            // getting scores and labels without clearing threshold to get confusion matrix
            JavaRDD<Tuple2<Object, Object>> scoresAndLabelsThresholded = svm.test(svmModel, testingData);
            MulticlassMetrics multiclassMetrics = new MulticlassMetrics(JavaRDD.toRDD(scoresAndLabelsThresholded));
            MulticlassConfusionMatrix multiclassConfusionMatrix = getMulticlassConfusionMatrix(multiclassMetrics,
                    mlModel);

            svmModel.clearThreshold();
            JavaRDD<Tuple2<Object, Object>> scoresAndLabels = svm.test(svmModel, testingData);
            ProbabilisticClassificationModelSummary probabilisticClassificationModelSummary = SparkModelUtils
                    .generateProbabilisticClassificationModelSummary(sparkContext, testingData, scoresAndLabels);

            // remove from cache
            testingData.unpersist();

            mlModel.setModel(new MLClassificationModel(svmModel));

            List<FeatureImportance> featureWeights = getFeatureWeights(includedFeatures, svmModel.weights().toArray());
            probabilisticClassificationModelSummary.setFeatures(includedFeatures.values().toArray(new String[0]));
            probabilisticClassificationModelSummary.setFeatureImportance(featureWeights);
            probabilisticClassificationModelSummary.setAlgorithm(SUPERVISED_ALGORITHM.SVM.toString());

            probabilisticClassificationModelSummary.setMulticlassConfusionMatrix(multiclassConfusionMatrix);
            Double modelAccuracy = getModelAccuracy(multiclassMetrics);
            probabilisticClassificationModelSummary.setModelAccuracy(modelAccuracy);
            probabilisticClassificationModelSummary.setDatasetVersion(workflow.getDatasetVersion());

            return probabilisticClassificationModelSummary;
        } catch (Exception e) {
            throw new MLModelBuilderException("An error occurred while building SVM model: " + e.getMessage(), e);
        }
    }

    /**
     * This method builds a linear regression model
     *
     * @param sparkContext JavaSparkContext initialized with the application
     * @param modelID      Model ID
     * @param trainingData Training data as a JavaRDD of LabeledPoints
     * @param testingData  Testing data as a JavaRDD of LabeledPoints
     * @param workflow     Machine learning workflow
     * @param mlModel      Deployable machine learning model
     * @throws MLModelBuilderException
     */
    private ModelSummary buildLinearRegressionModel(JavaSparkContext sparkContext, long modelID,
                                                    JavaRDD<LabeledPoint> trainingData, JavaRDD<LabeledPoint> testingData, Workflow workflow, MLModel mlModel,
                                                    SortedMap<Integer, String> includedFeatures) throws MLModelBuilderException {
        try {
            LinearRegression linearRegression = new LinearRegression();
            Map<String, String> hyperParameters = workflow.getHyperParameters();
            LinearRegressionModel linearRegressionModel = linearRegression.train(trainingData,
                    Integer.parseInt(hyperParameters.get(MLConstants.ITERATIONS)),
                    Double.parseDouble(hyperParameters.get(MLConstants.LEARNING_RATE)),
                    Double.parseDouble(hyperParameters.get(MLConstants.SGD_DATA_FRACTION)));

            // remove from cache
            trainingData.unpersist();
            // add test data to cache
            testingData.cache();

            Vector weights = linearRegressionModel.weights();
            if (!isValidWeights(weights)) {
                throw new MLModelBuilderException("Weights of the model generated are null or infinity. [Weights] "
                        + vectorToString(weights));
            }
            JavaRDD<Tuple2<Double, Double>> predictionsAndLabels = linearRegression.test(linearRegressionModel,
                    testingData).cache();
            ClassClassificationAndRegressionModelSummary regressionModelSummary = SparkModelUtils
                    .generateRegressionModelSummary(sparkContext, testingData, predictionsAndLabels);

            // remove from cache
            testingData.unpersist();

            mlModel.setModel(new MLGeneralizedLinearModel(linearRegressionModel));

            List<FeatureImportance> featureWeights = getFeatureWeights(includedFeatures, linearRegressionModel
                    .weights().toArray());
            regressionModelSummary.setFeatures(includedFeatures.values().toArray(new String[0]));
            regressionModelSummary.setFeatureImportance(featureWeights);
            regressionModelSummary.setAlgorithm(SUPERVISED_ALGORITHM.LINEAR_REGRESSION.toString());

            RegressionMetrics regressionMetrics = getRegressionMetrics(sparkContext, predictionsAndLabels);

            predictionsAndLabels.unpersist();

            Double meanSquaredError = regressionMetrics.meanSquaredError();
            regressionModelSummary.setMeanSquaredError(meanSquaredError);
            regressionModelSummary.setDatasetVersion(workflow.getDatasetVersion());

            return regressionModelSummary;
        } catch (Exception e) {
            throw new MLModelBuilderException("An error occurred while building linear regression model: "
                    + e.getMessage(), e);
        }
    }

    /**
     * This method builds a ridge regression model
     *
     * @param sparkContext JavaSparkContext initialized with the application
     * @param modelID      Model ID
     * @param trainingData Training data as a JavaRDD of LabeledPoints
     * @param testingData  Testing data as a JavaRDD of LabeledPoints
     * @param workflow     Machine learning workflow
     * @param mlModel      Deployable machine learning model
     * @throws MLModelBuilderException
     */
    private ModelSummary buildRidgeRegressionModel(JavaSparkContext sparkContext, long modelID,
                                                   JavaRDD<LabeledPoint> trainingData, JavaRDD<LabeledPoint> testingData, Workflow workflow, MLModel mlModel,
                                                   SortedMap<Integer, String> includedFeatures) throws MLModelBuilderException {
        try {
            RidgeRegression ridgeRegression = new RidgeRegression();
            Map<String, String> hyperParameters = workflow.getHyperParameters();
            RidgeRegressionModel ridgeRegressionModel = ridgeRegression.train(trainingData,
                    Integer.parseInt(hyperParameters.get(MLConstants.ITERATIONS)),
                    Double.parseDouble(hyperParameters.get(MLConstants.LEARNING_RATE)),
                    Double.parseDouble(hyperParameters.get(MLConstants.REGULARIZATION_PARAMETER)),
                    Double.parseDouble(hyperParameters.get(MLConstants.SGD_DATA_FRACTION)));

            // remove from cache
            trainingData.unpersist();
            // add test data to cache
            testingData.cache();

            Vector weights = ridgeRegressionModel.weights();
            if (!isValidWeights(weights)) {
                throw new MLModelBuilderException("Weights of the model generated are null or infinity. [Weights] "
                        + vectorToString(weights));
            }
            JavaRDD<Tuple2<Double, Double>> predictionsAndLabels = ridgeRegression.test(ridgeRegressionModel,
                    testingData).cache();
            ClassClassificationAndRegressionModelSummary regressionModelSummary = SparkModelUtils
                    .generateRegressionModelSummary(sparkContext, testingData, predictionsAndLabels);

            // remove from cache
            testingData.unpersist();

            mlModel.setModel(new MLGeneralizedLinearModel(ridgeRegressionModel));

            List<FeatureImportance> featureWeights = getFeatureWeights(includedFeatures, ridgeRegressionModel.weights()
                    .toArray());
            regressionModelSummary.setFeatures(includedFeatures.values().toArray(new String[0]));
            regressionModelSummary.setAlgorithm(SUPERVISED_ALGORITHM.RIDGE_REGRESSION.toString());
            regressionModelSummary.setFeatureImportance(featureWeights);

            RegressionMetrics regressionMetrics = getRegressionMetrics(sparkContext, predictionsAndLabels);

            predictionsAndLabels.unpersist();

            Double meanSquaredError = regressionMetrics.meanSquaredError();
            regressionModelSummary.setMeanSquaredError(meanSquaredError);
            regressionModelSummary.setDatasetVersion(workflow.getDatasetVersion());

            return regressionModelSummary;
        } catch (Exception e) {
            throw new MLModelBuilderException("An error occurred while building ridge regression model: "
                    + e.getMessage(), e);
        }
    }

    /**
     * This method builds a lasso regression model
     *
     * @param sparkContext JavaSparkContext initialized with the application
     * @param modelID      Model ID
     * @param trainingData Training data as a JavaRDD of LabeledPoints
     * @param testingData  Testing data as a JavaRDD of LabeledPoints
     * @param workflow     Machine learning workflow
     * @param mlModel      Deployable machine learning model
     * @throws MLModelBuilderException
     */
    private ModelSummary buildLassoRegressionModel(JavaSparkContext sparkContext, long modelID,
                                                   JavaRDD<LabeledPoint> trainingData, JavaRDD<LabeledPoint> testingData, Workflow workflow, MLModel mlModel,
                                                   SortedMap<Integer, String> includedFeatures) throws MLModelBuilderException {
        try {
            LassoRegression lassoRegression = new LassoRegression();
            Map<String, String> hyperParameters = workflow.getHyperParameters();
            LassoModel lassoModel = lassoRegression.train(trainingData,
                    Integer.parseInt(hyperParameters.get(MLConstants.ITERATIONS)),
                    Double.parseDouble(hyperParameters.get(MLConstants.LEARNING_RATE)),
                    Double.parseDouble(hyperParameters.get(MLConstants.REGULARIZATION_PARAMETER)),
                    Double.parseDouble(hyperParameters.get(MLConstants.SGD_DATA_FRACTION)));

            // remove from cache
            trainingData.unpersist();
            // add test data to cache
            testingData.cache();

            Vector weights = lassoModel.weights();
            if (!isValidWeights(weights)) {
                throw new MLModelBuilderException("Weights of the model generated are null or infinity. [Weights] "
                        + vectorToString(weights));
            }
            JavaRDD<Tuple2<Double, Double>> predictionsAndLabels = lassoRegression.test(lassoModel, testingData)
                    .cache();
            ClassClassificationAndRegressionModelSummary regressionModelSummary = SparkModelUtils
                    .generateRegressionModelSummary(sparkContext, testingData, predictionsAndLabels);

            // remove from cache
            testingData.unpersist();

            mlModel.setModel(new MLGeneralizedLinearModel(lassoModel));

            List<FeatureImportance> featureWeights = getFeatureWeights(includedFeatures, lassoModel.weights().toArray());
            regressionModelSummary.setFeatures(includedFeatures.values().toArray(new String[0]));
            regressionModelSummary.setAlgorithm(SUPERVISED_ALGORITHM.LASSO_REGRESSION.toString());
            regressionModelSummary.setFeatureImportance(featureWeights);

            RegressionMetrics regressionMetrics = getRegressionMetrics(sparkContext, predictionsAndLabels);

            predictionsAndLabels.unpersist();

            Double meanSquaredError = regressionMetrics.meanSquaredError();
            regressionModelSummary.setMeanSquaredError(meanSquaredError);
            regressionModelSummary.setDatasetVersion(workflow.getDatasetVersion());

            return regressionModelSummary;
        } catch (Exception e) {
            throw new MLModelBuilderException("An error occurred while building lasso regression model: "
                    + e.getMessage(), e);
        }
    }

    /**
     * This method builds a naive bayes model
     *
     * @param sparkContext JavaSparkContext initialized with the application
     * @param modelID      Model ID
     * @param trainingData Training data as a JavaRDD of LabeledPoints
     * @param testingData  Testing data as a JavaRDD of LabeledPoints
     * @param workflow     Machine learning workflow
     * @param mlModel      Deployable machine learning model
     * @throws MLModelBuilderException
     */
    private ModelSummary buildNaiveBayesModel(JavaSparkContext sparkContext, long modelID,
                                              JavaRDD<LabeledPoint> trainingData, JavaRDD<LabeledPoint> testingData, Workflow workflow, MLModel mlModel,
                                              SortedMap<Integer, String> includedFeatures) throws MLModelBuilderException {
        try {
            Map<String, String> hyperParameters = workflow.getHyperParameters();
            NaiveBayesClassifier naiveBayesClassifier = new NaiveBayesClassifier();
            NaiveBayesModel naiveBayesModel = naiveBayesClassifier.train(trainingData,
                    Double.parseDouble(hyperParameters.get(MLConstants.LAMBDA)));

            // remove from cache
            trainingData.unpersist();
            // add test data to cache
            testingData.cache();

            JavaPairRDD<Double, Double> predictionsAndLabels = naiveBayesClassifier.test(naiveBayesModel, testingData)
                    .cache();
            ClassClassificationAndRegressionModelSummary classClassificationAndRegressionModelSummary = SparkModelUtils
                    .getClassClassificationModelSummary(sparkContext, testingData, predictionsAndLabels);

            // remove from cache
            testingData.unpersist();

            mlModel.setModel(new MLClassificationModel(naiveBayesModel));

            classClassificationAndRegressionModelSummary.setFeatures(includedFeatures.values().toArray(new String[0]));
            classClassificationAndRegressionModelSummary.setAlgorithm(SUPERVISED_ALGORITHM.NAIVE_BAYES.toString());

            MulticlassMetrics multiclassMetrics = getMulticlassMetrics(sparkContext, predictionsAndLabels);

            predictionsAndLabels.unpersist();

            classClassificationAndRegressionModelSummary.setMulticlassConfusionMatrix(getMulticlassConfusionMatrix(
                    multiclassMetrics, mlModel));
            Double modelAccuracy = getModelAccuracy(multiclassMetrics);
            classClassificationAndRegressionModelSummary.setModelAccuracy(modelAccuracy);
            classClassificationAndRegressionModelSummary.setDatasetVersion(workflow.getDatasetVersion());

            return classClassificationAndRegressionModelSummary;
        } catch (Exception e) {
            throw new MLModelBuilderException("An error occurred while building naive bayes model: " + e.getMessage(),
                    e);
        }
    }

    /**
     * This method builds ensemble method Stacking
     *
     * @param sparkContext JavaSparkContext initialized with the application
     * @param modelID      Model ID
     * @param trainingData Training data as a JavaRDD of LabeledPoints
     * @param testingData  Testing data as a JavaRDD of LabeledPoints
     * @param workflow     Machine learning workflow
     * @param mlModel      Deployable machine learning model
     * @throws MLModelBuilderException
     */

    private ModelSummary buildStackingModel(MLModelConfigurationContext context, JavaSparkContext sparkContext, long modelID,
                                            JavaRDD<LabeledPoint> trainingData, JavaRDD<LabeledPoint> testingData, Workflow workflow, MLModel mlModel,
                                            SortedMap<Integer, String> includedFeatures) throws MLModelBuilderException {

        try {
            Map<String, Map<String, String>> hyperParameters = workflow.getAllHyperParameters();
            ArrayList<String> listBaseAlgorithms = new ArrayList<>();
            ArrayList<Map<String, String>> paramsBaseAlgorithms = new ArrayList<>();
            Map<String, String> paramsMetaAlgorithm = new HashMap<>();
            String metaAlgorithmName = null;


            Set<String> keys = hyperParameters.keySet();
            Iterator<String> it = keys.iterator();

            // get name and hyper-parameters of base and meta-learner
            while (it.hasNext()) {
                String name = it.next();
                if (name.contains(MLConstants.BASE_ALGORITHM_NAME)) {
                    String baseAlgorithmName = hyperParameters.get(name).get(MLConstants.ALGORITHM_NAME);
                    hyperParameters.get(name).remove(MLConstants.ALGORITHM_NAME);
                    listBaseAlgorithms.add(baseAlgorithmName);
                    paramsBaseAlgorithms.add(hyperParameters.get(name));


                }
                if (name.contains(MLConstants.META_ALGORITHM_NAME)) {
                    metaAlgorithmName = hyperParameters.get(name).get(MLConstants.ALGORITHM_NAME);
                    hyperParameters.get(name).remove(MLConstants.ALGORITHM_NAME);
                    paramsMetaAlgorithm = hyperParameters.get(MLConstants.META_ALGORITHM_NAME);
                }

            }

            Stacking stackedModel = new Stacking();

            stackedModel.train(context, sparkContext, workflow, modelID, trainingData, listBaseAlgorithms, paramsBaseAlgorithms,
                    metaAlgorithmName,
                    paramsMetaAlgorithm,
                    Integer.parseInt(hyperParameters.get(workflow.getAlgorithmName()).get(MLConstants.FOLDS)),
                    Integer.parseInt(hyperParameters.get(workflow.getAlgorithmName()).get(MLConstants.SEED)));

            mlModel.setModel(new MLClassificationModel(stackedModel));

            // remove from cache
            trainingData.unpersist();
            // add test data to cache
            testingData.cache();

            JavaPairRDD<Double, Double> predictionsAndLabels = stackedModel.test(sparkContext, modelID, testingData).cache();
            ClassClassificationAndRegressionModelSummary classClassificationAndRegressionModelSummary = SparkModelUtils
                    .getClassClassificationModelSummary(sparkContext, testingData, predictionsAndLabels);

            // remove from cache
            testingData.unpersist();


            classClassificationAndRegressionModelSummary.setFeatures(includedFeatures.values().toArray(new String[0]));
            classClassificationAndRegressionModelSummary.setAlgorithm(SUPERVISED_ALGORITHM.STACKING.toString());

            MulticlassMetrics multiclassMetrics = getMulticlassMetrics(sparkContext, predictionsAndLabels);

            predictionsAndLabels.unpersist();

            classClassificationAndRegressionModelSummary.setMulticlassConfusionMatrix(getMulticlassConfusionMatrix(
                    multiclassMetrics, mlModel));
            Double modelAccuracy = getModelAccuracy(multiclassMetrics);
            classClassificationAndRegressionModelSummary.setModelAccuracy(modelAccuracy);
            classClassificationAndRegressionModelSummary.setDatasetVersion(workflow.getDatasetVersion());

            return classClassificationAndRegressionModelSummary;


        } catch (Exception e) {
            throw new MLModelBuilderException("An error occurred while building stacking model: " + e.getMessage(),
                    e);
        }


    }

    /**
     * This method builds ensemble method Bagging
     *
     * @param sparkContext JavaSparkContext initialized with the application
     * @param modelID      Model ID
     * @param trainingData Training data as a JavaRDD of LabeledPoints
     * @param testingData  Testing data as a JavaRDD of LabeledPoints
     * @param workflow     Machine learning workflow
     * @param mlModel      Deployable machine learning model
     * @throws MLModelBuilderException
     */

    private ModelSummary buildBaggingModel(MLModelConfigurationContext context, JavaSparkContext sparkContext, long modelID,
                                           JavaRDD<LabeledPoint> trainingData, JavaRDD<LabeledPoint> testingData, Workflow workflow, MLModel mlModel,
                                           SortedMap<Integer, String> includedFeatures) throws MLModelBuilderException {

        try {
            Map<String, Map<String, String>> hyperParameters = workflow.getAllHyperParameters();
            ArrayList<String> listBaseAlgorithms = new ArrayList<>();
            ArrayList<Map<String, String>> paramsBaseAlgorithms = new ArrayList<>();
            Set<String> keys = hyperParameters.keySet();
            Iterator<String> it = keys.iterator();

            // get name and hyperparameters of base learners
            while (it.hasNext()) {
                String name = it.next();
                if (name.contains(MLConstants.BASE_ALGORITHM_NAME)) {
                    String baseAlgorithmName = hyperParameters.get(name).get(MLConstants.ALGORITHM_NAME);
                    hyperParameters.get(name).remove(MLConstants.ALGORITHM_NAME);
                    listBaseAlgorithms.add(baseAlgorithmName);
                    paramsBaseAlgorithms.add(hyperParameters.get(name));

                }
            }
            Bagging baggedModel = new Bagging();

            baggedModel.train(context, workflow, modelID, trainingData, listBaseAlgorithms, paramsBaseAlgorithms,
                    Integer.parseInt(hyperParameters.get(workflow.getAlgorithmName()).get(MLConstants.SEED)));

            mlModel.setModel(new MLClassificationModel(baggedModel));

            // remove from cache
            trainingData.unpersist();
            // add test data to cache
            testingData.cache();

            JavaPairRDD<Double, Double> predictionsAndLabels = baggedModel.test(sparkContext, modelID, testingData).cache();
            ClassClassificationAndRegressionModelSummary classClassificationAndRegressionModelSummary = SparkModelUtils
                    .getClassClassificationModelSummary(sparkContext, testingData, predictionsAndLabels);

            // remove from cache
            testingData.unpersist();


            classClassificationAndRegressionModelSummary.setFeatures(includedFeatures.values().toArray(new String[0]));
            classClassificationAndRegressionModelSummary.setAlgorithm(SUPERVISED_ALGORITHM.BAGGING.toString());

            MulticlassMetrics multiclassMetrics = getMulticlassMetrics(sparkContext, predictionsAndLabels);

            predictionsAndLabels.unpersist();

            classClassificationAndRegressionModelSummary.setMulticlassConfusionMatrix(getMulticlassConfusionMatrix(
                    multiclassMetrics, mlModel));
            Double modelAccuracy = getModelAccuracy(multiclassMetrics);
            classClassificationAndRegressionModelSummary.setModelAccuracy(modelAccuracy);
            classClassificationAndRegressionModelSummary.setDatasetVersion(workflow.getDatasetVersion());

            return classClassificationAndRegressionModelSummary;


        } catch (Exception e) {
            throw new MLModelBuilderException("An error occurred while building bagging model: " + e.getMessage(),
                    e);
        }


    }


    /**
     * @param features Array of names of features
     * @param weights  Array of weights of features
     * @return List of FeatureImportance in the model {@link FeatureImportance}
     */
    private List<FeatureImportance> getFeatureWeights(SortedMap<Integer, String> features, double[] weights) {
        List<FeatureImportance> featureWeights = new ArrayList<FeatureImportance>();
        int i = 0;
        for (String featureName : features.values()) {
            FeatureImportance featureImportance = new FeatureImportance();
            featureImportance.setLabel(featureName);
            featureImportance.setValue(weights[i]);
            featureWeights.add(featureImportance);
            i++;
        }
        return featureWeights;
    }

    /**
     * This method gets multi class metrics for a given set of prediction and label values
     *
     * @param sparkContext         JavaSparkContext
     * @param predictionsAndLabels Prediction and label values RDD
     */
    protected MulticlassMetrics getMulticlassMetrics(JavaSparkContext sparkContext,
                                                     JavaPairRDD<Double, Double> predictionsAndLabels) {
        List<Tuple2<Double, Double>> predictionsAndLabelsDoubleList = predictionsAndLabels.collect();
        List<Tuple2<Object, Object>> predictionsAndLabelsObjectList = new ArrayList<Tuple2<Object, Object>>();
        for (Tuple2<Double, Double> predictionsAndLabel : predictionsAndLabelsDoubleList) {
            Object prediction = predictionsAndLabel._1;
            Object label = predictionsAndLabel._2;
            Tuple2<Object, Object> tupleElement = new Tuple2<Object, Object>(prediction, label);
            predictionsAndLabelsObjectList.add(tupleElement);
        }
        JavaRDD<Tuple2<Object, Object>> predictionsAndLabelsJavaRDD = sparkContext
                .parallelize(predictionsAndLabelsObjectList).cache();
        RDD<Tuple2<Object, Object>> scoresAndLabelsRDD = JavaRDD.toRDD(predictionsAndLabelsJavaRDD);
        predictionsAndLabelsJavaRDD.unpersist();
        MulticlassMetrics multiclassMetrics = new MulticlassMetrics(scoresAndLabelsRDD);
        return multiclassMetrics;
    }

    /**
     * This method returns multiclass confusion matrix for a given multiclass metric object
     *
     * @param multiclassMetrics Multiclass metric object
     */
    protected MulticlassConfusionMatrix getMulticlassConfusionMatrix(MulticlassMetrics multiclassMetrics, MLModel mlModel) {
        MulticlassConfusionMatrix multiclassConfusionMatrix = new MulticlassConfusionMatrix();
        if (multiclassMetrics != null) {
            int size = multiclassMetrics.confusionMatrix().numCols();
            double[] matrixArray = multiclassMetrics.confusionMatrix().toArray();
            double[][] matrix = new double[size][size];
            // set values of matrix into a 2D array
            for (int i = 0; i < size; i++) {
                for (int j = 0; j < size; j++) {
                    matrix[i][j] = matrixArray[(j * size) + i];
                }
            }
            multiclassConfusionMatrix.setMatrix(matrix);

            List<Map<String, Integer>> encodings = mlModel.getEncodings();
            // decode only if encodings are available
            if (encodings != null) {
                // last index is response variable encoding
                Map<String, Integer> encodingMap = encodings.get(encodings.size() - 1);
                List<String> decodedLabels = new ArrayList<String>();
                for (double label : multiclassMetrics.labels()) {
                    Integer labelInt = (int) label;
                    String decodedLabel = MLUtils.getKeyByValue(encodingMap, labelInt);
                    if (decodedLabel != null) {
                        decodedLabels.add(decodedLabel);
                    } else {
                        continue;
                    }
                }
                multiclassConfusionMatrix.setLabels(decodedLabels);
            } else {
                List<String> labelList = toStringList(multiclassMetrics.labels());
                multiclassConfusionMatrix.setLabels(labelList);
            }

            multiclassConfusionMatrix.setSize(size);
        }
        return multiclassConfusionMatrix;
    }

    /**
     * This method gets regression metrics for a given set of prediction and label values
     *
     * @param sparkContext         JavaSparkContext
     * @param predictionsAndLabels Prediction and label values RDD
     */
    private RegressionMetrics getRegressionMetrics(JavaSparkContext sparkContext,
                                                   JavaRDD<Tuple2<Double, Double>> predictionsAndLabels) {
        List<Tuple2<Double, Double>> predictionsAndLabelsDoubleList = predictionsAndLabels.collect();
        List<Tuple2<Object, Object>> predictionsAndLabelsObjectList = new ArrayList<Tuple2<Object, Object>>();
        for (Tuple2<Double, Double> predictionsAndLabel : predictionsAndLabelsDoubleList) {
            Object prediction = predictionsAndLabel._1;
            Object label = predictionsAndLabel._2;
            Tuple2<Object, Object> tupleElement = new Tuple2<Object, Object>(prediction, label);
            predictionsAndLabelsObjectList.add(tupleElement);
        }
        JavaRDD<Tuple2<Object, Object>> predictionsAndLabelsJavaRDD = sparkContext
                .parallelize(predictionsAndLabelsObjectList).cache();
        RDD<Tuple2<Object, Object>> scoresAndLabelsRDD = JavaRDD.toRDD(predictionsAndLabelsJavaRDD);
        predictionsAndLabelsJavaRDD.unpersist();
        RegressionMetrics regressionMetrics = new RegressionMetrics(scoresAndLabelsRDD);
        return regressionMetrics;
    }

    /**
     * This method gets regression metrics for a given set of prediction and label values directly from Java RDD
     *
     * @param sparkContext         JavaSparkContext
     * @param predictionsAndLabels Prediction and label values RDD
     */
    private RegressionMetrics getRegressionMetrics(JavaSparkContext sparkContext,
                                                   JavaPairRDD<Double, Double> predictionsAndLabels) {
        List<Tuple2<Double, Double>> predictionsAndLabelsDoubleList = predictionsAndLabels.collect();
        List<Tuple2<Object, Object>> predictionsAndLabelsObjectList = new ArrayList<Tuple2<Object, Object>>();
        for (Tuple2<Double, Double> predictionsAndLabel : predictionsAndLabelsDoubleList) {
            Object prediction = predictionsAndLabel._1;
            Object label = predictionsAndLabel._2;
            Tuple2<Object, Object> tupleElement = new Tuple2<Object, Object>(prediction, label);
            predictionsAndLabelsObjectList.add(tupleElement);
        }
        JavaRDD<Tuple2<Object, Object>> predictionsAndLabelsJavaRDD = sparkContext
                .parallelize(predictionsAndLabelsObjectList).cache();
        RDD<Tuple2<Object, Object>> scoresAndLabelsRDD = JavaRDD.toRDD(predictionsAndLabelsJavaRDD);
        predictionsAndLabelsJavaRDD.unpersist();
        RegressionMetrics regressionMetrics = new RegressionMetrics(scoresAndLabelsRDD);
        return regressionMetrics;
    }

    /**
     * This method gets model accuracy from given multi-class metrics
     *
     * @param multiclassMetrics multi-class metrics object
     */
    protected Double getModelAccuracy(MulticlassMetrics multiclassMetrics) {
        DecimalFormat decimalFormat = new DecimalFormat(MLConstants.DECIMAL_FORMAT);

        Double modelAccuracy = 0.0;
        int confusionMatrixSize = multiclassMetrics.confusionMatrix().numCols();
        int confusionMatrixDiagonal = 0;
        long totalPopulation = arraySum(multiclassMetrics.confusionMatrix().toArray());
        for (int i = 0; i < confusionMatrixSize; i++) {
            int diagonalValueIndex = multiclassMetrics.confusionMatrix().index(i, i);
            confusionMatrixDiagonal += multiclassMetrics.confusionMatrix().toArray()[diagonalValueIndex];
        }
        if (totalPopulation > 0) {
            modelAccuracy = (double) confusionMatrixDiagonal / totalPopulation;
        }
        return Double.parseDouble(decimalFormat.format(modelAccuracy * 100));
    }

    /**
     * This summation of a given double array
     *
     * @param array Double array
     */
    protected long arraySum(double[] array) {
        long sum = 0;
        for (double i : array) {
            sum += i;
        }
        return sum;
    }

    private boolean isValidWeights(Vector weights) {
        for (int i = 0; i < weights.size(); i++) {
            double d = weights.apply(i);
            if (Double.isNaN(d) || Double.isInfinite(d)) {
                return false;
            }
        }
        return true;
    }

    private String vectorToString(Vector weights) {
        StringBuilder sb = new StringBuilder();
        for (int i = 1; i <= weights.size(); i++) {
            double d = weights.apply(i - 1);
            sb.append(d);
            if (i != weights.size()) {
                sb.append(",");
            }
        }
        return sb.toString();
    }

    private List<String> toStringList(double[] doubleArray) {
        List<String> stringList = new ArrayList<String>(doubleArray.length);
        for (int i = 0; i < doubleArray.length; i++) {
            stringList.add(String.valueOf(doubleArray[i]));
        }
        return stringList;
    }

}
