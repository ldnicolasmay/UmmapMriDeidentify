package dicomcopy

import java.io.IOException
import java.nio.file.{Files, Paths, Path}
import org.apache.commons.cli.{Options, CommandLineParser, DefaultParser, CommandLine}

object CopyDicomFileTree {

  def main(args: Array[String]): Unit = {

    // Parse command line options
    val options: Options = CliOptions.options
    val parser: CommandLineParser = new DefaultParser
    val cmd: CommandLine = parser.parse(options, args)

    // Capture command line arguments
    val dirSourceStr: String =
      if (cmd.hasOption("s")) cmd.getOptionValue("s") else null
    val dirTargetStr: String =
      if (cmd.hasOption("t")) cmd.getOptionValue("t") else null

    val dicomSubDirs: String =
       if (cmd.hasOption("d")) cmd.getOptionValues("d").mkString("|") else null
    val dicomBottomDir: String =
      if (cmd.hasOption("b")) cmd.getOptionValue("b") else null

    val dicomSeriesDescrips: String =
       if (cmd.hasOption("q")) cmd.getOptionValues("q").mkString("|") else null

    // Get Path objects from CLI argument Strings
    val dirSource: Path = Paths.get(dirSourceStr)
    val dirTarget: Path = Paths.get(dirTargetStr)

    // Create CopyFiles object to pass to walkFileTree method
    val cf: CopyFiles = new CopyFiles(dirSource, dirTarget, dicomSubDirs, dicomBottomDir, dicomSeriesDescrips)

    // Walk file tree with cf object
    try Files.walkFileTree(dirSource, cf)
    catch {
      case e: IOException => System.err.format("IOException: %s%n", e)
    }

  }

}
