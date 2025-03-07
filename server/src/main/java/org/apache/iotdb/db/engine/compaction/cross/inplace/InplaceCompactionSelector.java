/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.iotdb.db.engine.compaction.cross.inplace;

import org.apache.iotdb.db.conf.IoTDBConfig;
import org.apache.iotdb.db.conf.IoTDBDescriptor;
import org.apache.iotdb.db.engine.compaction.CompactionScheduler;
import org.apache.iotdb.db.engine.compaction.CompactionTaskManager;
import org.apache.iotdb.db.engine.compaction.cross.AbstractCrossSpaceCompactionSelector;
import org.apache.iotdb.db.engine.compaction.cross.CrossSpaceCompactionTaskFactory;
import org.apache.iotdb.db.engine.compaction.cross.inplace.manage.CrossSpaceMergeResource;
import org.apache.iotdb.db.engine.compaction.cross.inplace.selector.ICrossSpaceMergeFileSelector;
import org.apache.iotdb.db.engine.compaction.inner.utils.InnerSpaceCompactionUtils;
import org.apache.iotdb.db.engine.compaction.task.AbstractCompactionTask;
import org.apache.iotdb.db.engine.storagegroup.TsFileResource;
import org.apache.iotdb.db.engine.storagegroup.TsFileResourceList;
import org.apache.iotdb.db.exception.MergeException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;

public class InplaceCompactionSelector extends AbstractCrossSpaceCompactionSelector {
  private static final Logger LOGGER = LoggerFactory.getLogger("COMPACTION");
  private static IoTDBConfig config = IoTDBDescriptor.getInstance().getConfig();

  public InplaceCompactionSelector(
      String logicalStorageGroupName,
      String virtualStorageGroupId,
      String storageGroupDir,
      long timePartition,
      TsFileResourceList sequenceFileList,
      TsFileResourceList unsequenceFileList,
      CrossSpaceCompactionTaskFactory taskFactory) {
    super(
        logicalStorageGroupName,
        virtualStorageGroupId,
        storageGroupDir,
        timePartition,
        sequenceFileList,
        unsequenceFileList,
        taskFactory);
  }

  @Override
  public boolean selectAndSubmit() {
    boolean taskSubmitted = false;
    if ((CompactionTaskManager.currentTaskNum.get() >= config.getConcurrentCompactionThread())
        || (!config.isEnableCrossSpaceCompaction())
        || CompactionScheduler.isPartitionCompacting(
            logicalStorageGroupName + "-" + virtualGroupId, timePartition)) {
      if (CompactionTaskManager.currentTaskNum.get() >= config.getConcurrentCompactionThread()) {
        LOGGER.debug("End selection because too many threads");
      } else if (!config.isEnableCrossSpaceCompaction()) {
        LOGGER.debug("End selection because cross compaction is not enable");
      } else {
        LOGGER.debug(
            "End selection because {}-{} is compacting, task num in CompactionTaskManager is {}",
            logicalStorageGroupName,
            virtualGroupId,
            CompactionTaskManager.currentTaskNum.get());
      }
      return false;
    }
    Iterator<TsFileResource> seqIterator = sequenceFileList.iterator();
    Iterator<TsFileResource> unSeqIterator = unsequenceFileList.iterator();
    List<TsFileResource> seqFileList = new ArrayList<>();
    List<TsFileResource> unSeqFileList = new ArrayList<>();
    while (seqIterator.hasNext()) {
      seqFileList.add(seqIterator.next());
    }
    while (unSeqIterator.hasNext()) {
      unSeqFileList.add(unSeqIterator.next());
    }
    if (seqFileList.isEmpty() || unSeqFileList.isEmpty()) {
      return false;
    }
    if (unSeqFileList.size() > config.getMaxCompactionCandidateFileNum()) {
      unSeqFileList = unSeqFileList.subList(0, config.getMaxCompactionCandidateFileNum());
    }
    long budget = config.getMergeMemoryBudget();
    long timeLowerBound = System.currentTimeMillis() - Long.MAX_VALUE;
    CrossSpaceMergeResource mergeResource =
        new CrossSpaceMergeResource(seqFileList, unSeqFileList, timeLowerBound);

    ICrossSpaceMergeFileSelector fileSelector =
        InnerSpaceCompactionUtils.getCrossSpaceFileSelector(budget, mergeResource);
    try {
      List[] mergeFiles = fileSelector.select();
      if (mergeFiles.length == 0) {
        LOGGER.warn(
            "{} cannot select merge candidates under the budget {}",
            logicalStorageGroupName,
            budget);
        return false;
      }
      LOGGER.info(
          "select files for cross compaction, sequence files: {}, unsequence files {}",
          mergeFiles[0],
          mergeFiles[1]);
      // avoid pending tasks holds the metadata and streams
      mergeResource.clear();
      // do not cache metadata until true candidates are chosen, or too much metadata will be
      // cached during selection
      mergeResource.setCacheDeviceMeta(true);

      AbstractCompactionTask compactionTask =
          taskFactory.createTask(
              logicalStorageGroupName,
              virtualGroupId,
              timePartition,
              mergeResource,
              storageGroupDir,
              sequenceFileList,
              unsequenceFileList,
              mergeFiles[0],
              mergeFiles[1],
              fileSelector.getConcurrentMergeNum());
      CompactionTaskManager.getInstance().addTaskToWaitingQueue(compactionTask);
      taskSubmitted = true;
      LOGGER.info(
          "{} [Compaction] submit a task with {} sequence file and {} unseq files",
          logicalStorageGroupName + "-" + virtualGroupId,
          mergeResource.getSeqFiles().size(),
          mergeResource.getUnseqFiles().size());
    } catch (MergeException | IOException e) {
      LOGGER.error("{} cannot select file for cross space compaction", logicalStorageGroupName, e);
    }

    return taskSubmitted;
  }
}
