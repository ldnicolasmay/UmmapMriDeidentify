package dicomcopy

import java.nio.file.LinkOption.NOFOLLOW_LINKS
import java.nio.file.StandardCopyOption.{COPY_ATTRIBUTES, REPLACE_EXISTING}
import java.nio.file.{CopyOption, Path, Paths}
import java.util.Calendar
import java.util.concurrent.Callable
import picocli.CommandLine
import picocli.CommandLine.{Command, Option, Parameters}


@Command(
  name = "java -jar UmmapMriTreeScratch.jar",
  mixinStandardHelpOptions = true,
  version = Array("ConstructTree 0.1"),
  description = Array("Builds a tree from a source directory."),
  sortOptions = false,
  showDefaultValues = true,
  headerHeading = "Usage:%n",
  synopsisHeading = "%n",
  descriptionHeading = "%nDescription:%n%n",
  parameterListHeading = "%nParameters:%n",
  optionListHeading = "%nOptions:%n"
)
class ConstructTree extends Callable[Int] {

  @Parameters(index = "0", description = Array("Source directory"), paramLabel = "SOURCE_DIR")
  var sourceDirStr: String = _

  @Parameters(index = "1", description = Array("Target directory"), paramLabel = "TARGET_DIR")
  var targetDirStr: String = _

  @Option(
    names = Array("-i", "--intermed-dirs-regex"),
    arity = "1..*",
    description = Array("Regex string of allowable intermediate directories"),
    paramLabel = "INTERMED_DIRS_REGEX",
    required = false,
    defaultValue = "^hlp17umm\\d{5}_\\d{5}$|^dicom$|^s\\d{5}$"
  )
  var intermedDirsRegexArray: Array[String] = _

  @Option(
    names = Array("-d", "--dicom-file-regex"),
    arity = "1..*",
    description = Array("Regex string of allowable DICOM file names"),
    paramLabel = "DICOM_FILE_REGEX",
    required = false,
    defaultValue = "^i\\d+\\.MRDC\\.\\d+$"
  )
  var dicomFileRegexArray: Array[String] = _

  @Option(
    names = Array("-s", "--series-description-regex"),
    arity = "1..*",
    description = Array("Regex"),
    paramLabel = "SERIES_DESCRIPTION_REGEX",
    required = false,
    defaultValue = "^t1sag.*$|^t2flairsag.*$"
  )
  var seriesDescriptionRegexArray: Array[String] = _

  @Option(
    names = Array("-v", "--verbose"),
    description = Array("Verbose output"),
    paramLabel = "VERBOSE"
  )
  var verbose: Boolean = _

  @Option(
    names = Array("-t", "--print-file-trees"),
    description = Array("Print file trees"),
    paramLabel = "PRINT_FILE_TREES"
  )
  var printFileTrees: Boolean = _

  @throws(classOf[Exception])
  override def call(): Int = {

    // TODO Comment on actions in this driver object

    val startTS: Long = Calendar.getInstance().getTimeInMillis

    val sourceDirPath: Path = Paths.get(sourceDirStr)
    val targetDirPath: Path = Paths.get(targetDirStr)
    val definepathTS: Long = Calendar.getInstance().getTimeInMillis

    val intermedDirsRegex: String =
      sourceDirPath.getFileName.toString + "|" + intermedDirsRegexArray.mkString(sep = "|")
    val dicomFileRegex: String =
      dicomFileRegexArray.mkString(sep = "|")
    val seriesDescriptionRegex: String =
      seriesDescriptionRegexArray.mkString(sep = "|")
    val defineregexTS: Long = Calendar.getInstance().getTimeInMillis

    val sourceDirNode = DirNode(sourceDirPath, 0, intermedDirsRegex, dicomFileRegex)
    val sourcedirnodeTS: Long = Calendar.getInstance().getTimeInMillis

    val targetDirNode = DirNode(targetDirPath, 0, intermedDirsRegex, dicomFileRegex)
    val targetdirnodeTS: Long = Calendar.getInstance().getTimeInMillis

    val sourceDirNodeFiltered: DirNode =
      sourceDirNode
        .filterChildDirNodesWith(intermedDirNameFilter(intermedDirsRegex)(_))
        .filterChildFileNodesWith(dicomFileFilter(dicomFileRegex)(_))
        .filterChildDirNodesWith(numberOfFilesFilter(210)(_))
        .filterChildFileNodesWith(dicomFileT1T2Filter(dicomFileRegex, seriesDescriptionRegex)(_))
        .filterChildDirNodesWith(nonemptyDirNodesFilter(_))
    val sourcedirnodefilteredTS: Long = Calendar.getInstance().getTimeInMillis

    val targetDirNodeWithSourceRoot: DirNode =
      targetDirNode
        .substituteRootNodeName(
          targetDirNode.dirPath.getFileName.toString,
          sourceDirNode.dirPath.getFileName.toString
        )
    val targetdirnodewithsourcerootTS: Long = Calendar.getInstance().getTimeInMillis

    val sourceDirNodeFilteredMinusTargetNodeWithSourceRoot =
      sourceDirNodeFiltered
        .filterNotChildFileNodesWith(childFileNodeExistsIn2(targetDirNodeWithSourceRoot)(_))
        .filterChildDirNodesWith(nonemptyDirNodesFilter(_))
    val sourcedirnodefilteredminustargtenodewithsourcerootTS: Long = Calendar.getInstance().getTimeInMillis

    if (printFileTrees) {
      println("targetDirNodeWithSourceRoot:")
      targetDirNodeWithSourceRoot.printNode()
      println()

      println("sourceDirNodeFilteredMinusTargetNodeWithSourceRoot:")
      sourceDirNodeFilteredMinusTargetNodeWithSourceRoot.printNode()
      println()
    }

    // TODO Create command-line options for controlling overwriting and attribute-copying
    val copyOptions: Seq[CopyOption] =
      Seq(REPLACE_EXISTING, COPY_ATTRIBUTES, NOFOLLOW_LINKS)
    val fileCopier = new FileCopier(sourceDirPath, targetDirPath, copyOptions, verbose)

    sourceDirNodeFilteredMinusTargetNodeWithSourceRoot.copyNode(fileCopier)
    val copynodeTS: Long = Calendar.getInstance().getTimeInMillis

    println("Performance (time in milliseconds)\n----------------------------------")
    println(s"Define source and target directory paths:   " +
      s"${definepathTS - startTS}")
    println(s"Define regex strings:                       " +
      s"${defineregexTS - definepathTS}")
    println(s"Build source directory tree:                " +
      s"${sourcedirnodeTS - defineregexTS}")
    println(s"Build target directory tree:                " +
      s"${targetdirnodeTS - sourcedirnodeTS}")
    println(s"Filter source directory tree:               " +
      s"${sourcedirnodefilteredTS - targetdirnodeTS}")
    println(s"Replace target root with source root:       " +
      s"${targetdirnodewithsourcerootTS - sourcedirnodefilteredTS}")
    println(s"Subtract target tree from source tree:      " +
      s"${sourcedirnodefilteredminustargtenodewithsourcerootTS - targetdirnodewithsourcerootTS}")
    println(s"Copy only necessary nodes from source tree: " +
      s"${copynodeTS - sourcedirnodefilteredminustargtenodewithsourcerootTS}")
    println(s"                                            ------")
    println(s"Total runtime:                              ${copynodeTS - startTS}")

    0
  }
}

object ConstructTree extends App {
  val exitCode: Int = new CommandLine(new ConstructTree()).execute(args: _*)
  System.exit(exitCode)
}
