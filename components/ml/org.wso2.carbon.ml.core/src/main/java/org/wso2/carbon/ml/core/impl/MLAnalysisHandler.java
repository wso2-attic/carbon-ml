/*
 * Copyright (c) 2015, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.ml.core.impl;

import java.util.List;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.wso2.carbon.ml.commons.constants.MLConstants;
import org.wso2.carbon.ml.commons.domain.MLAnalysis;
import org.wso2.carbon.ml.commons.domain.MLCustomizedFeature;
import org.wso2.carbon.ml.commons.domain.config.MLAlgorithm;
import org.wso2.carbon.ml.commons.domain.FeatureSummary;
import org.wso2.carbon.ml.commons.domain.MLHyperParameter;
import org.wso2.carbon.ml.commons.domain.MLModelConfiguration;
import org.wso2.carbon.ml.commons.domain.MLModelData;
import org.wso2.carbon.ml.core.exceptions.MLAnalysisHandlerException;
import org.wso2.carbon.ml.core.utils.MLCoreServiceValueHolder;
import org.wso2.carbon.ml.database.exceptions.DatabaseHandlerException;

/**
 * {@link MLAnalysisHandler} is responsible for handling/delegating all the analysis related requests.
 */
public class MLAnalysisHandler {
    private static final Log log = LogFactory.getLog(MLAnalysisHandler.class);
    private MLCoreServiceValueHolder valueHolder;

    public MLAnalysisHandler() {
        valueHolder = MLCoreServiceValueHolder.getInstance();
    }
    
    public void createAnalysis(MLAnalysis analysis) throws MLAnalysisHandlerException {
        try {
            valueHolder.getDatabaseService().insertAnalysis(analysis);
            log.info(String.format("[Created] %s", analysis));
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }
    
    public void addCustomizedFeatures(long analysisId, List<MLCustomizedFeature> customizedFeatures, int tenantId, String userName)
            throws MLAnalysisHandlerException {
        try {
            valueHolder.getDatabaseService().insertFeatureCustomized(analysisId, customizedFeatures, tenantId, userName);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }
    
    public void addDefaultsIntoCustomizedFeatures(long analysisId, MLCustomizedFeature customizedValues)
            throws MLAnalysisHandlerException {
        try {
            valueHolder.getDatabaseService().insertDefaultsIntoFeatureCustomized(analysisId, customizedValues);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public List<FeatureSummary> getSummarizedFeatures(int tenantId, String userName, long analysisId, int limit, int offset) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getFeatures(tenantId, userName, analysisId, offset, limit);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public List<MLCustomizedFeature> getCustomizedFeatures(int tenantId, String userName, long analysisId, int limit, int offset) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getCustomizedFeatures(tenantId, userName, analysisId, offset, limit);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public List<String> getFeatureNames(String analysisId, String featureType) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getFeatureNames(analysisId, featureType);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public List<String> getFeatureNames(String analysisId) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getFeatureNames(analysisId);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }
    
    public String getResponseVariable(long analysisId) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getAStringModelConfiguration(analysisId, MLConstants.RESPONSE_VARIABLE);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public String getUserVariable(long analysisId) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getAStringModelConfiguration(analysisId, MLConstants.USER_VARIABLE);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public String getProductVariable(long analysisId) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getAStringModelConfiguration(analysisId, MLConstants.PRODUCT_VARIABLE);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public String getRatingVariable(long analysisId) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getAStringModelConfiguration(analysisId, MLConstants.RATING_VARIABLE);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public String getObservations(long analysisId) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getAStringModelConfiguration(analysisId, MLConstants.OBSERVATIONS);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public String getAlgorithmName(long analysisId) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getAStringModelConfiguration(analysisId, MLConstants.ALGORITHM_NAME);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public String getAlgorithmType(long analysisId) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getAStringModelConfiguration(analysisId, MLConstants.ALGORITHM_TYPE);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }
    
    public String getNormalLabels(long analysisId) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getAStringModelConfiguration(analysisId, MLConstants.NORMAL_LABELS);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public double getTrainDataFraction(long analysisId) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getADoubleModelConfiguration(analysisId, MLConstants.TRAIN_DATA_FRACTION);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }
    
    public String getNormalization(long analysisId) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getAStringModelConfiguration(analysisId, MLConstants.NORMALIZATION);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public String getNewNormalLabel(long analysisId) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getAStringModelConfiguration(analysisId, MLConstants.NEW_NORMAL_LABEL);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public String getNewAnomalyLabel(long analysisId) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getAStringModelConfiguration(analysisId, MLConstants.NEW_ANOMALY_LABEL);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }
    
    public String getSummaryStats(int tenantId, String userName, long analysisId, String featureName) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getSummaryStats(tenantId, userName, analysisId, featureName);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public void addModelConfigurations(long analysisId, List<MLModelConfiguration> modelConfigs)
            throws MLAnalysisHandlerException {
        try {
            valueHolder.getDatabaseService().insertModelConfigurations(analysisId, modelConfigs);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public void addHyperParameters(long analysisId, List<MLHyperParameter> hyperParameters, String algorithmName) throws MLAnalysisHandlerException {
        try {
            valueHolder.getDatabaseService().insertHyperParameters(analysisId, hyperParameters, algorithmName);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

    public List<MLHyperParameter> getHyperParameters(long analysisId,String algorithmName) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getHyperParametersOfModel(analysisId, algorithmName);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }
    
    public void addDefaultsIntoHyperParameters(long analysisId) throws MLAnalysisHandlerException {
        try {
            // read the algorithm name of this model
            String algorithmName = valueHolder.getDatabaseService().getAStringModelConfiguration(analysisId, MLConstants.ALGORITHM_NAME);
            if (algorithmName == null) {
                throw new MLAnalysisHandlerException("You have to set the model configurations (algorithm name) before loading default hyper parameters for model [id] "+analysisId);
            }
            // get the MLAlgorithm and then the hyper params of the model's algorithm
            List<MLHyperParameter> hyperParameters = null;
            for (MLAlgorithm mlAlgorithm : valueHolder.getAlgorithms()) {
                if (algorithmName.equalsIgnoreCase(mlAlgorithm.getName())) {
                    hyperParameters = mlAlgorithm.getParameters();
                    break;
                }
            }
            if (hyperParameters == null) {
                throw new MLAnalysisHandlerException("Cannot find the default hyper parameters for algorithm [name] "+algorithmName);
            }
            // add default hyper params
            valueHolder.getDatabaseService().insertHyperParameters(analysisId, hyperParameters, algorithmName);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }
    
    public void deleteAnalysis(int tenantId, String userName, long analysisId) throws MLAnalysisHandlerException {
        try {
            valueHolder.getDatabaseService().deleteAnalysis(tenantId, userName, analysisId);
            log.info(String.format("[Deleted] [analysis id] %s of [user] %s of [tenant] %s", analysisId, userName, tenantId));
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }
    
    public List<MLAnalysis> getAnalyses(int tenantId, String userName) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getAllAnalyses(tenantId, userName);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }
    
    public List<MLModelData> getAllModelsOfAnalysis(int tenantId, String userName, long analysisId) throws MLAnalysisHandlerException {
        try {
            return valueHolder.getDatabaseService().getAllModels(tenantId, userName, analysisId);
        } catch (DatabaseHandlerException e) {
            throw new MLAnalysisHandlerException(e.getMessage(), e);
        }
    }

}
