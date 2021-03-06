package rprocessing.mode.run;

import static com.google.common.base.Predicates.containsPattern;
import static com.google.common.base.Predicates.not;
import static com.google.common.base.Predicates.or;
import static com.google.common.collect.Collections2.filter;

import java.awt.Point;
import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.net.ConnectException;
import java.net.SocketTimeoutException;
import java.rmi.RemoteException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import com.google.common.base.Joiner;

import processing.app.Messages;
import processing.app.Platform;
import processing.app.Preferences;
import processing.app.SketchException;
import rprocessing.mode.RLangEditor;
import rprocessing.mode.RLangMode;

/**
 * 
 * @author github.com/gaocegege
 */
public class SketchServiceRunner {

  private static final boolean VERBOSE = Boolean.parseBoolean(System.getenv("VERBOSE_RLANG_MODE"));

  private final RLangMode mode;
  private final RLangEditor editor;
  private Process sketchServiceRunner;
  private SketchService sketchService;

  // If someone tries to run a sketch and, for some reason, there's no sketch runner,
  // remember the request and honor it when the sketch runner exists.
  private volatile Runnable pendingSketchRequest = null;

  private static final FilenameFilter JARS = new FilenameFilter() {
    @Override
    public boolean accept(final File dir, final String name) {
      return name.endsWith(".jar");
    }
  };

  private static void log(final String msg) {
    if (VERBOSE) {
      System.err.println(SketchServiceRunner.class.getSimpleName() + ": " + msg);
    }
  }

  public SketchServiceRunner(final RLangMode mode, final RLangEditor editor) {
    this.mode = mode;
    this.editor = editor;
    start();
  }

  /**
   * This constructor should only be used while instrumenting or debugging the {@link SketchRunner},
   * in which case it has already been started in the debugger or such like.
   */
  SketchServiceRunner(final RLangMode mode, final RLangEditor editor,
      final SketchService runningService) {
    this.mode = mode;
    this.editor = editor;
    this.sketchService = runningService;
  }

  public void start() {
    log("Starting sketch runner process.");
    final ProcessBuilder pb = createServerCommand();
    pb.inheritIO();
    log("Running:\n" + pb.command());
    try {
      sketchServiceRunner = pb.start();
    } catch (final IOException exception) {
      Messages.showError("RLang Mode Error", "Cannot start rlang sketch runner.", exception);
    }
  }

  private ProcessBuilder createServerCommand() {
    final List<String> command = new ArrayList<>();

    command.add(Platform.getJavaPath());

    if (Preferences.getBoolean("run.options.memory")) {
      command.add("-Xms" + Preferences.get("run.options.memory.initial") + "m");
      command.add("-Xmx" + Preferences.get("run.options.memory.maximum") + "m");
    }

    if (Platform.isMacOS()) {
      // Suppress dock icon.
      command.add("-Dapple.awt.UIElement=true");
      command.add("-Xdock:name=Processing");
    }

    if (VERBOSE) {
      command.add("-Dverbose=true");
    }

    command.add("-Djava.library.path=" + System.getProperty("java.library.path"));

    final List<String> cp = new ArrayList<>();
    cp.addAll(filter(
        Arrays
            .asList(System.getProperty("java.class.path").split(Pattern.quote(File.pathSeparator))),
        not(or(
            containsPattern(
                "(ant|ant-launcher|antlr|netbeans.*|osgi.*|jdi.*|ibm\\.icu.*|jna)\\.jar$"),
            containsPattern("/processing/app/(test|lib)/")))));
    for (final File jar : new File(Platform.getContentFile("core"), "library").listFiles(JARS)) {
      cp.add(jar.getAbsolutePath());
    }
    final File[] libJars = mode.getContentFile("mode").getAbsoluteFile().listFiles(JARS);
    if (libJars != null) {
      for (final File jar : libJars) {
        cp.add(jar.getAbsolutePath());
      }
    } else {
      log("No library jars found; I assume we're running in Eclipse.");
    }
    command.add("-cp");
    command.add(Joiner.on(File.pathSeparator).join(cp));

    // enable assertions
    command.add("-ea");

    // Run the SketchRunner main.
    command.add(SketchRunner.class.getName());

    // Give the runner its ID as an argument.
    command.add(editor.getId());

    return new ProcessBuilder(command);
  }

  private void handleRemoteException(final RemoteException exception) throws SketchException {
    final Throwable cause = exception.getCause();
    if (cause instanceof SocketTimeoutException || cause instanceof ConnectException) {
      log("SketchRunner either hung or not there. Restarting it.");
      restartServerProcess();
    } else {
      throw new SketchException(exception.getMessage());
    }
  }

  public void handleReady(final SketchService service) {
    log("handleReady()");
    sketchService = service;
    log("Successfully bound SketchRunner stub.");
    final Runnable req = pendingSketchRequest;
    pendingSketchRequest = null;
    if (req != null) {
      req.run();
    }
  }

  public void handleSketchStopped() {
    log("Sketch stopped.");
    editor.deactivateRun();
  }

  public void runSketch(final PdeSketch sketch) throws SketchException {
    // Create a pending request in case of various failure modes.
    pendingSketchRequest = new Runnable() {
      @Override
      public void run() {
        try {
          runSketch(sketch);
        } catch (final SketchException exception) {
          editor.statusError(exception);
        }
      }
    };
    if (sketchService == null) {
      log("Sketch service not running. Leaving pending request to run sketch.");
      restartServerProcess();
      return;
    }
    try {
      sketchService.startSketch(sketch);
      // If and only if we've successully request a sketch start, nuke the pending request.
      pendingSketchRequest = null;
      return;
    } catch (final RemoteException exception) {
      handleRemoteException(exception);
      log("Leaving pending request to run sketch.");
    }
  }

  public void stopSketch() throws SketchException {
    if (sketchService == null) {
      log("Sketch runner apparently not running; can't stop sketch.");
      handleSketchStopped();
      restartServerProcess();
      return;
    }
    try {
      sketchService.stopSketch();
    } catch (final RemoteException exception) {
      handleRemoteException(exception);
    }
  }

  public void shutdown() {
    if (sketchService != null) {
      log("Telling sketch runner to shutdown.");
      try {
        sketchService.shutdown();
      } catch (final RemoteException exception) {
        log(exception.toString());
      }
    }
    if (sketchServiceRunner != null) {
      log("Killing sketch runner process.");
      sketchServiceRunner.destroy();
      log("Waiting for sketch runner process to exit.");
      try {
        sketchServiceRunner.waitFor();
        log("Sketcher runner process exited normally.");
      } catch (final InterruptedException exception) {
        log("Interrupted while waiting for sketch runner to exit.");
      }
      sketchServiceRunner = null;
    }
  }

  private void restartServerProcess() {
    shutdown();
    start();
  }

  public void printStdOut(final String s) {
    editor.printOut(s);
  }

  public void printStdErr(final String s) {
    editor.printErr(s);
  }

  public void handleSketchException(final Exception exception) {
    editor.statusError(exception);
  }

  public void handleSketchMoved(final Point leftTop) {
    editor.setSketchLocation(leftTop);
  }
}
