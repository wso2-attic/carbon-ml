/*
 * Copyright (c) 2016, WSO2 Inc. (http://www.wso2.org) All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.wso2.carbon.ml.rest.api;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import javax.ws.rs.*;
import javax.ws.rs.core.MediaType;
import javax.ws.rs.core.Response;
import javax.ws.rs.core.StreamingOutput;
import com.owlike.genson.Genson;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.apache.cxf.jaxrs.ext.multipart.Multipart;
import org.apache.hadoop.fs.InvalidRequestException;
import org.apache.http.HttpHeaders;
import org.codehaus.jackson.map.ObjectMapper;
import org.json.JSONArray;
import org.json.JSONObject;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.ml.commons.constants.MLConstants;
import org.wso2.carbon.ml.commons.domain.MLModel;
import org.wso2.carbon.ml.commons.domain.MLModelData;
import org.wso2.carbon.ml.commons.domain.MLStorage;
import org.wso2.carbon.ml.commons.domain.ModelSummary;
import org.wso2.carbon.ml.core.exceptions.MLModelBuilderException;
import org.wso2.carbon.ml.core.exceptions.MLModelHandlerException;
import org.wso2.carbon.ml.core.exceptions.MLModelPublisherException;
import org.wso2.carbon.ml.core.impl.MLModelHandler;
import org.wso2.carbon.ml.commons.domain.config.MLAlgorithm;
import org.wso2.carbon.ml.core.exceptions.MLPmmlExportException;
import org.wso2.carbon.ml.core.utils.MLCoreServiceValueHolder;
import org.wso2.carbon.ml.core.utils.MLUtils;
import org.wso2.carbon.ml.rest.api.model.MLErrorBean;
import org.wso2.carbon.ml.rest.api.model.MLResponseBean;
import org.wso2.carbon.ml.rest.api.neuralNetworks.FeedForwardNetwork;
import org.wso2.carbon.ml.rest.api.neuralNetworks.HiddenLayerDetails;
import org.wso2.carbon.ml.rest.api.neuralNetworks.OutputLayerDetails;

/**
 * This class is to handle REST verbs GET , POST and DELETE.
 */
@Path("/models")
public class ModelApiV20 extends MLRestAPI {

    private static final Log logger = LogFactory.getLog(ModelApiV20.class);
    private MLModelHandler mlModelHandler;

    public ModelApiV20() {
        mlModelHandler = new MLModelHandler();
    }

    @OPTIONS
    public Response options() {
        return Response.ok().header(HttpHeaders.ALLOW, "GET POST DELETE").build();
    }

