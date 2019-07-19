/**
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

package org.apache.iotdb.db.engine.merge.recover;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.iotdb.db.engine.merge.manage.MergeResource;
import org.apache.iotdb.db.engine.merge.task.MergeTask;
import org.apache.iotdb.db.engine.storagegroup.TsFileResource;
import org.apache.iotdb.db.utils.MergeUtils;
import org.apache.iotdb.tsfile.read.common.Path;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * LogAnalyzer scans the "merge.log" file and recovers information such as files of last merge,
 * the last available positions of each file and how many timeseries and files have been merged.
 */
public class LogAnalyzer {

  private static final Logger logger = LoggerFactory.getLogger(LogAnalyzer.class);

  private MergeResource resource;
  private String taskName;
  private File logFile;

  private Map<File, Long> fileLastPositions = new HashMap<>();
  private Map<File, Long> tempFileLastPositions = new HashMap<>();

  private List<Path> mergedPaths = new ArrayList<>();
  private List<Path> unmergedPaths;
  private List<TsFileResource> unmergedFiles;
  private String currLine;

  public LogAnalyzer(MergeResource resource, String taskName, File logFile) {
    this.resource = resource;
    this.taskName = taskName;
    this.logFile = logFile;
  }

  /**
   * Scan through the logs to find out where the last merge has stopped and store the information
   * about the progress in the fields.
   * @return a Status indicating the completed stage of the last merge.
   * @throws IOException
   */
  public Status analyze() throws IOException {
    Status status = Status.NONE;
    try (BufferedReader bufferedReader =
        new BufferedReader(new FileReader(logFile))) {
      currLine = bufferedReader.readLine();
      if (currLine != null) {
        if (MergeLogger.STR_SEQ_FILES.equals(currLine)) {
          analyzeSeqFiles(bufferedReader);
        }
        if (MergeLogger.STR_UNSEQ_FILES.equals(currLine)) {
          analyzeUnseqFiles(bufferedReader);
        }
        if (MergeLogger.STR_MERGE_START.equals(currLine)) {
          status = Status.FILES_LOGGED;
          for (TsFileResource seqFile : resource.getSeqFiles()) {
            File mergeFile = new File(seqFile.getFile().getPath() + MergeTask.MERGE_SUFFIX);
            fileLastPositions.put(mergeFile, 0L);
          }
          unmergedPaths = MergeUtils.collectPaths(resource);
          analyzeMergedSeries(bufferedReader, unmergedPaths);
        }
        if (MergeLogger.STR_ALL_TS_END.equals(currLine)) {
          status = Status.ALL_TS_MERGED;
          unmergedFiles = resource.getSeqFiles();
          analyzeMergedFiles(bufferedReader);
        }
        if (MergeLogger.STR_MERGE_END.equals(currLine)) {
          status = Status.MERGE_END;
        }
      }
    }
    return status;
  }

  private void analyzeSeqFiles(BufferedReader bufferedReader) throws IOException {
    long startTime = System.currentTimeMillis();
    List<TsFileResource> mergeSeqFiles = new ArrayList<>();
    while ((currLine = bufferedReader.readLine()) != null) {
      if (currLine.equals(MergeLogger.STR_UNSEQ_FILES)) {
        break;
      }
      Iterator<TsFileResource> iterator = resource.getSeqFiles().iterator();
      while (iterator.hasNext()) {
        TsFileResource seqFile = iterator.next();
        if (seqFile.getFile().getAbsolutePath().equals(currLine)) {
          mergeSeqFiles.add(seqFile);
          iterator.remove();
          break;
        }
      }
    }
    if (logger.isDebugEnabled()) {
      logger.debug("{} found {} seq files after {}ms", taskName, mergeSeqFiles.size(),
          (System.currentTimeMillis() - startTime));
    }
    resource.setSeqFiles(mergeSeqFiles);
  }

