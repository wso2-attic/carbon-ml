/*
 * Copyright (c) 2014, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
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
package org.wso2.carbon.ml.database;

import org.wso2.carbon.ml.commons.domain.*;
import org.wso2.carbon.ml.commons.domain.config.MLConfiguration;
import org.wso2.carbon.ml.database.exceptions.DatabaseHandlerException;

import java.util.List;
import java.util.Map;

public interface DatabaseService {
    
    /**
     * Returns ML Configuration.
     */
    MLConfiguration getMlConfiguration();

    /**
     * Insert a new dataset-schema into the database.
     *
     * @param dataset MLDataset to be inserted
     * @throws DatabaseHandlerException
     */
    void insertDatasetSchema(MLDataset dataset) throws DatabaseHandlerException;

    /**
     * Insert a new dataset-version into the database.
     *
     * @param datasetVersion MLDatasetVersion to be inserted
     * @throws DatabaseHandlerException
     */
    void insertDatasetVersion(MLDatasetVersion datasetVersion) throws DatabaseHandlerException;

    /**
     * Insert a new project to the database.
     *
     * @param project MLProject to be inserted
     * @throws DatabaseHandlerException
     */
    void insertProject(MLProject project) throws DatabaseHandlerException;

    /**
     * Insert a new analysis into the database.
     *
     * @param analysis MLAnalysis to be inserted
     * @throws DatabaseHandlerException
     */
    void insertAnalysis(MLAnalysis analysis) throws DatabaseHandlerException;

    /**
     * Insert a new model into the database.
     *
     * @param model MLModelNew to be inserted
     * @throws DatabaseHandlerException
     */
    void insertModel(MLModelData model) throws DatabaseHandlerException;

    /**
     * Retrieves the path of the value-set having the given ID, from the
     * database.
     *
     * @param datasetVersionId Unique Identifier of the dataset-version
     * @return Absolute path of a given dataset-version
     * @throws DatabaseHandlerException
     */
    String getDatasetVersionUri(long datasetVersionId) throws DatabaseHandlerException;

    /**
     * Retrieves the path of the value-set having the given ID, from the
     * database.
     *
     * @param datasetId Unique Identifier of the dataset
     * @return Absolute path of a given dataset
     * @throws DatabaseHandlerException
     */
    String getDatasetUri(long datasetId) throws DatabaseHandlerException;

    /**
     * Get the dataset id.
     *
     * @param datasetName Name of the data-set
     * @param tenantId    Tenant Id
     * @param userName    Tenant user name
     * @return Unique Id of the data-set
     * @throws DatabaseHandlerException
     */
    long getDatasetId(String datasetName, int tenantId, String userName) throws DatabaseHandlerException;

    /**
     * Get the dataset-version id.
     *
     * @param datasetVersionName Name of the dataset version
     * @param tenantId           ID of the tenant
     * @param userName           Username of the tenant
     * @return ID of the dataset version
     * @throws DatabaseHandlerException
     */
    long getVersionsetId(String datasetVersionName, int tenantId, String userName) throws DatabaseHandlerException;

    /**
     * Returns data points of the selected sample as coordinates of three
     * features, needed for the scatter plot.
     *
     * @param scatterPlotPoints Scatter plot points
     * @return A JSON array of data points
     * @throws DatabaseHandlerException
     */
    List<Object> getScatterPlotPoints(ScatterPlotPoints scatterPlotPoints) throws DatabaseHandlerException;

    /**
     * Returns sample data for selected features.
     *
     * @param tenantId          Tenant id
     * @param user              Tenant user name
     * @param versionsetId      Unique Identifier of the value-set
     * @param featureListString String containing feature name list
     * @return A JSON array of data points
     * @throws DatabaseHandlerException
     */
    List<Object> getChartSamplePoints(int tenantId, String user, long versionsetId, String featureListString)
            throws DatabaseHandlerException;

    /**
     * Returns a set of features in a given range, from the alphabetically ordered set
     * of features, of a dataset.
     *
     * @param tenantId         ID of the tenant
     * @param userName         Tenant user name
     * @param analysisId       ID of the analysis
     * @param startIndex       Starting index of the set of features needed
     * @param numberOfFeatures Number of features needed, from the starting index
     * @return A list of Feature objects
     * @throws DatabaseHandlerException
     */
    List<FeatureSummary> getFeatures(int tenantId, String userName, long analysisId, int startIndex,
                                     int numberOfFeatures) throws DatabaseHandlerException;

    /**
     * Returns the customized set of features of an analysis in a given range, from the alphabetically ordered set
     * of features, of a dataset.
     *
     * @param tenantId         ID of the tenant
     * @param userName         Username of the tenant
     * @param analysisId       Unique ID of the analysis
     * @param startIndex       Starting index of the set of features needed
     * @param numberOfFeatures Number of features needed, from the starting index
     * @return A list of Feature objects
     * @throws DatabaseHandlerException
     */
    List<MLCustomizedFeature> getCustomizedFeatures(int tenantId, String userName, long analysisId, int startIndex,
                                                    int numberOfFeatures) throws DatabaseHandlerException;

    /**
     * Returns the names of the features, belongs to a particular type
     * (Categorical/Numerical), of the analysis.
     *
     * @param analysisId  Unique identifier of the current analysis
     * @param featureType Type of the feature
     * @return A list of feature names
     * @throws DatabaseHandlerException
     */
    List<String> getFeatureNames(String analysisId, String featureType) throws DatabaseHandlerException;

    /**
     * Returns all the feature names of an analysis.
     *
     * @param analysisId Unique identifier of the current analysis
     * @return A list of feature names
     * @throws DatabaseHandlerException
     */
    List<String> getFeatureNames(String analysisId) throws DatabaseHandlerException;

    /**
     * Returns the names of the features, belongs to a particular type
     * (Categorical/Numerical), of a dataset.
     *
     * @param datasetId   Unique identifier of a dataset
     * @param featureType Type of the feature
     * @return A list of feature names
     * @throws DatabaseHandlerException
     */
    List<String> getFeatureNames(long datasetId, String featureType) throws DatabaseHandlerException;

    /**
     * Retrieve and returns the Summary statistics for a given feature of a
     * given data-set version, from the database.
     *
     * @param tenantId    Tenant id
     * @param user        Tenant user name
     * @param analysisId  Unique identifier of the data-set
     * @param featureName Name of the feature of which summary statistics are needed
     * @return JSON string containing the summary statistics
     * @throws DatabaseHandlerException
     */
    String getSummaryStats(int tenantId, String user, long analysisId, String featureName) throws DatabaseHandlerException;

    /**
     * Retrieve and returns summary statistics for a given feature of a given dataset.
     *
     * @param datasetId   Unique identifier of a dataset
     * @param featureName Name of the feature of which summary statistics are needed
     * @return JSON string containing summary statistics
     * @throws DatabaseHandlerException
     */
    String getSummaryStats(long datasetId, String featureName) throws DatabaseHandlerException;

    /**
     * Retrieve the SamplePoints object for a given version-set.
     *
     * @param tenantId     Tenant id
     * @param user         Tenant user name
     * @param versionsetId ID of the dataset version
     * @return {@link SamplePoints} object of the dataset version
     * @throws DatabaseHandlerException
     */
    SamplePoints getVersionsetSample(int tenantId, String user, long versionsetId) throws DatabaseHandlerException;

    /**
     * Returns the number of features of a given data-set version.
     *
     * @param datasetVersionId Unique identifier of the dataset version
     * @return Number of features in the dataset version
     * @throws DatabaseHandlerException
     */
    int getFeatureCount(long datasetVersionId) throws DatabaseHandlerException;

    /**
     * Update the database with the summary stats of data-set-version.
     *
     * @param datasetSchemaId  Unique id of the dataset schema
     * @param datasetVersionId Unique Id of the data-set-version
     * @param summaryStats     Summary stats
     * @throws DatabaseHandlerException
     */
    void updateSummaryStatistics(long datasetSchemaId, long datasetVersionId, SummaryStats summaryStats)
            throws DatabaseHandlerException;

    /**
     * Update the database with the sample points of data-set-version.
     *
     * @param datasetVersionId Unique Id of the data-set-version
     * @param samplePoints     Sample points of this dataset version
     * @throws DatabaseHandlerException
     */
    void updateSamplePoints(long datasetVersionId, SamplePoints samplePoints) throws DatabaseHandlerException;

    /**
     * Delete the project.
     *
     * @param tenantId  ID of the tenant
     * @param userName  Username of the tenant
     * @param projectId ID of the project
     * @throws DatabaseHandlerException
     */
    void deleteProject(int tenantId, String userName, long projectId) throws DatabaseHandlerException;

    /**
     * Insert a list of Customized-Features into the database.
     *
     * @param analysisId         Analysis Id
     * @param customizedFeatures MLCustomizedFeature list
     * @param tenantId           Tenant Id
     * @param userName           Username
     * @throws DatabaseHandlerException
     */
    void insertFeatureCustomized(long analysisId, List<MLCustomizedFeature> customizedFeatures, int tenantId,
                                 String userName) throws DatabaseHandlerException;

    /**
     * Insert a list of ModelConfiguration into the database.
     *
     * @param analysisId   Analysis ID
     * @param modelConfigs MLModelConfiguration list
     * @throws DatabaseHandlerException
     */
    void insertModelConfigurations(long analysisId, List<MLModelConfiguration> modelConfigs)
            throws DatabaseHandlerException;

    /**
     * Insert a list of HyperParameters into the database.
     *
     * @param analysisId      Analysis ID
     * @param hyperParameters MLHyperParameter list
     * @param algorithmName   Algorithm name
     * @throws DatabaseHandlerException
     */
    void insertHyperParameters(long analysisId, List<MLHyperParameter> hyperParameters, String algorithmName)
            throws DatabaseHandlerException;


    /**
     * Update the model summary of a given model.
     *
     * @param modelId      model id
     * @param modelSummary ModelSummary
     * @throws DatabaseHandlerException
     */
    void updateModelSummary(long modelId, ModelSummary modelSummary) throws DatabaseHandlerException;

    /**
     * Update the storage details of a model.
     *
     * @param modelId     Model Id
     * @param storageType Storage type
     * @param location    Storage location
     * @throws DatabaseHandlerException
     */
    void updateModelStorage(long modelId, String storageType, String location) throws DatabaseHandlerException;

    /**
     * Check whether the given modelId is valid.
     *
     * @param tenantId ID of the tenant
     * @param userName Username of the tenant
     * @param modelId  ID of the model
     * @return Validity of the model ID
     * @throws DatabaseHandlerException
     */
    boolean isValidModelId(int tenantId, String userName, long modelId) throws DatabaseHandlerException;

    /**
     * Check whether the model is in a valid state.
     *
     * @param modelId  ID of the model
     * @param tenantId Tenant id
     * @param userName Tenant user name
     * @return Validity of the model status
     * @throws DatabaseHandlerException
     */
    boolean isValidModelStatus(long modelId, int tenantId, String userName) throws DatabaseHandlerException;

    /**
     * Get the ID of the dataset version used to generate the model.
     *
     * @param modelId ID of the model
     * @return Dataset version ID
     * @throws DatabaseHandlerException
     */
    long getDatasetVersionIdOfModel(long modelId) throws DatabaseHandlerException;

    /**
     * Get the dataset schema ID of the dataset version.
     *
     * @param datasetVersionId Unique ID of the dataset version
     * @return Dataset ID
     * @throws DatabaseHandlerException
     */
    long getDatasetId(long datasetVersionId) throws DatabaseHandlerException;

    /**
     * Get the data type of the model.
     *
     * @param modelId ID of the model
     * @return Data type of the model
     * @throws DatabaseHandlerException
     */
    String getDataTypeOfModel(long modelId) throws DatabaseHandlerException;

    /**
     * Get a string value of model configuration.
     *
     * @param analysisId Unique ID of the analysis
     * @param configKey  Model configuration key
     * @return Model configuration as a string
     * @throws DatabaseHandlerException
     */
    String getAStringModelConfiguration(long analysisId, String configKey) throws DatabaseHandlerException;

    /**
     * Get a double value of model configuration.
     *
     * @param analysisId unique id of the analysis
     * @param configKey  model configuration key
     * @return Model configuration as a double
     * @throws DatabaseHandlerException
     */
    double getADoubleModelConfiguration(long analysisId, String configKey) throws DatabaseHandlerException;

    /**
     * Get a boolean value of model configuration.
     *
     * @param analysisId unique id of the analysis
     * @param configKey  model configuration key
     * @return Model configuration as a double
     * @throws DatabaseHandlerException
     */
    boolean getABooleanModelConfiguration(long analysisId, String configKey) throws DatabaseHandlerException;

    /**
     * Get the list of Hyper-parameters of the model.
     *
     * @param analysisId    Unique ID of the analysis
     * @param algorithmName Algorithm name
     * @return List of {@link org.wso2.carbon.ml.commons.domain.MLHyperParameter} objects
     * @throws DatabaseHandlerException
     */
    List<MLHyperParameter> getHyperParametersOfModel(long analysisId, String algorithmName) throws DatabaseHandlerException;


     /**
     * Get the Hyper-parameters of the model as a Map.
     *
     * @param modelId unique id of the model

     * @return String map of model hyper-parameters
     * @throws DatabaseHandlerException
     */
    Map<String, Map<String, String>> getHyperParametersOfModelWithNameAsMap(long modelId) throws DatabaseHandlerException;

    /**
     * Get the Hyper-parameters of the model as a Map.
     *
     * @param modelId unique id of the model
     * @return String map of model hyper-parameters
     * @throws DatabaseHandlerException
     */
    Map<String, String> getHyperParametersOfModelAsMap(long modelId) throws DatabaseHandlerException;

    /**
     * Get the workflow of the analysis.
     *
     * @param analysisId unique id of the analysis
     * @return {@link org.wso2.carbon.ml.commons.domain.Workflow} object
     * @throws DatabaseHandlerException
     */
    Workflow getWorkflow(long analysisId, String algorithmName) throws DatabaseHandlerException;

    /**
     * Get the Model storage of the model.
     *
     * @param modelId unique id of the model
     * @return {@link org.wso2.carbon.ml.commons.domain.MLStorage} object
     * @throws DatabaseHandlerException
     */
    MLStorage getModelStorage(long modelId) throws DatabaseHandlerException;

    /**
     * Get the project having the given project name.
     *
     * @param tenantId    tenant id
     * @param userName    username
     * @param projectName project name
     * @return {@link org.wso2.carbon.ml.commons.domain.MLProject} object
     * @throws DatabaseHandlerException
     */
    MLProject getProject(int tenantId, String userName, String projectName) throws DatabaseHandlerException;

    /**
     * Returns project object for a given project ID from the database.
     *
     * @param tenantId ID of the tenant
     * @param userName Username of the tenant
     * @param projectId ID of the project
     * @return {@link org.wso2.carbon.ml.commons.domain.MLProject} object
     * @throws DatabaseHandlerException
     */
    MLProject getProject(int tenantId, String userName, long projectId) throws DatabaseHandlerException;

    /**
     * Get all the projects of the given tenant and username.
     *
     * @param tenantId tenant id
     * @param userName username
     * @return List of {@link org.wso2.carbon.ml.commons.domain.MLProject} objects
     * @throws DatabaseHandlerException
     */
    List<MLProject> getAllProjects(int tenantId, String userName) throws DatabaseHandlerException;

    /**
     * Get all models of a given project.
     *
     * @param tenantId  tenant ID
     * @param userName  username
     * @param projectId Project ID
     * @return List of {@link org.wso2.carbon.ml.commons.domain.MLModelData} objects
     * @throws DatabaseHandlerException
     */
    List<MLModelData> getProjectModels(int tenantId, String userName, long projectId) throws DatabaseHandlerException;

    /**
     * Get all the analyses of the given tenant and username.
     *
     * @param tenantId tenant id
     * @param userName username
     * @return List of {@link org.wso2.carbon.ml.commons.domain.MLAnalysis} objects
     * @throws DatabaseHandlerException
     */
    List<MLAnalysis> getAllAnalyses(int tenantId, String userName) throws DatabaseHandlerException;

    /**
     * Get the Model having the given model name.
     *
     * @param tenantId  tenant id
     * @param userName  username
     * @param modelName model name
     * @return {@link org.wso2.carbon.ml.commons.domain.MLModelData} object
     * @throws DatabaseHandlerException
     */
    MLModelData getModel(int tenantId, String userName, String modelName) throws DatabaseHandlerException;

    /**
     * Get the model name identified by the given model id.
     *
     * @param tenantId tenant id
     * @param userName username
     * @param modelId  model id
     * @return {@link org.wso2.carbon.ml.commons.domain.MLModelData} object
     * @throws DatabaseHandlerException
     */
    MLModelData getModel(int tenantId, String userName, long modelId) throws DatabaseHandlerException;

    /**
     * Get all models of the given tenant and username.
     *
     * @param tenantId tenant id
     * @param userName username
     * @return List of {@link org.wso2.carbon.ml.commons.domain.MLModelData} objects
     * @throws DatabaseHandlerException
     */
    List<MLModelData> getAllModels(int tenantId, String userName) throws DatabaseHandlerException;

    /**
     * Get all the dataset-versions of the given dataset schema.
     *
     * @param tenantId  ID of the tenant
     * @param userName  Username of the tenant
     * @param datasetId dataset schema id
     * @return List of {@link org.wso2.carbon.ml.commons.domain.MLDatasetVersion} objects
     * @throws DatabaseHandlerException
     */
    List<MLDatasetVersion> getAllVersionsetsOfDataset(int tenantId, String userName, long datasetId)
            throws DatabaseHandlerException;

    /**
     * Get all the dataset schemas of the given tenant and username.
     *
     * @param tenantId ID of the tenant
     * @param userName Username of the tenant
     * @return List of {@link org.wso2.carbon.ml.commons.domain.MLDataset} objects
     * @throws DatabaseHandlerException
     */
    List<MLDataset> getAllDatasets(int tenantId, String userName) throws DatabaseHandlerException;

    /**
     * Get the dataset schema identified by the given dataset schema id.
     *
     * @param tenantId  ID of the tenant
     * @param userName  Username of the tenant
     * @param datasetId Dataset schema ID
     * @return {@link org.wso2.carbon.ml.commons.domain.MLDataset} object
     * @throws DatabaseHandlerException
     */
    MLDataset getDataset(int tenantId, String userName, long datasetId) throws DatabaseHandlerException;

    /**
     * Get the dataset-version identified by the given dataset-version id.
     *
     * @param tenantId         ID of the tenant
     * @param userName         Username of the tenant
     * @param datasetVersionId ID of the dataset version
     * @return {@link org.wso2.carbon.ml.commons.domain.MLDatasetVersion} object
     * @throws DatabaseHandlerException
     */
    MLDatasetVersion getVersionset(int tenantId, String userName, long datasetVersionId) throws DatabaseHandlerException;

    /**
     * Get the unique identifier of the dataset version given the dataset schema and the version.
     *
     * @param datasetId ID of the dataset
     * @param version   Version of the dataset version
     * @param tenantId  ID of the tenant
     * @param userName  Username of the tenant
     * @return {@link org.wso2.carbon.ml.commons.domain.MLDatasetVersion} object
     * @throws DatabaseHandlerException
     */
    MLDatasetVersion getVersionSetWithVersion(long datasetId, String version, int tenantId, String userName)
            throws DatabaseHandlerException;

    /**
     * Insert the default feature attributes into the relevant customized feature attributes of a given analysis.
     *
     * @param analysisId       ID of the analysis
     * @param customizedValues customized feature
     * @throws DatabaseHandlerException
     */
    void insertDefaultsIntoFeatureCustomized(long analysisId, MLCustomizedFeature customizedValues)
            throws DatabaseHandlerException;

    /**
     * Get the dataset schema ID of a given analysis.
     *
     * @param analysisId ID of the analysis
     * @return ID of the dataset schema
     * @throws DatabaseHandlerException
     */
    long getDatasetSchemaIdFromAnalysisId(long analysisId) throws DatabaseHandlerException;

    /**
     * Delete the model from the database.
     *
     * @param tenantId tenant id
     * @param userName username
     * @param modelId  model id
     * @throws DatabaseHandlerException
     */
    void deleteModel(int tenantId, String userName, long modelId) throws DatabaseHandlerException;

    /**
     * Get all the analyses of a project.
     *
     * @param tenantId  tenant id
     * @param userName  user name
     * @param projectId project id
     * @return List of {@link org.wso2.carbon.ml.commons.domain.MLAnalysis} objects
     * @throws DatabaseHandlerException
     */
    List<MLAnalysis> getAllAnalysesOfProject(int tenantId, String userName, long projectId)
            throws DatabaseHandlerException;

    /**
     * Get all models of a given analysis.
     *
     * @param tenantId   ID of the tenant
     * @param userName   Username of the tenant
     * @param analysisId ID of the analysis
     * @return List of {@link org.wso2.carbon.ml.commons.domain.MLModelData} objects
     * @throws DatabaseHandlerException
     */
    List<MLModelData> getAllModels(int tenantId, String userName, long analysisId) throws DatabaseHandlerException;

    /**
     * Retrieve summary of the model.
     * 
     * @param modelId   ID of the model
     * @return {@link org.wso2.carbon.ml.commons.domain.ModelSummary} object
     * @throws DatabaseHandlerException
     */
    ModelSummary getModelSummary(long modelId) throws DatabaseHandlerException;

    /**
     * Update model status.
     *
     * @param modelId Unique id of the model
     * @param status  Status to be updated
     * @throws DatabaseHandlerException
     */
    void updateModelStatus(long modelId, String status) throws DatabaseHandlerException;

    /**
     * Get analysis by id.
     *
     * @param tenantId   Tenant id
     * @param userName   Tenant user name
     * @param analysisId Unique id of the analysis
     * @return {@link org.wso2.carbon.ml.commons.domain.MLAnalysis} object
     * @throws DatabaseHandlerException
     */
    MLAnalysis getAnalysis(int tenantId, String userName, long analysisId) throws DatabaseHandlerException;

    /**
     * Delete the dataset schema.
     *
     * @param datasetId unique id of dataset schema
     * @throws DatabaseHandlerException
     */
    void deleteDataset(long datasetId) throws DatabaseHandlerException;

    /**
     * Delete the dataset version.
     *
     * @param datasetVersionId unique id of dataset version
     * @throws DatabaseHandlerException
     */
    void deleteDatasetVersion(long datasetVersionId) throws DatabaseHandlerException;

    /**
     * Returns an analysis of a given name in a given project.
     *
     * @param tenantId     Tenant id
     * @param userName     Tenant user name
     * @param projectId    Unique id of the project
     * @param analysisName Name of the analysis
     * @return {@link org.wso2.carbon.ml.commons.domain.MLAnalysis} object
     * @throws DatabaseHandlerException
     */
    MLAnalysis getAnalysisOfProject(int tenantId, String userName, long projectId, String analysisName) throws DatabaseHandlerException;

    /**
     * Delete an analysis.
     *
     * @param tenantId   Tenant id
     * @param userName   Tenant user name
     * @param analysisId Unique id of the analysis
     * @throws DatabaseHandlerException
     */
    void deleteAnalysis(int tenantId, String userName, long analysisId) throws DatabaseHandlerException;

    /**
     * Update model error.
     *
     * @param modelId Unique id of the model
     * @param error   Error value to be updated
     * @throws DatabaseHandlerException
     */
    void updateModelError(long modelId, String error) throws DatabaseHandlerException;

    /**
     * Get feature names of a dataset ordered by feature index.
     *
     * @param datasetId       Unique id of dataset
     * @param columnSeparator Column separator (CSV or TSV)
     * @return A string containing the list of feature names separated by the given column separator
     * @throws DatabaseHandlerException
     */
    String getFeatureNamesInOrder(long datasetId, String columnSeparator) throws DatabaseHandlerException;

    /**
     * Get feature names of a dataset version ordered by feature index.
     *
     * @param datasetVersionId Unique id of the dataset version
     * @param columnSeparator  Column separator (CSV or TSV)
     * @return A string containing the list of feature names separated by the given column separator
     * @throws DatabaseHandlerException
     */
    String getFeatureNamesInOrderUsingDatasetVersion(long datasetVersionId, String columnSeparator)
            throws DatabaseHandlerException;

    /**
     * Get the summary stats of the dataset version.
     *
     * @param datasetVersionId Unique id of the dataset version
     * @return A map of <feature-name, summary-stat> pairs
     * @throws DatabaseHandlerException
     */
    Map<String, String> getSummaryStats(long datasetVersionId) throws DatabaseHandlerException;

    /**
     * Get feature names of a dataset.
     *
     * @param datasetId Unique id of the dataset.
     * @return A list of feature names
     * @throws DatabaseHandlerException
     */
    List<String> getFeatureNames(long datasetId) throws DatabaseHandlerException;

    /**
     * Executes the SHUTDOWN statement.
     *
     * @throws DatabaseHandlerException
     */
    void shutdown() throws DatabaseHandlerException;

}
