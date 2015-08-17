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
package org.trustedanalytics.atk.engine.model.plugins.dimensionalityreduction

import org.trustedanalytics.atk.domain.frame.{ FrameEntity, FrameReference }
import org.trustedanalytics.atk.domain.model.ModelReference
import org.trustedanalytics.atk.engine.plugin.ArgDoc

/**
 * Input arguments for principal components predict plugin
 */

case class PrincipalComponentsPredictArgs(@ArgDoc("""Handle to the model to be used.""") model: ModelReference,
                                          @ArgDoc("""Frame whose principal components are to be computed.""") frame: FrameReference,
                                          @ArgDoc("""List of observation column name(s) to be used for prediction.Default is the list of column name(s) used to train the model.""") observationColumns: Option[List[String]] = None,
                                          @ArgDoc("""The number of principal components to be predicted.Default is the count used to train the model.""") c: Option[Int] = None,
                                          @ArgDoc("""Indicator for whether the t-square index is to be computed.Default is false.""") tSquareIndex: Option[Boolean] = None,
                                          @ArgDoc("""The name of the output frame generated by predict.""") name: Option[String] = None) {
  require(model != null, "model is required")
  require(frame != null, "frame is required")
}

/**
 * Return of principal components predict plugin
 * @param outputFrame A new frame with existing columns and columns containing the projections on it
 * @param tSquaredIndex t-square index value if requested
 */
case class PrincipalComponentsPredictReturn(outputFrame: FrameEntity, tSquaredIndex: Option[Double])
