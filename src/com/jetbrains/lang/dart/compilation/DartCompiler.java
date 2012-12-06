package com.jetbrains.lang.dart.compilation;

import com.intellij.compiler.options.CompileStepBeforeRun;
import com.intellij.execution.configurations.RunConfiguration;
import com.intellij.openapi.compiler.CompileContext;
import com.intellij.openapi.compiler.CompileTask;
import com.intellij.openapi.compiler.CompilerMessageCategory;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.module.Module;
import com.intellij.openapi.module.ModuleType;
import com.intellij.openapi.projectRoots.Sdk;
import com.intellij.openapi.roots.CompilerModuleExtension;
import com.intellij.openapi.roots.ModuleRootManager;
import com.intellij.openapi.util.SystemInfo;
import com.intellij.openapi.vfs.VfsUtil;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.jetbrains.lang.dart.DartBundle;
import com.jetbrains.lang.dart.analyzer.AnalyzerDriver;
import com.jetbrains.lang.dart.analyzer.AnalyzerMessage;
import com.jetbrains.lang.dart.ide.DartSdkType;
import com.jetbrains.lang.dart.ide.module.DartModuleType;
import com.jetbrains.lang.dart.ide.runner.server.DartCommandLineRunConfiguration;
import com.jetbrains.lang.dart.util.DartSdkUtil;
import org.jetbrains.annotations.NotNull;

import java.util.List;

public class DartCompiler implements CompileTask {
  private static final Logger LOG = Logger.getInstance("#com.intellij.plugins.dart.compilation.DartCompiler");

  @Override
  public boolean execute(CompileContext context) {
    final RunConfiguration runConfiguration = CompileStepBeforeRun.getRunConfiguration(context.getCompileScope());
    if (runConfiguration instanceof DartCommandLineRunConfiguration) {
      // always return true
      run(context, (DartCommandLineRunConfiguration)runConfiguration);
    }
    return true;
  }

  private static boolean run(CompileContext context, DartCommandLineRunConfiguration configuration) {
    final Module module = configuration.getConfigurationModule().getModule();
    if (ModuleType.get(module) != DartModuleType.getInstance()) {
      // skip
      return true;
    }
    if (module == null) {
      context.addMessage(CompilerMessageCategory.ERROR,
                         DartBundle.message("no.module.for.run.configuration", configuration.getName()), null, -1, -1);
      return false;
    }
    return compileModule(context, module, configuration);
  }

  private static boolean compileModule(CompileContext context,
                                       @NotNull Module module,
                                       @NotNull DartCommandLineRunConfiguration configuration) {

    final String filePath = configuration.getFilePath();
    if (filePath == null) {
      context.addMessage(CompilerMessageCategory.ERROR, DartBundle.message("no.file.specified", configuration.getName()), null, -1, -1);
      return false;
    }

    VirtualFile libraryRoot = VirtualFileManager.getInstance().findFileByUrl(VfsUtil.pathToUrl(filePath));
    if (libraryRoot == null) {
      context.addMessage(CompilerMessageCategory.ERROR, DartBundle.message("cannot.find.file", filePath), null, -1, -1);
      return false;
    }

    final ModuleRootManager moduleRootManager = ModuleRootManager.getInstance(module);
    final Sdk sdk = moduleRootManager.getSdk();

    if (sdk == null) {
      context.addMessage(CompilerMessageCategory.ERROR, DartBundle.message("no.sdk.for.module", module.getName()), null, -1, -1);
      return false;
    }

    if (sdk.getSdkType() != DartSdkType.getInstance()) {
      context.addMessage(CompilerMessageCategory.ERROR, DartBundle.message("not.dart.sdk.for.module", module.getName()), null, -1, -1);
      return false;
    }

    final String sdkExePath = DartSdkUtil.getCompilerPathByFolderPath(sdk.getHomePath());
    if (sdkExePath == null || sdkExePath.isEmpty()) {
      context.addMessage(CompilerMessageCategory.ERROR, DartBundle.message("invalid.dart.sdk.for.module", module.getName()), null, -1, -1);
      return false;
    }

    final String analyzerUrl =
      VfsUtil.pathToUrl(sdk.getHomePath()) + "/bin/" + (SystemInfo.isWindows ? "dart_analyzer.bat" : "dart_analyzer");

    final VirtualFile analyzerExecutable = VirtualFileManager.getInstance().findFileByUrl(analyzerUrl);
    if (analyzerExecutable == null) {
      // can't "compile"
      return true;
    }

    final String outputUrl = CompilerModuleExtension.getInstance(module).getCompilerOutputUrl();
    final AnalyzerDriver analyzerDriver = new AnalyzerDriver(module.getProject(), analyzerExecutable, sdk.getHomePath(), libraryRoot);
    List<AnalyzerMessage> messages = analyzerDriver.analyze(VfsUtil.urlToPath(outputUrl), false);

    if (messages != null && messages.isEmpty()) {
      return true;
    }

    if (messages != null && !messages.isEmpty()) {
      for (AnalyzerMessage message : messages) {
        context.addMessage(
          toCompilerCategory(message.getType()),
          message.getMessage(),
          message.getVirtualFile().getUrl(),
          message.getLine() + 1,
          message.getOffset() + 1
        );
      }
    }
    return false;
  }

  private static CompilerMessageCategory toCompilerCategory(AnalyzerMessage.Type type) {
    switch (type) {
      case INFO:
        return CompilerMessageCategory.INFORMATION;
      case WARNING:
        return CompilerMessageCategory.WARNING;
      case ERROR:
        return CompilerMessageCategory.ERROR;
    }
    return CompilerMessageCategory.INFORMATION;
  }
}

