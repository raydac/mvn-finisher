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
import static java.util.Objects.requireNonNull;


import java.io.File;
import java.io.IOError;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import org.apache.maven.AbstractMavenLifecycleParticipant;
import org.apache.maven.Maven;
import org.apache.maven.MavenExecutionException;
import org.apache.maven.execution.BuildFailure;
import org.apache.maven.execution.BuildSuccess;
import org.apache.maven.execution.BuildSummary;
import org.apache.maven.execution.DefaultMavenExecutionResult;
import org.apache.maven.execution.MavenExecutionResult;
import org.apache.maven.execution.MavenSession;
import org.apache.maven.model.Plugin;
import org.apache.maven.model.PluginExecution;
import org.apache.maven.project.MavenProject;
import org.apache.maven.shared.invoker.DefaultInvocationRequest;
import org.apache.maven.shared.invoker.DefaultInvoker;
import org.apache.maven.shared.invoker.InvocationRequest;
import org.apache.maven.shared.invoker.InvocationResult;
import org.apache.maven.shared.invoker.Invoker;
import org.apache.maven.shared.invoker.MavenCommandLineBuilder;
import org.apache.maven.shared.invoker.MavenInvocationException;
import org.apache.maven.shared.utils.Os;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.shared.utils.cli.CommandLineException;
import org.apache.maven.shared.utils.cli.CommandLineTimeOutException;
import org.apache.maven.shared.utils.cli.CommandLineUtils;
import org.apache.maven.shared.utils.cli.Commandline;
import org.apache.maven.shared.utils.cli.ShutdownHookUtils;
import org.apache.maven.shared.utils.cli.shell.BourneShell;
import org.apache.maven.shared.utils.cli.shell.CmdShell;
import org.apache.maven.shared.utils.cli.shell.CommandShell;
import org.apache.maven.shared.utils.cli.shell.Shell;
import org.codehaus.plexus.component.annotations.Component;
import org.codehaus.plexus.component.annotations.Requirement;
import org.codehaus.plexus.logging.Logger;

@Component(role = AbstractMavenLifecycleParticipant.class, hint = "mvnfinisher")
public class MvnFinisherLifecycleParticipant extends AbstractMavenLifecycleParticipant {

  private static final String SHUTDOWN_HOOK_THREAD_ID = "mvn-finisher-shutdown-hook-thread";
  private static final String FLAG_FINISHING_SESSION = "mvn.finisher.finishing.session";

  public static final String SKIP_PROPERTY = "mvn.finisher.skip";
  public static final String FINISHING_PHASE = "finish";
  public static final String FINISHING_PHASE_OK = "finish-ok";
  public static final String FINISHING_PHASE_ERROR = "finish-error";
  public static final String FINISHING_PHASE_FORCE = "finish-force";
  public static final String FINISHING_FLAG_FILE = ".finishingStarted";

  private static final Set<String> ALL_FINISHING_PHASES = new HashSet<>(Arrays.asList(FINISHING_PHASE, FINISHING_PHASE_ERROR, FINISHING_PHASE_OK));
  private static final Set<String> ERROR_FINISHING_PHASES = new HashSet<>(Arrays.asList(FINISHING_PHASE, FINISHING_PHASE_ERROR));
  private static final Set<String> OK_FINISHING_PHASES = new HashSet<>(Arrays.asList(FINISHING_PHASE, FINISHING_PHASE_OK));
  private static final Set<String> FORCE_FINISHING_PHASES = new HashSet<>(Arrays.asList(FINISHING_PHASE, FINISHING_PHASE_FORCE));

  private final Map<MavenSession, List<MavenProject>> sessionProjectMap = new ConcurrentHashMap<>();
  private final Map<MavenSession, Boolean> processingSessions = new ConcurrentHashMap<>();

  private static final int MAX_FINISH_TASK_ALLOWED_TIME_SECONDS = 120;

  @Requirement
  private Maven maven;

