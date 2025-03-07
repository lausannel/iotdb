/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.qp.logical.sys;

import org.apache.iotdb.db.exception.query.QueryProcessException;
import org.apache.iotdb.db.metadata.PartialPath;
import org.apache.iotdb.db.qp.logical.Operator;
import org.apache.iotdb.db.qp.physical.PhysicalPlan;
import org.apache.iotdb.db.qp.physical.sys.SettlePlan;
import org.apache.iotdb.db.qp.strategy.PhysicalGenerator;

public class SettleOperator extends Operator {
  PartialPath sgPath;
  String tsFilePath;
  boolean isSgPath = false;

  public SettleOperator(int tokenIntType) {
    super(tokenIntType);
    operatorType = OperatorType.SETTLE;
  }

  public PartialPath getSgPath() {
    return sgPath;
  }

  public void setSgPath(PartialPath sgPath) {
    this.sgPath = sgPath;
  }

  public String getTsFilePath() {
    return tsFilePath;
  }

  public void setTsFilePath(String tsFilePath) {
    this.tsFilePath = tsFilePath;
  }

  public boolean getIsSgPath() {
    return isSgPath;
  }

  public void setIsSgPath(boolean isSgPath) {
    this.isSgPath = isSgPath;
  }

  @Override
  public PhysicalPlan generatePhysicalPlan(PhysicalGenerator generator)
      throws QueryProcessException {
    if (isSgPath) {
      return new SettlePlan(getSgPath());
    } else {
      return new SettlePlan(getTsFilePath());
    }
  }
}
