-- DATASET
CREATE TABLE ML_DATASET_SCHEMA(
DATASET_SCHEMA_ID BIGINT IDENTITY,
NAME VARCHAR(100),
TENANT_ID INT,
USERNAME VARCHAR(50),
COMMENTS VARCHAR(max),
SOURCE_TYPE VARCHAR(50),
TARGET_TYPE VARCHAR(50),
DATA_TYPE VARCHAR(50),
CONSTRAINT PK_DATASET_SCHEMA PRIMARY KEY(DATASET_SCHEMA_ID)
);

-- FEATURE_DEFAULTS
CREATE TABLE ML_FEATURE_DEFAULTS(
FEATURE_ID BIGINT IDENTITY,
DATASET_SCHEMA_ID BIGINT,
FEATURE_INDEX INT,
FEATURE_NAME VARCHAR(100) NOT NULL,
TYPE VARCHAR(20),
CONSTRAINT PK_FEATURE_DEFAULTS PRIMARY KEY(FEATURE_ID)
);

-- DATASET_VERSION
CREATE TABLE ML_DATASET_VERSION(
DATASET_VERSION_ID BIGINT IDENTITY,
DATASET_SCHEMA_ID BIGINT,
NAME VARCHAR(200),
VERSION VARCHAR(50),
TENANT_ID INT,
USERNAME VARCHAR(50),
URI VARCHAR(300),
SAMPLE_POINTS VARBINARY(max),
CONSTRAINT PK_DATASET_VERSION PRIMARY KEY(DATASET_VERSION_ID)
);

-- FEATURE_SUMMARY
CREATE TABLE ML_FEATURE_SUMMARY(
  FEATURE_ID BIGINT,
  FEATURE_NAME VARCHAR(100),
  DATASET_VERSION_ID BIGINT,
  SUMMARY VARCHAR(max)
);

-- DATA_SOURCE
CREATE TABLE ML_DATA_SOURCE(
DATASET_VERSION_ID BIGINT,
TENANT_ID INT,
USERNAME VARCHAR(50),
[KEY] VARCHAR(50),
VALUE VARCHAR(50)
);

-- PROJECT
CREATE TABLE ML_PROJECT(
PROJECT_ID BIGINT IDENTITY,
NAME VARCHAR(100),
DESCRIPTION VARCHAR(max),
DATASET_SCHEMA_ID BIGINT,
TENANT_ID INT,
USERNAME VARCHAR(50),
CREATED_TIME DATETIME2(0),
CONSTRAINT PK_PROJECT PRIMARY KEY(PROJECT_ID)
);

-- ANALYSIS
CREATE TABLE ML_ANALYSIS(
ANALYSIS_ID BIGINT IDENTITY,
PROJECT_ID BIGINT,
NAME VARCHAR(100),
TENANT_ID INT,
USERNAME VARCHAR(50),
COMMENTS VARCHAR(max),
CONSTRAINT PK_ANALYSIS PRIMARY KEY(ANALYSIS_ID)
);

-- MODEL
CREATE TABLE ML_MODEL(
MODEL_ID BIGINT IDENTITY,
NAME VARCHAR(200),
ANALYSIS_ID BIGINT,
DATASET_VERSION_ID BIGINT,
TENANT_ID INT,
USERNAME VARCHAR(50),
CREATED_TIME DATETIME2(0),
SUMMARY VARBINARY(max),
STORAGE_TYPE VARCHAR(50),
STORAGE_LOCATION VARCHAR(500),
STATUS VARCHAR(20),
ERROR VARCHAR(max),
CONSTRAINT PK_MODEL PRIMARY KEY(MODEL_ID)
);

-- MODEL_CONFIGURATION
CREATE TABLE ML_MODEL_CONFIGURATION(
ANALYSIS_ID BIGINT,
[KEY] VARCHAR(50),
VALUE VARCHAR(50),
CONSTRAINT PK_MODEL_CONFIGURATION PRIMARY KEY(ANALYSIS_ID,[KEY])
);

-- FEATURE_CUSTOMIZED
CREATE TABLE ML_FEATURE_CUSTOMIZED(
ANALYSIS_ID BIGINT,
TENANT_ID INT,
FEATURE_NAME VARCHAR(100),
FEATURE_INDEX INT,
FEATURE_TYPE VARCHAR(50),
IMPUTE_OPTION VARCHAR(50),
INCLUSION BIT,
LAST_MODIFIED_USER VARCHAR(50),
USERNAME VARCHAR(50),
LAST_MODIFIED_TIME DATETIME2(0),
FEATURE_ID BIGINT,
CONSTRAINT PK_FEATURE_CUSTOMIZED PRIMARY KEY(ANALYSIS_ID,FEATURE_NAME)
);

-- HYPER_PARAMETER
CREATE TABLE ML_HYPER_PARAMETER(
ANALYSIS_ID BIGINT,
ALGORITHM_NAME VARCHAR(50),
NAME VARCHAR(50),
TENANT_ID INT,
VALUE VARCHAR(50),
LAST_MODIFIED_USER VARCHAR(50),
USERNAME VARCHAR(50),
LAST_MODIFIED_TIME DATETIME2(0),
CONSTRAINT PK_HYPER_PARAMETER PRIMARY KEY(ANALYSIS_ID,NAME)
);