  @Requirement
  private Logger logger;

  private final List<MavenSession> nonProcessedMavenSessions = new CopyOnWriteArrayList<>();
  private static final AtomicBoolean shutdowning = new AtomicBoolean();

  public MvnFinisherLifecycleParticipant() {
    //--heat invocator because during force shutdown some class can be not found
    heatInvocator();
    //----
    if (!isShutdownActive()) {
      ShutdownHookUtils.addShutDownHook(new Thread(this::shutdown, SHUTDOWN_HOOK_THREAD_ID));
    }
  }

  private void silentClassLoad(final String name) {
    try {
      Class.forName(name);
    } catch (Exception ex) {
      //DO NOTHING
    }
  }

  private void heatInvocator() {
    try {
      final InvocationRequest request = new DefaultInvocationRequest();
      request.setPomFile(new File(String.format("unknown%s.pom", Long.toString(System.nanoTime(), 20))));
      request.setBaseDirectory(new File("unknwn_dir" + (System.nanoTime() & 0xFF)));
      request.setBatchMode(true);
      final Invoker invoker = new DefaultInvoker();
      final List<String> outList = new CopyOnWriteArrayList<>();
      invoker.setOutputHandler(outList::add);
      invoker.setErrorHandler(outList::add);
      InvocationResult result = invoker.execute(request);
      requireNonNull(result.getExecutionException());
    } catch (final Exception ex) {
      ex.printStackTrace();
    } finally {
      requireNonNull(StringUtils.capitalise("ddd"));
      requireNonNull(new Os("linux"));
      requireNonNull(new Shell());
      requireNonNull(new BourneShell());
      requireNonNull(new CmdShell());
      requireNonNull(new CommandShell());
      requireNonNull(new MavenCommandLineBuilder());
      requireNonNull(new Commandline.Argument());
      try {
        final List<String> outList = new CopyOnWriteArrayList<>();
        CommandLineUtils.executeCommandLine(new Commandline(new Shell()), outList::add, outList::add, 1);
      } catch (Exception ex) {
        //DO NOTHING
      }
      silentClassLoad("org.apache.maven.shared.utils.cli.CommandLineUtils$1");
      silentClassLoad("org.apache.maven.shared.utils.cli.CommandLineUtils$2");
      silentClassLoad("org.apache.maven.shared.utils.cli.StreamPumper");
      requireNonNull(CommandLineUtils.getSystemEnvVars());
      requireNonNull(InvocationRequest.CheckSumPolicy.Fail);
    }
  }

  private static String findProperty(
      final MavenSession session,
      final MavenProject project,
      final String key,
      final String defaultValue
  ) {
    final Properties properties = new Properties();
    if (project != null) {
      properties.putAll(project.getProperties());
    }
    if (session != null) {
      properties.putAll(session.getSystemProperties());
      properties.putAll(session.getUserProperties());
    }
    return properties.getProperty(key, defaultValue);
  }

  private static boolean isShutdownActive() {
    return SHUTDOWN_HOOK_THREAD_ID.equals(Thread.currentThread().getName()) || shutdowning.get();
  }

  private void deleteFinishingFlagIfExist(final MavenSession session) {
    if (!isShutdownActive()) {
      final File baseDirectory = new File(session.getRequest().getBaseDirectory());
      final File lockFile = new File(baseDirectory, FINISHING_FLAG_FILE);
      if (lockFile.isFile() && !lockFile.delete()) {
        throw new IOError(new IOException("Detected lock file can't be deleted: " + lockFile));
      }
    }
  }

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

