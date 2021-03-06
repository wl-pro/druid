/*
 * Druid - a distributed column store.
 * Copyright 2012 - 2015 Metamarkets Group Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.druid.indexing.common;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Function;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.Maps;
import com.google.common.collect.Multimap;
import com.google.common.collect.Multimaps;
import com.metamx.emitter.service.ServiceEmitter;
import com.metamx.metrics.MonitorScheduler;
import io.druid.client.FilteredServerView;
import io.druid.indexing.common.actions.SegmentInsertAction;
import io.druid.indexing.common.actions.TaskActionClient;
import io.druid.indexing.common.actions.TaskActionClientFactory;
import io.druid.indexing.common.config.TaskConfig;
import io.druid.indexing.common.task.Task;
import io.druid.query.QueryRunnerFactoryConglomerate;
import io.druid.segment.loading.DataSegmentArchiver;
import io.druid.segment.loading.DataSegmentKiller;
import io.druid.segment.loading.DataSegmentMover;
import io.druid.segment.loading.DataSegmentPusher;
import io.druid.segment.loading.SegmentLoader;
import io.druid.segment.loading.SegmentLoadingException;
import io.druid.server.coordination.DataSegmentAnnouncer;
import io.druid.timeline.DataSegment;
import org.joda.time.Interval;

import java.io.File;
import java.io.IOException;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ExecutorService;

/**
 * Stuff that may be needed by a Task in order to conduct its business.
 */
public class TaskToolbox
{
  private final TaskConfig config;
  private final Task task;
  private final TaskActionClientFactory taskActionClientFactory;
  private final ServiceEmitter emitter;
  private final DataSegmentPusher segmentPusher;
  private final DataSegmentKiller dataSegmentKiller;
  private final DataSegmentArchiver dataSegmentArchiver;
  private final DataSegmentMover dataSegmentMover;
  private final DataSegmentAnnouncer segmentAnnouncer;
  private final FilteredServerView newSegmentServerView;
  private final QueryRunnerFactoryConglomerate queryRunnerFactoryConglomerate;
  private final MonitorScheduler monitorScheduler;
  private final ExecutorService queryExecutorService;
  private final SegmentLoader segmentLoader;
  private final ObjectMapper objectMapper;
  private final File taskWorkDir;

  public TaskToolbox(
      TaskConfig config,
      Task task,
      TaskActionClientFactory taskActionClientFactory,
      ServiceEmitter emitter,
      DataSegmentPusher segmentPusher,
      DataSegmentKiller dataSegmentKiller,
      DataSegmentMover dataSegmentMover,
      DataSegmentArchiver dataSegmentArchiver,
      DataSegmentAnnouncer segmentAnnouncer,
      FilteredServerView newSegmentServerView,
      QueryRunnerFactoryConglomerate queryRunnerFactoryConglomerate,
      ExecutorService queryExecutorService,
      MonitorScheduler monitorScheduler,
      SegmentLoader segmentLoader,
      ObjectMapper objectMapper,
      final File taskWorkDir
  )
  {
    this.config = config;
    this.task = task;
    this.taskActionClientFactory = taskActionClientFactory;
    this.emitter = emitter;
    this.segmentPusher = segmentPusher;
    this.dataSegmentKiller = dataSegmentKiller;
    this.dataSegmentMover = dataSegmentMover;
    this.dataSegmentArchiver = dataSegmentArchiver;
    this.segmentAnnouncer = segmentAnnouncer;
    this.newSegmentServerView = newSegmentServerView;
    this.queryRunnerFactoryConglomerate = queryRunnerFactoryConglomerate;
    this.queryExecutorService = queryExecutorService;
    this.monitorScheduler = monitorScheduler;
    this.segmentLoader = segmentLoader;
    this.objectMapper = objectMapper;
    this.taskWorkDir = taskWorkDir;
  }

  public TaskConfig getConfig()
  {
    return config;
  }

  public TaskActionClient getTaskActionClient()
  {
    return taskActionClientFactory.create(task);
  }

  public ServiceEmitter getEmitter()
  {
    return emitter;
  }

  public DataSegmentPusher getSegmentPusher()
  {
    return segmentPusher;
  }

  public DataSegmentKiller getDataSegmentKiller()
  {
    return dataSegmentKiller;
  }

  public DataSegmentMover getDataSegmentMover()
  {
    return dataSegmentMover;
  }

  public DataSegmentArchiver getDataSegmentArchiver()
  {
    return dataSegmentArchiver;
  }

  public DataSegmentAnnouncer getSegmentAnnouncer()
  {
    return segmentAnnouncer;
  }

  public FilteredServerView getNewSegmentServerView()
  {
    return newSegmentServerView;
  }

  public QueryRunnerFactoryConglomerate getQueryRunnerFactoryConglomerate()
  {
    return queryRunnerFactoryConglomerate;
  }

  public ExecutorService getQueryExecutorService()
  {
    return queryExecutorService;
  }

  public MonitorScheduler getMonitorScheduler()
  {
    return monitorScheduler;
  }

  public ObjectMapper getObjectMapper()
  {
    return objectMapper;
  }

  public Map<DataSegment, File> fetchSegments(List<DataSegment> segments)
      throws SegmentLoadingException
  {
    Map<DataSegment, File> retVal = Maps.newLinkedHashMap();
    for (DataSegment segment : segments) {
      retVal.put(segment, segmentLoader.getSegmentFiles(segment));
    }

    return retVal;
  }

  public void pushSegments(Iterable<DataSegment> segments) throws IOException
  {
    // Request segment pushes for each set
    final Multimap<Interval, DataSegment> segmentMultimap = Multimaps.index(
        segments,
        new Function<DataSegment, Interval>()
        {
          @Override
          public Interval apply(DataSegment segment)
          {
            return segment.getInterval();
          }
        }
    );
    for (final Collection<DataSegment> segmentCollection : segmentMultimap.asMap().values()) {
      getTaskActionClient().submit(new SegmentInsertAction(ImmutableSet.copyOf(segmentCollection)));
    }

  }

  public File getTaskWorkDir()
  {
    return taskWorkDir;
  }
}
