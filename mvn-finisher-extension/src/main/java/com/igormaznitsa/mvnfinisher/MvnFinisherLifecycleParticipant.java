/*
 * Copyright 2019 Igor Maznitsa.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.igormaznitsa.mvnfinisher;

import static java.lang.String.format;


import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.Maven;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.BuildFailure;
import org.apache.maven.execution.BuildSuccess;
import org.apache.maven.execution.BuildSummary;
import org.apache.maven.execution.DefaultMavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionRequest;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "mvnfinisher")
public class MvnFinisherLifecycleParticipant extends AbstractMavenLifecycleParticipant {

  public static final String SKIP_PROPERTY = "mvn.finisher.skip";
  public static final String FINISHING_PHASE = "finish";
  public static final String FINISHING_PHASE_OK = "finish-ok";
  public static final String FINISHING_PHASE_ERROR = "finish-error";
  public static final String FINISHING_FLAG_FILE = ".finishingStarted";
  private static final Set<String> ALL_FINISHING_PHASES = new HashSet<>(Arrays.asList(FINISHING_PHASE, FINISHING_PHASE_ERROR, FINISHING_PHASE_OK));
  private static final Set<String> ERROR_FINISHING_PHASES = new HashSet<>(Arrays.asList(FINISHING_PHASE, FINISHING_PHASE_ERROR));
  private static final Set<String> OK_FINISHING_PHASES = new HashSet<>(Arrays.asList(FINISHING_PHASE, FINISHING_PHASE_OK));
  @Requirement
  private Maven maven;

  @Requirement
  private Logger logger;

  private AtomicBoolean called = new AtomicBoolean();

  private boolean tryLockFinishingOfSession(final MavenSession session) throws MavenExecutionException {
    try {
      final File baseDirectory = new File(session.getRequest().getBaseDirectory());
      final File lockFile = new File(baseDirectory, FINISHING_FLAG_FILE);
      if (lockFile.createNewFile()) {
        this.logger.debug("Successfully created locking file flag: " + lockFile);
        lockFile.deleteOnExit();
        return true;
      } else {
        this.logger.debug("Can't create locking file flag: " + lockFile);
        return false;
      }
    } catch (IOException ex) {
      throw new MavenExecutionException("Error during attempt to lock session folder for finishing", ex);
    }
  }

  private boolean isTrueSkipFlagFound(final MavenSession session, final MavenProject project) {
    String value = session.getUserProperties().getProperty(SKIP_PROPERTY, null);
    if (value == null && project != null) {
      value = project.getProperties().getProperty(SKIP_PROPERTY, null);
    }
    return Boolean.parseBoolean(value);
  }

  @Override
  public void afterSessionEnd(final MavenSession session) throws MavenExecutionException {
    if (tryLockFinishingOfSession(session)) {
      if (isTrueSkipFlagFound(session, null)) {
        this.logger.info("Skip finishing");
        return;
      }

      final MavenExecutionResult sessionResult = session.getResult();

      final List<SingleFinishingTask> allFoundTasks = new ArrayList<>();
      for (final MavenProject project : session.getProjects()) {
        final BuildSummary projectBuildSummary = sessionResult.getBuildSummary(project);
        if (projectBuildSummary == null) {
          this.logger.warn(String.format("Project '%s' is ignored because was not built", project.getId()));
          continue;
        }

        if (isTrueSkipFlagFound(session, project)) {
          this.logger.debug("Detected skip finishing flag for project: " + project.getId());
          continue;
        }
        for (final Plugin buildPlugin : project.getBuildPlugins()) {
          for (final PluginExecution execution : buildPlugin.getExecutions()) {
            if (ALL_FINISHING_PHASES.contains(execution.getPhase())) {
              final SingleFinishingTask task = new SingleFinishingTask(execution.getPhase(), project, buildPlugin, execution);
              this.logger.debug("Found finishing task: " + task);
              allFoundTasks.add(new SingleFinishingTask(execution.getPhase(), project, buildPlugin, execution));
            }
          }
        }
      }

      this.logger.info(String.format("Totally detected %d finishing task(s)", allFoundTasks.size()));

      Collections.reverse(allFoundTasks);
      Collections.sort(allFoundTasks, (x, y) -> {
        if (x.phase.equals(y.phase)) {
          return 0;
        }
        return FINISHING_PHASE.equals(x.phase) ? 1 : -1;
      });

      final String LINE = "------------------------------------------------------------------------";

      if (!allFoundTasks.isEmpty()) {
        this.logger.info(LINE);
        this.logger.info("START FINISHING");
        this.logger.info(LINE);

        int calledTaskCount = 0;
        int errorTaskCount = 0;

        boolean hasError = false;
        for (final SingleFinishingTask task : allFoundTasks) {
          final BuildSummary buildSummary = sessionResult.getBuildSummary(task.project);

          final boolean executionAllowed;

          if (buildSummary instanceof BuildSuccess) {
            executionAllowed = OK_FINISHING_PHASES.contains(task.phase);
          } else if (buildSummary instanceof BuildFailure) {
            executionAllowed = ERROR_FINISHING_PHASES.contains(task.phase);
          } else {
            this.logger.warn("Detected unexpected BuildSummary object type for project: " + buildSummary.getClass().getSimpleName());
            executionAllowed = false;
          }

          if (executionAllowed) {
            this.logger.debug("Detected finishing task: " + task);
            final MavenExecutionResult taskResult = task.execute(this.maven, session);
            calledTaskCount++;
            if (taskResult.hasExceptions()) {
              errorTaskCount++;
              this.logger.error("Error during finishing task: " + task);
              hasError = true;
            } else {
              this.logger.debug("Finishing task completed: " + task);
            }
          } else {
            this.logger.debug("Ignored finishing task: " + task);
          }
        }

        if (hasError) {
          this.logger.error(LINE);
          this.logger.error(format("FINISHING COMPLETED WITH ERRORS, executed %d task(s), %d error(s)", calledTaskCount, errorTaskCount));
          this.logger.error(LINE);
        } else {
          this.logger.info(LINE);
          this.logger.info(format("FINISHING COMPLETED SUCCESSFULLY, executed %d task(s) ", calledTaskCount));
          this.logger.info(LINE);
        }
      }
    } else {
      this.logger.debug("Looks like that finishing already started for session: " + session);
    }

  }

  private final class SingleFinishingTask {
    private final MavenProject project;
    private final Plugin plugin;
    private final PluginExecution execution;
    private final String phase;

    private SingleFinishingTask(final String phase, final MavenProject project, final Plugin plugin, final PluginExecution execution) {
      this.phase = phase;
      this.project = project;
      this.plugin = plugin;
      this.execution = execution;
    }

    @Override
    public String toString() {
      return format("Finishing task(%s, %s, %s, %s)", this.phase, this.project.getId(), this.plugin.getId(), this.execution.getId());
    }

    private MavenExecutionResult execute(final Maven maven, final MavenSession baseSession) {
      final MavenExecutionRequest newRequest = DefaultMavenExecutionRequest.copy(baseSession.getRequest());
      newRequest.setSelectedProjects(Collections.singletonList(this.project.getId()));

      final String projectId = project.getGroupId() + ':' + project.getArtifactId();
      newRequest.setSelectedProjects(Collections.singletonList(projectId));

      final List<String> goals = new ArrayList<>();
      for (final String g : this.execution.getGoals()) {
        goals.add(this.plugin.getKey() + ':' + g + '@' + this.execution.getId());
      }

      logger.info(format("Finishing:  %s %s", projectId, goals));

      newRequest.setGoals(goals);
      return maven.execute(newRequest);
    }
  }

}
