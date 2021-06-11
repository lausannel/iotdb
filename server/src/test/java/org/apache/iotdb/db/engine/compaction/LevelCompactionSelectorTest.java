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

package org.apache.iotdb.db.engine.compaction;

import org.apache.iotdb.db.constant.TestConstant;
import org.apache.iotdb.db.engine.compaction.innerSpaceCompaction.level.LevelCompactionExecutor;
import org.apache.iotdb.db.engine.storagegroup.TsFileResource;
import org.apache.iotdb.db.exception.StorageEngineException;
import org.apache.iotdb.db.exception.metadata.MetadataException;
import org.apache.iotdb.tsfile.exception.write.WriteProcessException;

import org.apache.commons.io.FileUtils;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.List;

import static org.junit.Assert.assertEquals;

public class LevelCompactionSelectorTest extends LevelCompactionTest {

  File tempSGDir;

  @Override
  @Before
  public void setUp() throws IOException, WriteProcessException, MetadataException {
    super.setUp();
    tempSGDir = new File(TestConstant.BASE_OUTPUT_PATH.concat("tempSG"));
    tempSGDir.mkdirs();
  }

  @Override
  @After
  public void tearDown() throws IOException, StorageEngineException {
    super.tearDown();
    FileUtils.deleteDirectory(tempSGDir);
  }

  /** just compaction once */
  @Test
  public void testCompactionSelector() throws NoSuchFieldException, IllegalAccessException {
    LevelCompactionExecutor levelCompactionExecutor =
        new LevelCompactionExecutor(COMPACTION_TEST_SG, tempSGDir.getPath());
    levelCompactionExecutor.addAll(seqResources, true);
    levelCompactionExecutor.addAll(unseqResources, false);
    levelCompactionExecutor.forkCurrentFileList(0);
    Field fieldForkedSequenceTsFileResources =
        LevelCompactionExecutor.class.getDeclaredField("forkedSequenceTsFileResources");
    fieldForkedSequenceTsFileResources.setAccessible(true);
    List<TsFileResource> forkedSequenceTsFileResources =
        (List<TsFileResource>) fieldForkedSequenceTsFileResources.get(levelCompactionExecutor);
    assertEquals(2, forkedSequenceTsFileResources.size());
  }
}
