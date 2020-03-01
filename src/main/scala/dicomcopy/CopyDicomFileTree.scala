package dicomcopy

import java.io.IOException
import java.nio.file.{Files, Path, Paths}

import org.apache.commons.cli.{CommandLine, CommandLineParser, DefaultParser, Options}


object CopyDicomFileTree {

  def main(args: Array[String]): Unit = {

    // Parse command line options
    val options: Options = CliOptions.options
    val parser: CommandLineParser = new DefaultParser
    val cmd: CommandLine = parser.parse(options, args)

    // Return String value of single CLI option argument
    def getCommandOptionValue(opt: String): String =
      if (cmd.hasOption(opt)) cmd.getOptionValue(opt) else ""

    // Return Array[String] values of multiple CLI option arguments
    def getCommandOptionValues(opt: String): Array[String] =
      if (cmd.hasOption(opt)) cmd.getOptionValues(opt) else Array()

    // Capture command line arguments
    val dirSourceStr: String = getCommandOptionValue("s")
    val dirTargetStr: String = getCommandOptionValue("t")
    val dicomSubDirs: String = getCommandOptionValues("d").mkString("|")
    val dicomBottomDir: String = getCommandOptionValue("b")
    val dicomSeriesDescrips: String = getCommandOptionValues("q").mkString("|")

    // Get Path objects from CLI argument values
    val dirSource: Path = Paths.get(dirSourceStr)
    val dirTarget: Path = Paths.get(dirTargetStr)

    // Create dicomcopy.CopyFiles object to pass to walkFileTree method
    val cf: CopyFiles = new CopyFiles(dirSource, dirTarget, dicomSubDirs, dicomBottomDir, dicomSeriesDescrips)

    // Walk file tree with cf object
    try Files.walkFileTree(dirSource, cf)
    catch {
      case e: IOException => System.err.format("IOException: %s%n", e)
    }

  }

}
