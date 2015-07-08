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
package org.wso2.carbon.ml.core.utils;

import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Pattern;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.lang.math.NumberUtils;
import org.apache.spark.api.java.JavaRDD;
import org.apache.spark.api.java.JavaSparkContext;
import org.apache.spark.sql.DataFrame;
import org.apache.spark.sql.Row;
import org.apache.spark.sql.SQLContext;
import org.wso2.carbon.analytics.api.AnalyticsDataAPI;
import org.wso2.carbon.analytics.datasource.commons.AnalyticsSchema;
import org.wso2.carbon.analytics.datasource.commons.ColumnDefinition;
import org.wso2.carbon.analytics.datasource.commons.exception.AnalyticsException;
import org.wso2.carbon.analytics.datasource.commons.exception.AnalyticsTableNotAvailableException;
import org.wso2.carbon.context.PrivilegedCarbonContext;
import org.wso2.carbon.ml.commons.constants.MLConstants;
import org.wso2.carbon.ml.commons.domain.*;
import org.wso2.carbon.ml.core.internal.MLModelConfigurationContext;
import org.wso2.carbon.ml.core.spark.transformations.HeaderFilter;
import org.wso2.carbon.ml.core.spark.transformations.LineToTokens;
import org.wso2.carbon.ml.commons.domain.config.MLProperty;
import org.wso2.carbon.ml.core.spark.transformations.DiscardedRowsFilter;
import org.wso2.carbon.ml.core.spark.transformations.RowsToLines;
import org.wso2.carbon.ml.core.exceptions.MLMalformedDatasetException;
import org.wso2.carbon.ml.database.DatabaseService;
import org.wso2.carbon.ml.database.exceptions.DatabaseHandlerException;
import org.wso2.carbon.utils.ConfigurationContextService;

public class MLUtils {

    /**
     * Generate a random sample of the dataset using Spark.
     */
    public static SamplePoints getSample(String path, String dataType, int sampleSize, boolean containsHeader,
            String sourceType, int tenantId) throws MLMalformedDatasetException {

        JavaSparkContext sparkContext = null;
        try {
            Map<String, Integer> headerMap = null;
            // List containing actual data of the sample.
            List<List<String>> columnData = new ArrayList<List<String>>();
            CSVFormat dataFormat = DataTypeFactory.getCSVFormat(dataType);

            // java spark context
            sparkContext = MLCoreServiceValueHolder.getInstance().getSparkContext();
            JavaRDD<String> lines;

            // parse lines in the dataset
            lines = sparkContext.textFile(path);
            return getSamplePoints(sampleSize, containsHeader, headerMap, columnData, dataFormat, lines);

        } catch (Exception e) {
            throw new MLMalformedDatasetException("Failed to extract the sample points from path: " + path
                    + ". Cause: " + e, e);
        }
    }

    /**
     * Generate a random sample of the dataset using Spark.
     */
    public static SamplePoints getSampleFromDAS(String path, int sampleSize, String sourceType, int tenantId)
            throws MLMalformedDatasetException {

        JavaSparkContext sparkContext = null;
        try {
            Map<String, Integer> headerMap = null;
            // List containing actual data of the sample.
            List<List<String>> columnData = new ArrayList<List<String>>();

            // java spark context
            sparkContext = MLCoreServiceValueHolder.getInstance().getSparkContext();
            JavaRDD<String> lines;
            String headerLine = extractHeaderLine(path, tenantId);
            headerMap = generateHeaderMap(headerLine, CSVFormat.RFC4180);

            // DAS case path = table name
            lines = getLinesFromDASTable(path, tenantId, sparkContext);

            return getSamplePoints(sampleSize, true, headerMap, columnData, CSVFormat.RFC4180, lines);

        } catch (Exception e) {
            throw new MLMalformedDatasetException("Failed to extract the sample points from path: " + path
                    + ". Cause: " + e, e);
        }
    }

