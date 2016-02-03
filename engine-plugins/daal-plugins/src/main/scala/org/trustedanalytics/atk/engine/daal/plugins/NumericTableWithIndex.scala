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
package org.trustedanalytics.atk.engine.daal.plugins

import java.nio.DoubleBuffer

import com.intel.daal.data_management.data.NumericTable
import com.intel.daal.services.DaalContext
import org.apache.spark.sql.Row
import org.apache.spark.sql.catalyst.expressions.GenericRow

import scala.collection.mutable.ListBuffer

class NumericTableWithIndex(index: Long, table: NumericTable) extends Serializable {
  val numRows = table.getNumberOfRows
  val numCols = table.getNumberOfColumns
  table.pack()

  def getTable(context: DaalContext): NumericTable = {
    table.unpack(context)
    table
  }

  def getTuple2: (Long, NumericTable) = (index, table)

  /**
   * Convert DAAL numeric table into iterator of Spark SQL rows
   *
   * @param context Daal context
   * @return Iterator of rows
   */
  def toRowIter(context: DaalContext): Iterator[Row] = {
    val unpackedTable = getTable(context)
    val numRows = unpackedTable.getNumberOfRows.toInt
    val numCols = unpackedTable.getNumberOfColumns.toInt

    val buffer = DoubleBuffer.allocate(numRows * numCols)
    val doubleBuffer = unpackedTable.getBlockOfRows(0, numRows, buffer)
    val rowBuffer = new ListBuffer[Row]()

    for (i <- 0 until numRows) {
      val rowArray = new Array[Any](numCols)
      for (j <- 0 until numCols) {
        rowArray(j) = doubleBuffer.get(i * numCols + j)
      }
      rowBuffer += new GenericRow(rowArray)
    }

    rowBuffer.iterator
  }
}
