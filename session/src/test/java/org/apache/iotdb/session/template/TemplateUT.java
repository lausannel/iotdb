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
package org.apache.iotdb.session.template;

import org.apache.iotdb.db.conf.IoTDBConstant;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.db.qp.physical.crud.CreateTemplatePlan;
import org.apache.iotdb.db.utils.EnvironmentUtils;
import org.apache.iotdb.rpc.StatementExecutionException;
import org.apache.iotdb.tsfile.file.metadata.enums.CompressionType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSDataType;
import org.apache.iotdb.tsfile.file.metadata.enums.TSEncoding;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.ByteBuffer;

import static org.junit.Assert.assertEquals;

public class TemplateUT {

  @Before
  public void setUp() throws Exception {
    System.setProperty(IoTDBConstant.IOTDB_CONF, "src/test/resources/");
    EnvironmentUtils.closeStatMonitor();
    EnvironmentUtils.envSetUp();
  }

  @After
  public void tearDown() throws Exception {
    EnvironmentUtils.cleanEnv();
  }

  @Test
  public void testTemplateTree() {
    Template sessionTemplate = new Template("treeTemplate", true);
    TemplateNode iNodeGPS = new InternalNode("GPS", false);
    TemplateNode iNodeV = new InternalNode("vehicle", true);
    TemplateNode mNodeX =
        new MeasurementNode("x", TSDataType.FLOAT, TSEncoding.RLE, CompressionType.SNAPPY);
    TemplateNode mNodeY =
        new MeasurementNode("y", TSDataType.FLOAT, TSEncoding.RLE, CompressionType.SNAPPY);
    ByteArrayOutputStream stream = new ByteArrayOutputStream();

    try {
      iNodeGPS.addChild(mNodeX);
      iNodeGPS.addChild(mNodeY);
      iNodeV.addChild(mNodeX);
      iNodeV.addChild(mNodeY);
      iNodeV.addChild(iNodeGPS);
      sessionTemplate.addToTemplate(iNodeGPS);
      sessionTemplate.addToTemplate(iNodeV);
      sessionTemplate.addToTemplate(mNodeX);
      sessionTemplate.addToTemplate(mNodeY);

      sessionTemplate.serialize(stream);
      ByteBuffer buffer = ByteBuffer.wrap(stream.toByteArray());

      CreateTemplatePlan plan = CreateTemplatePlan.deserializeFromReq(buffer);
      assertEquals("treeTemplate", plan.getName());
      assertEquals(
          "[[vehicle.GPS.y], [vehicle.GPS.x], [GPS.y], [GPS.x], [y, x], [vehicle.y, vehicle.x]]",
          plan.getMeasurements().toString());

    } catch (StatementExecutionException e) {
      e.printStackTrace();
    } catch (IOException e) {
      e.printStackTrace();
    } catch (MetadataException e) {
      e.printStackTrace();
    }
  }
}