    public static JavaRDD<String> getLinesFromDASTable(String tableName, int tenantId, JavaSparkContext sparkContext)
            throws AnalyticsTableNotAvailableException, AnalyticsException {
        JavaRDD<String> lines;
        String tableSchema = extractTableSchema(tableName, tenantId);
        SQLContext sqlCtx = new SQLContext(sparkContext);
        sqlCtx.sql("CREATE TEMPORARY TABLE ML_REF USING org.wso2.carbon.analytics.spark.core.util.AnalyticsRelationProvider "
                + "OPTIONS ("
                + "tenantId \""
                + tenantId
                + "\", "
                + "tableName \""
                + tableName
                + "\", "
                + "schema \""
                + tableSchema + "\"" + ")");

        DataFrame dataFrame = sqlCtx.sql("select * from ML_REF");
        JavaRDD<Row> rows = dataFrame.javaRDD();
        lines = rows.map(new RowsToLines(CSVFormat.RFC4180.getDelimiter() + ""));
        return lines;
    }

    private static SamplePoints getSamplePoints(int sampleSize, boolean containsHeader, Map<String, Integer> headerMap,
            List<List<String>> columnData, CSVFormat dataFormat, JavaRDD<String> lines) {
        int featureSize;
        int[] missing;
        int[] stringCellCount;
        // take the first line
        String firstLine = lines.first();
        // count the number of features
        featureSize = getFeatureSize(firstLine, dataFormat);

        List<Integer> featureIndices = new ArrayList<Integer>();
        for (int i = 0; i < featureSize; i++)
            featureIndices.add(i);

        JavaRDD<String[]> tokensDiscardedRemoved = filterRows(String.valueOf(dataFormat.getDelimiter()), lines.first(),
                lines, featureIndices);

        missing = new int[featureSize];
        stringCellCount = new int[featureSize];
        if (sampleSize >= 0 && featureSize > 0) {
            sampleSize = sampleSize / featureSize;
        }
        for (int i = 0; i < featureSize; i++) {
            columnData.add(new ArrayList<String>());
        }

        if (headerMap == null) {
            // generate the header map
            if (containsHeader) {
                headerMap = generateHeaderMap(lines.first(), dataFormat);
            } else {
                headerMap = generateHeaderMap(featureSize);
            }
        }

        // take a random sample
        List<String[]> sampleLines = tokensDiscardedRemoved.takeSample(false, sampleSize);

        // iterate through sample lines
        for (String[] columnValues : sampleLines) {
            for (int currentCol = 0; currentCol < featureSize; currentCol++) {
                // Check whether the row is complete.
                if (currentCol < columnValues.length) {
                    // Append the cell to the respective column.
                    columnData.get(currentCol).add(columnValues[currentCol]);

                    if (columnValues[currentCol].isEmpty()) {
                        // If the cell is empty, increase the missing value count.
                        missing[currentCol]++;
                    } else {
                        // check whether a column value is a string
                        if (!NumberUtils.isNumber(columnValues[currentCol])) {
                            stringCellCount[currentCol]++;
                        }
                    }
                } else {
                    columnData.get(currentCol).add(null);
                    missing[currentCol]++;
                }
            }
        }

        SamplePoints samplePoints = new SamplePoints();
        samplePoints.setHeader(headerMap);
        samplePoints.setSamplePoints(columnData);
        samplePoints.setMissing(missing);
        samplePoints.setStringCellCount(stringCellCount);
        return samplePoints;
    }