  private void shutdown() {
    if (shutdowning.compareAndSet(false, true)) {
      final List<MavenSession> nonClosedSessions = new ArrayList<>(this.nonProcessedMavenSessions);
      this.logger.debug("Start mvn-finisher shutdown hook, detected " + nonClosedSessions.size() + " non-closed sessions");
      Collections.reverse(nonClosedSessions);
      for (final MavenSession s : nonClosedSessions) {
        if (s.getRequest() != null && s.getRequest().getStartTime() == null) {
          this.logger.info("Ignoring unfinished session " + s + " because it was not started");
          continue;
        }
        try {
          this.logger.debug("Force finish of unfinished session: " + s);
          this.finishSession(s, true);
        } catch (MavenExecutionException ex) {
          this.logger.error("Detected MavenExecutionException", ex);
        } finally {
          this.logger.debug("Session finished: " + s);
        }
      }
      this.logger.debug("mvn-finisher shutdown hook work completed");
    }
  }

  private boolean isSkip(final MavenSession session, final MavenProject project) {
    return Boolean.parseBoolean(findProperty(session, project, SKIP_PROPERTY, "false"));
  }

  @Override
  public void afterProjectsRead(final MavenSession session) throws MavenExecutionException {
    if (Boolean.parseBoolean(session.getUserProperties().getProperty(FLAG_FINISHING_SESSION, "false"))) {
      this.logger.debug("Detected flag " + FLAG_FINISHING_SESSION);
    } else {
      deleteFinishingFlagIfExist(session);
      this.logger.debug("registering session in afterProjectsRead: " + session);
      this.nonProcessedMavenSessions.add(session);
      this.sessionProjectMap.put(session, new ArrayList<>(session.getProjects()));
    }
  }

