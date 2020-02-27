package dicomcopy

import java.io.IOException
import java.nio.file.{Files, Paths, Path}
//import org.apache.commons.cli._
import org.apache.commons.cli.{
  Options, Option, CommandLineParser, DefaultParser, CommandLine, HelpFormatter
}

object CopyDicomFileTree {

  def main(args: Array[String]): Unit = {

    // Define command line options
    val options: Options = new Options

    // Add help option
    val helpOpt: Option = Option.builder("h")
      .longOpt("help")
      .desc("print help")
      .build
    options.addOption(helpOpt)

    // Add source directory option
    val dirSourceOpt: Option = Option.builder("s")
      .required
      .longOpt("source")
      .desc("source directory")
      .hasArg
      .argName("SOURCEDIR")
      .build
    options.addOption(dirSourceOpt)

    // Add target directory option
    val dirTargetOpt: Option = Option.builder("t")
      .required
      .longOpt("target")
      .desc("target directory")
      .hasArg
      .argName("TARGETDIR")
      .build
    options.addOption(dirTargetOpt)

    // Add dicom sub-directories option
    val dicomSubDirsOpt: Option = Option.builder("d")
      .required
      .longOpt("subdirs")
      .desc("dicom subdirectories regex strings")
      .hasArgs
      .argName("DICOMSUBDIRS")
      .build
    options.addOption(dicomSubDirsOpt)

    // Add dicom bottom directory option
    val dicomBottomDirOpt: Option = Option.builder("b")
      .required
      .longOpt("bottomdir")
      .desc("dicom bottom directory regex string")
      .hasArg
      .argName("DICOMBOTTOMDIR")
      .build
    options.addOption(dicomBottomDirOpt)

    val header: String = "Do something useful with an input file\n\n"
    val footer: String = "\nPlease report issues at http://example.com/issues"
    val formatter: HelpFormatter = new HelpFormatter
    formatter.printHelp("java -jar UmmapMriDeidentify.jar", header, options, footer, true)

    // Parse command line options
    val parser: CommandLineParser = new DefaultParser
    val cmd: CommandLine = parser.parse(options, args)

    val dicomSubDirs: Array[String] =
      if (cmd.hasOption("d")) cmd.getOptionValues("d") else null
    val dicomBottomDir: String =
      if (cmd.hasOption("b")) cmd.getOptionValue("b") else null

    val dirSourceStr: String =
      if (cmd.hasOption("s")) cmd.getOptionValue("s") else null
    val dirTargetStr: String =
      if (cmd.hasOption("t")) cmd.getOptionValue("t") else null

    val dirSource: Path = Paths.get(dirSourceStr)
    val dirTarget: Path = Paths.get(dirTargetStr)

    val cf: CopyFiles = new CopyFiles(dirSource, dirTarget, dicomSubDirs, dicomBottomDir)

    try Files.walkFileTree(dirSource, cf)
    catch {
      case e: IOException => System.err.format("IOException: %s%n", e)
    }

  }

}