    public static String extractTableSchema(String path, int tenantId) throws AnalyticsTableNotAvailableException,
            AnalyticsException {
        if (path == null) {
            return null;
        }
        AnalyticsDataAPI analyticsDataApi = (AnalyticsDataAPI) PrivilegedCarbonContext.getThreadLocalCarbonContext()
                .getOSGiService(AnalyticsDataAPI.class, null);
        // table schema will be something like; <col1_name> <col1_type>,<col2_name> <col2_type>
        StringBuilder sb = new StringBuilder();
        AnalyticsSchema analyticsSchema = analyticsDataApi.getTableSchema(tenantId, path);
        Map<String, ColumnDefinition> columnsMap = analyticsSchema.getColumns();
        for (Iterator<Map.Entry<String, ColumnDefinition>> iterator = columnsMap.entrySet().iterator(); iterator
                .hasNext();) {
            Map.Entry<String, ColumnDefinition> column = iterator.next();
            sb.append(column.getKey() + " " + column.getValue().getType().name() + ",");
        }

        return sb.substring(0, sb.length() - 1);
    }

    public static String extractHeaderLine(String path, int tenantId) throws AnalyticsTableNotAvailableException,
            AnalyticsException {
        if (path == null) {
            return null;
        }

        AnalyticsDataAPI analyticsDataApi = (AnalyticsDataAPI) PrivilegedCarbonContext.getThreadLocalCarbonContext()
                .getOSGiService(AnalyticsDataAPI.class, null);
        // header line will be something like; <col1_name>,<col2_name>
        StringBuilder sb = new StringBuilder();
        AnalyticsSchema analyticsSchema = analyticsDataApi.getTableSchema(tenantId, path);
        Map<String, ColumnDefinition> columnsMap = analyticsSchema.getColumns();
        for (String columnName : columnsMap.keySet()) {
            sb.append(columnName + ",");
        }

        return sb.substring(0, sb.length() - 1);
    }

    public static class DataTypeFactory {
        public static CSVFormat getCSVFormat(String dataType) {
            if ("TSV".equalsIgnoreCase(dataType)) {
                return CSVFormat.TDF;
            }
            return CSVFormat.RFC4180;
        }
    }

    public static class ColumnSeparatorFactory {
        public static String getColumnSeparator(String dataType) {
            CSVFormat csvFormat = DataTypeFactory.getCSVFormat(dataType);
            return csvFormat.getDelimiter() + "";
        }
    }

    /**
     * Retrieve the indices of features where discard row imputaion is applied.
     * 
     * @param workflow Machine learning workflow
     * @param imputeOption Impute option
     * @return Returns indices of features where discard row imputaion is applied
     */
    public static List<Integer> getImputeFeatureIndices(Workflow workflow, List<Integer> newToOldIndicesList,
            String imputeOption) {
        List<Integer> imputeFeatureIndices = new ArrayList<Integer>();
        for (Feature feature : workflow.getFeatures()) {
            if (feature.getImputeOption().equals(imputeOption) && feature.isInclude() == true) {
                int currentIndex = feature.getIndex();
                int newIndex = newToOldIndicesList.indexOf(currentIndex) != -1 ? newToOldIndicesList
                        .indexOf(currentIndex) : currentIndex;
                imputeFeatureIndices.add(newIndex);
            }
        }
        return imputeFeatureIndices;
    }

    /**
     * Retrieve the index of a feature in the dataset.
     * 
     * @param feature Feature name
     * @param headerRow First row (header) in the data file
     * @param columnSeparator Column separator character
     * @return Index of the response variable
     */
    public static int getFeatureIndex(String feature, String headerRow, String columnSeparator) {
        int featureIndex = 0;
        String[] headerItems = headerRow.split(columnSeparator);
        for (int i = 0; i < headerItems.length; i++) {
            if (headerItems[i] != null) {
                String column = headerItems[i].replace("\"", "").trim();
                if (feature.equals(column)) {
                    featureIndex = i;
                    break;
                }
            }
        }
        return featureIndex;
    }

    /**
     * Retrieve the index of a feature in the dataset.
     */
    public static int getFeatureIndex(String featureName, List<Feature> features) {
        if (featureName == null || features == null) {
            return -1;
        }
        for (Feature feature : features) {
            if (featureName.equals(feature.getName())) {
                return feature.getIndex();
            }
        }
        return -1;
    }