  private void finishSession(final MavenSession session, final boolean force) throws MavenExecutionException {
    if (this.processingSessions.putIfAbsent(session, false) == null) {
      try {
        final List<MavenProject> sessionProjects = sessionProjectMap.get(session);

        if (tryLockFinishingOfSession(session)) {
          if (isSkip(session, null)) {
            this.logger.info("Skip finishing");
            return;
          }

          final MavenExecutionResult sessionResult = session.getResult();

          final List<SingleFinishingTask> allFoundTasks = new ArrayList<>();
          sessionProjects.forEach(project -> {
            final BuildSummary projectBuildSummary = sessionResult.getBuildSummary(project);
            if (projectBuildSummary == null && !force) {
              this.logger.warn(format("Project '%s' is ignored because session was not created", project.getId()));
              return;
            }
            if (isSkip(session, project)) {
              this.logger.debug("Detected skip finishing flag for project: " + project.getId());
              return;
            }
            for (final Plugin buildPlugin : project.getBuild().getPlugins()) {
              for (final PluginExecution execution : buildPlugin.getExecutions()) {
                if (ALL_FINISHING_PHASES.contains(execution.getPhase())) {
                  final SingleFinishingTask task = new SingleFinishingTask(execution.getPhase(), project, buildPlugin, execution);
                  this.logger.info("Found finishing task: " + task.execution.getId() + " (" + project.getArtifactId() + ')');
                  allFoundTasks.add(task);
                }
              }
            }
          });

          this.logger.info(String.format("Totally detected %d potential finishing task(s)", allFoundTasks.size()));

          allFoundTasks.sort((x, y) -> {
            if (x.phase.equals(y.phase)) {
              return 0;
            }
            return FINISHING_PHASE.equals(x.phase) ? 1 : -1;
          });

          final String LINE = "------------------------------------------------------------------------";

          if (!allFoundTasks.isEmpty()) {
            this.logger.info(LINE);
            if (force) {
              this.logger.warn("START FORCE FINISHING");
            } else {
              this.logger.info("START FINISHING");
            }
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
                if (buildSummary != null) {
                  this.logger.warn("Detected unexpected BuildSummary object type for project: " + buildSummary.getClass().getSimpleName());
                }
                if (force) {
                  executionAllowed = FORCE_FINISHING_PHASES.contains(task.phase);
                } else {
                  executionAllowed = false;
                }
              }

              if (executionAllowed) {
                this.logger.debug("Detected finishing task: " + task);
                final MavenExecutionResult taskResult = task.execute(session);
                calledTaskCount++;
                if (taskResult.hasExceptions()) {
                  errorTaskCount++;
                  this.logger.error("Error during finishing task: " + task);
                  for (final Throwable e : taskResult.getExceptions()) {
                    this.logger.debug("DETECTED ERROR: " + e.getMessage(), e);
                  }
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
      } finally {
        this.processingSessions.put(session, true);
      }
    }
  }

  @Override
  public void afterSessionEnd(final MavenSession session) throws MavenExecutionException {
    this.logger.debug("afterSessionEnd: " + session);
    this.nonProcessedMavenSessions.remove(session);
    finishSession(session, false);
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
      return format("Finishing task(%s, %s, %s, %s, %s)", this.project.getFile(), this.phase, this.project.getId(), this.plugin.getId(), this.execution.getId());
    }

    private MavenExecutionResult execute(final MavenSession session) {
      final String projectId = project.getGroupId() + ':' + project.getArtifactId();
      logger.debug(String.format("executing %s for project %s pom file is %s",
          SingleFinishingTask.class.getSimpleName(),
          projectId,
          project.getFile()));

      final InvocationRequest request = new DefaultInvocationRequest();
      request.setProfiles(session.getSettings().getActiveProfiles());
      request.setAlsoMake(false);
      request.setAlsoMakeDependents(false);
      request.setBatchMode(true);
      request.setThreads("1");
      request.setOffline(session.isOffline());
      request.setShellEnvironmentInherited(true);
      request.setPomFile(project.getFile());
      request.setRecursive(true);
      request.setBaseDirectory(project.getBasedir());

      final Properties properties = new Properties();
      properties.putAll(session.getUserProperties());
      properties.put(FLAG_FINISHING_SESSION, "true");
      request.setProperties(properties);
      request.setTimeoutInSeconds(MAX_FINISH_TASK_ALLOWED_TIME_SECONDS);

      final List<String> goals = new ArrayList<>();
      for (final String g : this.execution.getGoals()) {
        goals.add(this.plugin.getKey() + ':' + g + '@' + this.execution.getId());
      }
      logger.debug("Prepared goals: " + goals);
      request.setGoals(goals);

      logger.debug(format("Finishing:  %s %s", projectId, goals));

      final long startTime = System.currentTimeMillis();

      final MavenExecutionResult result = new DefaultMavenExecutionResult();
      result.setProject(project);

      final Invoker invoker = new DefaultInvoker();
      final List<String> outputList = new CopyOnWriteArrayList<>();
      final List<String> errList = new CopyOnWriteArrayList<>();
      invoker.setOutputHandler(outputList::add);
      invoker.setErrorHandler(errList::add);
      final InvocationResult invokeResult;
      try {
        invokeResult = invoker.execute(request);
        final CommandLineException cliException = invokeResult.getExecutionException();
        if (cliException != null) {
          throw cliException;
        }
      } catch (Exception ex) {
        if (ex instanceof CommandLineTimeOutException) {
          logger.error(String.format("Finish task interrupted because longer than %d seconds!", MAX_FINISH_TASK_ALLOWED_TIME_SECONDS));
        } else {
          logger.error("Can't invoke maven", ex);
        }
        result.addBuildSummary(new BuildFailure(project, System.currentTimeMillis() - startTime, ex));
        return result;
      }
      final int exitCode = invokeResult.getExitCode();

      final long time = System.currentTimeMillis() - startTime;

      for (final String s : outputList) {
        logger.debug("OUT> " + s);
      }

      if (exitCode == 0) {
        for (final String s : errList) {
          logger.debug("ERR> " + s);
        }
        result.addBuildSummary(new BuildSuccess(project, time));
      } else {
        for (final String s : errList) {
          logger.error(s);
        }
        result.addBuildSummary(new BuildFailure(project, time, new MavenInvocationException("Exit code is " + exitCode)));
      }
      return result;
    }
  }

}