  private void analyzeUnseqFiles(BufferedReader bufferedReader) throws IOException {
    long startTime = System.currentTimeMillis();
    List<TsFileResource> mergeUnseqFiles = new ArrayList<>();
    while ((currLine = bufferedReader.readLine()) != null) {
      if (currLine.equals(MergeLogger.STR_MERGE_START)) {
        break;
      }
      Iterator<TsFileResource> iterator = resource.getUnseqFiles().iterator();
      while (iterator.hasNext()) {
        TsFileResource unseqFile = iterator.next();
        if (unseqFile.getFile().getAbsolutePath().equals(currLine)) {
          mergeUnseqFiles.add(unseqFile);
          iterator.remove();
          break;
        }
      }
    }
    if (logger.isDebugEnabled()) {
      logger.debug("{} found {} unseq files after {}ms", taskName, mergeUnseqFiles.size(),
          (System.currentTimeMillis() - startTime));
    }
    resource.setUnseqFiles(mergeUnseqFiles);
  }

  private void analyzeMergedSeries(BufferedReader bufferedReader, List<Path> unmergedPaths) throws IOException {
    Path currTS = null;
    long startTime = System.currentTimeMillis();
    while ((currLine = bufferedReader.readLine()) != null) {
      if (currLine.equals(MergeLogger.STR_ALL_TS_END)) {
        break;
      }
      if (currLine.contains(MergeLogger.STR_START)) {
        // a TS starts to merge
        String[] splits = currLine.split(" ");
        currTS = new Path(splits[0]);
        tempFileLastPositions.clear();
      } else if (!currLine.contains(MergeLogger.STR_END)) {
        // file position
        String[] splits = currLine.split(" ");
        File file = new File(splits[0]);
        Long position = Long.parseLong(splits[1]);
        tempFileLastPositions.put(file, position);
      } else {
        // a TS ends merging
        unmergedPaths.remove(currTS);
        for (Entry<File, Long> entry : tempFileLastPositions.entrySet()) {
          fileLastPositions.put(entry.getKey(), entry.getValue());
        }
        mergedPaths.add(currTS);
      }
    }
    if (logger.isDebugEnabled()) {
      logger.debug("{} found {} series have already been merged after {}ms", taskName,
          mergedPaths.size(), (System.currentTimeMillis() - startTime));
    }
  }

  private void analyzeMergedFiles(BufferedReader bufferedReader) throws IOException {
    File currFile = null;
    long startTime = System.currentTimeMillis();
    int mergedCnt = 0;
    while ((currLine = bufferedReader.readLine()) != null) {
      if (currLine.equals(MergeLogger.STR_MERGE_END)) {
        break;
      }
      if (currLine.contains(MergeLogger.STR_START)) {
        String[] splits = currLine.split(" ");
        currFile = new File(splits[0]);
        Long lastPost = Long.parseLong(splits[2]);
        fileLastPositions.put(currFile, lastPost);
      } else if (currLine.contains(MergeLogger.STR_END)) {
        fileLastPositions.remove(currFile);
        String seqFilePath = currFile.getAbsolutePath().replace(MergeTask.MERGE_SUFFIX, "");
        Iterator<TsFileResource> unmergedFileIter = unmergedFiles.iterator();
        while (unmergedFileIter.hasNext()) {
          TsFileResource seqFile = unmergedFileIter.next();
          if (seqFile.getFile().getAbsolutePath().equals(seqFilePath)) {
            mergedCnt ++;
            unmergedFileIter.remove();
            break;
          }
        }
      }
    }
    if (logger.isDebugEnabled()) {
      logger.debug("{} found {} files have already been merged after {}ms", taskName,
          mergedCnt, (System.currentTimeMillis() - startTime));
    }
  }

  public enum Status {
    // almost nothing has been done
    NONE,
    // at least the files to be merged are known
    FILES_LOGGED,
    // all the timeseries have been merged (merged chunks are generated)
    ALL_TS_MERGED,
    // all the merge files are merged with the origin files and the job is almost done
    MERGE_END
  }

  public List<Path> getUnmergedPaths() {
    return unmergedPaths;
  }

  public List<TsFileResource> getUnmergedFiles() {
    return unmergedFiles;
  }

  public List<Path> getMergedPaths() {
    return mergedPaths;
  }

  public Map<File, Long> getFileLastPositions() {
    return fileLastPositions;
  }
}