    /**
     * @param workflow Workflow
     * @return A list of indices of features to be included in the model
     */
    public static SortedMap<Integer, String> getIncludedFeaturesAfterReordering(Workflow workflow,
            List<Integer> newToOldIndicesList, int responseIndex) {
        SortedMap<Integer, String> inlcudedFeatures = new TreeMap<Integer, String>();
        List<Feature> features = workflow.getFeatures();
        for (Feature feature : features) {
            if (feature.isInclude() == true && feature.getIndex() != responseIndex) {
                int currentIndex = feature.getIndex();
                int newIndex = newToOldIndicesList.indexOf(currentIndex);
                inlcudedFeatures.put(newIndex, feature.getName());
            }
        }
        return inlcudedFeatures;
    }

    /**
     * @param workflow Workflow
     * @return A list of indices of features to be included in the model
     */
    public static SortedMap<Integer, String> getIncludedFeatures(Workflow workflow, int responseIndex) {
        SortedMap<Integer, String> inlcudedFeatures = new TreeMap<Integer, String>();
        List<Feature> features = workflow.getFeatures();
        for (Feature feature : features) {
            if (feature.isInclude() == true && feature.getIndex() != responseIndex) {
                inlcudedFeatures.put(feature.getIndex(), feature.getName());
            }
        }
        return inlcudedFeatures;
    }

    /**
     * @param tenantId Tenant ID of the current user
     * @param datasetId ID of the datstet
     * @param userName Name of the current user
     * @param name Dataset name
     * @param version Dataset version
     * @param targetPath path of the stored data set
     * @param samplePoints Sample points of the dataset
     * @return Dataset Version Object
     */
    public static MLDatasetVersion getMLDatsetVersion(int tenantId, long datasetId, String userName, String name,
            String version, String targetPath, SamplePoints samplePoints) {
        MLDatasetVersion valueSet = new MLDatasetVersion();
        valueSet.setTenantId(tenantId);
        valueSet.setDatasetId(datasetId);
        valueSet.setName(name);
        valueSet.setVersion(version);
        valueSet.setTargetPath(targetPath);
        valueSet.setSamplePoints(samplePoints);
        valueSet.setUserName(userName);
        return valueSet;
    }

    /**
     * @return Current date and time
     */
    public static String getDate() {
        DateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd_HH-mm-ss");
        Date date = new Date();
        return dateFormat.format(date);
    }

    /**
     * Get {@link Properties} from a list of {@link MLProperty}
     * 
     * @param mlProperties list of {@link MLProperty}
     * @return {@link Properties}
     */
    public static Properties getProperties(List<MLProperty> mlProperties) {
        Properties properties = new Properties();
        for (MLProperty mlProperty : mlProperties) {
            if (mlProperty != null) {
                properties.put(mlProperty.getName(), mlProperty.getValue());
            }
        }
        return properties;

    }

    /**
     * @param inArray String array
     * @return Double array
     */
    public static double[] toDoubleArray(String[] inArray) {
        double[] outArray = new double[inArray.length];
        int idx = 0;
        for (String string : inArray) {
            outArray[idx] = Double.parseDouble(string);
            idx++;
        }

        return outArray;
    }

    public static Map<String, Integer> generateHeaderMap(int numberOfFeatures) {
        Map<String, Integer> headerMap = new HashMap<String, Integer>();
        for (int i = 1; i <= numberOfFeatures; i++) {
            headerMap.put("V" + i, i - 1);
        }
        return headerMap;
    }

    public static Map<String, Integer> generateHeaderMap(String line, CSVFormat format) {
        Map<String, Integer> headerMap = new HashMap<String, Integer>();
        String[] values = line.split("" + format.getDelimiter());
        int i = 0;
        for (String value : values) {
            headerMap.put(value, i);
            i++;
        }
        return headerMap;
    }

    public static int getFeatureSize(String line, CSVFormat format) {
        String[] values = line.split("" + format.getDelimiter());
        return values.length;
    }

