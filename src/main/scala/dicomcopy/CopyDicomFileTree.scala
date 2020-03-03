package dicomcopy

import java.io.IOException
import java.nio.file.{CopyOption, Files, Path, Paths, StandardCopyOption}
import java.nio.file.CopyOption
import java.nio.file.StandardCopyOption._

import com.pixelmed.dicom.{AttributeList, DicomException}
import java.nio.file.LinkOption._

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
    val preserve: Boolean = cmd.hasOption("p")
    val overwrite: Boolean = cmd.hasOption("o")

    // Get Path objects from CLI argument values
    val dirSource: Path = Paths.get(dirSourceStr)
    val dirTarget: Path = Paths.get(dirTargetStr)

    // Translate preserve and overwrite flags to copyOptions passed to CopyFiles object
    val copyOptions: Seq[CopyOption] =
      if (preserve && overwrite) Seq(REPLACE_EXISTING, COPY_ATTRIBUTES, NOFOLLOW_LINKS)
      else if (preserve && !overwrite) Seq(COPY_ATTRIBUTES, NOFOLLOW_LINKS)
      else if (!preserve && overwrite) Seq(REPLACE_EXISTING, NOFOLLOW_LINKS)
      else Seq(NOFOLLOW_LINKS)

    // Create dicomcopy.CopyFiles object to pass to walkFileTree method
    val cf: CopyFiles = new CopyFiles(
      dirSource, dirTarget, dicomSubDirs, dicomBottomDir, dicomSeriesDescrips, copyOptions
    )

    // Walk file tree with cf object
    try Files.walkFileTree(dirSource, cf)
    catch {
      case e: IOException => System.err.format("IOException: %s%n", e)
    }

    val dicomFile: Path = Paths.get("/home/hynso/MRI/consensus_downloads_temp/hlp17umm00732_03796/dicom/s00003/i1484278.MRDC.3")
    val attrList = new AttributeList
    try attrList.read(dicomFile.toString)
    catch {
      case e: DicomException =>
        System.err.println(s"DicomException: " +
          s"getAttributeListFromPath(${dicomFile.toString}): $e")
      case e: IOException =>
        System.err.println(s"IOException: " +
          s"getAttributeListFromPath(${dicomFile.toString}): $e")
    }
    println(attrList)

  }

}
