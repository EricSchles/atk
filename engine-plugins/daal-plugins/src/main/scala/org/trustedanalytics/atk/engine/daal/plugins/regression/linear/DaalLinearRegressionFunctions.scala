/*
// Copyright (c) 2015 Intel Corporation 
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//      http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.
*/

package org.trustedanalytics.atk.engine.daal.plugins.regression.linear

import java.io.Serializable

import com.intel.daal.algorithms.ModelSerializer
import com.intel.daal.algorithms.linear_regression.Model
import com.intel.daal.algorithms.linear_regression.prediction._
import com.intel.daal.algorithms.linear_regression.training._
import com.intel.daal.data_management.data.NumericTable
import com.intel.daal.services.DaalContext
import org.apache.spark.frame.FrameRdd
import org.apache.spark.rdd.RDD
import org.apache.spark.sql
import org.trustedanalytics.atk.domain.schema.{ Column, DataTypes, FrameSchema }
import org.trustedanalytics.atk.engine.daal.plugins.conversions.DaalConversionImplicits._
import org.trustedanalytics.atk.engine.daal.plugins.conversions.DaalFrameRddFunctions
import org.trustedanalytics.atk.engine.daal.plugins.{ DistributedLabeledTable, NumericTableWithIndex }

object DaalLinearRegressionFunctions extends Serializable {

  /**
   * Train linear regression model using QR decomposition
   *
   * @param frameRdd Input frame
   * @param featureColumns Feature columns
   * @param dependentVariableColumns Dependent variable columns
   * @return DAAL trained linear regression model
   */
  def trainLinearModel(context: DaalContext,
                       frameRdd: FrameRdd,
                       featureColumns: List[String],
                       dependentVariableColumns: List[String]): Model = {

    val trainTables = new DistributedLabeledTable(frameRdd, featureColumns, dependentVariableColumns)
    val partialModels = computePartialLinearModels(trainTables)
    val trainedModel = mergeLinearModels(context, partialModels)
    trainedModel
  }

  /**
   * Compute partial results for linear regression  using QR decomposition
   *
   * @param trainTables RDD of features and dependent variables for training
   * @return RDD of partial results
   */
  def computePartialLinearModels(trainTables: DistributedLabeledTable): RDD[PartialResult] = {
    val linearModelsRdd = trainTables.rdd.map {
      case (featureTable, labelTable) =>
        val linearRegressionModel = computeLinearModelsLocal(featureTable, labelTable)
        linearRegressionModel
    }
    linearModelsRdd
  }

  /**
   * Compute partial linear model locally using QR decomposition
   *
   * This function is run once for each Spark partition
   *
   * @param featureTable Feature table
   * @param labelTable Dependent variable table
   * @return Partial result of training
   */
  def computeLinearModelsLocal(featureTable: NumericTableWithIndex, labelTable: NumericTableWithIndex): PartialResult = {
    val context = new DaalContext()
    val linearRegressionTraining = new TrainingDistributedStep1Local(context, classOf[java.lang.Double], TrainingMethod.qrDense)
    linearRegressionTraining.input.set(TrainingInputId.data, featureTable.getTable(context))
    linearRegressionTraining.input.set(TrainingInputId.dependentVariable, labelTable.getTable(context))
    val lrResult = linearRegressionTraining.compute()
    lrResult.pack()
    context.dispose()
    lrResult
  }

  /**
   * Merge partial results of linear regression models using QR decomposition at Spark master
   *
   * @param linearModels RDD of partial results of linear regression
   * @return Trained linear regression model
   */
  def mergeLinearModels(context: DaalContext, linearModels: RDD[PartialResult]): Model = {
    val linearRegressionTraining = new TrainingDistributedStep2Master(context, classOf[java.lang.Double], TrainingMethod.qrDense)

    /* Build and retrieve final linear model */
    val linearModelsArray = linearModels.collect()
    linearModelsArray.foreach { partialModel =>
      partialModel.unpack(context)
      linearRegressionTraining.input.add(MasterInputId.partialModels, partialModel)
    }

    linearRegressionTraining.compute()
    val trainingResult = linearRegressionTraining.finalizeCompute()
    val trainedModel = trainingResult.get(TrainingResultId.model)

    trainedModel
  }

  def predictLinearModel(modelData: DaalLinearRegressionModelData,
                         frameRdd: FrameRdd,
                         featureColumns: List[String]): FrameRdd = {

    val rowWrapper = frameRdd.rowWrapper

    val predictResultsRdd = frameRdd.mapPartitions(iter => {
      val context = new DaalContext()
      val trainedModel = ModelSerializer.deserializeQrModel(context, modelData.serializedModel.toArray)
      require(modelData.featureColumns.length == featureColumns.length,
        "Number of feature columns for train and predict should be same")

      val rows = DaalFrameRddFunctions.convertRowsToNumericTable(rowWrapper, featureColumns, iter) match {
        case Some(testData) =>
          val predictions = predictLinearModelLocal(context, trainedModel, testData)
          testData.dispose()
          predictions.toRowIter(context)
        case _ => List.empty[sql.Row].iterator
      }

      context.dispose()
      rows
    })

    val predictColumns = modelData.labelColumns.map(col => Column("predict_" + col, DataTypes.float64))
    frameRdd.zipFrameRdd(new FrameRdd(FrameSchema(predictColumns), predictResultsRdd))
  }

  def predictLinearModelLocal(context: DaalContext, trainedModel: Model, testData: NumericTable): NumericTable = {
    val predictAlgorithm = new PredictionBatch(context, classOf[java.lang.Double], PredictionMethod.defaultDense)

    // Getting number of rows/columns to prevent seg-faults --- not sure why this happens
    testData.unpack(context)
    require(testData.getNumberOfColumns > 0 && testData.getNumberOfRows > 0)
    predictAlgorithm.input.set(PredictionInputId.data, testData)
    predictAlgorithm.input.set(PredictionInputId.model, trainedModel)

    /* Compute and retrieve prediction results */
    val predictionResult = predictAlgorithm.compute()

    val predictions = predictionResult.get(PredictionResultId.prediction)
    predictions
  }
}