    /**
     * Create a new Model.
     *
     * @param model {@link MLModelData} object
     * @return JSON of {@link MLModelData} object
     */
    @POST
    @Produces("application/json")
    @Consumes("application/json")
    public Response createModel(MLModelData model) {
        if (model.getAnalysisId() == 0 || model.getVersionSetId() == 0) {
            logger.error("Required parameters missing");
            return Response.status(Response.Status.BAD_REQUEST).entity("Required parameters missing").build();
        }
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        try {
            int tenantId = carbonContext.getTenantId();
            String userName = carbonContext.getUsername();
            model.setTenantId(tenantId);
            model.setUserName(userName);
            MLModelData insertedModel = mlModelHandler.createModel(model);

            //hide null json fields in response
            String[] fieldsToHide = {MLConstants.ML_MODEL_DATA_ID, MLConstants.ML_MODEL_DATA_CREATED_TIME,
                    MLConstants.ML_MODEL_DATA_DATASET_VERSION, MLConstants.ML_MODEL_DATA_ERROR,
                    MLConstants.ML_MODEL_DATA_MODEL_SUMMARY};
            Genson.Builder builder = new Genson.Builder();
            for (int i = 0; i < fieldsToHide.length; i++) {
                builder = builder.exclude(fieldsToHide[i], MLModelData.class);
            }
            Genson genson = builder.create();
            String insertedModelJson = genson.serialize(insertedModel);
            return Response.ok(insertedModelJson).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg("Error occurred while creating a model : " + model, e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Create a new model storage
     *
     * @param modelId Unique id of the model
     * @param storage {@link MLStorage} object
     */
    @POST
    @Path("/{modelId}/storages")
    @Produces("application/json")
    @Consumes("application/json")
    public Response addStorage(@PathParam("modelId") long modelId, MLStorage storage) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            mlModelHandler.addStorage(modelId, storage);
            return Response.ok().build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while adding storage for the model [id] %s of tenant [id] %s and [user] %s .",
                    modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Build the model
     *
     * @param modelId Unique id of the model to be built.
     */
    @POST
    @Path("/{modelId}")
    @Produces("application/json")
    @Consumes("application/json")
    public Response buildModel(@PathParam("modelId") long modelId) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            mlModelHandler.buildModel(tenantId, userName, modelId);
            return Response.ok().build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while building the model [id] %s of tenant [id] %s and [user] %s .", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        } catch (MLModelBuilderException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while building the model [id] %s of tenant [id] %s and [user] %s .", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Publish the model to ML registry
     *
     * @param modelId Unique id of the model to be published
     * @return JSON of {@link MLResponseBean} containing the published location of the model
     */
    @POST
    @Path("/{modelId}/publish")
    @Produces("application/json")
    @Consumes("application/json")
    public Response publishModel(@PathParam("modelId") long modelId, @QueryParam("mode") String mode) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        boolean isPMMLSupported = false;

        try {
            MLModelData model = mlModelHandler.getModel(tenantId, userName, modelId);
            // check pmml support
            if (model != null) {
                final MLModel generatedModel = mlModelHandler.retrieveModel(model.getId());
                String algorithmName = generatedModel.getAlgorithmName();
                List<MLAlgorithm> mlAlgorithms = MLCoreServiceValueHolder.getInstance().getAlgorithms();
                for (MLAlgorithm mlAlgorithm : mlAlgorithms) {
                    if (algorithmName.equals(mlAlgorithm.getName()) && mlAlgorithm.getPmmlExportable()) {
                        isPMMLSupported = true;
                        break;
                    }
                }
                if (isPMMLSupported && (mode == null || mode.equals(MLConstants.ML_MODEL_FORMAT_PMML))) {
                    String registryPath = mlModelHandler.publishModel(tenantId, userName, modelId, MLModelHandler.Format.PMML);
                    return Response.ok(new MLResponseBean(registryPath)).build();
                } else if (mode == null || mode.equals(MLConstants.ML_MODEL_FORMAT_SERIALIZED)) {
                    String registryPath = mlModelHandler.publishModel(tenantId, userName, modelId, MLModelHandler.Format.SERIALIZED);
                    return Response.ok(new MLResponseBean(registryPath)).build();
                } else {
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (InvalidRequestException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while publishing the model [id] %s of tenant [id] %s and [user] %s .", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.BAD_REQUEST).entity(new MLErrorBean(e.getMessage())).build();
        } catch (MLModelPublisherException | MLModelHandlerException | MLPmmlExportException e) {
            String msg = MLUtils.getErrorMsg(
                    String.format("Error occurred while publishing the model [id] %s of tenant [id] %s and [user] %s .",
                            modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Predict using a file and return as a list of predicted values.
     *
     * @param modelId      Unique id of the model
     * @param dataFormat   Data format of the file (CSV or TSV)
     * @param inputStream  File input stream generated from the file used for predictions
     * @param percentile   a threshold value used to identified cluster boundaries
     * @param skipDecoding whether the decoding should not be done (true or false)
     * @return JSON array of predictions
     */
    @POST
    @Path("/predict")
    @Produces(MediaType.APPLICATION_JSON)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response predict(@Multipart("modelId") long modelId, @Multipart("dataFormat") String dataFormat,
                            @Multipart("file") InputStream inputStream, @QueryParam("percentile") double percentile,
                            @QueryParam("skipDecoding") boolean skipDecoding) {

        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            // validate input parameters
            // if it is a file upload, check whether the file is sent
            if (inputStream == null || inputStream.available() == 0) {
                String msg = String.format(
                        "Error occurred while reading the file for model [id] %s of tenant [id] %s and [user] %s .",
                        modelId, tenantId, userName);
                logger.error(msg);
                return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(msg)).build();
            }
            List<?> predictions = mlModelHandler.predict(tenantId, userName, modelId, dataFormat, inputStream,
                    percentile, skipDecoding);
            return Response.ok(predictions).build();
        } catch (IOException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while reading the file for model [id] %s of tenant [id] %s and [user] %s.",
                    modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.BAD_REQUEST).entity(new MLErrorBean(e.getMessage())).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while predicting from model [id] %s of tenant [id] %s and [user] %s.", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Predict using a file and return predictions as a CSV.
     *
     * @param modelId      Unique id of the model
     * @param dataFormat   Data format of the file (CSV or TSV)
     * @param columnHeader Whether the file contains the column header as the first row (YES or NO)
     * @param inputStream  Input stream generated from the file used for predictions
     * @param percentile   a threshold value used to identified cluster boundaries
     * @param skipDecoding whether the decoding should not be done (true or false)
     * @return A file as a {@link StreamingOutput}
     */
    @POST
    @Path("/predictionStreams")
    @Produces(MediaType.APPLICATION_OCTET_STREAM)
    @Consumes(MediaType.MULTIPART_FORM_DATA)
    public Response streamingPredqict(@Multipart("modelId") long modelId, @Multipart("dataFormat") String dataFormat,
                                      @Multipart("columnHeader") String columnHeader, @Multipart("file") InputStream inputStream,
                                      @QueryParam("percentile") double percentile, @QueryParam("skipDecoding") boolean skipDecoding) {

        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            // validate input parameters
            // if it is a file upload, check whether the file is sent
            if (inputStream == null || inputStream.available() == 0) {
                String msg = String.format(
                        "No file found to predict with model [id] %s of tenant [id] %s and [user] %s .",
                        modelId, tenantId, userName);
                logger.error(msg);
                return Response.status(Response.Status.BAD_REQUEST).entity(new MLErrorBean(msg))
                        .type(MediaType.APPLICATION_JSON).build();
            }
            final String predictions = mlModelHandler.streamingPredict(tenantId, userName, modelId, dataFormat,
                    columnHeader, inputStream, percentile, skipDecoding);

            StreamingOutput stream = new StreamingOutput() {
                @Override
                public void write(OutputStream outputStream) throws IOException {
                    Writer writer = new BufferedWriter(new OutputStreamWriter(outputStream, StandardCharsets.UTF_8));
                    writer.write(predictions);
                    writer.flush();
                    writer.close();
                }
            };
            return Response
                    .ok(stream)
                    .header("Content-disposition",
                            "attachment; filename=Predictions_" + modelId + "_" + MLUtils.getDate() + MLConstants.CSV)
                    .build();
        } catch (IOException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while reading the file for model [id] %s of tenant [id] %s and [user] %s.",
                    modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.BAD_REQUEST).entity(new MLErrorBean(e.getMessage()))
                    .type(MediaType.APPLICATION_JSON).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while predicting from model [id] %s of tenant [id] %s and [user] %s.", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .type(MediaType.APPLICATION_JSON).build();
        }
    }

    /**
     * Make predictions using a model
     *
     * @param modelId      Unique id of the model
     * @param data         List of string arrays containing the feature values used for predictions
     * @param percentile   a threshold value used to identified cluster boundaries
     * @param skipDecoding whether the decoding should not be done (true or false)
     * @return JSON array of predicted values
     */
    @POST
    @Path("/{modelId}/predict")
    @Produces("application/json")
    @Consumes("application/json")
    public Response predict(@PathParam("modelId") long modelId, List<String[]> data,
                            @QueryParam("percentile") double percentile, @QueryParam("skipDecoding") boolean skipDecoding) {

        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            long t1 = System.currentTimeMillis();
            List<?> predictions = mlModelHandler.predict(tenantId, userName, modelId, data, percentile, skipDecoding);
            logger.info(String.format("Prediction from model [id] %s finished in %s seconds.", modelId,
                    (System.currentTimeMillis() - t1) / 1000.0));
            return Response.ok(predictions).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while predicting from model [id] %s of tenant [id] %s and [user] %s.", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Get the model data
     *
     * @param modelName Name of the model
     * @return JSON of {@link MLModelData} object
     */
    @GET
    @Path("/{modelName}")
    @Produces("application/json")
    public Response getModel(@PathParam("modelName") String modelName) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            MLModelData model = mlModelHandler.getModel(tenantId, userName, modelName);
            if (model == null) {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
            return Response.ok(model).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while retrieving a model [name] %s of tenant [id] %s and [user] %s .", modelName,
                    tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Get all models
     *
     * @return JSON array of {@link MLModelData} objects
     */
    @GET
    @Produces("application/json")
    public Response getAllModels() {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            List<MLModelData> models = mlModelHandler.getAllModels(tenantId, userName);
            return Response.ok(models).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(
                    String.format("Error occurred while retrieving all models of tenant [id] %s and [user] %s .",
                            tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Delete a model
     *
     * @param modelId Unique id of the model
     */
    @DELETE
    @Path("/{modelId}")
    @Produces("application/json")
    public Response deleteModel(@PathParam("modelId") long modelId) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            mlModelHandler.deleteModel(tenantId, userName, modelId);
            auditLog.info(String.format("User [name] %s of tenant [id] %s deleted a model [id] %s ", userName,
                    tenantId, modelId));
            return Response.ok().build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while deleting a model [id] %s of tenant [id] %s and [user] %s .", modelId,
                    tenantId, userName), e);
            logger.error(msg, e);
            auditLog.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Get the model summary
     *
     * @param modelId Unique id of the model
     * @return JSON of {@link ModelSummary} object
     */
    @GET
    @Path("/{modelId}/summary")
    @Produces("application/json")
    @Consumes("application/json")
    public Response getModelSummary(@PathParam("modelId") long modelId) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            ModelSummary modelSummary = mlModelHandler.getModelSummary(modelId);
            return Response.ok(modelSummary).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while retrieving summary of the model [id] %s of tenant [id] %s and [user] %s .",
                    modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Download the model
     *
     * @param modelId Name of the model
     * @return A {@link MLModel} as a {@link StreamingOutput}
     */
    @GET
    @Path("/{modelId}/export")
    public Response exportModel(@PathParam("modelId") long modelId, @QueryParam("mode") String mode) {
        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        boolean isPMMLSupported = false;

        try {
            MLModelData model = mlModelHandler.getModel(tenantId, userName, modelId);

            if (model != null) {
                String modelName = model.getName();
                final MLModel generatedModel = mlModelHandler.retrieveModel(model.getId());

                // check pmml support
                String algorithmName = generatedModel.getAlgorithmName();
                List<MLAlgorithm> mlAlgorithms = MLCoreServiceValueHolder.getInstance().getAlgorithms();
                for (MLAlgorithm mlAlgorithm : mlAlgorithms) {
                    if (algorithmName.equals(mlAlgorithm.getName()) && mlAlgorithm.getPmmlExportable()) {
                        isPMMLSupported = true;
                        break;
                    }
                }

                if (isPMMLSupported && (mode == null || mode.equals(MLConstants.ML_MODEL_FORMAT_PMML))) {
                    final String pmmlModel = mlModelHandler.exportAsPMML(generatedModel);
                    logger.info(String.format("Successfully exported model [id] %s into pmml format", modelId));
                    return Response.ok(pmmlModel)
                            .header("Content-disposition", "attachment; filename=" + modelName + "PMML.xml").build();
                } else if (mode == null || mode.equals(MLConstants.ML_MODEL_FORMAT_SERIALIZED)) {
                    StreamingOutput stream = new StreamingOutput() {
                        public void write(OutputStream outputStream) throws IOException {
                            ObjectOutputStream out = new ObjectOutputStream(outputStream);
                            out.writeObject(generatedModel);
                        }
                    };
                    return Response.ok(stream).header("Content-disposition", "attachment; filename=" + modelName)
                            .build();
                } else {
                    return Response.status(Response.Status.BAD_REQUEST).build();
                }
            } else {
                return Response.status(Response.Status.NOT_FOUND).build();
            }
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(
                    String.format("Error occurred while retrieving model [name] %s of tenant [id] %s and [user] %s .",
                            modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        } catch (MLPmmlExportException e) {

            String msg = MLUtils.getErrorMsg(String.format(
                    "Error occurred while exporting to pmml model [name] %s of tenant [id] %s and [user] %s .",
                    modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();

        }
    }

    /**
     * Get a list of recommended products for a given user using the given model.
     *
     * @param modelId      id of the recommendation model to be used.
     * @param userId       id of the user.
     * @param noOfProducts number of recommendations required.
     * @return an array of product recommendations.
     */
    @GET
    @Path("/{modelId}/product-recommendations")
    @Produces("application/json")
    public Response getProductRecommendations(@PathParam("modelId") long modelId,
                                              @QueryParam("user-id") int userId,
                                              @QueryParam("no-of-products") int noOfProducts) {

        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            List<?> recommendations =
                    mlModelHandler.getProductRecommendations(tenantId, userName, modelId, userId, noOfProducts);
            return Response.ok(recommendations).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format("Error occurred while getting recommendations from model [id] %s of tenant [id] %s and [user] %s.",
                    modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Get a list of recommended users for a given product using the given model.
     *
     * @param modelId   id of the recommendation model to be used.
     * @param productId id of the product.
     * @param noOfUsers number of recommendations required.
     * @return an array of user recommendations.
     */
    @GET
    @Path("/{modelId}/user-recommendations")
    @Produces("application/json")
    public Response getUserRecommendations(@PathParam("modelId") long modelId,
                                           @QueryParam("product-id") int productId,
                                           @QueryParam("no-of-users") int noOfUsers) {

        PrivilegedCarbonContext carbonContext = PrivilegedCarbonContext.getThreadLocalCarbonContext();
        int tenantId = carbonContext.getTenantId();
        String userName = carbonContext.getUsername();
        try {
            List<?> recommendations =
                    mlModelHandler.getUserRecommendations(tenantId, userName, modelId, productId, noOfUsers);
            return Response.ok(recommendations).build();
        } catch (MLModelHandlerException e) {
            String msg = MLUtils.getErrorMsg(String.format("Error occurred while getting recommendations from model [id] %s of tenant [id] %s and [user] %s.",
                    modelId, tenantId, userName), e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }

    /**
     * Create a model for Neural networks.
     *
     * @return JSON of the performance/evaluation statistics of the trained model
     */
    @POST
    @Path("/neural-network")
    @Consumes("application/json")
    @Produces("application/json")

    public Response getNeuralNetwork(String networkDetails) {
        try {
            String statistics = "";
            //read json object
            JSONObject networkDetail = new JSONObject(networkDetails);
            //convert the json data to pass to the respective neural network class
            String networkName = networkDetail.getString("networkName");
            long seed = networkDetail.getLong("seed");
            double learningRate = networkDetail.getDouble("learningRate");
            int bachSize = networkDetail.getInt("batchSize");
            double nepoches = networkDetail.getDouble("nepoches");
            int iterations = networkDetail.getInt("iteration");
            String optimizationAlgorithm = networkDetail.getString("optimizationAlgorithms");
            String updater = networkDetail.getString("updater");
            double momentum = networkDetail.getDouble("momentum");
            boolean pretrain = networkDetail.getBoolean("pretrain");
            boolean backprop = networkDetail.getBoolean("backprop");
            int noHiddenLayers = networkDetail.getInt("hiddenlayerno");
            int inputLayerNodes = networkDetail.getInt("inputlayernodes");
            int datasetId = networkDetail.getInt("datasetId");
            int versionId = networkDetail.getInt("versionID");
            int analysisId = networkDetail.getInt("analysisID");
            JSONArray jsonArrayHiddenDetails = networkDetail.getJSONArray("hiddenlayerDetails");
            JSONArray jsonArrayOutputDetails = networkDetail.getJSONArray("outputlayerDetails");
            List<HiddenLayerDetails> hiddenLayerList = new ArrayList<>();
            List<OutputLayerDetails> outputLayerList = new ArrayList<>();

            for (int i = 0; i < jsonArrayHiddenDetails.length(); i++) {
                JSONObject hiddenJSONObject = jsonArrayHiddenDetails.getJSONObject(i);
                int hiddenNodes = hiddenJSONObject.getInt("hiddenlayernodes");
                String hiddenWeightInit = hiddenJSONObject.getString("hiddenlayerweightinit");
                String hiddenActivation = hiddenJSONObject.getString("hiddenlayeractivation");
                hiddenLayerList.add(new HiddenLayerDetails(hiddenNodes, hiddenWeightInit, hiddenActivation));
            }

            for (int j = 0; j < jsonArrayOutputDetails.length(); j++) {
                JSONObject outputJSONObject = jsonArrayOutputDetails.getJSONObject(j);
                int outputNodes = outputJSONObject.getInt("outputlayernodes");
                String outputWeightInit = outputJSONObject.getString("outputlayerweightinit");
                String outputActivation = outputJSONObject.getString("outputlayeractivation");
                String outputLossFunction = outputJSONObject.getString("outputlaterlossfunction");
                outputLayerList.add(new OutputLayerDetails(outputNodes, outputWeightInit, outputActivation, outputLossFunction));
            }

            //make FeedForwardNetwork class object
            FeedForwardNetwork net = new FeedForwardNetwork();
            //Call createFeedForwardNetwork method
            statistics = net.createFeedForwardNetwork(seed, learningRate, bachSize, nepoches, iterations, optimizationAlgorithm, updater, momentum, pretrain, backprop, noHiddenLayers, inputLayerNodes, datasetId, versionId, analysisId, hiddenLayerList, outputLayerList);
            ObjectMapper objectMapper = new ObjectMapper();
            Object statJson = objectMapper.readValue(objectMapper.writeValueAsString(statistics), Object.class);
            logger.info("API Response " + statJson.toString());
            return Response.ok(statJson).build();

        }

        //Catch IOException
        catch (IOException e){
            String msg = MLUtils.getErrorMsg("IO exception has been fired in the server side!!!", e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
        //catch InterruptedException
        catch(InterruptedException e){
            String msg = MLUtils.getErrorMsg("Interrupted exception has been fired in the server side!!!", e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
        //catch all exceptions
        catch(Exception e){
            String msg = MLUtils.getErrorMsg("Error has been fired in the server side!!!", e);
            logger.error(msg, e);
            return Response.status(Response.Status.INTERNAL_SERVER_ERROR).entity(new MLErrorBean(e.getMessage()))
                    .build();
        }
    }
}