    /**
     * Applies the discard filter to a JavaRDD
     * 
     * @param delimiter Column separator of the dataset
     * @param headerRow Header row
     * @param lines JavaRDD which contains the dataset
     * @param featureIndices Indices of the features to apply filter
     * @return filtered JavaRDD
     */
    public static JavaRDD<String[]> filterRows(String delimiter, String headerRow, JavaRDD<String> lines,
            List<Integer> featureIndices) {
        String columnSeparator = String.valueOf(delimiter);
        HeaderFilter headerFilter = new HeaderFilter(headerRow);
        JavaRDD<String> data = lines.filter(headerFilter);
        Pattern pattern = Pattern.compile(columnSeparator);
        LineToTokens lineToTokens = new LineToTokens(pattern);
        JavaRDD<String[]> tokens = data.map(lineToTokens);

        // get feature indices for discard imputation
        DiscardedRowsFilter discardedRowsFilter = new DiscardedRowsFilter(featureIndices);
        // Discard the row if any of the impute indices content have a missing value
        JavaRDD<String[]> tokensDiscardedRemoved = tokens.filter(discardedRowsFilter);

        return tokensDiscardedRemoved;
    }

    /**
     * format an error message.
     */
    public static String getErrorMsg(String customMessage, Exception ex) {
        if (ex != null) {
            return customMessage + " Cause: " + ex.getClass().getName() + " - " + ex.getMessage();
        }
        return customMessage;
    }

    /**
     * Utility method to get key from value of a map
     *
     * @param map Map to be searched for a key
     * @param value Value of the key
     */
    public static <T, E> T getKeyByValue(Map<T, E> map, E value) {
        for (Map.Entry<T, E> entry : map.entrySet()) {
            if (Objects.equals(value, entry.getValue())) {
                return entry.getKey();
            }
        }
        return null;
    }

    /**
     * Utility method to get the link to model build result page
     *
     * @param context ML model configuration context
     * @return link to model build result page
     * @throws DatabaseHandlerException
     */
    public static String getLink(MLModelConfigurationContext context, String status) throws DatabaseHandlerException {

        MLModelData mlModelData = context.getModel();
        long modelId = mlModelData.getId();
        String modelName = mlModelData.getName();
        long analysisId = mlModelData.getAnalysisId();
        int tenantId = mlModelData.getTenantId();
        String userName = mlModelData.getUserName();

        MLAnalysis analysis = null;
        String analysisName = null;
        MLProject mlProject = null;
        String projectName = null;
        long datasetId;
        DatabaseService databaseService = MLCoreServiceValueHolder.getInstance().getDatabaseService();
        try {
            analysis = databaseService.getAnalysis(tenantId, userName, analysisId);
            analysisName = analysis.getName();
            long projectId = analysis.getProjectId();

            mlProject = databaseService.getProject(tenantId, userName, projectId);
            projectName = mlProject.getName();
            datasetId = mlProject.getDatasetId();
        } catch (DatabaseHandlerException e) {
            throw new DatabaseHandlerException("Failed to generate link for model ID: " + modelId + ". Cause: " + e, e);
        }
        ConfigurationContextService configContextService = MLCoreServiceValueHolder.getInstance()
                .getConfigurationContextService();
        String mlUrl = configContextService.getServerConfigContext().getProperty("ml.url").toString();

        String link = mlUrl + "/site/analysis/analysis.jag?analysisId=" + analysisId + "&analysisName=" + analysisName + "&datasetId=" + datasetId;
        if(status.equals(MLConstants.MODEL_STATUS_COMPLETE)) {
            link = mlUrl + "/site/analysis/view-model.jag?analysisId=" + analysisId + "&datasetId=" + datasetId + "&modelId=" + modelId + "&projectName=" + projectName + "&" +
                    "analysisName=" + analysisName + "&modelName=" + modelName +"&fromCompare=false";
        }
        return link;
    }
}
